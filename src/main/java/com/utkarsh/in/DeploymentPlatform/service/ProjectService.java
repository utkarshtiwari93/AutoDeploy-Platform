package com.utkarsh.in.DeploymentPlatform.service;

import com.utkarsh.in.DeploymentPlatform.dto.request.ProjectRequest;
import com.utkarsh.in.DeploymentPlatform.dto.response.ProjectResponse;
import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.entity.Project;
import com.utkarsh.in.DeploymentPlatform.entity.User;
import com.utkarsh.in.DeploymentPlatform.exception.ResourceNotFoundException;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentRepository;
import com.utkarsh.in.DeploymentPlatform.repository.ProjectRepository;
import com.utkarsh.in.DeploymentPlatform.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentRepository;
import com.utkarsh.in.DeploymentPlatform.service.DockerService;
import com.utkarsh.in.DeploymentPlatform.service.NginxService;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;
    private final NginxService nginxService;
    private final AuthUtil authUtil;


    public List<ProjectResponse> getAllProjects() {
        Long userId = authUtil.getCurrentUserId();
        return projectRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProjectById(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));
        return toResponse(project);
    }

    public ProjectResponse createProject(ProjectRequest request) {
        User currentUser = authUtil.getCurrentUser();

        Project project = Project.builder()
                .user(currentUser)
                .name(request.getName())
                .repoUrl(request.getRepoUrl())
                .status("INACTIVE")
                .build();

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        project.setName(request.getName());
        project.setRepoUrl(request.getRepoUrl());

        Project updated = projectRepository.save(project);
        return toResponse(updated);
    }

    public void deleteProject(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        // Stop and clean up all deployments for this project
        List<Deployment> deployments = deploymentRepository
                .findByProjectId(projectId);

        for (Deployment deployment : deployments) {
            if (deployment.getContainerId() != null) {
                try {
                    if (dockerService.isContainerRunning(deployment.getContainerId())) {
                        dockerService.stopContainer(deployment.getContainerId());
                    }
                    dockerService.removeContainer(deployment.getContainerId());
                } catch (Exception e) {
                    log.warn("Could not remove container {} during project delete: {}",
                            deployment.getContainerId(), e.getMessage());
                }
            }

            if (deployment.getImageId() != null) {
                try {
                    dockerService.removeImage(deployment.getImageId());
                } catch (Exception e) {
                    log.warn("Could not remove image {} during project delete: {}",
                            deployment.getImageId(), e.getMessage());
                }
            }

            try {
                nginxService.removeProxyConfig(deployment.getId());
            } catch (Exception e) {
                log.warn("Could not remove Nginx config for deployment {}: {}",
                        deployment.getId(), e.getMessage());
            }
        }

        projectRepository.delete(project);
        log.info("Project {} and all its deployments cleaned up and deleted", projectId);
    }

    private ProjectResponse toResponse(Project project) {
        String latestStatus = null;
        String publicUrl = null;

        List<Deployment> deployments = deploymentRepository
                .findByProjectIdOrderByDeployedAtDesc(project.getId());

        if (!deployments.isEmpty()) {
            Deployment latest = deployments.get(0);
            latestStatus = latest.getStatus().name();
            publicUrl = latest.getPublicUrl();
        }

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .repoUrl(project.getRepoUrl())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .userId(project.getUser().getId())
                .latestDeploymentStatus(latestStatus)
                .publicUrl(publicUrl)
                .build();
    }
}