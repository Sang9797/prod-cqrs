/**
 * Scenario: Read-heavy workload.
 *
 * Simulates a reporting / dashboard consumer that only reads:
 *   1. List all orders for a random customer
 *   2. Get a specific order by ID (picked from the list)
 *   3. Repeat with another customer
 *
 * Used in load-test.js as a second scenario alongside the write-heavy
 * order-flow to produce a mixed read/write ratio that reflects production.
 *
 * Imported by load-test.js.  Does not export options.
 */

import { sleep } from 'k6';
import { listOrdersByCustomer, getOrderById } from '../services/order-service.js';
import { expectStatus, expectOkWithField } from '../utils/checks.js';
import { randomCustomerId, randomInt } from '../utils/data.js';

/**
 * Executes one read-heavy iteration.
 *
 * @param {Object} authHeaders  Authenticated headers
 */
export function runReadHeavy(authHeaders) {
  const customerId = randomCustomerId();

  // ── List orders for a customer ────────────────────────────────────────────
  const listRes = listOrdersByCustomer(customerId, authHeaders);
  expectStatus(listRes, [200], 'readHeavy:listOrders');

  // If any orders exist, pick one and fetch it individually to exercise the
  // getOrderById path.  If the customer has no orders yet, skip the GET.
  let orders = [];
  try {
    orders = JSON.parse(listRes.body);
  } catch {
    // empty or non-JSON body — tolerated during ramp-up before data exists
  }

  sleep(randomInt(1, 2));

  if (Array.isArray(orders) && orders.length > 0) {
    const picked = orders[Math.floor(Math.random() * orders.length)];
    const getRes = getOrderById(picked.orderId, authHeaders);
    expectOkWithField(getRes, 'orderId', 'readHeavy:getOrderById');

    sleep(randomInt(1, 2));
  }

  sleep(randomInt(1, 3));
}
