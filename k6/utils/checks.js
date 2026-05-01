/**
 * Reusable check helpers and failure loggers.
 *
 * Keeping check logic here avoids duplicating the same assertions
 * across every scenario and makes it easy to tighten SLOs in one place.
 */

import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ── Custom metrics ────────────────────────────────────────────────────────────

/** Tracks how often the full order-lifecycle scenario succeeds end-to-end. */
export const orderLifecycleSuccess = new Rate('order_lifecycle_success');

/** Tracks p95 latency of the business-critical order-creation step. */
export const orderCreateLatency = new Trend('order_create_latency_ms', true);

/** Tracks p95 latency of order confirmation. */
export const orderConfirmLatency = new Trend('order_confirm_latency_ms', true);

// ── Generic check wrappers ────────────────────────────────────────────────────

/**
 * Asserts the response has one of the expected HTTP status codes and logs a
 * structured failure message when the assertion fails.
 *
 * @param {import('k6/http').Response} res
 * @param {number[]} expectedStatuses
 * @param {string} label  short description used in the failure log
 * @returns {boolean}  true if the check passed
 */
export function expectStatus(res, expectedStatuses, label) {
  const passed = check(res, {
    [`${label}: status ${expectedStatuses.join('|')}`]: (r) =>
      expectedStatuses.includes(r.status),
  });

  if (!passed) {
    console.error(
      `[FAIL] ${label} — ` +
        `status: ${res.status}, ` +
        `duration: ${res.timings.duration.toFixed(0)}ms, ` +
        `url: ${res.url}, ` +
        `body: ${res.body ? res.body.substring(0, 300) : '<empty>'}`,
    );
  }

  return passed;
}

/**
 * Asserts a 200 OK response and that the body is valid JSON containing an
 * expected field.
 *
 * @param {import('k6/http').Response} res
 * @param {string} requiredField  top-level JSON key that must be present
 * @param {string} label
 * @returns {boolean}
 */
export function expectOkWithField(res, requiredField, label) {
  return check(res, {
    [`${label}: status 200`]: (r) => r.status === 200,
    [`${label}: body has ${requiredField}`]: (r) => {
      try {
        return JSON.parse(r.body)[requiredField] !== undefined;
      } catch {
        return false;
      }
    },
    [`${label}: response time < 500ms`]: (r) => r.timings.duration < 500,
  });
}

/**
 * Asserts a 201 Created response and extracts the created resource's ID.
 * Returns null and logs on failure so the caller can skip dependent steps.
 *
 * @param {import('k6/http').Response} res
 * @param {string} idField  JSON key holding the new resource's ID
 * @param {string} label
 * @returns {string|null}
 */
export function expectCreated(res, idField, label) {
  const passed = check(res, {
    [`${label}: status 201`]: (r) => r.status === 201,
    [`${label}: ${idField} present`]: (r) => {
      try {
        return JSON.parse(r.body)[idField] !== undefined;
      } catch {
        return false;
      }
    },
    [`${label}: response time < 600ms`]: (r) => r.timings.duration < 600,
  });

  if (!passed) {
    console.error(
      `[FAIL] ${label} — ` +
        `status: ${res.status}, ` +
        `duration: ${res.timings.duration.toFixed(0)}ms, ` +
        `body: ${res.body ? res.body.substring(0, 300) : '<empty>'}`,
    );
    return null;
  }

  try {
    return JSON.parse(res.body)[idField];
  } catch {
    console.error(`[FAIL] ${label} — could not parse response body as JSON`);
    return null;
  }
}

/**
 * Asserts a 204 No Content response (used for confirm / cancel).
 *
 * @param {import('k6/http').Response} res
 * @param {string} label
 * @returns {boolean}
 */
export function expectNoContent(res, label) {
  return expectStatus(res, [204], label);
}
