package com.utkarsh.in.DeploymentPlatform.service;

import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.entity.DeploymentLog;
import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import com.utkarsh.in.DeploymentPlatform.exception.ResourceNotFoundException;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentLogRepository;
import com.utkarsh.in.DeploymentPlatform.repository.DeploymentRepository;
import com.utkarsh.in.DeploymentPlatform.repository.ProjectRepository;
import com.utkarsh.in.DeploymentPlatform.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogStreamService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final ProjectRepository projectRepository;
    private final AuthUtil authUtil;

    private static final Set<DeploymentStatus> TERMINAL_STATUSES = Set.of(
            DeploymentStatus.RUNNING,
            DeploymentStatus.FAILED,
            DeploymentStatus.STOPPED,
            DeploymentStatus.SUPERSEDED
    );

    private static final long POLL_INTERVAL_MS = 500;
    private static final long MAX_STREAM_DURATION_MS = 15 * 60 * 1000L;

    public SseEmitter streamLogs(Long projectId, Long deploymentId, Long lastEventId) {
        Long userId = authUtil.getCurrentUserId();

        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + projectId));

        deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deployment not found with id: " + deploymentId));

        SseEmitter emitter = new SseEmitter(MAX_STREAM_DURATION_MS);

        emitter.onTimeout(() -> {
            log.info("SSE emitter timed out for deployment {}", deploymentId);
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("SSE emitter error for deployment {}: {}", deploymentId, e.getMessage());
            emitter.complete();
        });

        startStreaming(emitter, deploymentId, lastEventId != null ? lastEventId : 0L);

        return emitter;
    }

    @Async("deploymentExecutor")
    public void startStreaming(SseEmitter emitter, Long deploymentId, Long lastSeenId) {
        log.info("Starting log stream for deployment {} from lastId={}", deploymentId, lastSeenId);

        long lastId = lastSeenId;
        long startTime = System.currentTimeMillis();

        try {
            while (true) {

                // Check timeout
                if (System.currentTimeMillis() - startTime > MAX_STREAM_DURATION_MS) {
                    sendEvent(emitter, "timeout", "Stream timeout reached", -1L);
                    break;
                }

                // Fetch new log lines since last seen ID
                List<DeploymentLog> newLogs = deploymentLogRepository
                        .findByDeploymentIdAndIdGreaterThanOrderByCreatedAtAsc(
                                deploymentId, lastId);

                for (DeploymentLog logEntry : newLogs) {
                    String data = formatLogLine(logEntry);
                    sendEvent(emitter, "log", data, logEntry.getId());
                    lastId = logEntry.getId();
                }

                // Check if deployment has reached a terminal status
                Deployment deployment = deploymentRepository
                        .findById(deploymentId)
                        .orElse(null);

                if (deployment == null) {
                    sendEvent(emitter, "error", "Deployment not found", -1L);
                    break;
                }

                if (TERMINAL_STATUSES.contains(deployment.getStatus())) {
                    // Send any remaining logs one more time to be safe
                    List<DeploymentLog> finalLogs = deploymentLogRepository
                            .findByDeploymentIdAndIdGreaterThanOrderByCreatedAtAsc(
                                    deploymentId, lastId);

                    for (DeploymentLog logEntry : finalLogs) {
                        String data = formatLogLine(logEntry);
                        sendEvent(emitter, "log", data, logEntry.getId());
                        lastId = logEntry.getId();
                    }

                    // Send terminal status event so client knows to close
                    sendEvent(emitter, "status",
                            deployment.getStatus().name(), -1L);

                    log.info("Deployment {} reached terminal status: {}, closing stream",
                            deploymentId, deployment.getStatus());
                    break;
                }

                // Also stream current status so client can show progress
                sendEvent(emitter, "status", deployment.getStatus().name(), -1L);

                Thread.sleep(POLL_INTERVAL_MS);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Log stream interrupted for deployment {}", deploymentId);
        } catch (Exception e) {
            log.error("Log stream error for deployment {}: {}", deploymentId, e.getMessage());
        } finally {
            emitter.complete();
            log.info("SSE stream closed for deployment {}", deploymentId);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName,
                           String data, Long eventId) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventName)
                    .data(data);

            if (eventId != null && eventId > 0) {
                event.id(String.valueOf(eventId));
            }

            emitter.send(event);
        } catch (IOException e) {
            log.warn("Failed to send SSE event - client likely disconnected: {}",
                    e.getMessage());
            throw new RuntimeException("Client disconnected", e);
        }
    }

    private String formatLogLine(DeploymentLog logEntry) {
        return String.format("[%s] %s",
                logEntry.getLogLevel(),
                logEntry.getMessage());
    }
}