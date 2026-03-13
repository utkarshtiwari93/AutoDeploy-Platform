package com.utkarsh.in.DeploymentPlatform.controller;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.utkarsh.in.DeploymentPlatform.service.DockerService;
import com.utkarsh.in.DeploymentPlatform.service.NginxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker")
@RequiredArgsConstructor
public class DockerController {

    private final DockerService dockerService;
    private final NginxService nginxService;

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        List<Container> containers = dockerService.listRunningContainers();
        return ResponseEntity.ok(Map.of(
                "status", "connected",
                "runningContainers", String.valueOf(containers.size())
        ));
    }

    @GetMapping("/containers")
    public ResponseEntity<List<Container>> listContainers() {
        return ResponseEntity.ok(dockerService.listAllContainers());
    }

    @GetMapping("/images")
    public ResponseEntity<List<Image>> listImages() {
        return ResponseEntity.ok(dockerService.listImages());
    }

    @GetMapping("/port")
    public ResponseEntity<Map<String, Integer>> findFreePort() {
        int port = dockerService.findAvailablePort();
        return ResponseEntity.ok(Map.of("availablePort", port));
    }

    @GetMapping("/nginx/preview/{deploymentId}/{port}")
    public ResponseEntity<Map<String, String>> previewNginxConfig(
            @PathVariable Long deploymentId,
            @PathVariable int port) {

        String serverName = nginxService.buildServerName(deploymentId);
        String publicUrl  = "http://" + serverName;
        boolean enabled   = nginxService != null;

        return ResponseEntity.ok(Map.of(
                "serverName", serverName,
                "publicUrl", publicUrl,
                "hostPort", String.valueOf(port),
                "nginxEnabled", String.valueOf(enabled),
                "configPreview", """
                    server {
                        listen 80;
                        server_name %s;
                        location / {
                            proxy_pass http://127.0.0.1:%d;
                        }
                    }
                    """.formatted(serverName, port)
        ));
    }
}