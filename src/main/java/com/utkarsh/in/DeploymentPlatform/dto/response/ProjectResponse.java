package com.utkarsh.in.DeploymentPlatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String repoUrl;
    private String status;
    private LocalDateTime createdAt;
    private Long userId;
    private String latestDeploymentStatus;
    private String publicUrl;
}