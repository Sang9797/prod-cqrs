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

**Authentication:** DB-based via `AppUserDetailsService` → `UserJpaRepository`. Users, roles, and permissions are stored in the `users`, `roles`, `permissions`, `user_roles`, `role_permissions` tables. Seeded by Flyway (prod) or `src/test/resources/data.sql` (test profile).

**Profiles:**
- `test` — H2 in-memory, Flyway disabled, JPA `create-drop`, users seeded from `data.sql`
- `local` — PostgreSQL on localhost
- `prod` — PostgreSQL via PgBouncer (Docker Compose), secrets from env vars

**Flyway migrations:** `src/main/resources/db/migration/` — runs automatically on startup (prod/local profiles).
- `V1` — orders schema
- `V2` — inventory schema
- `V3` — seed inventory data
- `V4` — users/roles/permissions schema

**Environment variables (prod):** `APP_JWT_SECRET` (required, base64 HS256 key), `DB_PASSWORD` (required). See `.env.example`. `APP_ADMIN_USER` / `APP_ADMIN_PASS` are **not used** — users are managed via the DB.

**Checkstyle rules** (cannot be auto-fixed): no wildcard imports, `UPPER_SNAKE_CASE` constants, one statement per line, `switch` must have `default`, array brackets on type.
