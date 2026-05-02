# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Quick start (H2 in-memory, no Docker)
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Local development — infra in Docker, app runs locally
make docker-up-infra   # postgres, pgbouncer, prometheus, grafana only
make run               # app with local profile connecting to docker infra

# Full Docker stack
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

This is a CQRS + DDD order and inventory service. Commands (writes) and queries (reads) flow through independent buses to independent handlers.

```
OrderController / InventoryController (REST)
  ├─ commandBus.dispatch(cmd) → CommandBus → PlaceOrder/Confirm/CancelOrderCommandHandler
  └─ queryBus.dispatch(query) → QueryBus  → GetOrderById/ListOrdersByCustomerQueryHandler
                                                    ↓
                                             OrderRepository → PostgreSQL

InventoryGraphQlController (GraphQL)
  ├─ queryBus.dispatch(query, fields) → GetInventoryReport/ProductStock/LowStockQueryHandler
  │                                           ↓ dynamic SQL — only requested columns fetched
  │                                     NamedParameterJdbcTemplate → PostgreSQL
  └─ InventoryFieldAuthController — @SchemaMapping per sensitive field, checks Authentication
```

### Layers

**Domain** (`domain/`) — zero Spring dependencies
- `Order` aggregate root: state machine `PENDING → CONFIRMED → SHIPPED → DELIVERED` (or `→ CANCELLED`)
- `Money`, `OrderItem` — immutable value objects
- `OrderStatus` — enum with `isCancellable()` behavior

**Application** (`application/`) — use cases as commands/queries
- Commands in `application/command/`, handlers in `application/handler/command/` — each `@Transactional`
- Queries in `application/query/`, handlers in `application/handler/query/`
- Query records carry `Set<String> fields` — GraphQL passes only requested fields, REST passes `Set.of()` (means all)

**Bus** (`bus/`) — handler registry pattern
- `CommandBus` / `QueryBus` discover handlers via Spring DI at startup (each handler declares its type via `commandType()`)
- Adding `@Component` to a new handler is sufficient — no manual registration

**Infrastructure** (`infrastructure/persistence/`) — bridges domain ↔ JPA
- `OrderRepository` (domain interface) ↔ `OrderJpaRepository` (Spring Data) via `OrderJpaEntity`
- `UserJpaEntity`, `RoleJpaEntity`, `PermissionJpaEntity` — back DB-based authentication

**Presentation** (`presentation/`)
- `OrderController` / `InventoryController` — REST, depend only on `CommandBus` + `QueryBus`
- `InventoryGraphQlController` — GraphQL query/mutation routing (no auth logic)
- `InventoryFieldAuthController` — GraphQL field-level auth via `@SchemaMapping` (SRP: auth only)
- `AuthController` — `POST /api/v1/auth/login` issues JWT tokens
- `GlobalExceptionHandler`: `InvalidOrderStateException` → 409, `OrderNotFoundException` → 404
- `SecurityConfig`: stateless JWT, `DaoAuthenticationProvider` + `AppUserDetailsService` (DB users), CSRF disabled
- `PersistedQueryFilter` (`@Order(1)`): APQ hash registry — runs before Spring Security
- `GraphQlConfig`: complexity limit (100) + depth limit (10) instrumentation

### Adding a new command

1. `application/command/ShipOrderCommand.java` — record implementing `Command<R>`
2. `application/handler/command/ShipOrderCommandHandler.java` — `@Component @Transactional`, implements `CommandHandler<ShipOrderCommand, R>`
3. Domain method on `Order` for the business logic
4. Endpoint in `OrderController`

CommandBus discovers the handler automatically.

### Adding a new GraphQL query

1. Add the type and query to `src/main/resources/graphql/inventory.graphqls`
2. Create a query record in `application/query/` with `Set<String> fields`
3. Create a handler in `application/handler/query/` — use `COLUMN_MAP` + `buildSelect(fields)` pattern for field-driven SQL
4. Add `@QueryMapping` method in `InventoryGraphQlController` — call `requestedFields(env)` and pass to query record
5. If the query returns sensitive fields, add `@SchemaMapping` methods in `InventoryFieldAuthController`

## Key Technical Details

**Virtual Threads (Java 25):** `spring.threads.virtual.enabled=true` enables virtual threads across Tomcat, `@Async`, and `@Scheduled`. HikariCP pool is 10 connections — blocking JDBC calls yield the carrier thread, so thousands of vthreads share a small pool.

**Field-driven SQL (GraphQL):** Query handlers build a dynamic `SELECT` clause from the `fields` set passed by the controller. The expensive `inventory_transactions` JOIN is skipped entirely when none of `totalReceived`, `totalShipped`, `transactionCount`, `lastMovement` are requested. See `docs/GRAPHQL.md` §8 for full detail.

**Authentication:** DB-based via `AppUserDetailsService` → `UserJpaRepository`. Users, roles, and permissions are stored in the `users`, `roles`, `permissions`, `user_roles`, `role_permissions` tables. Seeded by Liquibase (prod/local) or `src/test/resources/data.sql` (test profile).

**Profiles:**
- `test` — H2 in-memory, Liquibase disabled, JPA `create-drop`, users seeded from `data.sql`
- `local` — PostgreSQL on localhost
- `prod` — PostgreSQL via PgBouncer (Docker Compose), secrets from env vars

**Liquibase migrations:** `src/main/resources/db/changelog/` — runs automatically on startup (prod/local profiles).
- `001` — orders schema
- `002` — inventory schema
- `003` — seed inventory data
- `004` — users/roles/permissions schema + seed users (`admin` / `adminpass`, `john` / `userpass`)

**Test credentials (local/prod profiles — from Liquibase seed):**
- `admin` / `admin123` (ROLE_ADMIN)
- `john` / `userpass` (ROLE_USER)

**Environment variables (prod):** `APP_JWT_SECRET` (required, base64 HS256 key), `DB_PASSWORD` (required). See `.env.example`.

**Observability:**
- Actuator management port: `9090` — all `/actuator/**` endpoints are permitted without JWT
- Prometheus scrapes `http://172.24.0.1:9090/actuator/prometheus` when running with `make docker-up-infra` + `make run` on Linux (Docker monitoring network gateway). `host.docker.internal` does **not** resolve on Linux.
- Custom Micrometer metrics: `orders_placed_total`, `orders_confirmed_total`, `orders_cancelled_total`, `inventory_reservations_total`, `inventory_releases_total`, `inventory_adjustments_total`, `cqrs_command_duration_seconds{command}`, `cqrs_query_duration_seconds{query}`
- Grafana dashboards auto-provisioned: community ID 19004 (Spring Boot 3.x — correct for SB3 + G1GC, uses `process_uptime_seconds` / `area="heap"`) downloaded by `docker/grafana/entrypoint.sh` on first start; custom CQRS business metrics dashboard at `docker/grafana/provisioning/dashboards/cqrs-business-metrics.json`. Do NOT use dashboard 4701 — it targets Spring Boot 2.x metric names that no longer exist.

**Checkstyle rules** (cannot be auto-fixed): no wildcard imports, `UPPER_SNAKE_CASE` constants, one statement per line, `switch` must have `default`, array brackets on type.
