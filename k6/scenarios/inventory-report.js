/**
 * Scenario: Inventory Report (slow query benchmark).
 *
 * Each VU iteration fires the full inventory report endpoint which executes
 * a multi-table JOIN with an unindexed aggregation subquery over 100 000
 * inventory_transactions rows.
 *
 * Workflow per VU:
 *   1. Full report with no filters         (worst case — all 50 000 rows)
 *   2. Report filtered by random warehouse
 *   3. Product stock lookup for a seeded product
 *   4. Low-stock alert list
 *
 * Run baseline BEFORE adding the optimisation index, then add:
 *   CREATE INDEX idx_inv_tx_product_warehouse
 *       ON inventory_transactions(product_id, warehouse_id);
 * and re-run to compare.
 */

import { sleep } from 'k6';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import {
  getInventoryReport,
  getProductStock,
  getLowStock,
} from '../services/inventory-service.js';
import { randomInt } from '../utils/data.js';

export const inventoryReportLatency = new Trend('inventory_report_latency', true);
export const inventoryReportSuccess = new Rate('inventory_report_success');

/** A fixed pool of seeded warehouse IDs from V3 migration. */
const WAREHOUSE_IDS = Array.from({ length: 10 }, (_, i) => `wh-${i + 1}`);

/** A sample of seeded product IDs from V3 migration. */
const PRODUCT_IDS = Array.from({ length: 100 }, (_, i) => `prod-${i + 1}`);

/**
 * @param {Object} authHeaders  Authenticated headers
 */
export function runInventoryReport(authHeaders) {
  // ── Step 1: full unfiltered report (most expensive — full table scan) ─────
  const fullReportRes = getInventoryReport({ pageSize: 100, page: 0 }, authHeaders);
  inventoryReportLatency.add(fullReportRes.timings.duration);

  const fullOk = check(fullReportRes, {
    'inventoryReport: status 200': (r) => r.status === 200,
    'inventoryReport: body is array': (r) => {
      try {
        return Array.isArray(JSON.parse(r.body));
      } catch {
        return false;
      }
    },
  });
  inventoryReportSuccess.add(fullOk);

  sleep(randomInt(1, 2));

  // ── Step 2: filtered by warehouse (still hits the slow subquery) ──────────
  const whId = WAREHOUSE_IDS[randomInt(0, WAREHOUSE_IDS.length - 1)];
  const filteredRes = getInventoryReport({ warehouseId: whId, pageSize: 50 }, authHeaders);

  check(filteredRes, {
    'inventoryReport filtered: status 200': (r) => r.status === 200,
  });

  sleep(randomInt(1, 2));

  // ── Step 3: product-level stock (fast indexed lookup) ────────────────────
  const productId = PRODUCT_IDS[randomInt(0, PRODUCT_IDS.length - 1)];
  const stockRes = getProductStock(productId, authHeaders);

  check(stockRes, {
    'productStock: status 200': (r) => r.status === 200,
  });

  sleep(randomInt(1, 2));

  // ── Step 4: low-stock alert ────────────────────────────────────────────────
  const lowStockRes = getLowStock(20, 50, authHeaders);

  check(lowStockRes, {
    'lowStock: status 200': (r) => r.status === 200,
  });

  sleep(randomInt(1, 3));
}
