/**
 * Load Test — CQRS Order Service
 * ═══════════════════════════════════════════════════════════════════════════
 * Goal: validate that the service meets SLOs under realistic sustained load.
 *
 * SLOs enforced via thresholds (test fails if any are breached):
 *   • p95 latency      < 300 ms  (all endpoints combined)
 *   • error rate       < 1 %
 *   • login p99        < 500 ms
 *   • createOrder p95  < 400 ms
 *   • order_lifecycle_success rate > 95 %
 *
 * VU profile:
 *   0 → 50  (2 m ramp-up)
 *   50      (5 m steady)
 *   50→100  (2 m ramp-up)
 *   100     (5 m steady)
 *   100→0   (2 m cool-down)
 *
 * Mixed workload — two named scenarios run in parallel:
 *   orderFlow  (80 % of VUs) — full create → confirm lifecycle
 *   readHeavy  (20 % of VUs) — list + get only
 *
 * Usage:
 *   k6 run k6/load-test.js
 *   BASE_URL=https://staging.example.com k6 run k6/load-test.js
 *   k6 run --out influxdb=http://localhost:8086/k6 k6/load-test.js
 *   k6 run --out experimental-prometheus-rw=http://localhost:9090/api/v1/write k6/load-test.js
 */

import { sleep } from 'k6';
import { CREDENTIALS, LOAD_THRESHOLDS, LOAD_STAGES } from './config/config.js';
import { loginOnce } from './utils/auth.js';
import { runOrderFlow } from './scenarios/order-flow.js';
import { runReadHeavy } from './scenarios/read-heavy.js';

// ── k6 options ────────────────────────────────────────────────────────────────
export const options = {
  /**
   * Named scenarios let k6 track metrics per scenario and give each its own
   * executor, so orderFlow and readHeavy ramp independently.
   */
  scenarios: {
    orderFlow: {
      executor: 'ramping-vus',
      // 80 % of target VUs drive the write path
      stages: LOAD_STAGES.map((s) => ({ ...s, target: Math.ceil(s.target * 0.8) })),
      gracefulRampDown: '30s',
      exec: 'orderFlowScenario',
    },
    readHeavy: {
      executor: 'ramping-vus',
      // 20 % of target VUs drive the read path; starts 30 s after orderFlow
      // so the database has some data before readers begin.
      startTime: '30s',
      stages: LOAD_STAGES.map((s) => ({ ...s, target: Math.ceil(s.target * 0.2) })),
      gracefulRampDown: '30s',
      exec: 'readHeavyScenario',
    },
  },

  thresholds: {
    ...LOAD_THRESHOLDS,
    // End-to-end lifecycle success rate must stay above 95 %
    order_lifecycle_success: ['rate>0.95'],
  },
};

// ── setup() — runs once before VUs start ─────────────────────────────────────
/**
 * Authenticates once and distributes the token to all VUs.
 * This keeps the auth endpoint out of the steady-state measurement window.
 *
 * @returns {{ token: string, authHeaders: Object }}
 */
export function setup() {
  console.log(`[setup] Authenticating as ${CREDENTIALS.username}…`);
  const session = loginOnce(CREDENTIALS);
  console.log('[setup] Token obtained — starting load test.');
  return session;
}

// ── VU entry points ───────────────────────────────────────────────────────────

/**
 * Write-heavy scenario: full order lifecycle.
 * k6 calls this function once per VU iteration for the orderFlow scenario.
 *
 * @param {{ authHeaders: Object }} data  injected by setup()
 */
export function orderFlowScenario(data) {
  runOrderFlow(data.authHeaders);
}

/**
 * Read-heavy scenario: list + get.
 *
 * @param {{ authHeaders: Object }} data  injected by setup()
 */
export function readHeavyScenario(data) {
  runReadHeavy(data.authHeaders);
}

// ── teardown() — runs once after all VUs finish ───────────────────────────────
export function teardown(data) {
  console.log('[teardown] Load test complete.');
  // data.token is available here if you need to clean up test data
  // e.g. call a bulk-delete API to remove orders created during the test
}
