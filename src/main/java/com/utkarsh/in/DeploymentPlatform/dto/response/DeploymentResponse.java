package com.utkarsh.in.DeploymentPlatform.dto.response;

import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class DeploymentResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private DeploymentStatus status;
    private String containerId;
    private Integer hostPort;
    private String imageTag;
    private String imageId;
    private String publicUrl;
    private LocalDateTime deployedAt;
    private LocalDateTime updatedAt;
}