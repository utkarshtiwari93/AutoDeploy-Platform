package com.utkarsh.in.DeploymentPlatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.docker")
@Getter
@Setter
public class DockerProperties {
    private String socketPath;
    private int containerPort;
    private int hostPortRangeStart;
    private int hostPortRangeEnd;
    private int buildTimeoutMinutes;
    private String buildDir;
}