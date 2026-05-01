/**
 * Scenario: Full order lifecycle.
 *
 * Simulates a realistic user session:
 *   1. List existing orders for a random customer  (read — warm cache)
 *   2. Create a new order                          (write — critical path)
 *   3. Retrieve the newly created order            (read — verify write)
 *   4. 70 % chance: confirm the order              (state transition)
 *      30 % chance: cancel the order               (alternative path)
 *
 * Think time (1–3 s) is added between steps to simulate human pacing and
 * prevent the test from saturating the server with back-to-back requests.
 *
 * This file is imported by load-test.js and stress-test.js; it does NOT
 * export options so each entry-point can set its own thresholds / stages.
 */

import { sleep } from 'k6';
import {
  createOrder,
  getOrderById,
  listOrdersByCustomer,
  confirmOrder,
  cancelOrder,
} from '../services/order-service.js';
import {
  expectOkWithField,
  expectCreated,
  expectNoContent,
  expectStatus,
  orderLifecycleSuccess,
  orderCreateLatency,
  orderConfirmLatency,
} from '../utils/checks.js';
import {
  randomCustomerId,
  buildOrderPayload,
  buildCancelPayload,
  randomInt,
} from '../utils/data.js';

/**
 * Executes one full order-lifecycle iteration.
 *
 * @param {Object} authHeaders  Authenticated headers (Authorization: Bearer ...)
 */
export function runOrderFlow(authHeaders) {
  let lifecycleOk = true;
  const customerId = randomCustomerId();

  // ── Step 1: list orders for this customer (read) ──────────────────────────
  const listRes = listOrdersByCustomer(customerId, authHeaders);
  const listOk = expectStatus(listRes, [200], 'listOrders');
  lifecycleOk = lifecycleOk && listOk;

  sleep(randomInt(1, 2));

  // ── Step 2: create a new order (write — critical path) ───────────────────
  const orderPayload = buildOrderPayload(customerId);
  const createRes = createOrder(orderPayload, authHeaders);

  // Record raw latency for this step into a custom trend metric
  orderCreateLatency.add(createRes.timings.duration);

  const orderId = expectCreated(createRes, 'orderId', 'createOrder');
  if (!orderId) {
    // Cannot proceed without an order ID — mark lifecycle as failed and stop
    orderLifecycleSuccess.add(false);
    return;
  }

  sleep(randomInt(1, 2));

  // ── Step 3: verify the order was persisted (read-after-write) ────────────
  const getRes = getOrderById(orderId, authHeaders);
  const getOk = expectOkWithField(getRes, 'orderId', 'getOrderById');
  lifecycleOk = lifecycleOk && getOk;

  sleep(randomInt(1, 2));

  // ── Step 4: confirm (70 %) or cancel (30 %) ──────────────────────────────
  if (Math.random() < 0.7) {
    const confirmRes = confirmOrder(orderId, authHeaders);
    orderConfirmLatency.add(confirmRes.timings.duration);
    const confirmOk = expectNoContent(confirmRes, 'confirmOrder');
    lifecycleOk = lifecycleOk && confirmOk;
  } else {
    const cancelRes = cancelOrder(orderId, buildCancelPayload(), authHeaders);
    const cancelOk = expectNoContent(cancelRes, 'cancelOrder');
    lifecycleOk = lifecycleOk && cancelOk;
  }

  // Record whether the entire scenario completed without a single failure
  orderLifecycleSuccess.add(lifecycleOk);

  sleep(randomInt(1, 3));
}
