/**
 * Stress Test — CQRS Order Service
 * ═══════════════════════════════════════════════════════════════════════════
 * Goal: find the breaking point — the VU count at which latency spikes,
 *       error rate climbs, or the JVM starts dropping requests.
 *
 * Unlike the load test there are NO hard thresholds; the test always exits
 * 0 so you can observe the full degradation curve.  Soft thresholds are
 * defined for reporting only (they show up in the summary but don't fail CI).
 *
 * Stages (all VUs drive the full order lifecycle):
 *   0 → 100  (2 m)   — baseline warm-up
 *   100      (3 m)   — measure at 100 VUs
 *   100→300  (2 m)   — first stress ramp
 *   300      (3 m)   — measure at 300 VUs
 *   300→600  (2 m)   — second stress ramp
 *   600      (3 m)   — measure at 600 VUs
 *   600→1000 (2 m)   — peak ramp
 *   1000     (3 m)   — measure at 1000 VUs (likely breaking point)
 *   1000→0   (2 m)   — cool-down
 *
 * Each VU logs in independently to also stress the auth endpoint.
 *
 * Usage:
 *   k6 run k6/stress-test.js
 *   BASE_URL=https://staging.example.com k6 run k6/stress-test.js
 *   k6 run --out influxdb=http://localhost:8086/k6 k6/stress-test.js
 *   k6 run --out experimental-prometheus-rw=http://localhost:9090/api/v1/write k6/stress-test.js
 */

import { CREDENTIALS, STRESS_STAGES } from './config/config.js';
import { loginPerVU } from './utils/auth.js';
import { runOrderFlow } from './scenarios/order-flow.js';

// ── k6 options ────────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      stages: STRESS_STAGES,
      gracefulRampDown: '30s',
    },
  },

  /**
   * Soft thresholds — "abortOnFail: false" means breaching them is reported
   * in the summary but does NOT abort the test early.  This lets you observe
   * the full degradation curve even after the service starts struggling.
   */
  thresholds: {
    http_req_duration: [
      { threshold: 'p(95)<300', abortOnFail: false },
      { threshold: 'p(99)<1000', abortOnFail: false },
    ],
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: false }],
    order_lifecycle_success: [{ threshold: 'rate>0.80', abortOnFail: false }],
  },
};

// ── Default VU function ───────────────────────────────────────────────────────
/**
 * Each VU authenticates on every iteration.
 * This deliberately stresses the /auth/login endpoint alongside the order
 * APIs and avoids a stale shared token during a multi-hour stress run.
 */
export default function () {
  // Per-VU login: measures auth endpoint under load as a side-effect
  const { authHeaders } = loginPerVU(CREDENTIALS);

  // Full order lifecycle drives all five order endpoints
  runOrderFlow(authHeaders);
}
