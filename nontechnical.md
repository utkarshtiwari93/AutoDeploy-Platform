# Non-Technical Interview Questions — AutoDeploy Platform

---

## 1. Project Overview

**Q: Tell me about this project in simple terms.**
> AutoDeploy is a mini version of platforms like Heroku or Railway. A developer pastes a GitHub repository URL into the platform, and AutoDeploy automatically clones the repo, builds a Docker image from the Dockerfile, runs it as a container, and makes it accessible via a URL — all with real-time build logs visible in the browser. Think of it as a self-hosted deployment platform you own and control.

**Q: Why did you build this project?**
> I wanted to understand what happens under the hood when you click "Deploy" on platforms like Railway or Render. Instead of just using these tools, I wanted to build one myself to deeply understand containerization, CI/CD pipelines, real-time communication, and full-stack development end to end.

**Q: How long did this project take?**
> 15 days of focused work — one major feature per day. The backend took Days 1-10 and the React frontend took Days 11-15.

**Q: What is the tech stack?**
> Backend: Java 21, Spring Boot 3.5, MySQL, Flyway, Docker Java SDK, JWT authentication.
> Frontend: React 18, Vite, Tailwind CSS, React Router, Axios.
> Infrastructure: Docker, Nginx, Linux VPS.

---

## 2. Problem Solving

**Q: What was the hardest problem you faced?**
> The Docker Java SDK dependency conflict with Spring Boot 3.5. Both use `httpclient5` but different versions that are binary incompatible. The app crashed at startup with `NoClassDefFoundError: TlsSocketStrategy`. I solved it by explicitly excluding the transitive dependency from docker-java and declaring the correct Spring Boot version explicitly in `pom.xml`.

**Q: How did you debug the CORS issue?**
> The React frontend was blocked by CORS. I checked the browser DevTools Network tab which showed the preflight OPTIONS request was failing with no `Access-Control-Allow-Origin` header. I traced it to Spring Security intercepting the OPTIONS request before the CORS filter could run. The fix was adding `.cors(Customizer.withDefaults())` to SecurityConfig so Spring Security applies CORS headers first.

**Q: What would you do differently if you started over?**
> I would add environment variable support from Day 1 — it's a fundamental feature that many real apps need. I would also design the port assignment system to be more robust using a dedicated DB table instead of port scanning, and add webhook support for auto-deploy on git push from the beginning.

**Q: How did you handle the SSE security issue?**
> When SSE connections reconnected after the initial request, Tomcat dispatched a new async request through the Spring Security filter chain but with no authentication context — causing `Access Denied`. I fixed it by adding `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()` so Spring Security skips auth checks on async re-dispatches.

---

## 3. Architecture Decisions

**Q: Why did you choose MySQL over PostgreSQL?**
> Both would work equally well for this project. I chose MySQL because of familiarity and because it's more commonly used in the enterprise Java ecosystem. PostgreSQL would be a better choice for production with more advanced features like JSONB columns for storing deployment metadata.

**Q: Why did you use polling for SSE instead of pushing directly?**
> The deployment pipeline runs in a separate async thread from the SSE streaming thread. Having the pipeline directly push to the SSE emitter would create tight coupling and thread-safety issues. Polling the DB every 500ms decouples the two systems — the pipeline just writes logs and the SSE service reads them independently. It also naturally supports resume via Last-Event-ID.

**Q: Why did you choose JWT over session-based auth?**
> The backend is stateless — no server-side session storage needed. JWTs are self-contained, work naturally with REST APIs, and scale horizontally since any server instance can validate a token without shared session storage. This is standard practice for modern REST APIs.

**Q: Why did you keep Nginx disabled on Windows?**
> Nginx runs natively on Linux but requires WSL or extra setup on Windows. Since the primary development environment is Windows and the Nginx integration is a production concern, I added an `app.nginx.enabled` flag. On the Linux VPS it's flipped to `true` and everything works automatically. This let me develop and test everything locally without needing Nginx running.

---

## 4. Teamwork & Process

**Q: How did you plan the 15-day structure?**
> I broke the project into logical layers — each day builds on the previous one. Days 1-3 were the data layer (schema, entities, auth). Days 4-6 were the Docker integration. Days 7-9 were advanced features (SSE, Nginx, cleanup). Day 10 was a testing checkpoint before the frontend. Days 11-13 were the UI layer. Days 14-15 were polish and deployment.

**Q: How did you test your work?**
> Primarily through Postman for the backend API — I had a collection with all endpoints organized by feature. For the frontend I tested manually in the browser. On Day 10 I did a full end-to-end test covering all flows: auth, project CRUD, deploy, SSE streaming, stop, restart, redeploy, and delete.

**Q: What would you add if you had more time?**
> - GitHub webhook integration for auto-deploy on `git push`
> - Private repository support via GitHub OAuth token
> - Environment variable injection per deployment
> - Database provisioning (spin up a MySQL container per project)
> - Resource limits (CPU/memory) per container
> - Team collaboration (multiple users per project)
> - Deploy previews for pull requests

---

## 5. Learning & Growth

**Q: What did you learn from this project?**
> I learned how container orchestration platforms work under the hood — the entire lifecycle from source code to running container. I deepened my understanding of Spring Security, async programming in Java, Server-Sent Events, and how to handle real-world dependency conflicts. On the frontend I learned how to consume SSE streams without the native EventSource API limitation.

**Q: What is the most interesting technical concept you learned?**
> Server-Sent Events with resume support via `Last-Event-ID`. The idea that a client can disconnect, reconnect, and seamlessly continue receiving a stream from where it left off — using nothing but a standard HTTP header — is elegant. Most people would jump to WebSockets for this but SSE is simpler and perfectly suited for one-way streaming like build logs.

**Q: How does this project compare to real platforms like Heroku?**
> The core pipeline is the same — clone, build, run, proxy. But real platforms add: private repo support, environment variables, databases, custom domains with SSL, horizontal scaling, health checks, rollbacks, build caches, team management, billing, and massive infrastructure. AutoDeploy is the proof-of-concept that shows I understand the fundamentals.

---

## 6. Scenario Questions

**Q: What happens if Docker daemon goes down while an app is deploying?**
> The `dockerClient.buildImageCmd()` call would throw a `DockerClientException`. This is caught by the `try-catch` in `runDeploymentPipeline`, the deployment status is set to `FAILED`, and an error log is saved. The SSE stream would send `event: status / data: FAILED` and close.

**Q: What happens if two users deploy the same repository simultaneously?**
> They would each get their own isolated deployment — separate build directories (`C:/tmp/deployments/{deploymentId}`), separate Docker images (`app-{projectId}-{hash}`), separate containers, and separate ports. No conflicts since everything is scoped by `deploymentId`.

**Q: What happens if the build directory isn't cleaned up after a failed build?**
> The `CleanupScheduler` runs hourly and calls `cleanupOrphanedBuildDirs()` which scans `C:/tmp/deployments/` for directories whose `deploymentId` no longer exists in the database, and deletes them with `FileUtils.deleteDirectory()`.

**Q: How would you prevent a user from deploying a malicious Dockerfile?**
> This is an important security concern. Mitigations would include:
> - Running containers with limited Linux capabilities (`--cap-drop ALL`)
> - No `--privileged` flag
> - CPU and memory limits per container
> - Network isolation — containers on a separate Docker network
> - Scanning the Dockerfile for dangerous instructions before building
> - Running the build in a sandboxed environment
> Currently AutoDeploy doesn't implement these — it's a dev tool, not a multi-tenant public platform.
