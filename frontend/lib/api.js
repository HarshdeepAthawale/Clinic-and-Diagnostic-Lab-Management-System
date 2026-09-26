/**
 * The only way the frontend talks to the backend (Docs/API.md).
 *
 * - Calls go to /api on this origin; next.config.js forwards them to Spring Boot.
 * - The JWT lives in an httpOnly cookie the browser sends automatically (ADR-009).
 * - Every mutating request carries X-CSRF-Protection: 1 (ADR-014).
 */

export const CSRF_HEADER = 'X-CSRF-Protection';
const SAFE_METHODS = new Set(['GET', 'HEAD']);

/** An API failure with the backend's `{ error, code, fields }` shape. */
export class ApiError extends Error {
  /**
   * @param {number} status HTTP status (0 when the server couldn't be reached)
   * @param {string} code machine-readable code, e.g. INVALID_CREDENTIALS
   * @param {string} message human-readable message from the server
   * @param {Record<string, string>} [fields] per-field validation messages
   */
  constructor(status, code, message, fields) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields ?? null;
  }
}

/**
 * @param {string} path path under /api, e.g. '/auth/me'
 * @param {{ method?: string, body?: unknown, signal?: AbortSignal }} [options]
 * @returns {Promise<any>} parsed JSON, or null for 204 responses
 */
export async function api(path, { method = 'GET', body, signal } = {}) {
  const upperMethod = method.toUpperCase();
  const headers = { Accept: 'application/json' };
  if (!SAFE_METHODS.has(upperMethod)) headers[CSRF_HEADER] = '1';
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`/api${path}`, {
      method: upperMethod,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal,
    });
  } catch (error) {
    if (error?.name === 'AbortError') throw error;
    throw new ApiError(0, 'NETWORK_ERROR', "Can't reach the server. Check your connection.");
  }

  if (response.status === 204) return null;

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.code ?? `HTTP_${response.status}`,
      data?.error ?? 'Something went wrong',
      data?.fields,
    );
  }
  return data;
}
