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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
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

        projectRepository.delete(project);
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