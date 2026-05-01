/**
 * Central configuration for all k6 test scenarios.
 *
 * Override at runtime:
 *   BASE_URL=https://staging.example.com k6 run load-test.js
 *   K6_USERNAME=admin K6_PASSWORD=changeme k6 run load-test.js
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const CREDENTIALS = {
  username: __ENV.K6_USERNAME || 'admin',
  password: __ENV.K6_PASSWORD || 'changeme',
};

/** Default HTTP request timeout for all calls. */
export const TIMEOUT = '10s';

/** Default headers sent with every authenticated request. */
export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  Accept: 'application/json',
};

/**
 * Shared thresholds used by load-test.js.
 * stress-test.js intentionally omits these to find the breaking point.
 */
export const LOAD_THRESHOLDS = {
  // 95th-percentile latency must stay under 300 ms
  http_req_duration: ['p(95)<300'],
  // Fewer than 1 % of requests may fail
  http_req_failed: ['rate<0.01'],
  // Login endpoint specifically: p99 < 500 ms
  'http_req_duration{endpoint:login}': ['p(99)<500'],
  // Order creation specifically: p95 < 400 ms
  'http_req_duration{endpoint:createOrder}': ['p(95)<400'],
};

/**
 * VU-ramping stages for the load test:
 *   Phase 1 – ramp up   0 → 50  (2 min)
 *   Phase 2 – steady   50       (5 min)
 *   Phase 3 – ramp up  50 → 100 (2 min)
 *   Phase 4 – steady  100       (5 min)
 *   Phase 5 – ramp down  → 0   (2 min)
 */
export const LOAD_STAGES = [
  { duration: '2m', target: 50 },
  { duration: '5m', target: 50 },
  { duration: '2m', target: 100 },
  { duration: '5m', target: 100 },
  { duration: '2m', target: 0 },
];

/**
 * VU-ramping stages for the stress test.
 * Ramp aggressively to expose the breaking point.
 */
export const STRESS_STAGES = [
  { duration: '2m', target: 100 },
  { duration: '3m', target: 100 },
  { duration: '2m', target: 300 },
  { duration: '3m', target: 300 },
  { duration: '2m', target: 600 },
  { duration: '3m', target: 600 },
  { duration: '2m', target: 1000 },
  { duration: '3m', target: 1000 },
  { duration: '2m', target: 0 },
];

/**
 * A fixed pool of customer IDs reused across VUs.
 * Mirrors realistic traffic: a bounded set of known customers
 * generating repeated reads and writes.
 */
export const CUSTOMER_POOL = Array.from(
  { length: 50 },
  (_, i) => `customer-${String(i + 1).padStart(3, '0')}`,
);

/** Product catalogue used when building order payloads. */
export const PRODUCT_CATALOGUE = [
  { productId: 'prod-001', productName: 'Widget Pro',    unitPrice: 29.99 },
  { productId: 'prod-002', productName: 'Gadget Lite',   unitPrice: 14.99 },
  { productId: 'prod-003', productName: 'Thingamajig X', unitPrice: 49.99 },
  { productId: 'prod-004', productName: 'Doohickey Plus', unitPrice: 9.99 },
  { productId: 'prod-005', productName: 'Gizmo Ultra',   unitPrice: 99.99 },
];
