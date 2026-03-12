package com.utkarsh.in.DeploymentPlatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 2, max = 100, message = "Project name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Repository URL is required")
    @Pattern(
            regexp = "^https://(github\\.com|gitlab\\.com|bitbucket\\.org)/.+/.+$",
            message = "Repository URL must be a valid GitHub, GitLab, or Bitbucket HTTPS URL"
    )
    private String repoUrl;
}