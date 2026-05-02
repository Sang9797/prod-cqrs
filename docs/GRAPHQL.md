# GraphQL — Inventory API

> Complete reference for the GraphQL layer of the CQRS Order Service.  
> Covers architecture, authentication, field-level authorization, persisted queries,
> query protection, field-driven SQL generation, and all operations with copy-paste examples.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Request Lifecycle — Sequence Diagram](#2-request-lifecycle--sequence-diagram)
3. [Authentication](#3-authentication)
4. [Endpoints & Tooling](#4-endpoints--tooling)
5. [Schema](#5-schema)
6. [Queries](#6-queries)
7. [Mutations](#7-mutations)
8. [Field-Driven SQL Generation](#8-field-driven-sql-generation)
9. [Field-Level Authorization](#9-field-level-authorization)
10. [Persisted Queries (APQ)](#10-persisted-queries-apq)
11. [Query Protection — Complexity & Depth Limits](#11-query-protection--complexity--depth-limits)
12. [Error Reference](#12-error-reference)
13. [Role & Permission Matrix](#13-role--permission-matrix)

---

## 1. Architecture Overview

```
HTTP POST /graphql
        │
        ▼
┌─────────────────────────┐   @Order(1)
│   PersistedQueryFilter  │  ── APQ hash lookup / registration
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│     JwtAuthFilter       │  ── validates Bearer token, populates SecurityContext
└────────────┬────────────┘
             │
             ▼
┌──────────────────────────────────────────┐
│         Spring GraphQL Engine            │
│                                          │
│  Instrumentation (runs before execution) │
│  ├─ MaxQueryComplexityInstrumentation    │  max score 100
│  └─ MaxQueryDepthInstrumentation         │  max depth 10
│                                          │
│  Controller dispatch                     │
│  ├─ InventoryGraphQlController           │  @QueryMapping / @MutationMapping
│  │      └─ QueryBus / CommandBus         │  → query handlers → JDBC / JPA
│  │                                       │
│  └─ InventoryFieldAuthController         │  @SchemaMapping (per sensitive field)
│         └─ checks Authentication         │  returns null when unauthorized
└──────────────────────────────────────────┘
```

**Separation of concerns across classes:**

| Class | Responsibility |
|---|---|
| `PersistedQueryFilter` | APQ hash registry — runs before Spring Security |
| `JwtAuthFilter` | JWT validation — populates `SecurityContextHolder` |
| `GraphQlConfig` | Registers complexity + depth instrumentation beans |
| `InventoryGraphQlController` | Routes queries/mutations to the CQRS bus |
| `InventoryFieldAuthController` | Enforces field-level authorization per GraphQL type |

---

## 2. Request Lifecycle — Sequence Diagram

### 2a. Normal authenticated query

```
Client          PersistedQueryFilter   JwtAuthFilter   Spring GraphQL   QueryBus   DB
  │                     │                   │                │              │        │
  │─ POST /graphql ────►│                   │                │              │        │
  │  Authorization:     │                   │                │              │        │
  │  Bearer <token>     │                   │                │              │        │
  │                     │                   │                │              │        │
  │             no APQ extensions           │                │              │        │
  │             pass through ──────────────►│                │              │        │
  │                     │                   │                │              │        │
  │                     │      validate JWT  │                │              │        │
  │                     │      set Auth ────►│                │              │        │
  │                     │                   │                │              │        │
  │                     │                   │─ dispatch ─────►│              │        │
  │                     │                   │                │─ dispatch ──►│        │
  │                     │                   │                │              │─ SQL ─►│
  │                     │                   │                │              │◄── rows─│
  │                     │                   │                │◄─ result ────│        │
  │                     │                   │                │              │        │
  │                     │                   │   InventoryFieldAuthController         │
  │                     │                   │   resolves sensitive fields            │
  │                     │                   │   (null if INVENTORY_PRICE missing)    │
  │                     │                   │                │              │        │
  │◄─ 200 JSON ─────────────────────────────────────────────│              │        │
```

### 2b. Persisted Query — first request (register)

```
Client                       PersistedQueryFilter
  │                                  │
  │─ POST /graphql ─────────────────►│
  │  { "query": "query { ... }",     │
  │    "extensions": {               │
  │      "persistedQuery": {         │
  │        "sha256Hash": "abc123"    │
  │      }                           │
  │    }                             │
  │  }                               │
  │                                  │
  │                   registry.put("abc123", query)
  │                                  │
  │                   pass full request downstream
  │◄─ 200 normal response ──────────│
```

### 2c. Persisted Query — subsequent request (hash only)

```
Client                       PersistedQueryFilter
  │                                  │
  │─ POST /graphql ─────────────────►│
  │  { "extensions": {               │
  │      "persistedQuery": {         │
  │        "sha256Hash": "abc123"    │
  │      }                           │
  │    }                             │
  │  }                               │
  │                                  │
  │                   registry.get("abc123") → found
  │                   inject query into body
  │                   pass to downstream filters
  │◄─ 200 normal response ──────────│
```

### 2d. Persisted Query — unknown hash

```
Client                       PersistedQueryFilter
  │                                  │
  │─ POST /graphql ─────────────────►│
  │  hash only, no query body        │
  │                                  │
  │                   registry.get("xyz") → null
  │◄─ 200 PersistedQueryNotFound ───│
  │                                  │
  │  (client retries with full query — see 2b)
```

### 2e. Query rejected by complexity limit

```
Client                  Spring GraphQL Engine
  │                             │
  │─ POST /graphql (deep) ─────►│
  │                             │
  │                   MaxQueryComplexityInstrumentation
  │                   score > 100  → abort
  │◄─ 200 { errors: [...] } ───│
```

---

## 3. Authentication

All `/api/v1/graphql` operations require a JWT. Obtain one from the REST login endpoint first.

### Step 1 — Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}' | jq .
```

Response:

```json
{ "token": "eyJhbGci..." }
```

### Step 2 — Use the token

Add `Authorization: Bearer <token>` to every GraphQL request.

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGci..." \
  -d '{"query":"{ lowStock(threshold:5, limit:10) { productId sku quantityAvailable } }"}' \
  | jq .
```

### Token details

| Property | Value |
|---|---|
| Algorithm | HS256 |
| Default expiry | 24 hours (`APP_JWT_EXPIRATION_MS`) |
| Header | `Authorization: Bearer <token>` |
| Secret override | `APP_JWT_SECRET` env var |

---

## 4. Endpoints & Tooling

| URL | Purpose |
|---|---|
| `POST /graphql` | Main API endpoint |
| `GET /graphiql` | Browser IDE (dev/test only) |
| `GET /graphql/schema.graphqls` | Print full schema |

**GraphiQL** — open `http://localhost:8080/graphiql` in your browser.  
Add the header `Authorization: Bearer <token>` in the Headers panel before running queries.

---

## 5. Schema

```graphql
type Query {
  inventoryReport(
    categoryId: String       # filter by category UUID
    warehouseId: String      # filter by warehouse UUID
    minStock: Int = 0        # minimum quantityAvailable
    page: Int = 0            # zero-based page index
    pageSize: Int = 100      # max 100 per page
  ): [InventoryReportItem!]!

  productStock(productId: ID!): [ProductStockItem!]!

  lowStock(threshold: Int = 10, limit: Int = 100): [LowStockItem!]!
}

type Mutation {
  reserveInventory(input: ReserveInput!): Boolean!
  releaseInventory(input: ReleaseInput!): Boolean!
  adjustInventory(input: AdjustInput!):  Boolean!
}
```

### InventoryReportItem

| Field | Type | Auth required |
|---|---|---|
| `parentCategoryName` | String! | any authenticated user |
| `categoryName` | String! | any authenticated user |
| `productId` | ID! | any authenticated user |
| `sku` | String! | any authenticated user |
| `productName` | String! | any authenticated user |
| `currency` | String! | any authenticated user |
| `warehouseId` | ID! | any authenticated user |
| `warehouseName` | String! | any authenticated user |
| `region` | String! | any authenticated user |
| `quantityAvailable` | Int! | any authenticated user |
| `quantityReserved` | Int! | any authenticated user |
| `quantityFree` | Int! | any authenticated user |
| `lastMovement` | String | any authenticated user |
| `unitPrice` | Float | **INVENTORY_PRICE** permission |
| `totalReceived` | Int | **INVENTORY_PRICE** permission |
| `totalShipped` | Int | **INVENTORY_PRICE** permission |
| `transactionCount` | Int | **INVENTORY_PRICE** permission |

### ProductStockItem

| Field | Type | Auth required |
|---|---|---|
| `productId` | ID! | any authenticated user |
| `sku` | String! | any authenticated user |
| `productName` | String! | any authenticated user |
| `currency` | String! | any authenticated user |
| `categoryName` | String! | any authenticated user |
| `warehouseId` | ID! | any authenticated user |
| `warehouseName` | String! | any authenticated user |
| `region` | String! | any authenticated user |
| `quantityAvailable` | Int! | any authenticated user |
| `quantityReserved` | Int! | any authenticated user |
| `quantityFree` | Int! | any authenticated user |
| `lastUpdated` | String | any authenticated user |
| `unitPrice` | Float | **INVENTORY_PRICE** permission |

### LowStockItem

All fields are accessible to any authenticated user — no sensitive pricing data.

---

## 6. Queries

### `inventoryReport` — paginated report across categories and warehouses

**Why it exists:** The primary reporting query. Joins products, categories, warehouses, and
optionally the inventory\_transactions table. The transaction join is **skipped automatically**
when the client does not request `totalReceived`, `totalShipped`, `transactionCount`, or
`lastMovement` — this is the main performance advantage of GraphQL over REST for this endpoint.

```graphql
query InventoryReport(
  $categoryId: String
  $warehouseId: String
  $minStock: Int
  $page: Int
  $pageSize: Int
) {
  inventoryReport(
    categoryId: $categoryId
    warehouseId: $warehouseId
    minStock: $minStock
    page: $page
    pageSize: $pageSize
  ) {
    parentCategoryName
    categoryName
    productId
    sku
    productName
    currency
    warehouseId
    warehouseName
    region
    quantityAvailable
    quantityReserved
    quantityFree
    lastMovement
    # Fields below return null unless caller has INVENTORY_PRICE permission:
    unitPrice
    totalReceived
    totalShipped
    transactionCount
  }
}
```

**curl example (minimal fields, no tx join triggered):**

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "query { inventoryReport(minStock: 0, page: 0, pageSize: 10) { productId sku quantityAvailable quantityFree } }"
  }' | jq .
```

**curl example (with variables):**

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "query Report($cat: String, $page: Int, $size: Int) { inventoryReport(categoryId: $cat, page: $page, pageSize: $size) { productId sku quantityAvailable } }",
    "variables": { "cat": "cat-electronics", "page": 0, "size": 20 }
  }' | jq .
```

---

### `productStock` — stock levels for a single product across all warehouses

```graphql
query ProductStock($productId: ID!) {
  productStock(productId: $productId) {
    warehouseId
    warehouseName
    region
    quantityAvailable
    quantityReserved
    quantityFree
    lastUpdated
    # INVENTORY_PRICE only:
    unitPrice
  }
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "query { productStock(productId: \"prod-001\") { warehouseId quantityAvailable quantityFree } }"
  }' | jq .
```

---

### `lowStock` — products below a stock threshold

Returns items where `quantityAvailable < threshold`. No pricing fields — safe for all roles.

```graphql
query LowStock($threshold: Int, $limit: Int) {
  lowStock(threshold: $threshold, limit: $limit) {
    productId
    sku
    productName
    warehouseId
    warehouseName
    region
    quantityAvailable
    quantityReserved
    quantityFree
  }
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "query { lowStock(threshold: 5, limit: 50) { productId sku quantityAvailable warehouseName } }"
  }' | jq .
```

---

## 7. Mutations

All mutations require `INVENTORY_WRITE` permission (granted to both `ROLE_ADMIN` and `ROLE_USER`).

### `reserveInventory` — hold stock for an order

Decrements `quantityAvailable`, increments `quantityReserved`.

```graphql
mutation Reserve($input: ReserveInput!) {
  reserveInventory(input: $input)
}
```

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "mutation { reserveInventory(input: { productId: \"prod-001\", warehouseId: \"wh-01\", quantity: 5, orderId: \"ord-abc\" }) }"
  }' | jq .
```

---

### `releaseInventory` — return reserved stock (cancel scenario)

Increments `quantityAvailable`, decrements `quantityReserved`.

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "mutation { releaseInventory(input: { productId: \"prod-001\", warehouseId: \"wh-01\", quantity: 5, orderId: \"ord-abc\" }) }"
  }' | jq .
```

---

### `adjustInventory` — manual stock correction

`delta` is signed: positive adds stock, negative removes it.

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "mutation { adjustInventory(input: { productId: \"prod-001\", warehouseId: \"wh-01\", delta: -3, reason: \"damaged goods\" }) }"
  }' | jq .
```

---

## 8. Field-Driven SQL Generation

### The problem with REST

A REST endpoint like `GET /inventory/report` always runs the same SQL and returns every column —
even if the client only needs two fields. Wasted DB I/O, wasted network bandwidth, wasted
serialisation time.

### How this project solves it

When a GraphQL query arrives, the controller reads **exactly which fields the client asked for**
from the selection set, then passes that `Set<String>` all the way down to the JDBC query
handler. The handler builds a `SELECT` clause that contains **only those columns**, and
conditionally adds expensive JOINs only when their fields were requested.

The whole pipeline, end to end:

```
Client GraphQL query                      DB
  { inventoryReport {                      │
      productId                            │
      sku          }  }                    │
       │                                   │
       ▼                                   │
InventoryGraphQlController                 │
  requestedFields(env)                     │
  → Set { "productId", "sku" }            │
       │                                   │
       ▼                                   │
GetInventoryReportQuery(fields = {"productId","sku"})
       │                                   │
       ▼                                   │
GetInventoryReportQueryHandler             │
  buildSelect(fields)                      │
  → "SELECT p.product_id, p.sku"          │
  needsTxJoin(fields) → false             │
  → no LEFT JOIN inventory_transactions   │
       │                                   │
       └──── SQL ─────────────────────────►│
             SELECT p.product_id, p.sku    │
             FROM products p               │
             JOIN ...                      │
◄────────────────────────────────── rows ──┘
```

### Step 1 — reading the selection set in the controller

`DataFetchingEnvironment` gives access to exactly what the client requested. `requestedFields()`
extracts the immediate field names as a plain `Set<String>`:

```java
// InventoryGraphQlController.java
@QueryMapping
public List<InventoryReportItem> inventoryReport(..., DataFetchingEnvironment env) {
    return queryBus.dispatch(
        new GetInventoryReportQuery(
            categoryId, warehouseId, minStock, page, pageSize,
            requestedFields(env)));   // ← passes the field set
}

private static Set<String> requestedFields(DataFetchingEnvironment env) {
    return env.getSelectionSet().getImmediateFields().stream()
        .map(f -> f.getName())
        .collect(Collectors.toUnmodifiableSet());
}
```

The `Set` uses the **GraphQL field names** (`productId`, `unitPrice`, `totalReceived`, …) — the
same names defined in the schema and in `COLUMN_MAP`.

### Step 2 — the field→SQL column map

Each handler defines a static `LinkedHashMap<String, String>` that maps every GraphQL field name
to its SQL expression:

```java
// GetInventoryReportQueryHandler.java
static {
    COLUMN_MAP.put("productId",    "p.product_id");
    COLUMN_MAP.put("sku",          "p.sku");
    COLUMN_MAP.put("productName",  "p.name AS product_name");
    COLUMN_MAP.put("unitPrice",    "p.unit_price");
    COLUMN_MAP.put("currency",     "p.currency");
    COLUMN_MAP.put("warehouseId",  "w.warehouse_id");
    COLUMN_MAP.put("warehouseName","w.name AS warehouse_name");
    COLUMN_MAP.put("region",       "w.region");
    COLUMN_MAP.put("quantityAvailable", "i.quantity_available");
    COLUMN_MAP.put("quantityReserved",  "i.quantity_reserved");
    COLUMN_MAP.put("quantityFree",
        "(i.quantity_available - i.quantity_reserved) AS quantity_free");
    COLUMN_MAP.put("lastMovement", "i.last_updated AS last_movement");
    // tx fields added to COLUMN_MAP_WITH_TX only:
    COLUMN_MAP_WITH_TX.put("totalReceived",    "COALESCE(tx.total_received, 0) AS total_received");
    COLUMN_MAP_WITH_TX.put("totalShipped",     "COALESCE(tx.total_shipped, 0) AS total_shipped");
    COLUMN_MAP_WITH_TX.put("transactionCount", "COALESCE(tx.transaction_count, 0) AS transaction_count");
    COLUMN_MAP_WITH_TX.put("lastMovement",     "COALESCE(tx.last_movement, i.last_updated) AS last_movement");
}
```

### Step 3 — building the dynamic SELECT clause

```java
private static String buildSelect(Set<String> fields, Map<String, String> columnMap) {
    if (fields.isEmpty()) {
        // REST caller — fetch every column
        return columnMap.values().stream().collect(Collectors.joining(", "));
    }
    // GraphQL caller — only the requested columns
    String cols = columnMap.entrySet().stream()
        .filter(e -> fields.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .collect(Collectors.joining(", "));
    return cols.isEmpty() ? "1" : cols;
}
```

`fields.isEmpty()` is the REST fallback — REST callers call `GetInventoryReportQuery.all(...)` which passes `Set.of()`, so all columns are selected.

### Step 4 — conditional JOIN on inventory\_transactions

The `inventory_transactions` table is expensive: it has no composite index on
`(product_id, warehouse_id)` and requires a full-scan aggregation subquery. It is only joined
when the client explicitly requests one of:

```
totalReceived  |  totalShipped  |  transactionCount  |  lastMovement
```

```java
private static final Set<String> TX_FIELDS =
    Set.of("totalReceived", "totalShipped", "transactionCount", "lastMovement");

private static boolean needsTxJoin(Set<String> fields) {
    if (fields.isEmpty()) return true;          // REST — always join
    return fields.stream().anyMatch(TX_FIELDS::contains);
}
```

When `needsTxJoin` returns `false`, the generated SQL is:

```sql
SELECT p.product_id, p.sku
FROM products p
JOIN product_categories pc ON p.category_id = pc.category_id
LEFT JOIN product_categories pc_parent ON pc.parent_category_id = pc_parent.category_id
JOIN inventory i ON p.product_id = i.product_id
JOIN warehouses w ON i.warehouse_id = w.warehouse_id
WHERE p.is_active = true AND ...
```

When it returns `true` (client asked for `totalReceived` etc.), the subquery is appended:

```sql
SELECT p.product_id, p.sku, ...,
       COALESCE(tx.total_received, 0) AS total_received, ...
FROM products p
JOIN ...
LEFT JOIN (
    SELECT product_id, warehouse_id,
           SUM(CASE WHEN quantity_delta > 0 THEN quantity_delta ELSE 0 END) AS total_received,
           SUM(CASE WHEN quantity_delta < 0 THEN ABS(quantity_delta) ELSE 0 END) AS total_shipped,
           COUNT(*) AS transaction_count,
           MAX(created_at) AS last_movement
    FROM inventory_transactions
    GROUP BY product_id, warehouse_id
) tx ON p.product_id = tx.product_id AND i.warehouse_id = tx.warehouse_id
WHERE ...
```

### Step 5 — mapping only requested columns from ResultSet

The `mapRow` method reads only the columns that were actually selected, avoiding
`ResultSet.getString()` calls for columns that were never fetched:

```java
private static InventoryReportItem mapRow(
        ResultSet rs, int rowNum, Set<String> fields, boolean hasTx) throws SQLException {
    boolean all = fields.isEmpty();

    // unitPrice column only read if it was requested
    BigDecimal unitPrice = null;
    if (all || fields.contains("unitPrice")) {
        unitPrice = rs.getBigDecimal("unit_price");
    }

    return new InventoryReportItem(
        str(rs, "product_id",   all || fields.contains("productId")),
        str(rs, "sku",          all || fields.contains("sku")),
        // ...
        hasTx ? rs.getLong("total_received") : 0L,
        hasTx ? rs.getLong("total_shipped")  : 0L,
        // ...
    );
}

// Helper: read column only when selected, return null otherwise
private static String str(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getString(col) : null;
}
private static int num(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getInt(col) : 0;
}
```

### Concrete SQL examples

**Client requests only `productId` and `sku`:**

```graphql
query { inventoryReport(page: 0, pageSize: 10) { productId sku } }
```

Generated SQL:

```sql
SELECT p.product_id, p.sku
FROM products p
JOIN product_categories pc ON p.category_id = pc.category_id
LEFT JOIN product_categories pc_parent ON pc.parent_category_id = pc_parent.category_id
JOIN inventory i ON p.product_id = i.product_id
JOIN warehouses w ON i.warehouse_id = w.warehouse_id
WHERE p.is_active = true AND i.quantity_available >= 0
ORDER BY pc_parent.name NULLS LAST, pc.name, p.name, w.name
LIMIT 10 OFFSET 0
```

No `LEFT JOIN inventory_transactions`. No `unit_price`, no aggregation columns.

---

**Client requests `quantityAvailable`, `totalReceived`, `totalShipped`:**

```graphql
query {
  inventoryReport(page: 0, pageSize: 10) {
    productId
    quantityAvailable
    totalReceived
    totalShipped
  }
}
```

Generated SQL (tx join included because `totalReceived` was requested):

```sql
SELECT p.product_id, i.quantity_available,
       COALESCE(tx.total_received, 0) AS total_received,
       COALESCE(tx.total_shipped, 0)  AS total_shipped
FROM products p
JOIN product_categories pc ON p.category_id = pc.category_id
LEFT JOIN product_categories pc_parent ON pc.parent_category_id = pc_parent.category_id
JOIN inventory i ON p.product_id = i.product_id
JOIN warehouses w ON i.warehouse_id = w.warehouse_id
LEFT JOIN (
    SELECT product_id, warehouse_id,
           SUM(CASE WHEN quantity_delta > 0 THEN quantity_delta ELSE 0 END) AS total_received,
           SUM(CASE WHEN quantity_delta < 0 THEN ABS(quantity_delta) ELSE 0 END) AS total_shipped,
           COUNT(*) AS transaction_count,
           MAX(created_at) AS last_movement
    FROM inventory_transactions
    GROUP BY product_id, warehouse_id
) tx ON p.product_id = tx.product_id AND i.warehouse_id = tx.warehouse_id
WHERE p.is_active = true AND i.quantity_available >= 0
ORDER BY pc_parent.name NULLS LAST, pc.name, p.name, w.name
LIMIT 10 OFFSET 0
```

### REST vs GraphQL comparison

| | REST `GET /api/v1/inventory/report` | GraphQL `inventoryReport` |
|---|---|---|
| Columns fetched | always all 17 | only what client asked |
| tx JOIN | always included | only if tx fields requested |
| `fields` set | `Set.of()` (empty = all) | populated from selection set |
| Performance (no tx fields) | slow (full scan + join) | fast (no join) |
| Performance (all fields) | same | same |

### Sequence diagram — field-driven SQL generation

```
Client        Controller               Query record        Handler             DB
  │                │                        │                  │                │
  │─ { productId   │                        │                  │                │
  │    sku }      ─┤                        │                  │                │
  │                │                        │                  │                │
  │        requestedFields(env)             │                  │                │
  │        → Set{"productId","sku"}         │                  │                │
  │                │                        │                  │                │
  │                │─ new Query(fields) ───►│                  │                │
  │                │                        │                  │                │
  │                │                   queryBus.dispatch() ───►│                │
  │                │                        │                  │                │
  │                │                        │         needsTxJoin(fields)       │
  │                │                        │         → false (no tx fields)    │
  │                │                        │                  │                │
  │                │                        │         buildSelect(fields)        │
  │                │                        │         → "p.product_id, p.sku"   │
  │                │                        │                  │                │
  │                │                        │         SQL (no tx JOIN) ─────────►│
  │                │                        │                  │◄── 2 columns ──│
  │                │                        │                  │                │
  │                │                        │         mapRow (reads only        │
  │                │                        │         product_id, sku)          │
  │                │                        │◄─ List<InventoryReportItem> ──────│
  │◄─ JSON { productId, sku } ─────────────│                  │                │
```

---

## 9. Field-Level Authorization

### How it works

Sensitive fields (`unitPrice`, `totalReceived`, `totalShipped`, `transactionCount`) are resolved
by `InventoryFieldAuthController` instead of the default Java record accessor. Spring GraphQL
calls these `@SchemaMapping` methods for each field on each returned object.

```
Spring GraphQL resolves InventoryReportItem.unitPrice
        │
        ▼
InventoryFieldAuthController.unitPrice(item, auth)
        │
        ├─ auth.getAuthorities() contains "INVENTORY_PRICE" ?
        │       YES → return item.unitPrice().doubleValue()
        │       NO  → return null
```

The field still appears in the response — it is just `null`. The schema declares these fields as
nullable (`Float`, `Int`) precisely for this reason. The client can detect the absence without
any schema change.

### Sequence diagram — field resolution with mixed permissions

```
GraphQL Engine          InventoryFieldAuthController      SecurityContext
      │                           │                            │
      │─ resolve unitPrice ──────►│                            │
      │                           │─ getAuthentication() ─────►│
      │                           │◄─ Authentication ──────────│
      │                           │                            │
      │                           │  check authorities for     │
      │                           │  "INVENTORY_PRICE"         │
      │                           │                            │
      │                     ROLE_USER: not found               │
      │◄─ null ───────────────────│                            │
      │                           │                            │
      │─ resolve unitPrice ──────►│  (next item, ROLE_ADMIN)   │
      │                           │─ getAuthentication() ─────►│
      │                           │◄─ Authentication ──────────│
      │                           │  "INVENTORY_PRICE" found   │
      │◄─ 199.99 ─────────────────│                            │
```

### Example responses

**ROLE\_USER** (no `INVENTORY_PRICE`):

```json
{
  "data": {
    "inventoryReport": [
      {
        "productId": "prod-001",
        "sku": "SKU-001",
        "quantityAvailable": 42,
        "unitPrice": null,
        "totalReceived": null,
        "totalShipped": null,
        "transactionCount": null
      }
    ]
  }
}
```

**ROLE\_ADMIN** (has `INVENTORY_PRICE`):

```json
{
  "data": {
    "inventoryReport": [
      {
        "productId": "prod-001",
        "sku": "SKU-001",
        "quantityAvailable": 42,
        "unitPrice": 199.99,
        "totalReceived": 500,
        "totalShipped": 458,
        "transactionCount": 23
      }
    ]
  }
}
```

---

## 10. Persisted Queries (APQ)

### What it is

Automatic Persisted Queries (Apollo APQ protocol) let clients send a short SHA-256 hash instead
of the full query string on repeat requests. This reduces bandwidth and prevents clients from
sending arbitrary ad-hoc queries.

The registry is in-memory (`ConcurrentHashMap`) — it resets on server restart. For production
with multiple replicas, replace it with a shared Redis store.

### How to use

#### First request — register the query

Send the full query **and** the hash together. The server stores the mapping and executes normally.

```bash
HASH="e3b0c44298fc1c149afb..." # SHA-256 of the query string

curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "query { lowStock(threshold: 5, limit: 10) { productId sku quantityAvailable } }",
    "extensions": {
      "persistedQuery": {
        "version": 1,
        "sha256Hash": "'"$HASH"'"
      }
    }
  }' | jq .
```

#### Subsequent requests — hash only

Omit the `query` field. The server looks up the hash and executes the stored query.

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "extensions": {
      "persistedQuery": {
        "version": 1,
        "sha256Hash": "'"$HASH"'"
      }
    }
  }' | jq .
```

#### Unknown hash — server response

If the hash is not in the registry (e.g. after server restart), the server responds:

```json
{
  "errors": [
    {
      "message": "PersistedQueryNotFound",
      "extensions": { "code": "PERSISTED_QUERY_NOT_FOUND" }
    }
  ]
}
```

The client must retry with the full query (first-request flow above).

### Full APQ flow diagram

```
Client                          Server (PersistedQueryFilter)
  │                                         │
  │  1. POST {hash + full query} ──────────►│
  │                                registry.put(hash, query)
  │◄── 200 normal result ──────────────────│
  │                                         │
  │  2. POST {hash only} ──────────────────►│
  │                                registry.get(hash) → found
  │                                inject query into request
  │◄── 200 normal result ──────────────────│
  │                                         │
  │  [server restarts]                      │
  │                                registry cleared
  │                                         │
  │  3. POST {hash only} ──────────────────►│
  │                                registry.get(hash) → null
  │◄── 200 {PersistedQueryNotFound} ───────│
  │                                         │
  │  4. POST {hash + full query} ──────────►│  (retry, same as step 1)
  │◄── 200 normal result ──────────────────│
```

---

## 11. Query Protection — Complexity & Depth Limits

### Why it matters

Without limits, a client can craft a deeply nested or wide query that causes an N+1 explosion or
full table scans. Both protections run **before** any resolver is called — they are pure static
analysis on the query AST.

### Complexity limit (max score: 100)

Each field in the query adds to a score. The default weight per field is 1. If the total exceeds
100, the request is rejected immediately with an error.

```graphql
# This query has a score of ~10 — well within limit
query {
  inventoryReport(pageSize: 5) {   # score: 1
    productId                       # score: 1
    sku                             # score: 1
    quantityAvailable               # score: 1
    quantityFree                    # score: 1
  }
}

# A query selecting all 17 fields on 5 report items = score 17
# Still fine. A query selecting 101 fields total → rejected.
```

**Error response when limit exceeded:**

```json
{
  "errors": [
    {
      "message": "maximum query complexity exceeded 100",
      "extensions": { "classification": "ExecutionAborted" }
    }
  ]
}
```

### Depth limit (max depth: 10)

Measures how many levels deep the query nesting goes. The current schema is flat (max real depth
is 2), so this guard exists to block fragment abuse if the schema evolves.

```graphql
# Depth 1 — fine
query { lowStock { productId } }

# Depth > 10 — rejected
query { a { b { c { d { e { f { g { h { i { j { k { } } } } } } } } } } } }
```

**Error response when depth exceeded:**

```json
{
  "errors": [
    {
      "message": "maximum query depth exceeded 10",
      "extensions": { "classification": "ExecutionAborted" }
    }
  ]
}
```

### Configuration

Limits are set in `GraphQlConfig`:

```java
// GraphQlConfig.java
@Bean Instrumentation maxComplexity() { return new MaxQueryComplexityInstrumentation(100); }
@Bean Instrumentation maxDepth()      { return new MaxQueryDepthInstrumentation(10); }
```

To change limits, update those two numbers and rebuild. No YAML configuration required.

---

## 12. Error Reference

| Scenario | HTTP status | `errors[0].message` |
|---|---|---|
| Missing / invalid JWT | 401 | `Unauthorized` (from security filter, no GraphQL body) |
| Query complexity > 100 | 200 | `maximum query complexity exceeded 100` |
| Query depth > 10 | 200 | `maximum query depth exceeded 10` |
| APQ hash not found | 200 | `PersistedQueryNotFound` |
| Validation error (bad arguments) | 200 | field-level validation message |
| Unhandled server exception | 200 | `An unexpected error occurred` |

> GraphQL always returns HTTP 200 for execution errors — check the `errors` array, not the status code.

---

## 13. Role & Permission Matrix

| Role | Permissions | Can see pricing fields |
|---|---|---|
| `ROLE_ADMIN` | `INVENTORY_READ`, `INVENTORY_WRITE`, `INVENTORY_PRICE`, `ORDER_READ`, `ORDER_WRITE` | Yes |
| `ROLE_USER` | `INVENTORY_READ`, `INVENTORY_WRITE`, `ORDER_READ`, `ORDER_WRITE` | No (`null`) |

**Test credentials (local / test profile):**

| Username | Password | Role |
|---|---|---|
| `admin` | `changeme` | ROLE\_ADMIN |
| `testuser` | `testpass` | ROLE\_USER |
