# CQRS Order Service

Production-grade CQRS with Command/Query Bus.

| | |
|---|---|
| **Java** | 25 LTS (September 2025) |
| **Spring Boot** | 4.0.1 |
| **Concurrency** | Virtual Threads (Project Loom) |
| **Database** | PostgreSQL 17 |
| **Migrations** | Flyway |
| **Proxy** | Nginx (TLS 1.3, rate limiting, security headers) |
| **Monitoring** | Prometheus + Grafana |

---

## Quick start

### Prerequisites
- Java 25 — download from https://jdk.java.net/25/
- Maven 3.9+
- Docker + Docker Compose

### Run locally (H2 in-memory, no Docker needed)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
```
Open http://localhost:8080/swagger-ui.html

### Run full production stack
```bash
make docker-up
```

| Service | URL |
|---|---|
| API | http://localhost:8080/api/v1/orders |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3000 (admin/admin) |

### Run tests
```bash
make test
```

### Run penetration tests
```bash
make docker-up
make pentest
```

### Generate Postman collection
```bash
make postman
```
Generates `postman/collection.json` offline (no running app needed).
Import it into Postman via **Import → Upload File**.

The collection includes auto-login (JWT), sample request bodies, and saved variables (`orderId`, `token`).
Override the base URL: `BASE_URL=https://staging.example.com make postman`

---

## Code Style & Formatting

This project enforces **Google Java Format** (formatting) and **Checkstyle** (naming & coding standards).
Both checks run automatically on every Maven build — any violation fails the build immediately.

### Auto-format all source files

Run this before committing to fix all formatting issues in one step:

```bash
mvn spotless:apply
```

What it fixes automatically:
- Indentation (2 spaces, Google style)
- Import ordering and grouping
- Line wrapping at 100 characters
- Trailing whitespace and missing newlines at end of file

### Check formatting without changing files

```bash
mvn spotless:check
```

Exits with an error and shows a diff for every file that is not correctly formatted.
Does **not** modify any file — safe to run in CI.

### Check code style (naming, imports, coding rules)

```bash
mvn checkstyle:check
```

Enforces:
- No wildcard imports (`import java.util.*` is forbidden)
- Naming conventions — `UpperCamelCase` for types, `UPPER_SNAKE_CASE` for `static final` constants, `lowerCamelCase` for methods and fields
- One statement per line
- No multiple variable declarations on one line
- Every `switch` must have a `default` case
- `switch` fall-through must be intentional (documented)
- Array brackets on the type (`String[] args`, not `String args[]`)
- Long literals use uppercase `L` (`100L`, not `100l`)

Configuration file: [`checkstyle.xml`](./checkstyle.xml) at project root.

### Run all checks together (recommended before pushing)

```bash
mvn validate
```

Runs Spotless check then Checkstyle in sequence. This is also triggered automatically by
`mvn compile`, `mvn test`, `mvn package`, and any other Maven goal.

### Typical fix workflow

```bash
# 1. Auto-format everything
mvn spotless:apply

# 2. Verify formatting is now clean
mvn spotless:check

# 3. Check naming and coding rules (manual fixes required if violations remain)
mvn checkstyle:check

# 4. Run full build to confirm everything passes
mvn validate
```

### IDE setup

To get format-on-save behaviour in your editor:

**IntelliJ IDEA**
1. Install the [google-java-format plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format)
2. Enable it under **Settings → google-java-format Settings → Enable google-java-format**
3. Install the [Save Actions plugin](https://plugins.jetbrains.com/plugin/7642-save-actions) and enable **Reformat file** on save

**VS Code**
1. Install the [Language Support for Java](https://marketplace.visualstudio.com/items?itemName=redhat.java) extension
2. Set `"java.format.settings.url"` to point to a Google style XML or use the Spotless extension

**From the terminal (any editor)**
```bash
# Run before every commit — formats only staged/changed files is not supported,
# so format all and stage again:
mvn spotless:apply && git add -u
```

---

## Architecture

```
HTTP Client
  └── Nginx (TLS, rate-limit, security headers)
        └── OrderController     [Presentation]
              ├── commandBus.dispatch(cmd)
              │     └── CommandBus → PlaceOrderCommandHandler
              │                      ConfirmOrderCommandHandler
              │                      CancelOrderCommandHandler
              │                           └── OrderRepository → PostgreSQL
              └── queryBus.dispatch(query)
                    └── QueryBus  → GetOrderByIdQueryHandler
                                    ListOrdersByCustomerQueryHandler
                                         └── OrderRepository → PostgreSQL
```

### Virtual Threads
Enabled via `spring.threads.virtual.enabled=true`.
- Each HTTP request runs on a virtual thread
- JDBC blocking calls yield the carrier thread (no thread starvation)
- Small HikariCP pool (10 connections) serves thousands of concurrent requests
- ZGC with generational mode for low-latency GC

### Adding a new operation
1. Create `MyNewCommand.java` implementing `Command<R>`
2. Create `MyNewCommandHandler.java` with `@Component`
3. Add endpoint to `OrderController`

The `CommandBus` discovers the new handler automatically. Nothing else changes.

---

## Security

- Stateless API (no sessions)
- HTTP Basic auth (replace with JWT in production)
- CSRF disabled (stateless REST APIs don't need it)
- All secrets via environment variables (never hardcoded)
- Nginx: TLS 1.3 only, security headers, rate limiting
- Management port (9090) never exposed externally
- Non-root Docker container
- ZGC + container-aware JVM flags
