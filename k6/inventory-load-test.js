/**
 * Inventory Load Test — slow query benchmark + optimisation workflow
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Purpose:
 *   1. Establish a BASELINE showing the slow inventory report latency
 *      (no composite index on inventory_transactions(product_id, warehouse_id)).
 *   2. Apply the optimisation SQL (see below).
 *   3. Re-run to quantify improvement — compare p95/p99 latency in Grafana.
 *
 * Optimisation SQL (run against PostgreSQL after baseline):
 *   CREATE INDEX CONCURRENTLY idx_inv_tx_product_warehouse
 *       ON inventory_transactions(product_id, warehouse_id);
 *
 * Usage:
 *   # Baseline (before index):
 *   k6 run k6/inventory-load-test.js
 *
 *   # Push metrics to Prometheus for Grafana dashboard:
 *   k6 run --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
 *       k6/inventory-load-test.js
 *
 *   # With custom base URL:
 *   BASE_URL=http://localhost:80 k6 run k6/inventory-load-test.js
 *
 * VU profile (lighter than order load test — each VU fires expensive DB query):
 *   0 → 10  (1 m ramp-up)
 *   10      (3 m steady — record baseline)
 *   10→ 25  (1 m ramp-up)
 *   25      (3 m steady — observe degradation)
 *   25→  0  (1 m cool-down)
 *
 * Thresholds (expected to FAIL on baseline, PASS after optimisation):
 *   p95 inventory_report_latency < 2000 ms   (2 s)
 *   p99 inventory_report_latency < 5000 ms   (5 s)
 *   inventory_report_success rate  > 95 %
 */

import { CREDENTIALS } from './config/config.js';
import { loginOnce } from './utils/auth.js';
import { runInventoryReport } from './scenarios/inventory-report.js';

export const options = {
  scenarios: {
    inventoryReport: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: 10 },
        { duration: '3m', target: 10 },
        { duration: '1m', target: 25 },
        { duration: '3m', target: 25 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
      exec: 'inventoryReportScenario',
    },
  },

  thresholds: {
    // Latency thresholds — will likely FAIL on baseline (slow query), PASS after index added
    inventory_report_latency: ['p(95)<2000', 'p(99)<5000'],
    // Success rate must stay above 95 % regardless of latency
    inventory_report_success: ['rate>0.95'],
    // Overall HTTP error rate
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  console.log('[setup] Authenticating for inventory load test…');
  const session = loginOnce(CREDENTIALS);
  console.log('[setup] Ready.');
  return session;
}

export function inventoryReportScenario(data) {
  runInventoryReport(data.authHeaders);
}

export function teardown() {
  console.log('[teardown] Inventory load test complete.');
  console.log('[teardown] Check Grafana at http://localhost:3000 for latency charts.');
  console.log('[teardown] To optimise, run:');
  console.log(
    '[teardown]   docker exec cqrs-postgres psql -U orders_user -d orders_db -c '
      + '"CREATE INDEX CONCURRENTLY idx_inv_tx_product_warehouse ON inventory_transactions(product_id, warehouse_id);"',
  );
}
