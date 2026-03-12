CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS projects (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(255) NOT NULL,
    repo_url   VARCHAR(500) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'INACTIVE',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deployments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT       NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'QUEUED',
    container_id VARCHAR(255),
    host_port    INT,
    image_tag    VARCHAR(255),
    image_id     VARCHAR(255),
    public_url   VARCHAR(500),
    deployed_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_deployments_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deployment_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    deployment_id BIGINT       NOT NULL,
    message       TEXT         NOT NULL,
    log_level     VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_logs_deployment FOREIGN KEY (deployment_id)
        REFERENCES deployments (id) ON DELETE CASCADE
);

CREATE INDEX idx_projects_user_id       ON projects (user_id);
CREATE INDEX idx_deployments_project_id ON deployments (project_id);
CREATE INDEX idx_logs_deployment_id     ON deployment_logs (deployment_id);