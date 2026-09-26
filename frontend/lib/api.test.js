import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, CSRF_HEADER } from './api';

function mockFetch(status, body) {
  const fetchMock = vi.fn().mockResolvedValue({
    status,
    ok: status >= 200 && status < 300,
    json: () => (body === undefined ? Promise.reject(new Error('no body')) : Promise.resolve(body)),
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe('api', () => {
  it('calls /api on the same origin and does not send the CSRF header on GET', async () => {
    const fetchMock = mockFetch(200, { id: 1 });

    await expect(api('/auth/me')).resolves.toEqual({ id: 1 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/me');
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('same-origin');
    expect(init.headers[CSRF_HEADER]).toBeUndefined();
  });

  it('adds the CSRF header and JSON body on mutations (ADR-014)', async () => {
    const fetchMock = mockFetch(200, { ok: true });

    await api('/auth/login', { method: 'post', body: { email: 'a@b.c' } });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('POST');
    expect(init.headers[CSRF_HEADER]).toBe('1');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"email":"a@b.c"}');
  });

  it('sends the CSRF header even without a body', async () => {
    const fetchMock = mockFetch(204);

    await expect(api('/auth/logout', { method: 'POST' })).resolves.toBeNull();
    expect(fetchMock.mock.calls[0][1].headers[CSRF_HEADER]).toBe('1');
  });

  it('turns error responses into ApiError with code and field errors', async () => {
    mockFetch(400, { error: 'Some fields are invalid', code: 'VALIDATION_FAILED', fields: { email: 'bad' } });

    const error = await api('/auth/register', { method: 'POST', body: {} }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.fields).toEqual({ email: 'bad' });
  });

  it('falls back to an HTTP code when the error body is not JSON', async () => {
    mockFetch(502);

    const error = await api('/auth/me').catch((e) => e);

    expect(error.code).toBe('HTTP_502');
  });

  it('reports network failures as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await api('/auth/me').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
  });
});
