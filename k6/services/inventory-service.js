/**
 * HTTP helpers for the Inventory API.
 */

import http from 'k6/http';
import { BASE_URL, TIMEOUT, DEFAULT_HEADERS } from '../config/config.js';

const BASE = `${BASE_URL}/api/v1/inventory`;

/**
 * GET /api/v1/inventory/report
 * The deliberately slow query — full table scan on inventory_transactions.
 */
export function getInventoryReport(params, authHeaders) {
  const qs = buildQueryString({
    categoryId: params.categoryId || null,
    warehouseId: params.warehouseId || null,
    minStock: params.minStock !== undefined ? params.minStock : 0,
    page: params.page || 0,
    pageSize: params.pageSize || 100,
  });
  return http.get(`${BASE}/report${qs}`, {
    headers: authHeaders,
    timeout: TIMEOUT,
    tags: { endpoint: 'inventoryReport' },
  });
}

/**
 * GET /api/v1/inventory/products/{productId}/stock
 */
export function getProductStock(productId, authHeaders) {
  return http.get(`${BASE}/products/${productId}/stock`, {
    headers: authHeaders,
    timeout: TIMEOUT,
    tags: { endpoint: 'productStock' },
  });
}

/**
 * GET /api/v1/inventory/low-stock
 */
export function getLowStock(threshold, limit, authHeaders) {
  const qs = buildQueryString({ threshold: threshold || 10, limit: limit || 50 });
  return http.get(`${BASE}/low-stock${qs}`, {
    headers: authHeaders,
    timeout: TIMEOUT,
    tags: { endpoint: 'lowStock' },
  });
}

/**
 * POST /api/v1/inventory/reserve
 */
export function reserveInventory(payload, authHeaders) {
  return http.post(`${BASE}/reserve`, JSON.stringify(payload), {
    headers: { ...DEFAULT_HEADERS, ...authHeaders },
    timeout: TIMEOUT,
    tags: { endpoint: 'reserveInventory' },
  });
}

/**
 * POST /api/v1/inventory/adjust
 */
export function adjustInventory(payload, authHeaders) {
  return http.post(`${BASE}/adjust`, JSON.stringify(payload), {
    headers: { ...DEFAULT_HEADERS, ...authHeaders },
    timeout: TIMEOUT,
    tags: { endpoint: 'adjustInventory' },
  });
}

function buildQueryString(params) {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  return parts.length > 0 ? '?' + parts.join('&') : '';
}
