# Technical Interview Questions — AutoDeploy Platform

---

## 1. Spring Boot & Java

**Q: What is Spring Boot and why did you use it?**
> Spring Boot is a framework built on top of Spring that auto-configures your application based on dependencies. I used it because it removes boilerplate configuration, has built-in embedded Tomcat, and integrates seamlessly with JPA, Security, and Flyway.

**Q: What is the difference between @Component, @Service, @Repository, and @Controller?**
> All are Spring-managed beans but semantically different. `@Repository` adds exception translation for DB errors. `@Service` marks business logic layer. `@Controller` handles HTTP. `@Component` is generic. Spring treats them the same under the hood but using the right one improves readability and enables AOP.

**Q: What is @RestControllerAdvice and how did you use it?**
> It is a combination of `@ControllerAdvice` and `@ResponseBody`. I used it in `GlobalExceptionHandler` to catch exceptions thrown anywhere in the application and return consistent JSON error responses like `{ "error": "message" }` with the correct HTTP status code.

**Q: What is the difference between @Transactional and without it?**
> Without `@Transactional`, each JPA operation is its own transaction. With it, all operations in the method run in a single transaction — if any fails, all are rolled back. I used it in service methods that do multiple DB writes.

**Q: What is Spring Security and how did you configure it?**
> Spring Security is a security framework for authentication and authorization. I configured it with:
> - Stateless sessions (JWT, no server-side session)
> - JWT filter runs before `UsernamePasswordAuthenticationFilter`
> - `/api/auth/**` is public, everything else requires a valid token
> - CORS configured via `CorsConfigurationSource` bean
> - ASYNC and ERROR dispatcher types permitted for SSE

**Q: What is OncePerRequestFilter and why did you extend it for JWT?**
> `OncePerRequestFilter` guarantees the filter runs exactly once per request, even in forward/include scenarios. I extended it for `JwtFilter` so the token validation logic runs once per HTTP request, reads the Authorization header, validates the JWT, and sets the `SecurityContextHolder`.

**Q: What is @Async and how did you use it?**
> `@Async` makes a method run in a separate thread from a thread pool. I used it in `runDeploymentPipeline` so the git clone + docker build runs in the background without blocking the HTTP response. The client gets a `202 Accepted` immediately while the build runs asynchronously.

**Q: What is DelegatingSecurityContextAsyncTaskExecutor?**
> By default, `@Async` threads don't inherit the Spring Security context. `DelegatingSecurityContextAsyncTaskExecutor` wraps the executor and copies the `SecurityContext` to the async thread. I used it so async deployment threads could still access the authenticated user.

**Q: What is @ConfigurationProperties?**
> It binds a group of properties from `application.yml` to a Java class. I used it for `DockerProperties`, `NginxProperties`, and `CleanupProperties` to cleanly map `app.docker.*`, `app.nginx.*`, `app.cleanup.*` to typed Java objects instead of using `@Value` everywhere.

**Q: What is @Scheduled and how did you use it?**
> `@Scheduled` runs a method on a schedule. I used cron expressions:
> - `"0 0 * * * *"` — hourly cleanup of old deployments
> - `"0 0 0 * * *"` — daily Docker image prune
> Required `@EnableScheduling` on the main class.

**Q: What is @PreDestroy?**
> It marks a method to run before the Spring bean is destroyed — i.e. on application shutdown. I used it in `CleanupScheduler` to log a graceful shutdown message.

---

## 2. JWT Authentication

**Q: What is JWT and how does it work?**
> JSON Web Token is a self-contained token with three base64-encoded parts: Header (algorithm), Payload (claims like userId, email, expiry), and Signature (HMAC-SHA256 of header+payload using a secret key). The server signs it on login and the client sends it on every request. The server validates the signature — no DB lookup needed.

**Q: Where did you store the JWT on the frontend?**
> In `localStorage`. On every Axios request, an interceptor reads it and adds `Authorization: Bearer <token>` to the header.

**Q: What is the risk of storing JWT in localStorage?**
> XSS (Cross-Site Scripting) attacks can read localStorage. A safer option is `httpOnly` cookies which are not accessible via JavaScript. For this project localStorage was acceptable since it's a dev/portfolio tool.

**Q: How do you invalidate a JWT?**
> JWTs are stateless so you can't invalidate them server-side without a blacklist. Options: short expiry + refresh tokens, or maintain a token blacklist in Redis. In this project tokens expire after 24 hours (`expiration: 86400000` ms).

**Q: What claims did you put in the JWT?**
> `email` and `userId`. These let any authenticated endpoint know who the user is without a DB query — extracted directly from the token via `JwtUtil.extractEmail()` and `extractUserId()`.

**Q: What library did you use for JWT?**
> `jjwt 0.11.5` — `io.jsonwebtoken`. Used `Jwts.builder()` to create tokens and `Jwts.parserBuilder()` to validate and parse them.

---

## 3. Docker Integration

**Q: What is Docker and what problem does it solve?**
> Docker packages an application and its dependencies into a container — a lightweight, isolated runtime environment. It solves "works on my machine" problems by making the build and runtime environment identical everywhere.

**Q: What Docker Java SDK did you use and why?**
> `docker-java 3.3.4`. It provides a Java API to interact with the Docker daemon — build images, run containers, stop/remove containers — without shelling out to the `docker` CLI.

**Q: How did you connect to Docker on Windows?**
> Via TCP socket `tcp://localhost:2375`. Docker Desktop on Windows exposes this endpoint when "Expose daemon on tcp://localhost:2375 without TLS" is enabled. On Linux you'd use the Unix socket `unix:///var/run/docker.sock`.

**Q: How did you build a Docker image programmatically?**
> Using `dockerClient.buildImageCmd(buildDir).withTags(Set.of(imageTag))`. I attached a `BuildImageResultCallback` which streamed each build log line — I saved these to the `deployment_logs` table. When the callback completed, I had the final `imageId`.

**Q: How did you run a container programmatically?**
> Using `createContainerCmd(imageTag)` with `ExposedPort`, `PortBindings` (hostPort:8080), `HostConfig` with `RestartPolicy.onFailureRestart(3)`, and a container name `app-{deploymentId}`. Then `startContainerCmd(containerId)`.

**Q: How did you find an available port?**
> Using `ServerSocket` — try to bind to a port, if it succeeds the port is free. Scanned from `hostPortRangeStart` (3000) to `hostPortRangeEnd` (9000), also cross-checked against ports used by existing running containers.

**Q: What dependency conflict did you face with Docker Java SDK?**
> Spring Boot 3.5.x includes `httpclient5 5.4.x` but `docker-java-transport-httpclient5` pulls in an older incompatible version. Fixed by:
> 1. Excluding docker-java's transitive `httpclient5` and `httpcore5`
> 2. Declaring explicit versions: `httpclient5:5.4.1`, `httpcore5:5.3.3`, `httpcore5-h2:5.3.3`

---

## 4. Database & JPA

**Q: What is Flyway and why did you use it?**
> Flyway is a database migration tool. Instead of manually running SQL scripts, Flyway runs versioned migration files (`V1__init.sql`) in order. This makes DB schema changes reproducible, tracked, and version-controlled alongside the code.

**Q: What is the naming convention for Flyway migrations?**
> `V{version}__{description}.sql` — e.g. `V1__init.sql`, `V2__add_column.sql`. The double underscore is required.

**Q: What JPA relationships did you use?**
> - `@ManyToOne` — Deployment → Project, DeploymentLog → Deployment
> - `@OneToMany` — Project has many Deployments
> - Used `FetchType.LAZY` to avoid N+1 queries

**Q: What is the N+1 query problem?**
> When you load a list of N entities and then for each entity JPA runs a separate query to load a related entity — resulting in N+1 total queries. Solved with `JOIN FETCH`, `@EntityGraph`, or `FetchType.EAGER` where appropriate.

**Q: Why did you use @Query in DeploymentRepository?**
> For custom JPQL queries that Spring Data JPA can't derive from method names. For example `findOldDeploymentsByStatuses` needed a WHERE clause with both a list of statuses AND a date comparison — too complex for derived query methods.

**Q: What is HikariCP?**
> It's the default connection pool in Spring Boot. It maintains a pool of reusable DB connections so you don't pay the cost of opening a new connection on every request. Visible in startup logs as `HikariPool-1 - Starting...`.

---

## 5. REST API Design

**Q: What HTTP status codes did you use and why?**
> - `200 OK` — successful GET, action completed
> - `201 Created` — new resource created (POST /projects)
> - `202 Accepted` — async operation started (POST /deployments)
> - `204 No Content` — successful DELETE
> - `400 Bad Request` — validation error
> - `401 Unauthorized` — wrong credentials
> - `403 Forbidden` — no token
> - `404 Not Found` — resource not found or wrong owner
> - `409 Conflict` — duplicate email, already deploying
> - `500 Internal Server Error` — unexpected failure

**Q: Why does POST /deployments return 202 instead of 200?**
> Because the deployment is asynchronous — the server accepted the request but the work isn't done yet. `202 Accepted` semantically means "I got it, I'm working on it." The client polls or streams to track progress.

**Q: How did you implement ownership checks?**
> Using `findByIdAndUserId(id, userId)` in repositories. This single query both fetches the resource AND verifies it belongs to the current user. If not found → `ResourceNotFoundException` → 404. This avoids leaking information about whether a resource exists at all.

**Q: What is CORS and how did you fix it?**
> Cross-Origin Resource Sharing — browsers block requests to a different origin unless the server sends `Access-Control-Allow-Origin` headers. Fixed by:
> 1. Creating a `CorsConfigurationSource` bean that allows `localhost:5173` and `localhost:5174`
> 2. Adding `.cors(Customizer.withDefaults())` to `SecurityConfig` so Spring Security applies CORS before blocking requests

---

## 6. Server-Sent Events (SSE)

**Q: What is SSE and how is it different from WebSockets?**
> SSE (Server-Sent Events) is a one-way persistent HTTP connection — server pushes data to the client. WebSockets are bidirectional. SSE is simpler, works over plain HTTP/1.1, supports automatic reconnection, and is ideal for log streaming where the client only needs to receive data.

**Q: How did you implement SSE in Spring Boot?**
> Using `SseEmitter`. The controller returns a `SseEmitter` with a 15-minute timeout. A `@Async` method polls the DB every 500ms for new log rows and calls `emitter.send()` with event name, data, and ID.

**Q: Why did you poll the DB instead of pushing directly?**
> Because the SSE streaming happens in a different thread from the deployment pipeline. Polling the DB decouples the two — the pipeline just writes logs, the SSE service reads them. It also supports resume via `Last-Event-ID`.

**Q: What is Last-Event-ID?**
> A browser SSE header — when a connection drops and reconnects, the browser sends the ID of the last event it received. The server uses this to replay only missed events. I implemented this with `findByDeploymentIdAndIdGreaterThan(deploymentId, lastSeenId)`.

**Q: Why did you use fetch() instead of EventSource on the frontend?**
> The native `EventSource` API doesn't support custom headers like `Authorization: Bearer <token>`. So I used `fetch()` with the auth header and manually read the `ReadableStream`, parsing SSE event lines (`event:`, `data:`, `id:`) myself.

---

## 7. Nginx

**Q: What is Nginx and what role does it play in this project?**
> Nginx is a high-performance web server and reverse proxy. In AutoDeploy it serves two purposes:
> 1. Serves the React frontend static files
> 2. Reverse proxies requests from `app-{id}.domain.com` to the correct container port

**Q: What is a reverse proxy?**
> A server that receives requests and forwards them to an internal server. The client talks to Nginx on port 80, Nginx forwards to the container on port 3001. The client never directly connects to the container.

**Q: What is sites-available vs sites-enabled in Nginx?**
> `sites-available` stores all config files. `sites-enabled` contains symlinks to configs that are actually active. This lets you enable/disable sites without deleting configs. I auto-generate configs in `sites-available` and create symlinks in `sites-enabled` per deployment.

---

## 8. React Frontend

**Q: What is Context API and how did you use it?**
> React's built-in state management for global state. I used `AuthContext` to store the JWT token and user object, with `login()`, `register()`, and `logout()` methods. Any component can access auth state via `useAuth()` hook without prop drilling.

**Q: What is a protected route?**
> A React Router wrapper component that checks if the user is authenticated. If no token exists → redirects to `/login`. If token exists → renders the child component. Prevents unauthenticated access to `/dashboard`, `/projects`, etc.

**Q: What are Axios interceptors?**
> Middleware functions that run before every request (request interceptor) or after every response (response interceptor). I used:
> - Request interceptor — reads token from localStorage, adds `Authorization: Bearer` header
> - Response interceptor — catches 401/403, clears localStorage, redirects to login

**Q: What is Vite?**
> A modern frontend build tool that uses ES modules for near-instant dev server startup and hot module replacement. Much faster than webpack-based CRA. I used it with the React template.

**Q: How did you implement real-time status updates on the dashboard?**
> Using `setInterval` to poll `GET /api/projects` every 5 seconds. When a deployment is in progress, the status badge updates automatically. When it reaches `RUNNING` the interval continues but no visible changes occur.

---

## 9. General Architecture

**Q: What design patterns did you use?**
> - **Repository pattern** — data access abstracted behind JPA repositories
> - **Service layer pattern** — business logic in `@Service` classes
> - **DTO pattern** — separate request/response objects from entities
> - **Factory/Builder** — `DockerClient` created via `DockerClientBuilder`
> - **Observer-like** — SSE polling simulates event-driven log streaming

**Q: How would you scale this platform?**
> - Use a message queue (Kafka/RabbitMQ) instead of `@Async` for deployments
> - Multiple worker nodes pulling from the queue
> - Replace DB polling for SSE with Redis Pub/Sub
> - Use Kubernetes instead of raw Docker for container orchestration
> - Add a load balancer in front of multiple API instances

**Q: What would you add for production readiness?**
> - HTTPS/TLS on all endpoints
> - Rate limiting on auth endpoints
> - Resource limits (CPU/memory) per container
> - Private repo support via GitHub OAuth
> - Environment variable injection per deployment
> - Database provisioning per project
> - Monitoring with Prometheus + Grafana
> - Centralized logging with ELK stack

**Q: What is the difference between STOPPED and SUPERSEDED?**
> `STOPPED` — manually stopped by the user. Container was running and was stopped.
> `SUPERSEDED` — automatically set when a redeploy happens. The old deployment is replaced by a new one.

**Q: How did you handle concurrent deployments?**
> In `DeploymentService.initiateDeploy()` I check if any deployment for the project is in an active state (`QUEUED`, `CLONING`, `BUILDING`, `STARTING`, `RUNNING`). If yes → throw `ConflictException` → 409. This prevents two simultaneous builds for the same project.
