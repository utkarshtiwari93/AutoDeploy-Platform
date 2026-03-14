package com.utkarsh.in.DeploymentPlatform.service;

import com.utkarsh.in.DeploymentPlatform.config.CleanupProperties;
import com.utkarsh.in.DeploymentPlatform.config.DockerProperties;
import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupScheduler {

    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;
    private final NginxService nginxService;
    private final CleanupProperties cleanupProperties;
    private final DockerProperties dockerProperties;

    // Runs every hour
    @Scheduled(cron = "0 0 * * * *")
    public void runHourlyCleanup() {
        if (!cleanupProperties.isEnabled()) {
            log.info("Cleanup is disabled — skipping");
            return;
        }

        log.info("====== Starting hourly cleanup ======");
        cleanupOldDeployments();
        cleanupOrphanedBuildDirs();
        log.info("====== Hourly cleanup complete ======");
    }

    // Runs every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyImagePrune() {
        if (!cleanupProperties.isEnabled()) return;

        log.info("====== Starting daily Docker image prune ======");
        pruneDockerImages();
        log.info("====== Daily image prune complete ======");
    }

    public void cleanupOldDeployments() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(cleanupProperties.getRetentionDays());

        List<DeploymentStatus> statusesToClean = List.of(
                DeploymentStatus.FAILED,
                DeploymentStatus.SUPERSEDED,
                DeploymentStatus.STOPPED
        );

        List<Deployment> oldDeployments = deploymentRepository
                .findOldDeploymentsByStatuses(statusesToClean, cutoff);

        log.info("Found {} old deployments to clean up", oldDeployments.size());

        for (Deployment deployment : oldDeployments) {
            cleanupSingleDeployment(deployment);
        }
    }

    public void cleanupSingleDeployment(Deployment deployment) {
        log.info("Cleaning up deployment {} (status: {}, age: {})",
                deployment.getId(),
                deployment.getStatus(),
                deployment.getUpdatedAt());

        // Stop and remove container if still exists
        if (deployment.getContainerId() != null) {
            try {
                if (dockerService.isContainerRunning(deployment.getContainerId())) {
                    dockerService.stopContainer(deployment.getContainerId());
                    log.info("Stopped container: {}", deployment.getContainerId());
                }
                dockerService.removeContainer(deployment.getContainerId());
                log.info("Removed container: {}", deployment.getContainerId());
            } catch (Exception e) {
                log.warn("Could not remove container {}: {}",
                        deployment.getContainerId(), e.getMessage());
            }
        }

        // Remove Docker image
        if (deployment.getImageId() != null) {
            try {
                dockerService.removeImage(deployment.getImageId());
                log.info("Removed image: {}", deployment.getImageId());
            } catch (Exception e) {
                log.warn("Could not remove image {} (may still be in use): {}",
                        deployment.getImageId(), e.getMessage());
            }
        }

        // Remove Nginx config
        try {
            nginxService.removeProxyConfig(deployment.getId());
        } catch (Exception e) {
            log.warn("Could not remove Nginx config for deployment {}: {}",
                    deployment.getId(), e.getMessage());
        }

        log.info("Cleanup complete for deployment {}", deployment.getId());
    }

    public void cleanupOrphanedBuildDirs() {
        String buildDirPath = cleanupProperties.getBuildDir();

        if (buildDirPath == null || buildDirPath.isBlank()) {
            log.warn("Cleanup build-dir is not configured — skipping orphaned dir cleanup");
            return;
        }

        File buildRoot = new File(buildDirPath);

        if (!buildRoot.exists() || !buildRoot.isDirectory()) {
            log.info("Build directory does not exist, skipping: {}", buildRoot.getAbsolutePath());
            return;
        }

        File[] subdirs = buildRoot.listFiles(File::isDirectory);
        if (subdirs == null || subdirs.length == 0) {
            log.info("No orphaned build directories found");
            return;
        }

        log.info("Checking {} build directories for cleanup", subdirs.length);

        for (File dir : subdirs) {
            try {
                Long deploymentId = Long.parseLong(dir.getName());
                boolean deploymentExists = deploymentRepository.existsById(deploymentId);

                if (!deploymentExists) {
                    FileUtils.deleteDirectory(dir);
                    log.info("Deleted orphaned build directory: {}", dir.getAbsolutePath());
                }
            } catch (NumberFormatException e) {
                log.warn("Unexpected directory in build root: {}", dir.getName());
            } catch (IOException e) {
                log.warn("Could not delete build directory {}: {}",
                        dir.getAbsolutePath(), e.getMessage());
            }
        }
    }

    public void pruneDockerImages() {
        try {
            List<Deployment> allDeployments = deploymentRepository.findAll();

            for (Deployment deployment : allDeployments) {
                DeploymentStatus status = deployment.getStatus();
                boolean isOld = deployment.getUpdatedAt()
                        .isBefore(LocalDateTime.now()
                                .minusDays(cleanupProperties.getRetentionDays()));

                if (isOld && deployment.getImageId() != null &&
                        (status == DeploymentStatus.FAILED ||
                                status == DeploymentStatus.SUPERSEDED ||
                                status == DeploymentStatus.STOPPED)) {

                    try {
                        dockerService.removeImage(deployment.getImageId());
                        log.info("Pruned old image: {} for deployment {}",
                                deployment.getImageId(), deployment.getId());
                    } catch (Exception e) {
                        log.warn("Could not prune image {}: {}",
                                deployment.getImageId(), e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error during image prune: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("====== Application shutting down — cleanup check ======");
        log.info("Running containers will continue running in Docker.");
        log.info("They will be managed on next startup.");
        log.info("====== Shutdown cleanup complete ======");
    }
}