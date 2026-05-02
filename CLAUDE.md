# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Quick start (H2 in-memory)
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Full stack (PostgreSQL, Nginx, Prometheus, Grafana)
make docker-up

# Testing
mvn test
make test

# Code style (must pass before committing)
mvn spotless:apply    # auto-format (Google Java Format, 2-space indent, 100-char lines)
mvn validate          # check formatting + Checkstyle naming rules — fails on violations

# Build
mvn clean package -DskipTests

# Generate Postman collection (offline, no running app needed)
make postman                                    # generates postman/collection.json
BASE_URL=https://staging.example.com make postman  # override base URL

# Docker cleanup
make docker-down
make clean
```

## Architecture

This is a CQRS + DDD order service. Commands (writes) and queries (reads) flow through independent buses to independent handlers.

```
OrderController
  ├─ commandBus.dispatch(cmd) → CommandBus → PlaceOrder/Confirm/CancelOrderCommandHandler
  └─ queryBus.dispatch(query) → QueryBus  → GetOrderById/ListOrdersByCustomerQueryHandler
                                                    ↓
                                             OrderRepository → PostgreSQL
```

### Layers

**Domain** (`domain/`) — zero Spring dependencies
- `Order` aggregate root: state machine `PENDING → CONFIRMED → SHIPPED → DELIVERED` (or `→ CANCELLED`)
- `Money`, `OrderItem` — immutable value objects
- `OrderStatus` — enum with `isCancellable()` behavior

**Application** (`application/`) — use cases as commands/queries
- Commands in `application/command/`, handlers in `application/handler/command/` — each `@Transactional`
- Queries in `application/query/`, handlers in `application/handler/query/`

**Bus** (`bus/`) — handler registry pattern
- `CommandBus` / `QueryBus` discover handlers via Spring DI at startup (each handler declares its type via `commandType()`)
- Adding `@Component` to a new handler is sufficient — no manual registration

**Infrastructure** (`infrastructure/persistence/`) — bridges domain ↔ JPA
- `OrderRepository` (domain interface) ↔ `OrderJpaRepository` (Spring Data) via `OrderJpaEntity`

**Presentation** (`presentation/`)
- `OrderController` depends only on `CommandBus` + `QueryBus`
- `GlobalExceptionHandler`: `InvalidOrderStateException` → 409, `OrderNotFoundException` → 404
- `SecurityConfig`: stateless, HTTP Basic, CSRF disabled; `/api/v1/**` requires auth; health/swagger public

### Adding a new command

1. `application/command/ShipOrderCommand.java` — record implementing `Command<R>`
2. `application/handler/command/ShipOrderCommandHandler.java` — `@Component @Transactional`, implements `CommandHandler<ShipOrderCommand, R>`
3. Domain method on `Order` for the business logic
4. Endpoint in `OrderController`

CommandBus discovers the handler automatically.

## Key Technical Details

**Virtual Threads (Java 25):** `spring.threads.virtual.enabled=true` enables virtual threads across Tomcat, `@Async`, and `@Scheduled`. HikariCP pool is 10 connections — blocking JDBC calls yield the carrier thread, so thousands of vthreads share a small pool.

**Profiles:**
- `test` — H2 in-memory, Flyway disabled, JPA creates schema
- `local` — PostgreSQL on localhost
- `prod` — PostgreSQL via PgBouncer (Docker Compose), secrets from env vars

**Flyway migrations:** `src/main/resources/db/migration/V1__create_orders_schema.sql` runs automatically on startup.

**Checkstyle rules** (cannot be auto-fixed): no wildcard imports, `UPPER_SNAKE_CASE` constants, one statement per line, `switch` must have `default`, array brackets on type.
