/**
 * Random data generators for building realistic, varied request payloads.
 *
 * Varied payloads prevent caching artefacts from masking real latency
 * and make query plans exercise multiple rows instead of one hot row.
 */

import { CUSTOMER_POOL, PRODUCT_CATALOGUE } from '../config/config.js';

/**
 * Returns a random integer in [min, max] (inclusive).
 *
 * @param {number} min
 * @param {number} max
 * @returns {number}
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Returns a random element from an array.
 *
 * @template T
 * @param {T[]} arr
 * @returns {T}
 */
export function randomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Returns a random customer ID from the shared pool.
 * Using a pool (rather than fully random IDs) means reads will
 * hit rows that actually exist in the database.
 *
 * @returns {string}
 */
export function randomCustomerId() {
  return randomFrom(CUSTOMER_POOL);
}

/**
 * Builds a random order payload.
 * Item count varies (1–4) and products are drawn from the catalogue
 * so foreign-key constraints are never violated.
 *
 * @param {string} [customerId]  override customer ID (default: random from pool)
 * @returns {Object}  body ready for JSON.stringify()
 */
export function buildOrderPayload(customerId) {
  const customer = customerId || randomCustomerId();
  const itemCount = randomInt(1, 4);

  // Pick itemCount distinct products (no duplicate line items)
  const shuffled = [...PRODUCT_CATALOGUE].sort(() => Math.random() - 0.5);
  const selectedProducts = shuffled.slice(0, itemCount);

  const items = selectedProducts.map((product) => ({
    productId: product.productId,
    productName: product.productName,
    quantity: randomInt(1, 10),
    unitPrice: product.unitPrice,
    currency: 'USD',
  }));

  return { customerId: customer, items };
}

/**
 * Builds a cancel-order payload with a random reason chosen from a
 * realistic set.  Varied reasons exercise the full text column.
 *
 * @returns {Object}
 */
export function buildCancelPayload() {
  const reasons = [
    'Customer requested cancellation',
    'Item out of stock',
    'Duplicate order',
    'Payment failed',
    'Shipping address incorrect',
  ];
  return { reason: randomFrom(reasons) };
}

/**
 * Generates a UUID v4 string without external dependencies.
 * Used wherever a realistic random ID is needed (e.g. non-existent order
 * probes to measure 404 latency).
 *
 * @returns {string}
 */
export function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
