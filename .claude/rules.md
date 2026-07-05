# F1 Telemetry Project Rules & Coding Standards

This document establishes the patterns, code styles, and technical constraints that MUST be followed when making enhancements or writing new features for this project.

---

## 1. Core Technology Stack

- **Backend Core**: Java 21 (LTS) utilizing Spring Boot 3.4.x.
- **UDP Ingestion Layer**: Netty Core (`io.netty.channel`) for low-latency asynchronous socket event loops.
- **Persistence**: Spring Data JPA with a PostgreSQL 15+ backend database.
- **Real-Time Layer**: Spring WebSockets utilizing the STOMP message broker sub-protocol.
- **Frontend SPA**: Vanilla JavaScript, CSS variables (no Tailwind CSS), HTML5 Canvas, and Chart.js.

---

## 2. Coding Standards & Conventions

### Java Style Guidelines
- **Naming**: Use standard Java conventions. PascalCase for classes/interfaces, camelCase for methods/variables, and UPPER_SNAKE_CASE for static final constants.
- **Lombok**: Use `@Getter`, `@Setter`, `@RequiredArgsConstructor`, and `@Slf4j` where appropriate to minimize boilerplate. Avoid using `@Data` on entity classes to prevent performance and infinity-loop issues in Hibernate mappings.
- **Modern Java Idioms**: Prefer switch expressions, pattern matching for `instanceof`, records for immutable DTOs, and block text for SQL or multi-line strings.

### REST API Style Guidelines
- **Response Schemas**: Wrap responses in consistent DTO schemas.
- **HTTP Methods**: Strictly map resources (GET to fetch, POST to create, PUT/PATCH to modify, DELETE to remove).
- **Security**: Secure all business and history routes using JWT authorization filters. Only authenticate user endpoints `/api/auth/register` and `/api/auth/login` as open routes.

---

## 3. High-Performance Constraints (CRITICAL)

### Low-Allocation Parsing
- **Zero GC Pause Objective**: The UDP ingestion pipeline runs at up to 60Hz. Unpacking binary data must avoid allocating unnecessary short-lived objects.
- **ByteBuf Allocation**: Use Netty's reference-counted `ByteBuf` instead of creating large byte arrays. Ensure that resources are released correctly via standard Netty pipelines or explicit `ReferenceCountUtil.release()`.

### Thread Boundary Isolation
- **Netty Event Loops**: Keep the UDP ingestion event loop thread non-blocking. Never run database writes, JPA queries, or complex disk IO on the Netty ingestion threads.
- **Asynchronous Storage**: Offload telemetry persistence using an unbounded in-memory buffer (`LinkedBlockingQueue`). Flush metrics to PostgreSQL in bulk updates on a separate scheduler thread annotated with `@Async("persistenceTaskExecutor")`.

---

## 4. Logging & Exception Handlers

### Throttled Logging
- **Console Optimization**: At 60Hz UDP data streams, writing logs on every packet will flood the terminal and degrade application execution speeds.
- **Metric Gates**: Implement counter filters (e.g., `count % 60 == 0`) inside packet listeners to log status updates only once per second.

### Robust Exception Handling
- **Channel Recovery**: When a parsing error occurs, do not let it crash the Netty pipeline or terminate the listening loop. Catch exceptions locally, increment error counters, and log diagnostic info throttled.
- **API Exceptions**: Map REST controllers with `@ControllerAdvice` and `@ExceptionHandler` returning custom JSON structures containing user-friendly error codes instead of stack traces.
