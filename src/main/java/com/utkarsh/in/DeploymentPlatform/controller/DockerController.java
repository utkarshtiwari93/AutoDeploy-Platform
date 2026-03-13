package com.utkarsh.in.DeploymentPlatform.controller;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.utkarsh.in.DeploymentPlatform.service.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docker")
@RequiredArgsConstructor
public class DockerController {

    private final DockerService dockerService;

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
}