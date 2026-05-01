/**
 * API service layer — Order Service.
 *
 * Every function wraps one HTTP call and returns the raw k6 Response object.
 * Checks and metric recording are left to the caller (scenarios) so that
 * the service layer stays a thin, reusable adapter over the HTTP module.
 *
 * Endpoints covered:
 *   POST   /api/v1/orders                      createOrder()
 *   GET    /api/v1/orders/{id}                 getOrderById()
 *   GET    /api/v1/orders?customerId=...        listOrdersByCustomer()
 *   POST   /api/v1/orders/{id}/confirm         confirmOrder()
 *   DELETE /api/v1/orders/{id}                 cancelOrder()
 */

import http from 'k6/http';
import { BASE_URL, TIMEOUT } from '../config/config.js';

const ORDERS_BASE = `${BASE_URL}/api/v1/orders`;

/**
 * Creates a new order.
 *
 * @param {Object} payload   { customerId, items: [{ productId, productName, quantity, unitPrice, currency }] }
 * @param {Object} headers   Authenticated headers from auth.buildAuthHeaders()
 * @returns {import('k6/http').Response}
 */
export function createOrder(payload, headers) {
  return http.post(ORDERS_BASE, JSON.stringify(payload), {
    headers,
    timeout: TIMEOUT,
    tags: { endpoint: 'createOrder' },
  });
}

/**
 * Retrieves a single order by its ID.
 *
 * @param {string} orderId
 * @param {Object} headers
 * @returns {import('k6/http').Response}
 */
export function getOrderById(orderId, headers) {
  return http.get(`${ORDERS_BASE}/${orderId}`, {
    headers,
    timeout: TIMEOUT,
    tags: { endpoint: 'getOrderById' },
  });
}

/**
 * Lists all orders for a customer.
 *
 * @param {string} customerId
 * @param {Object} headers
 * @returns {import('k6/http').Response}
 */
export function listOrdersByCustomer(customerId, headers) {
  return http.get(`${ORDERS_BASE}?customerId=${customerId}`, {
    headers,
    timeout: TIMEOUT,
    tags: { endpoint: 'listOrders' },
  });
}

/**
 * Confirms a PENDING order (transitions it to CONFIRMED).
 *
 * @param {string} orderId
 * @param {Object} headers
 * @returns {import('k6/http').Response}
 */
export function confirmOrder(orderId, headers) {
  return http.post(`${ORDERS_BASE}/${orderId}/confirm`, null, {
    headers,
    timeout: TIMEOUT,
    tags: { endpoint: 'confirmOrder' },
  });
}

/**
 * Cancels a PENDING or CONFIRMED order.
 *
 * @param {string} orderId
 * @param {Object} payload  { reason: string }
 * @param {Object} headers
 * @returns {import('k6/http').Response}
 */
export function cancelOrder(orderId, payload, headers) {
  return http.del(`${ORDERS_BASE}/${orderId}`, JSON.stringify(payload), {
    headers,
    timeout: TIMEOUT,
    tags: { endpoint: 'cancelOrder' },
  });
}
