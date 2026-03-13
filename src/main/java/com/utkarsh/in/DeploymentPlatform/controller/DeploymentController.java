package com.utkarsh.in.DeploymentPlatform.controller;

import com.utkarsh.in.DeploymentPlatform.dto.response.DeploymentLogResponse;
import com.utkarsh.in.DeploymentPlatform.dto.response.DeploymentResponse;
import com.utkarsh.in.DeploymentPlatform.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping
    public ResponseEntity<DeploymentResponse> deploy(@PathVariable Long projectId) {
        DeploymentResponse response = deploymentService.initiateDeploy(projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DeploymentResponse>> getAllDeployments(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(deploymentService.getDeploymentsByProject(projectId));
    }

    @GetMapping("/latest")
    public ResponseEntity<DeploymentResponse> getLatestDeployment(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(deploymentService.getLatestDeployment(projectId));
    }

    @GetMapping("/{deploymentId}/logs")
    public ResponseEntity<List<DeploymentLogResponse>> getLogs(
            @PathVariable Long projectId,
            @PathVariable Long deploymentId) {
        return ResponseEntity.ok(
                deploymentService.getDeploymentLogs(projectId, deploymentId));
    }

    @GetMapping("/{deploymentId}/status")
    public ResponseEntity<Map<String, String>> getStatus(
            @PathVariable Long projectId,
            @PathVariable Long deploymentId) {
        DeploymentResponse latest = deploymentService.getLatestDeployment(projectId);
        return ResponseEntity.ok(Map.of(
                "deploymentId", String.valueOf(latest.getId()),
                "status", latest.getStatus().name()
        ));
    }
}