#!/usr/bin/env bash
set -euo pipefail

# ── Generate Postman collection offline (no running app needed) ───────────────
#
# Usage:  ./scripts/gen-postman.sh
#         make postman
#
# Output: postman/collection.json  (importable via Postman → Import)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_DIR/postman"
OUTPUT_FILE="$OUTPUT_DIR/collection.json"

BASE_URL="${BASE_URL:-http://localhost:8080}"

mkdir -p "$OUTPUT_DIR"

cat > "$OUTPUT_FILE" << ENDOFCOLLECTION
{
  "info": {
    "name": "CQRS Order Service",
    "description": "Production-grade CQRS Order Service — Spring Boot 4 / Java 25 / Virtual Threads",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    { "key": "baseUrl",  "value": "${BASE_URL}", "type": "string" },
    { "key": "username", "value": "admin",       "type": "string" },
    { "key": "password", "value": "changeme",    "type": "string" },
    { "key": "token",    "value": "",             "type": "string" },
    { "key": "orderId",  "value": "",             "type": "string" }
  ],
  "auth": {
    "type": "bearer",
    "bearer": [
      { "key": "token", "value": "{{token}}", "type": "string" }
    ]
  },
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          "// Auto-login if token is empty",
          "if (!pm.variables.get('token')) {",
          "  pm.sendRequest({",
          "    url: pm.variables.get('baseUrl') + '/api/v1/auth/login',",
          "    method: 'POST',",
          "    header: { 'Content-Type': 'application/json' },",
          "    body: { mode: 'raw', raw: JSON.stringify({ username: pm.variables.get('username'), password: pm.variables.get('password') }) }",
          "  }, function (err, res) {",
          "    if (!err && res.code === 200) {",
          "      pm.collectionVariables.set('token', res.json().token);",
          "    }",
          "  });",
          "}"
        ]
      }
    }
  ],
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "Login",
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 200) {",
                  "  pm.collectionVariables.set('token', pm.response.json().token);",
                  "  pm.test('Token saved', function () { pm.expect(pm.response.json().token).to.be.a('string'); });",
                  "}"
                ]
              }
            }
          ],
          "request": {
            "auth": { "type": "noauth" },
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/auth/login",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "auth", "login"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"username\": \"{{username}}\",\n  \"password\": \"{{password}}\"\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        }
      ]
    },
    {
      "name": "Orders",
      "item": [
        {
          "name": "Place a new order",
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "  pm.collectionVariables.set('orderId', pm.response.json().id);",
                  "  pm.test('Order ID saved', function () { pm.expect(pm.response.json().id).to.be.a('string'); });",
                  "}"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/orders",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "orders"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"customerId\": \"customer-001\",\n  \"items\": [\n    {\n      \"productId\": \"prod-001\",\n      \"productName\": \"Widget\",\n      \"quantity\": 2,\n      \"unitPrice\": 29.99,\n      \"currency\": \"USD\"\n    }\n  ]\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        },
        {
          "name": "Get order by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{baseUrl}}/api/v1/orders/{{orderId}}",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "orders", "{{orderId}}"]
            }
          }
        },
        {
          "name": "List orders by customer ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{baseUrl}}/api/v1/orders?customerId=customer-001",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "orders"],
              "query": [
                { "key": "customerId", "value": "customer-001", "description": "Customer ID to filter by" }
              ]
            }
          }
        },
        {
          "name": "Confirm a PENDING order",
          "request": {
            "method": "POST",
            "url": {
              "raw": "{{baseUrl}}/api/v1/orders/{{orderId}}/confirm",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "orders", "{{orderId}}", "confirm"]
            }
          }
        },
        {
          "name": "Cancel a PENDING or CONFIRMED order",
          "request": {
            "method": "DELETE",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/orders/{{orderId}}",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "orders", "{{orderId}}"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"reason\": \"Customer requested cancellation\"\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        }
      ]
    },
    {
      "name": "Inventory",
      "item": [
        {
          "name": "Full inventory report",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/report?page=0&pageSize=100",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "report"],
              "query": [
                { "key": "categoryId",  "value": "",    "description": "Filter by category",  "disabled": true },
                { "key": "warehouseId", "value": "",    "description": "Filter by warehouse", "disabled": true },
                { "key": "minStock",    "value": "0",   "description": "Minimum stock filter" },
                { "key": "page",        "value": "0",   "description": "Page number (0-based)" },
                { "key": "pageSize",    "value": "100", "description": "Page size (1-500)" }
              ]
            }
          }
        },
        {
          "name": "Get product stock across warehouses",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/products/prod-001/stock",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "products", "prod-001", "stock"]
            }
          }
        },
        {
          "name": "List low-stock products",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/low-stock?threshold=10&limit=100",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "low-stock"],
              "query": [
                { "key": "threshold", "value": "10",  "description": "Free stock threshold" },
                { "key": "limit",     "value": "100", "description": "Max results (1-500)" }
              ]
            }
          }
        },
        {
          "name": "Reserve stock for an order",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/reserve",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "reserve"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"productId\": \"prod-001\",\n  \"warehouseId\": \"wh-001\",\n  \"quantity\": 5,\n  \"orderId\": \"{{orderId}}\"\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        },
        {
          "name": "Release previously reserved stock",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/release",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "release"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"productId\": \"prod-001\",\n  \"warehouseId\": \"wh-001\",\n  \"quantity\": 5,\n  \"orderId\": \"{{orderId}}\"\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        },
        {
          "name": "Manual stock adjustment",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/v1/inventory/adjust",
              "host": ["{{baseUrl}}"],
              "path": ["api", "v1", "inventory", "adjust"]
            },
            "body": {
              "mode": "raw",
              "raw": "{\n  \"productId\": \"prod-001\",\n  \"warehouseId\": \"wh-001\",\n  \"delta\": 50,\n  \"reason\": \"Received shipment\"\n}",
              "options": { "raw": { "language": "json" } }
            }
          }
        }
      ]
    }
  ]
}
ENDOFCOLLECTION

echo "Postman collection generated: $OUTPUT_FILE"
echo ""
echo "Import into Postman: Open Postman → Import → Upload File → select collection.json"
echo ""
echo "Features:"
echo "  • Auto-login: runs Login first, saves JWT token for all subsequent requests"
echo "  • Place Order saves orderId for use in Get/Confirm/Cancel"
echo "  • Override base URL: BASE_URL=https://staging.example.com make postman"
