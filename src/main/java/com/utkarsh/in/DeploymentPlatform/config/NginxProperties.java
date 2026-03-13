package com.utkarsh.in.DeploymentPlatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.nginx")
@Getter
@Setter
public class NginxProperties {
    private boolean enabled;
    private String sitesAvailable;
    private String sitesEnabled;
    private String domainSuffix;
    private String nginxReloadCommand;
}