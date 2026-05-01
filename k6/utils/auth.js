/**
 * Authentication utilities.
 *
 * Two caching strategies are exported:
 *
 *   loginOnce(credentials)
 *     Call from setup() in load-test.js to obtain a single token that is
 *     passed to every VU via the data object.  Minimises auth load when
 *     measuring throughput of business endpoints.
 *
 *   loginPerVU(credentials)
 *     Call inside the default function so each VU logs in independently.
 *     Used in stress-test.js to exercise the auth endpoint under heavy load
 *     and to avoid sharing a token that may expire during long runs.
 */

import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, TIMEOUT, DEFAULT_HEADERS } from '../config/config.js';

const LOGIN_URL = `${BASE_URL}/api/v1/auth/login`;

/**
 * Performs a login request and returns the raw JWT string.
 * Aborts the test iteration on failure so bad credentials are caught early.
 *
 * @param {{ username: string, password: string }} credentials
 * @returns {string} JWT access token
 */
export function login(credentials) {
  const payload = JSON.stringify({
    username: credentials.username,
    password: credentials.password,
  });

  const res = http.post(LOGIN_URL, payload, {
    headers: DEFAULT_HEADERS,
    timeout: TIMEOUT,
    tags: { endpoint: 'login' },
  });

  const ok = check(res, {
    'login: status 200': (r) => r.status === 200,
    'login: token present': (r) => {
      try {
        return JSON.parse(r.body).token !== undefined;
      } catch {
        return false;
      }
    },
  });

  if (!ok) {
    fail(
      `Login failed — status: ${res.status}, body: ${res.body.substring(0, 200)}`,
    );
  }

  return JSON.parse(res.body).token;
}

/**
 * Intended for use in setup(): fetches one token shared across all VUs.
 * The returned object is passed as the `data` argument to each VU.
 *
 * @param {{ username: string, password: string }} credentials
 * @returns {{ token: string, authHeaders: Object }}
 */
export function loginOnce(credentials) {
  const token = login(credentials);
  return {
    token,
    authHeaders: buildAuthHeaders(token),
  };
}

/**
 * Intended for use inside the default function when each VU needs its own
 * token (e.g. stress test, or when token TTL < test duration).
 *
 * @param {{ username: string, password: string }} credentials
 * @returns {{ token: string, authHeaders: Object }}
 */
export function loginPerVU(credentials) {
  return loginOnce(credentials);
}

/**
 * Builds the full set of HTTP headers for authenticated requests.
 *
 * @param {string} token  JWT access token
 * @returns {Object}
 */
export function buildAuthHeaders(token) {
  return {
    ...DEFAULT_HEADERS,
    Authorization: `Bearer ${token}`,
  };
}
