# AutoDeploy — Complete Architecture & Flow

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                               │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────┐      │
│   │              React Frontend  (Vite + Tailwind)           │      │
│   │                                                          │      │
│   │   Login / Register → Dashboard → Project Detail         │      │
│   │                              → Log Viewer (SSE)         │      │
│   │                                                          │      │
│   │   Axios (Bearer token on every request)                  │      │
│   │   fetch() ReadableStream (SSE log streaming)             │      │
│   └──────────────────┬───────────────────────────────────────┘      │
│                      │  HTTP / SSE                                  │
└──────────────────────┼──────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          NGINX (Production)                         │
│                                                                     │
│   Port 80 / 443                                                     │
│   /          → serve React static files                            │
│   /api        → proxy_pass to Spring Boot :8080                    │
│   app-{id}.domain.com → proxy_pass to container :{hostPort}        │
└──────────────────────┬──────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT BACKEND  :8080                      │
│                                                                     │
│  ┌─────────────┐   ┌──────────────┐   ┌────────────────────────┐   │
│  │ AuthController│  │ProjectController│ │DeploymentController    │   │
│  │ /api/auth/** │  │/api/projects/**│ │/api/projects/{id}/     │   │
│  └──────┬───────┘  └──────┬───────┘  │ deployments/**         │   │
│         │                 │          └──────────┬─────────────┘   │
│         ▼                 ▼                     ▼                  │
│  ┌─────────────┐   ┌──────────────┐   ┌────────────────────────┐   │
│  │ AuthService │   │ProjectService│   │  DeploymentService     │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘   │
│         │                 │                     │                  │
│         ▼                 ▼                     ▼                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    JPA Repositories                          │   │
│  │  UserRepo  ProjectRepo  DeploymentRepo  DeploymentLogRepo    │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                       │
│  ┌──────────────────────────┼───────────────────────────────────┐   │
│  │         Supporting Services              │                   │   │
│  │  DockerService  NginxService  CleanupScheduler LogStreamSvc  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────────────┘
           ┌───────────┴────────────┐
           ▼                        ▼
┌─────────────────┐      ┌──────────────────────────────────────────┐
│   MySQL :3306   │      │          Docker Engine                   │
│                 │      │                                          │
│  users          │      │  Images:  app-2-8ad05684:latest          │
│  projects       │      │           app-3-9bc12345:latest          │
│  deployments    │      │                                          │
│  deployment_logs│      │  Containers:                             │
└─────────────────┘      │   app-2  :3001 → :8080 (RUNNING)        │
                         │   app-3  :3002 → :8080 (RUNNING)        │
                         │   app-4  :3003 → :8080 (STOPPED)        │
                         └──────────────────────────────────────────┘
```

---

## Deployment Pipeline Flow

```
User clicks "Deploy"
        │
        ▼
POST /api/projects/{id}/deployments
        │
        ▼
┌───────────────────────────────────┐
│  DeploymentService.initiateDeploy │
│                                   │
│  1. Check concurrent deploy?      │
│     YES → 409 Conflict            │
│     NO  → continue                │
│                                   │
│  2. Create Deployment (QUEUED)    │
│  3. Save to DB                    │
│  4. Return 202 Accepted           │
└───────────────┬───────────────────┘
                │ @Async (background thread)
                ▼
┌───────────────────────────────────────────────────────────┐
│               runDeploymentPipeline()                     │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  STEP 1: CLONING                                    │  │
│  │                                                     │  │
│  │  status → CLONING                                   │  │
│  │  ProcessBuilder: git clone {repoUrl} {buildDir}     │  │
│  │  Stream stdout/stderr → saveLog(deployment, line)   │  │
│  │  Check exit code == 0? NO → FAILED                  │  │
│  │  Check Dockerfile exists? NO → FAILED               │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                  │
│  ┌──────────────────────▼──────────────────────────────┐  │
│  │  STEP 2: BUILDING                                   │  │
│  │                                                     │  │
│  │  status → BUILDING                                  │  │
│  │  dockerClient.buildImageCmd(buildDir)               │  │
│  │    .withTags(imageTag)                              │  │
│  │    .exec(BuildImageResultCallback)                  │  │
│  │      → each log line → saveLog(deployment, line)    │  │
│  │  imageId saved when complete                        │  │
│  │  status → BUILD_COMPLETE                            │  │
│  │  delete build directory                             │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                  │
│  ┌──────────────────────▼──────────────────────────────┐  │
│  │  STEP 3: STARTING                                   │  │
│  │                                                     │  │
│  │  status → STARTING                                  │  │
│  │  findAvailablePort() → scan 3000-9000               │  │
│  │  dockerClient.createContainerCmd(imageTag)          │  │
│  │    .withExposedPorts(8080)                          │  │
│  │    .withPortBindings(hostPort:8080)                 │  │
│  │    .withName("app-{deploymentId}")                  │  │
│  │    .withHostConfig(RestartPolicy.onFailure(3))      │  │
│  │  startContainerCmd(containerId)                     │  │
│  └──────────────────────┬──────────────────────────────┘  │
│                         │                                  │
│  ┌──────────────────────▼──────────────────────────────┐  │
│  │  STEP 4: RUNNING                                    │  │
│  │                                                     │  │
│  │  nginxService.createProxyConfig(depId, hostPort)    │  │
│  │  publicUrl = "http://localhost:{hostPort}"           │  │
│  │  save containerId, hostPort, publicUrl to DB        │  │
│  │  status → RUNNING                                   │  │
│  │  project.status → ACTIVE                            │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

---

## SSE Log Streaming Flow

```
Browser                    Spring Boot                    MySQL
   │                           │                            │
   │  GET .../logs/stream      │                            │
   │  Authorization: Bearer    │                            │
   │  Last-Event-ID: 0         │                            │
   │ ─────────────────────────►│                            │
   │                           │                            │
   │                    ┌──────┴──────┐                     │
   │                    │ validate     │                     │
   │                    │ ownership    │                     │
   │                    │ create       │                     │
   │                    │ SseEmitter   │                     │
   │                    └──────┬──────┘                     │
   │                           │ @Async startStreaming()     │
   │◄── HTTP 200 (keep-alive) ─┤                            │
   │                           │                            │
   │                    ┌──────┴──────┐                     │
   │                    │   LOOP      │                     │
   │                    │ every 500ms │                     │
   │                    └──────┬──────┘                     │
   │                           │ SELECT * FROM logs         │
   │                           │ WHERE deployment_id=X      │
   │                           │ AND id > lastSeenId        │
   │                           │ ──────────────────────────►│
   │                           │◄── new log rows ───────────│
   │                           │                            │
   │◄── event: log ────────────│                            │
   │    id: 1                  │                            │
   │    data: [INFO] Cloning.. │                            │
   │                           │                            │
   │◄── event: log ────────────│                            │
   │    id: 2                  │                            │
   │    data: [INFO] Step 1/5  │                            │
   │                           │                            │
   │◄── event: status ─────────│                            │
   │    data: BUILDING         │                            │
   │                           │                            │
   │        ... more logs ...  │                            │
   │                           │                            │
   │◄── event: status ─────────│                            │
   │    data: RUNNING          │                            │
   │                           │                            │
   │◄── connection closed ─────│                            │
   │                           │                            │
```

---

## Authentication Flow

```
┌──────────┐         ┌──────────────┐         ┌──────────┐
│  Client  │         │  Spring Boot │         │  MySQL   │
└────┬─────┘         └──────┬───────┘         └────┬─────┘
     │                      │                      │
     │  POST /auth/login     │                      │
     │  { email, password }  │                      │
     │─────────────────────►│                      │
     │                      │  SELECT * FROM users  │
     │                      │  WHERE email=?        │
     │                      │─────────────────────►│
     │                      │◄─── user record ──────│
     │                      │                      │
     │                      │  BCrypt.verify(       │
     │                      │    password,          │
     │                      │    user.passwordHash) │
     │                      │                      │
     │                      │  Jwts.builder()       │
     │                      │   .claim(email)       │
     │                      │   .claim(userId)      │
     │                      │   .sign(secretKey)    │
     │                      │   .compact()          │
     │                      │                      │
     │◄── { token: "eyJ..." }│                      │
     │                      │                      │
     │  GET /api/projects    │                      │
     │  Authorization:       │                      │
     │  Bearer eyJ...        │                      │
     │─────────────────────►│                      │
     │                      │                      │
     │               ┌──────┴──────┐               │
     │               │  JwtFilter  │               │
     │               │  extract    │               │
     │               │  email from │               │
     │               │  token      │               │
     │               │  set        │               │
     │               │  SecurityCtx│               │
     │               └──────┬──────┘               │
     │                      │                      │
     │◄── 200 [ projects ] ─│                      │
     │                      │                      │
```

---

## Container Lifecycle State Machine

```
                    POST /deployments
                          │
                          ▼
                      ┌────────┐
                      │ QUEUED │
                      └───┬────┘
                          │ git clone starts
                          ▼
                      ┌─────────┐
                      │ CLONING │
                      └───┬─────┘
                          │ clone success       clone fail
                          ├────────────────────────────────►  FAILED
                          │
                          ▼
                      ┌──────────┐
                      │ BUILDING │
                      └───┬──────┘
                          │ build success       build fail
                          ├────────────────────────────────►  FAILED
                          │
                          ▼
                   ┌────────────────┐
                   │ BUILD_COMPLETE  │
                   └───┬────────────┘
                       │ container starts
                       ▼
                   ┌──────────┐
                   │ STARTING │
                   └───┬──────┘
                       │
                       ▼
                   ┌─────────┐
              ┌────│ RUNNING │────┐
              │    └─────────┘    │
              │         │         │
         restart      stop     redeploy
              │         │         │
              ▼         ▼         ▼
          ┌─────────┐ ┌────────┐ ┌────────────┐
          │ RUNNING │ │STOPPED │ │ SUPERSEDED │
          └─────────┘ └───┬────┘ └────────────┘
                          │
                        deploy
                          │
                          ▼
                       QUEUED → ... → RUNNING
```

---

## Database Schema

```
┌──────────────────────────────────┐
│              users               │
├──────────────┬───────────────────┤
│ id           │ BIGINT PK AI      │
│ name         │ VARCHAR(100)      │
│ email        │ VARCHAR(255) UQ   │
│ password     │ VARCHAR(255)      │
│ created_at   │ DATETIME          │
└──────────────┴───────────────────┘
        │ 1
        │
        │ N
┌──────────────────────────────────┐
│             projects             │
├──────────────┬───────────────────┤
│ id           │ BIGINT PK AI      │
│ user_id      │ BIGINT FK→users   │
│ name         │ VARCHAR(100)      │
│ repo_url     │ VARCHAR(500)      │
│ status       │ VARCHAR(50)       │
│ created_at   │ DATETIME          │
│ updated_at   │ DATETIME          │
└──────────────┴───────────────────┘
        │ 1
        │
        │ N
┌──────────────────────────────────┐
│           deployments            │
├──────────────┬───────────────────┤
│ id           │ BIGINT PK AI      │
│ project_id   │ BIGINT FK→projects│
│ status       │ VARCHAR(50)       │
│ container_id │ VARCHAR(255)      │
│ host_port    │ INT               │
│ image_tag    │ VARCHAR(255)      │
│ image_id     │ VARCHAR(255)      │
│ public_url   │ VARCHAR(500)      │
│ deployed_at  │ DATETIME          │
│ updated_at   │ DATETIME          │
└──────────────┴───────────────────┘
        │ 1
        │
        │ N
┌──────────────────────────────────┐
│         deployment_logs          │
├──────────────┬───────────────────┤
│ id           │ BIGINT PK AI      │
│ deployment_id│ BIGINT FK→deploys │
│ message      │ TEXT              │
│ level        │ VARCHAR(20)       │
│ created_at   │ DATETIME          │
└──────────────┴───────────────────┘
```

---

## Frontend Component Tree

```
App.jsx
├── BrowserRouter
│   └── AuthProvider (Context)
│       └── ToastProvider (Context)
│           └── Routes
│               ├── /login          → Login.jsx
│               ├── /register       → Register.jsx
│               ├── /dashboard      → ProtectedRoute → Dashboard.jsx
│               │                       ├── StatusBadge (component)
│               │                       └── NewProjectModal (component)
│               ├── /projects/:id   → ProtectedRoute → ProjectDetail.jsx
│               │                       └── StatusBadge (component)
│               ├── /projects/:id/  → ProtectedRoute → LogViewer.jsx
│               │   deployments/        └── LogLine (component)
│               │   :depId/logs
│               └── *               → NotFound.jsx
```

---

## Request/Response Flow (Full Example: Deploy)

```
1. User clicks "Deploy" on Dashboard
   │
   ▼
2. handleDeploy(projectId) called in Dashboard.jsx
   │
   ▼
3. axios.post('/projects/3/deployments')
   │  adds Authorization: Bearer eyJ... header
   │
   ▼
4. JwtFilter validates token, sets SecurityContext
   │
   ▼
5. DeploymentController.triggerDeployment(projectId=3)
   │
   ▼
6. DeploymentService.initiateDeploy(projectId=3)
   │  checks no active deployment → OK
   │  creates Deployment { status: QUEUED }
   │  saves to MySQL
   │  calls runDeploymentPipeline() @Async
   │  returns DeploymentResponse { id: 12, status: QUEUED }
   │
   ▼
7. HTTP 202 Accepted → { id: 12, status: "QUEUED" }
   │
   ▼
8. addToast("Deployment started!", "success") shown in UI
   │
   ▼
9. Dashboard polls GET /projects every 5s
   │  status updates: QUEUED → CLONING → BUILDING → RUNNING
   │
   ▼
10. User navigates to /projects/3/deployments/12/logs
    │
    ▼
11. LogViewer.jsx connects via fetch() to
    GET /api/projects/3/deployments/12/logs/stream
    │
    ▼
12. LogStreamController returns SseEmitter
    │  async thread polls deployment_logs every 500ms
    │  sends events as they arrive
    │
    ▼
13. Browser renders log lines in real time
    │  colors applied per log content
    │  auto-scrolls to bottom
    │
    ▼
14. status: RUNNING received → stream closes
    │  green success banner shown
    │  link to http://localhost:{port}
```

---

## Production Deployment Architecture

```
Internet
    │
    ▼
┌──────────────────────────────────────────────────┐
│                  VPS (Ubuntu 22.04)              │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │              Nginx  :80 / :443             │  │
│  │                                            │  │
│  │  autodeploy.com       → /var/www/autodeploy│  │
│  │  autodeploy.com/api   → localhost:8080     │  │
│  │  app-1.autodeploy.com → localhost:3001     │  │
│  │  app-2.autodeploy.com → localhost:3002     │  │
│  └────────────────────────────────────────────┘  │
│                   │           │                  │
│                   ▼           ▼                  │
│  ┌────────────┐  ┌──────────────────────────┐    │
│  │  React     │  │  Spring Boot :8080        │    │
│  │  /var/www  │  │  (systemd service)        │    │
│  │  static    │  └────────────┬─────────────┘    │
│  │  files     │               │                  │
│  └────────────┘               ▼                  │
│                  ┌────────────────────────────┐  │
│                  │    MySQL :3306             │  │
│                  └────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │         Docker Engine                      │  │
│  │  unix:///var/run/docker.sock               │  │
│  │                                            │  │
│  │  container: app-1  port 3001               │  │
│  │  container: app-2  port 3002               │  │
│  │  container: app-3  port 3003               │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```
