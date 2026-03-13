package com.utkarsh.in.DeploymentPlatform.controller;

import com.utkarsh.in.DeploymentPlatform.service.LogStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects/{projectId}/deployments")
@RequiredArgsConstructor
public class LogStreamController {

    private final LogStreamService logStreamService;

    @GetMapping(
            value = "/{deploymentId}/logs/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamLogs(
            @PathVariable Long projectId,
            @PathVariable Long deploymentId,
            @RequestHeader(
                    value = "Last-Event-ID",
                    required = false
            ) Long lastEventId) {

        return logStreamService.streamLogs(projectId, deploymentId, lastEventId);
    }
}