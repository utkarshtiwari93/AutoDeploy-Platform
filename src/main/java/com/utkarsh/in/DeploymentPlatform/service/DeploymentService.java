package com.utkarsh.in.DeploymentPlatform.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.utkarsh.in.DeploymentPlatform.config.DockerProperties;
import com.utkarsh.in.DeploymentPlatform.dto.response.DeploymentLogResponse;
import com.utkarsh.in.DeploymentPlatform.dto.response.DeploymentResponse;
import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.entity.DeploymentLog;
import com.utkarsh.in.DeploymentPlatform.entity.Project;
import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import com.utkarsh.in.DeploymentPlatform.exception.ConflictException;
import com.utkarsh.in.DeploymentPlatform.exception.ResourceNotFoundException;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentLogRepository;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentRepository;
import com.utkarsh.in.DeploymentPlatform.repository.ProjectRepository;
import com.utkarsh.in.DeploymentPlatform.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final ProjectRepository projectRepository;
    private final DockerClient dockerClient;
    private final DockerProperties dockerProperties;
    private final AuthUtil authUtil;
    private final DockerService dockerService;


    public DeploymentResponse initiateDeploy(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        boolean alreadyRunning = deploymentRepository.existsByProjectIdAndStatusIn(
                projectId,
                List.of(
                        DeploymentStatus.QUEUED,
                        DeploymentStatus.CLONING,
                        DeploymentStatus.BUILDING,
                        DeploymentStatus.STARTING
                )
        );

        if (alreadyRunning) {
            throw new ConflictException(
                    "A deployment is already in progress for project: " + project.getName());
        }

        String imageTag = "app-" + projectId + "-" + UUID.randomUUID().toString().substring(0, 8);

        Deployment deployment = Deployment.builder()
                .project(project)
                .status(DeploymentStatus.QUEUED)
                .imageTag(imageTag)
                .build();

        Deployment saved = deploymentRepository.save(deployment);

        runDeploymentPipeline(saved.getId(), project.getRepoUrl(), imageTag);

        return toResponse(saved);
    }

    @Async("deploymentExecutor")
    public void runDeploymentPipeline(Long deploymentId, String repoUrl, String imageTag) {
        log.info("Starting deployment pipeline for deploymentId={}", deploymentId);

        String buildPath = dockerProperties.getBuildDir() + "/" + deploymentId;
        File buildDir = new File(buildPath);

        try {
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment not found"));

            // Step 1 - Clone
            updateStatus(deployment, DeploymentStatus.CLONING);
            saveLog(deployment, "Starting git clone from: " + repoUrl, "INFO");

            boolean cloneSuccess = cloneRepository(repoUrl, buildPath, deployment);
            if (!cloneSuccess) {
                failDeployment(deployment, "Git clone failed. Check the repository URL.");
                return;
            }

            saveLog(deployment, "Repository cloned successfully", "INFO");

            // Step 2 - Validate Dockerfile exists
            File dockerfile = new File(buildPath + "/Dockerfile");
            if (!dockerfile.exists()) {
                failDeployment(deployment,
                        "ERROR: No Dockerfile found at repository root. " +
                                "Please add a Dockerfile to your project.");
                return;
            }

            saveLog(deployment, "Dockerfile found — starting Docker build", "INFO");

            // Step 3 - Build image
            updateStatus(deployment, DeploymentStatus.BUILDING);

            boolean buildSuccess = buildDockerImage(buildDir, imageTag, deployment);
            if (!buildSuccess) {
                failDeployment(deployment, "Docker image build failed. Check your Dockerfile.");
                return;
            }

            saveLog(deployment, "Docker image built successfully: " + imageTag, "INFO");
            updateStatus(deployment, DeploymentStatus.BUILD_COMPLETE);

// Step 4 - Start container
            updateStatus(deployment, DeploymentStatus.STARTING);
            saveLog(deployment, "Finding available port...", "INFO");

            int hostPort = dockerService.findAvailablePort();
            saveLog(deployment, "Assigned port: " + hostPort, "INFO");
            saveLog(deployment, "Starting container from image: " + imageTag, "INFO");

            String containerId = dockerService.runContainer(imageTag, deploymentId, hostPort);

            String publicUrl = "http://localhost:" + hostPort;

            deployment.setContainerId(containerId);
            deployment.setHostPort(hostPort);
            deployment.setPublicUrl(publicUrl);
            deploymentRepository.save(deployment);

            saveLog(deployment, "Container started successfully: " + containerId, "INFO");
            saveLog(deployment, "Application is live at: " + publicUrl, "INFO");
            updateStatus(deployment, DeploymentStatus.RUNNING);

// Update project status to ACTIVE
            Project project = deployment.getProject();
            project.setStatus("ACTIVE");
            projectRepository.save(project);

            log.info("Deployment {} is RUNNING at {}", deploymentId, publicUrl);

        } catch (Exception e) {
            log.error("Unexpected error in deployment pipeline: {}", e.getMessage(), e);
            try {
                Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
                if (deployment != null) {
                    failDeployment(deployment, "Unexpected error: " + e.getMessage());
                }
            } catch (Exception ex) {
                log.error("Could not update deployment status to FAILED", ex);
            }
        } finally {
            cleanupBuildDir(buildDir);
        }
    }

    private boolean cloneRepository(String repoUrl, String targetPath, Deployment deployment) {
        try {
            List<String> command = List.of("git", "clone", repoUrl, targetPath);

            saveLog(deployment, "Running: git clone " + repoUrl, "INFO");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        saveLog(deployment, trimmed, "INFO");
                        log.info("[CLONE] {}", trimmed);
                    }
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                saveLog(deployment, "Git clone timed out after 5 minutes", "ERROR");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                saveLog(deployment, "Git clone failed with exit code: " + exitCode, "ERROR");
                return false;
            }

            return true;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            saveLog(deployment, "Git clone error: " + e.getMessage(), "ERROR");
            return false;
        }
    }

    private boolean buildDockerImage(File buildDir, String imageTag, Deployment deployment) {
        try {
            final boolean[] success = {true};

            dockerClient.buildImageCmd(buildDir)
                    .withTags(Set.of(imageTag))
                    .withNoCache(false)
                    .exec(new BuildImageResultCallback() {

                        @Override
                        public void onNext(BuildResponseItem item) {
                            if (item.getStream() != null) {
                                String line = item.getStream().trim();
                                if (!line.isEmpty()) {
                                    saveLog(deployment, line, "INFO");
                                    log.info("[BUILD] {}", line);
                                }
                            }
                            if (item.isErrorIndicated()) {
                                String error = item.getErrorDetail() != null
                                        ? item.getErrorDetail().getMessage()
                                        : "Unknown build error";
                                saveLog(deployment, "BUILD ERROR: " + error, "ERROR");
                                log.error("[BUILD ERROR] {}", error);
                                success[0] = false;
                            }

                            // Save the image ID when build completes
                            if (item.isBuildSuccessIndicated() && item.getImageId() != null) {
                                deployment.setImageId(item.getImageId());
                                deploymentRepository.save(deployment);
                            }

                            super.onNext(item);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            saveLog(deployment,
                                    "Build stream error: " + throwable.getMessage(), "ERROR");
                            success[0] = false;
                            super.onError(throwable);
                        }
                    })
                    .awaitCompletion(
                            dockerProperties.getBuildTimeoutMinutes(),
                            TimeUnit.MINUTES
                    );

            return success[0];

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            saveLog(deployment, "Docker build interrupted: " + e.getMessage(), "ERROR");
            return false;
        } catch (Exception e) {
            saveLog(deployment, "Docker build exception: " + e.getMessage(), "ERROR");
            return false;
        }
    }

    private void updateStatus(Deployment deployment, DeploymentStatus status) {
        deployment.setStatus(status);
        deploymentRepository.save(deployment);
        log.info("Deployment {} status -> {}", deployment.getId(), status);
    }

    private void failDeployment(Deployment deployment, String reason) {
        saveLog(deployment, reason, "ERROR");
        updateStatus(deployment, DeploymentStatus.FAILED);
        log.error("Deployment {} FAILED: {}", deployment.getId(), reason);
    }

    public void saveLog(Deployment deployment, String message, String level) {
        try {
            DeploymentLog logEntry = DeploymentLog.builder()
                    .deployment(deployment)
                    .message(message)
                    .logLevel(level)
                    .build();
            deploymentLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save deployment log: {}", e.getMessage());
        }
    }

    private void cleanupBuildDir(File buildDir) {
        try {
            if (buildDir.exists()) {
                FileUtils.deleteDirectory(buildDir);
                log.info("Cleaned up build directory: {}", buildDir.getAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("Could not clean up build directory {}: {}",
                    buildDir.getAbsolutePath(), e.getMessage());
        }
    }

    public List<DeploymentResponse> getDeploymentsByProject(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        return deploymentRepository
                .findByProjectIdOrderByDeployedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public DeploymentResponse getLatestDeployment(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        return deploymentRepository
                .findTopByProjectIdOrderByDeployedAtDesc(projectId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No deployments found for project: " + projectId));
    }

    public List<DeploymentLogResponse> getDeploymentLogs(Long projectId, Long deploymentId) {
        Long userId = authUtil.getCurrentUserId();
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        return deploymentLogRepository
                .findByDeploymentIdOrderByCreatedAtAsc(deploymentId)
                .stream()
                .map(this::toLogResponse)
                .collect(Collectors.toList());
    }

    private DeploymentResponse toResponse(Deployment d) {
        return DeploymentResponse.builder()
                .id(d.getId())
                .projectId(d.getProject().getId())
                .projectName(d.getProject().getName())
                .status(d.getStatus())
                .containerId(d.getContainerId())
                .hostPort(d.getHostPort())
                .imageTag(d.getImageTag())
                .imageId(d.getImageId())
                .publicUrl(d.getPublicUrl())
                .deployedAt(d.getDeployedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private DeploymentLogResponse toLogResponse(DeploymentLog l) {
        return DeploymentLogResponse.builder()
                .id(l.getId())
                .deploymentId(l.getDeployment().getId())
                .message(l.getMessage())
                .logLevel(l.getLogLevel())
                .createdAt(l.getCreatedAt())
                .build();
    }

    public DeploymentResponse restartDeployment(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        Deployment latest = deploymentRepository
                .findTopByProjectIdOrderByDeployedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No deployments found for project: " + projectId));

        if (latest.getContainerId() == null) {
            throw new RuntimeException("No container found for this deployment");
        }

        try {
            dockerService.restartContainer(latest.getContainerId());
            saveLog(latest, "Container restarted successfully", "INFO");
            updateStatus(latest, DeploymentStatus.RUNNING);
        } catch (Exception e) {
            saveLog(latest, "Restart failed: " + e.getMessage(), "ERROR");
            throw new RuntimeException("Failed to restart: " + e.getMessage(), e);
        }

        return toResponse(latest);
    }

    public DeploymentResponse redeployProject(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        // Stop and remove the old container if exists
        deploymentRepository
                .findTopByProjectIdOrderByDeployedAtDesc(projectId)
                .ifPresent(old -> {
                    if (old.getContainerId() != null) {
                        try {
                            saveLog(old, "Stopping old container for redeploy...", "INFO");
                            dockerService.stopContainer(old.getContainerId());
                            dockerService.removeContainer(old.getContainerId());
                            saveLog(old, "Old container removed", "INFO");
                        } catch (Exception e) {
                            log.warn("Could not stop old container: {}", e.getMessage());
                        }
                    }
                    old.setStatus(DeploymentStatus.SUPERSEDED);
                    deploymentRepository.save(old);
                });

        // Kick off a fresh deployment
        return initiateDeploy(projectId);
    }

    public void stopDeployment(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        Deployment latest = deploymentRepository
                .findTopByProjectIdOrderByDeployedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No deployments found for project: " + projectId));

        if (latest.getContainerId() != null) {
            dockerService.stopContainer(latest.getContainerId());
            saveLog(latest, "Container stopped", "INFO");
        }

        updateStatus(latest, DeploymentStatus.STOPPED);

        Project project = latest.getProject();
        project.setStatus("INACTIVE");
        projectRepository.save(project);
    }
}