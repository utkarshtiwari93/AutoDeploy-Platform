package com.utkarsh.in.DeploymentPlatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cleanup")
@Getter
@Setter
public class CleanupProperties {
    private boolean enabled;
    private int retentionDays;
    private String buildDir;
}