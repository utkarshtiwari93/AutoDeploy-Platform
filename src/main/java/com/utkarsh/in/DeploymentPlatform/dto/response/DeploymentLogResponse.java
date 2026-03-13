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
public class DeploymentLogResponse {
    private Long id;
    private Long deploymentId;
    private String message;
    private String logLevel;
    private LocalDateTime createdAt;
}