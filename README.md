# CQRS Order Service

Production-grade CQRS + DDD order and inventory service with GraphQL, JWT authentication, and a full observability stack.

| | |
|---|---|
| **Java** | 25 LTS |
| **Spring Boot** | 4.0.1 |
| **Concurrency** | Virtual Threads (Project Loom) |
| **Database** | PostgreSQL 17 |
| **Migrations** | Flyway |
| **API** | REST (OpenAPI) + GraphQL |
| **Auth** | JWT (HS256) + Role/Permission-based access |
| **Proxy** | Nginx (TLS 1.3, rate limiting, security headers) |
| **Monitoring** | Prometheus + Grafana |

---

## Quick Start

### Prerequisites
- Java 25 — https://jdk.java.net/25/
- Maven 3.9+
- Docker + Docker Compose

### Option 1 — H2 in-memory (no Docker)

Fastest way to get running. No database setup needed.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

### Option 2 — Local app + infra in Docker (recommended for development)

Runs PostgreSQL, PgBouncer, Prometheus, and Grafana in Docker.
Your Spring Boot app runs locally so you get fast restarts and debugger access.

```bash
# Step 1 — start infra only
make docker-up-infra

# Step 2 — run app locally against that infra
make run
```

### Option 3 — Full Docker stack

Everything in containers, production-like.

```bash
make docker-up
```

---

## Service URLs

| Service | URL | Notes |
|---|---|---|
| REST API | http://localhost:8080/api/v1 | |
| Swagger UI | http://localhost:8080/swagger-ui.html | |
| GraphQL endpoint | http://localhost:8080/graphql | POST |
| GraphiQL browser IDE | http://localhost:8080/graphiql | dev only |
| Prometheus | http://localhost:9091 | |
| Grafana | http://localhost:3000 | admin / admin |
| PostgreSQL | localhost:5432 | db=orders\_db user=orders\_user |

---

## Authentication

All API endpoints (REST and GraphQL) require a JWT token.

### 1. Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}' | jq .
```

### 2. Use the token

```bash
export TOKEN="<token from login response>"

# REST
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders

# GraphQL
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query":"{ lowStock(threshold: 5) { productId sku quantityAvailable } }"}' | jq .
```

### Test credentials (test / local profiles)

| Username | Password | Role |
|---|---|---|
| `admin` | `changeme` | ROLE\_ADMIN — full access including pricing fields |
| `testuser` | `testpass` | ROLE\_USER — no access to pricing fields |

---

## Make Commands

```bash
make docker-up-infra   # start infra only (postgres, pgbouncer, prometheus, grafana) — no app
make docker-up         # start full stack including app container
make docker-down       # stop all containers
make run               # run app locally (local profile, connects to docker infra)
make test              # run all tests
make build             # build production JAR
make postman           # generate Postman collection (offline)
make load-test         # k6 load test
make stress-test       # k6 stress test
make pentest           # security penetration tests
make clean             # remove build artifacts and containers
```

---

## GraphQL

The inventory domain is fully exposed via GraphQL. See **[docs/GRAPHQL.md](docs/GRAPHQL.md)** for the complete reference including:

- Architecture and request lifecycle diagrams
- All queries and mutations with curl examples
- Field-driven SQL generation (only requested columns are fetched from DB)
- Field-level authorization (`unitPrice`, `totalReceived` etc. require `INVENTORY_PRICE` permission)
- Automatic Persisted Queries (APQ)
- Query complexity and depth limits

Quick example:

```graphql
query {
  lowStock(threshold: 10, limit: 50) {
    productId
    sku
    productName
    quantityAvailable
    warehouseName
  }
}
```

---

## Architecture

```
HTTP Client
  └── Nginx (TLS, rate-limit, security headers)
        ├── REST: OrderController / InventoryController   [Presentation]
        │           ├── commandBus.dispatch(cmd)
        │           │     └── CommandBus → PlaceOrder/Confirm/CancelOrderCommandHandler
        │           └── queryBus.dispatch(query)
        │                 └── QueryBus  → GetOrderById/ListOrdersByCustomerQueryHandler
        │                                       └── OrderRepository → PostgreSQL
        │
        └── GraphQL: InventoryGraphQlController
                      ├── queryBus.dispatch(query, fields)
                      │     └── GetInventoryReport/ProductStock/LowStockQueryHandler
                      │           └── Dynamic SQL (only requested columns) → PostgreSQL
                      └── InventoryFieldAuthController
                            └── @SchemaMapping — nulls sensitive fields for ROLE_USER
```

### CQRS + DDD Layers

**Domain** (`domain/`) — zero Spring dependencies
- `Order` aggregate root: state machine `PENDING → CONFIRMED → SHIPPED → DELIVERED` (or `→ CANCELLED`)
- `Money`, `OrderItem` — immutable value objects

**Application** (`application/`) — use cases as commands/queries
- Commands in `application/command/`, handlers in `application/handler/command/`
- Queries in `application/query/`, handlers in `application/handler/query/`
- Query records carry a `Set<String> fields` — GraphQL passes only requested field names, REST passes `Set.of()` (all)

**Bus** (`bus/`) — handler registry
- `CommandBus` / `QueryBus` auto-discover handlers via Spring DI — adding `@Component` is enough

**Infrastructure** (`infrastructure/persistence/`) — bridges domain ↔ JPA
- `OrderRepository` ↔ `OrderJpaRepository` via `OrderJpaEntity`
- User/Role/Permission JPA entities back the DB-based authentication

**Presentation** (`presentation/`)
- `OrderController` / `InventoryController` — REST
- `InventoryGraphQlController` — GraphQL queries and mutations
- `InventoryFieldAuthController` — GraphQL field-level authorization (SRP separation)
- `AuthController` — `/api/v1/auth/login` issues JWT tokens
- `SecurityConfig` — stateless JWT, `DaoAuthenticationProvider`, CSRF disabled
- `PersistedQueryFilter` — APQ hash registry (runs before Spring Security)
- `GraphQlConfig` — complexity (max 100) and depth (max 10) instrumentation

### Virtual Threads

`spring.threads.virtual.enabled=true` enables virtual threads across Tomcat, `@Async`, and `@Scheduled`.
JDBC blocking calls yield the carrier thread, so thousands of virtual threads share a small HikariCP pool (10 connections).

---

## Security

- **Authentication:** JWT HS256, stateless (no sessions)
- **Authorization:** Role + Permission model stored in DB (`users → user_roles → roles → role_permissions → permissions`)
- **Field-level auth:** Sensitive GraphQL fields (`unitPrice`, `totalReceived`, `totalShipped`, `transactionCount`) return `null` for users without `INVENTORY_PRICE` permission
- **GraphQL protection:** Query complexity limit (100) and depth limit (10) — rejects expensive queries before execution
- **Persisted Queries:** Clients send a hash instead of full query text on repeat requests
- **Transport:** Nginx TLS 1.3, security headers, rate limiting
- **Secrets:** All via environment variables — see `.env.example`
- **Management port (9090):** Never exposed externally — Prometheus scrapes it internally

---

## Environment Variables

Copy `.env.example` to `.env` and fill in production values:

```bash
cp .env.example .env
```

| Variable | Required | Description |
|---|---|---|
| `DB_PASSWORD` | yes | PostgreSQL password |
| `APP_JWT_SECRET` | yes | Base64-encoded HS256 key (min 32 bytes). Generate: `openssl rand -base64 32` |
| `APP_JWT_EXPIRATION_MS` | no | Token TTL in ms (default: 86400000 = 24h) |
| `GRAFANA_USER` | no | Grafana admin username (default: admin) |
| `GRAFANA_PASS` | no | Grafana admin password |

---

## Code Style

Google Java Format (2-space indent, 100-char lines) + Checkstyle naming rules. Both enforced on every Maven build.

```bash
mvn spotless:apply   # auto-fix formatting
mvn validate         # check formatting + Checkstyle (run before pushing)
```

Checkstyle rules (must be fixed manually): no wildcard imports, `UPPER_SNAKE_CASE` constants, one statement per line, `switch` must have `default`, array brackets on type.

---

## Testing

```bash
make test                              # all tests (H2 in-memory, test profile)
mvn test -Dtest=OrderIntegrationTest   # single test class
```

Test profile uses H2 with `ddl-auto: create-drop` and seeds users via `src/test/resources/data.sql`.

---

## Postman Collection

```bash
make postman                                       # generates postman/collection.json
BASE_URL=https://staging.example.com make postman  # override base URL
```

Import into Postman via **Import → Upload File**. Includes auto-login (JWT), sample request bodies, and saved variables (`orderId`, `token`).
