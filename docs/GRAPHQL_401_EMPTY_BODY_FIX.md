# GraphQL: 401 Unauthorized & Empty Response Body — Root Cause & Fix

## Symptoms

- `POST /graphql` with a valid JWT returns **HTTP 401** (no data, no errors).
- Even after confirming in the debugger that `inventoryReport()` returned data,
  the client receives an **empty body** (`Content-Length: 0`).
- REST endpoints (`/api/v1/**`) with the same JWT work fine (HTTP 200).

---

## Root Cause 1 — Expired JWT token

The simplest reason for a 401 is an expired token. The default token lifetime is
**24 hours** (`APP_JWT_EXPIRATION_MS=86400000`). If the token in the `Authorization`
header is older than 24 hours, Spring Security rejects the request before it reaches
the controller.

**Fix:** obtain a fresh token before every test session.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

---

## Root Cause 2 — Spring for GraphQL uses async (deferred) response writing

### What happens

Spring for GraphQL (`GraphQlHttpHandler`) does **not** write the HTTP response body
synchronously inside `doFilter()`. It starts the GraphQL execution and writes the
response body **after** `chain.doFilter()` returns, via an async servlet dispatch
(`DispatcherType.ASYNC`).

```
First REQUEST dispatch
  └─ chain.doFilter() returns immediately (execution deferred)
  └─ any filter's finally-block runs ← response body not written yet

ASYNC dispatch (triggered internally by Spring for GraphQL)
  └─ GraphQL execution completes
  └─ response body is written here
```

### Why this caused HTTP 401

`SecurityConfig` only configured JWT authentication for the initial `REQUEST`
dispatch. When the `ASYNC` dispatch fired, Spring Security ran again but found
**no SecurityContext** — `JwtAuthFilter` is an `OncePerRequestFilter` that skips
async dispatches by default, and with `SessionCreationPolicy.STATELESS` the
context is never saved to a session. Spring Security then called
`response.sendError(401)`, overwriting the response.

**Fix** — permit `ASYNC` and `ERROR` dispatch types in `SecurityConfig`; the initial
`REQUEST` dispatch is already authenticated:

```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
    .requestMatchers("/actuator/**").permitAll()
    // ... rest of rules
)
```

### Why the response body was empty (`Content-Length: 0`)

`RequestLoggingFilter` wrapped every response with `ContentCachingResponseWrapper`.
In the `finally` block it called `copyBodyToResponse()` — but this ran during the
initial `REQUEST` dispatch before Spring for GraphQL had written anything. The
wrapper's buffer was empty, so `copyBodyToResponse()` set `Content-Length: 0` and
committed the real response with no body. When the `ASYNC` dispatch later tried to
write the actual JSON, the response was already committed and the bytes were dropped.

**Fix** — `RequestLoggingFilter` now skips `/graphql` entirely via `shouldNotFilter()`.
A dedicated `GraphQlLoggingInterceptor` (`WebGraphQlInterceptor`) handles GraphQL
request/response logging instead, running inside the GraphQL pipeline where the
full execution result is always available.

```
Before fix:                          After fix:
RequestLoggingFilter                 RequestLoggingFilter
  wraps response                       shouldNotFilter("/graphql") → true
  copyBodyToResponse() → empty         → does nothing for /graphql
  Content-Length: 0 committed
                                     GraphQlLoggingInterceptor
ASYNC dispatch                         intercepts inside GraphQL pipeline
  Security → 401                       logs op, timing, data/errors
  body write ignored
```

---

## Root Cause 3 — PostgreSQL cannot infer the type of a NULL parameter

When `categoryId` or `warehouseId` is `null` (not provided in the GraphQL query),
`NamedParameterJdbcTemplate` sends a typeless `NULL` to PostgreSQL. The SQL
pattern `(? IS NULL OR column = ?)` causes:

```
PSQLException: ERROR: could not determine data type of parameter $2
```

PostgreSQL needs to know the column type to evaluate `$2 IS NULL` in a prepared
statement when the value is `NULL`.

**Fix** — use `MapSqlParameterSource` with an explicit `Types.VARCHAR` for nullable
string parameters:

```java
// GetInventoryReportQueryHandler.java
MapSqlParameterSource params = new MapSqlParameterSource()
    .addValue("minStock",     query.minStock())
    .addValue("categoryId",   query.categoryId(),   Types.VARCHAR)  // explicit type
    .addValue("warehouseId",  query.warehouseId(),  Types.VARCHAR)  // explicit type
    .addValue("pageSize",     query.pageSize())
    .addValue("offset",       (long) query.page() * query.pageSize());
```

This tells the JDBC driver the SQL type to use when binding `NULL`, so PostgreSQL
can evaluate `NULL::varchar IS NULL` correctly.

---

## Files Changed

| File | Change |
|---|---|
| `SecurityConfig.java` | Added `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()` |
| `RequestLoggingFilter.java` | `shouldNotFilter()` now also returns `true` for `/graphql` |
| `GraphQlLoggingInterceptor.java` | New — logs GraphQL op, timing, user, data/errors |
| `GetInventoryReportQueryHandler.java` | Switched to `MapSqlParameterSource` with `Types.VARCHAR` for nullable params |
