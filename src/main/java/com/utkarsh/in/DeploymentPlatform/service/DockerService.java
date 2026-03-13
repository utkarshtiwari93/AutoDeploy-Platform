package com.utkarsh.in.DeploymentPlatform.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.utkarsh.in.DeploymentPlatform.config.DockerProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private final DockerClient dockerClient;
    private final DockerProperties dockerProperties;

    @PostConstruct
    public void verifyDockerConnection() {
        try {
            var info = dockerClient.infoCmd().exec();
            log.info("==============================================");
            log.info("Docker connection OK");
            log.info("Docker version   : {}", info.getServerVersion());
            log.info("Operating system : {}", info.getOperatingSystem());
            log.info("Total containers : {}", info.getContainers());
            log.info("Running containers: {}", info.getContainersRunning());
            log.info("==============================================");
        } catch (Exception e) {
            log.error("==============================================");
            log.error("Docker connection FAILED: {}", e.getMessage());
            log.error("Make sure Docker Desktop is running");
            log.error("==============================================");
            throw new RuntimeException("Cannot connect to Docker daemon. " +
                    "Ensure Docker is running and accessible.", e);
        }
    }

    public List<Container> listRunningContainers() {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(false)
                    .exec();
            log.info("Found {} running containers", containers.size());
            return containers;
        } catch (DockerException e) {
            log.error("Failed to list containers: {}", e.getMessage());
            throw new RuntimeException("Failed to list containers", e);
        }
    }

    public List<Container> listAllContainers() {
        try {
            return dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();
        } catch (DockerException e) {
            throw new RuntimeException("Failed to list all containers", e);
        }
    }

    public List<Image> listImages() {
        try {
            return dockerClient.listImagesCmd().exec();
        } catch (DockerException e) {
            throw new RuntimeException("Failed to list images", e);
        }
    }

    public InspectContainerResponse inspectContainer(String containerId) {
        try {
            return dockerClient.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException e) {
            throw new RuntimeException("Container not found: " + containerId, e);
        }
    }

    public boolean isContainerRunning(String containerId) {
        try {
            InspectContainerResponse response = inspectContainer(containerId);
            Boolean running = response.getState().getRunning();
            return running != null && running;
        } catch (Exception e) {
            return false;
        }
    }

    public int findAvailablePort() {
        int start = dockerProperties.getHostPortRangeStart();
        int end   = dockerProperties.getHostPortRangeEnd();

        for (int port = start; port <= end; port++) {
            if (isPortAvailable(port) && !isPortUsedByContainer(port)) {
                log.info("Found available port: {}", port);
                return port;
            }
        }
        throw new RuntimeException(
                "No available ports found in range " + start + "-" + end);
    }

    public void pullImage(String imageName) {
        try {
            log.info("Pulling Docker image: {}", imageName);
            dockerClient.pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(5, TimeUnit.MINUTES);
            log.info("Successfully pulled image: {}", imageName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image pull interrupted: " + imageName, e);
        } catch (DockerException e) {
            throw new RuntimeException("Failed to pull image: " + imageName, e);
        }
    }

    public void removeImage(String imageId) {
        try {
            dockerClient.removeImageCmd(imageId).withForce(true).exec();
            log.info("Removed image: {}", imageId);
        } catch (NotFoundException e) {
            log.warn("Image not found for removal: {}", imageId);
        } catch (DockerException e) {
            log.error("Failed to remove image {}: {}", imageId, e.getMessage());
        }
    }

    public void pruneStoppedContainers() {
        try {
            List<Container> allContainers = listAllContainers();
            for (Container container : allContainers) {
                String state = container.getState();
                if ("exited".equalsIgnoreCase(state) || "dead".equalsIgnoreCase(state)) {
                    try {
                        dockerClient.removeContainerCmd(container.getId())
                                .withForce(false)
                                .exec();
                        log.info("Pruned stopped container: {}", container.getId());
                    } catch (Exception e) {
                        log.warn("Could not remove container {}: {}", container.getId(), e.getMessage());
                    }
                }
            }
            log.info("Prune complete");
        } catch (Exception e) {
            log.error("Failed to prune containers: {}", e.getMessage());
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isPortUsedByContainer(int port) {
        try {
            List<Container> containers = listAllContainers();
            for (Container container : containers) {
                if (container.getPorts() != null) {
                    for (var binding : container.getPorts()) {
                        if (binding.getPublicPort() != null &&
                                binding.getPublicPort() == port) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}