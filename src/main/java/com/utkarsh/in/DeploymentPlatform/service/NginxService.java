package com.utkarsh.in.DeploymentPlatform.service;

import com.utkarsh.in.DeploymentPlatform.config.NginxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class NginxService {

    private final NginxProperties nginxProperties;

    public String createProxyConfig(Long deploymentId, int hostPort) {
        String serverName = buildServerName(deploymentId);
        String publicUrl  = buildPublicUrl(deploymentId);

        if (!nginxProperties.isEnabled()) {
            log.info("[Nginx DISABLED] Would create config for {} → localhost:{}",
                    serverName, hostPort);
            log.info("[Nginx DISABLED] Public URL would be: {}", publicUrl);
            return publicUrl;
        }

        String configContent = buildNginxConfig(serverName, hostPort);
        String configPath    = nginxProperties.getSitesAvailable()
                + "/app-" + deploymentId + ".conf";
        String symlinkPath   = nginxProperties.getSitesEnabled()
                + "/app-" + deploymentId + ".conf";

        try {
            // Write config file
            writeConfigFile(configPath, configContent);
            log.info("Nginx config written: {}", configPath);

            // Create symlink in sites-enabled
            createSymlink(configPath, symlinkPath);
            log.info("Symlink created: {}", symlinkPath);

            // Reload Nginx
            reloadNginx();

            log.info("Nginx reloaded — app-{} available at {}", deploymentId, publicUrl);
            return publicUrl;

        } catch (IOException e) {
            log.error("Failed to create Nginx config: {}", e.getMessage());
            throw new RuntimeException("Nginx config creation failed: " + e.getMessage(), e);
        }
    }

    public void removeProxyConfig(Long deploymentId) {
        if (!nginxProperties.isEnabled()) {
            log.info("[Nginx DISABLED] Would remove config for deployment {}", deploymentId);
            return;
        }

        String configPath  = nginxProperties.getSitesAvailable()
                + "/app-" + deploymentId + ".conf";
        String symlinkPath = nginxProperties.getSitesEnabled()
                + "/app-" + deploymentId + ".conf";

        try {
            // Remove symlink first
            Path symlink = Paths.get(symlinkPath);
            if (Files.exists(symlink)) {
                Files.delete(symlink);
                log.info("Removed symlink: {}", symlinkPath);
            }

            // Remove config file
            Path config = Paths.get(configPath);
            if (Files.exists(config)) {
                Files.delete(config);
                log.info("Removed config: {}", configPath);
            }

            // Reload Nginx
            reloadNginx();
            log.info("Nginx reloaded after removing app-{} config", deploymentId);

        } catch (IOException e) {
            log.error("Failed to remove Nginx config for deployment {}: {}",
                    deploymentId, e.getMessage());
        }
    }

    public String buildPublicUrl(Long deploymentId) {
        if (!nginxProperties.isEnabled()) {
            return null;
        }
        return "http://app-" + deploymentId + "." + nginxProperties.getDomainSuffix();
    }

    public String buildServerName(Long deploymentId) {
        return "app-" + deploymentId + "." + nginxProperties.getDomainSuffix();
    }

    private String buildNginxConfig(String serverName, int hostPort) {
        return String.format("""
                server {
                    listen 80;
                    server_name %s;

                    location / {
                        proxy_pass http://127.0.0.1:%d;
                        proxy_http_version 1.1;
                        proxy_set_header Upgrade $http_upgrade;
                        proxy_set_header Connection 'upgrade';
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_cache_bypass $http_upgrade;
                        proxy_read_timeout 300s;
                        proxy_connect_timeout 75s;
                    }
                }
                """, serverName, hostPort);
    }

    private void writeConfigFile(String path, String content) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void createSymlink(String targetPath, String symlinkPath) throws IOException {
        Path target  = Paths.get(targetPath);
        Path symlink = Paths.get(symlinkPath);

        if (Files.exists(symlink)) {
            Files.delete(symlink);
        }

        Files.createSymbolicLink(symlink, target);
    }

    private void reloadNginx() {
        try {
            String command = nginxProperties.getNginxReloadCommand();
            log.info("Reloading Nginx with command: {}", command);

            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Nginx] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Nginx reload failed with exit code: {}", exitCode);
            } else {
                log.info("Nginx reloaded successfully");
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Nginx reload error: {}", e.getMessage());
        }
    }
}