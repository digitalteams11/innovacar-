import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';
import '../../i18n';
import api, { classifyApiError, isCancelledError } from '../axios';

/**
 * Regression coverage for the false "API server unavailable" toast: a
 * cancelled request (AbortController.abort() on a debounced typeahead like
 * SmartVehicleSelector's `/availability/vehicles` lookup), a single slow
 * request, or one isolated network blip must never be classified — or
 * toasted — as a full backend outage. Only classifyApiError/isCancelledError
 * are pure enough to unit test directly; the interceptor-level dedupe/retry
 * behavior is exercised by grabbing the registered rejection handler off the
 * shared `api` instance (axios exposes it at
 * `interceptors.response.handlers[0].rejected`) and invoking it with
 * synthetic AxiosError-shaped objects, the same way axios itself would.
 */

function axiosError(overrides: Record<string, any>) {
  const err: any = new Error(overrides.message || 'Request failed');
  err.isAxiosError = true;
  err.config = { url: '/some/endpoint', headers: {} };
  Object.assign(err, overrides);
  return err;
}

function getRejectedHandler() {
  const handlers = (api.interceptors.response as any).handlers as Array<{ rejected: (e: any) => any }>;
  return handlers[handlers.length - 1].rejected;
}

describe('classifyApiError', () => {
  it('classifies a cancelled request as REQUEST_CANCELLED', () => {
    const err = axiosError({ code: 'ERR_CANCELED' });
    expect(classifyApiError(err)).toBe('REQUEST_CANCELLED');
    expect(isCancelledError(err)).toBe(true);
  });

  it('classifies a DOM AbortError as REQUEST_CANCELLED', () => {
    const err = axiosError({ name: 'AbortError' });
    expect(classifyApiError(err)).toBe('REQUEST_CANCELLED');
  });

  it('classifies a client timeout as TIMEOUT, not an outage', () => {
    const err = axiosError({ code: 'ECONNABORTED', message: 'timeout of 20000ms exceeded' });
    expect(classifyApiError(err)).toBe('TIMEOUT');
  });

  it('classifies ERR_NETWORK_CHANGED distinctly from a generic outage', () => {
    const err = axiosError({ code: 'ERR_NETWORK_CHANGED' });
    expect(classifyApiError(err)).toBe('NETWORK_CHANGED');
  });

  it('classifies a DNS failure as DNS_UNREACHABLE', () => {
    const err = axiosError({ message: 'ERR_NAME_NOT_RESOLVED' });
    expect(classifyApiError(err)).toBe('DNS_UNREACHABLE');
  });

  it('classifies a bare connection failure (no code, no response) as BACKEND_UNAVAILABLE', () => {
    const err = axiosError({ code: 'ERR_NETWORK' });
    expect(classifyApiError(err)).toBe('BACKEND_UNAVAILABLE');
  });

  it.each([
    [400, 'VALIDATION_ERROR'],
    [422, 'VALIDATION_ERROR'],
    [401, 'UNAUTHORIZED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [409, 'CONFLICT'],
    [429, 'RATE_LIMITED'],
    [500, 'SERVER_ERROR'],
    [502, 'BACKEND_UNAVAILABLE'],
    [503, 'BACKEND_UNAVAILABLE'],
    [504, 'BACKEND_UNAVAILABLE'],
    [418, 'UNKNOWN'],
  ] as const)('classifies HTTP %i as %s', (status, expected) => {
    const err = axiosError({ response: { status, data: {} } });
    expect(classifyApiError(err)).toBe(expected);
  });
});

describe('response interceptor', () => {
  let toastEvents: CustomEvent[];
  let handleToast: (e: Event) => void;
  let testClock = Date.now();

  beforeEach(() => {
    vi.useFakeTimers();
    // The interceptor's network-failure confirmation window is a module-level
    // rolling 4s buffer, so each test must start far enough ahead of the
    // previous one that any leftover timestamps are already stale and get
    // pruned on this test's first call — otherwise tests would pollute each
    // other's "how many failures have we seen recently" count.
    testClock += 60_000;
    vi.setSystemTime(testClock);
    toastEvents = [];
    handleToast = (e: Event) => toastEvents.push(e as CustomEvent);
    window.addEventListener('app-toast', handleToast);
    localStorage.setItem('user', JSON.stringify({ id: 1 }));
    // logClientFailure fires a fire-and-forget fetch; keep it inert in tests.
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true } as any)));
  });

  afterEach(() => {
    window.removeEventListener('app-toast', handleToast);
    localStorage.clear();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('a cancelled request is rejected silently — no toast, empty userMessage', async () => {
    const rejected = getRejectedHandler();
    const err = axiosError({ code: 'ERR_CANCELED' });

    await expect(rejected(err)).rejects.toBe(err);

    expect(err.userMessage).toBe('');
    expect(err.errorCode).toBe('REQUEST_CANCELLED');
    expect(toastEvents).toHaveLength(0);
  });

  it('a single network-error-shaped failure does not toast (needs confirmation)', async () => {
    const rejected = getRejectedHandler();
    const err = axiosError({ code: 'ERR_NETWORK' });

    await expect(rejected(err)).rejects.toBe(err);

    expect(err.userMessage).toMatch(/unavailable/i);
    expect(toastEvents).toHaveLength(0);
  });

  it('two network-error-shaped failures within the confirmation window toast once', async () => {
    const rejected = getRejectedHandler();

    await expect(rejected(axiosError({ code: 'ERR_NETWORK' }))).rejects.toBeTruthy();
    await expect(rejected(axiosError({ code: 'ERR_NETWORK' }))).rejects.toBeTruthy();

    expect(toastEvents).toHaveLength(1);
    expect(toastEvents[0].detail.message).toMatch(/unavailable/i);
  });

  it('parallel failures beyond the second stay deduplicated within the throttle window', async () => {
    const rejected = getRejectedHandler();
    for (let i = 0; i < 5; i += 1) {
      await expect(rejected(axiosError({ code: 'ERR_NETWORK' }))).rejects.toBeTruthy();
    }
    expect(toastEvents).toHaveLength(1);
  });

  it('a client timeout never triggers the global outage toast', async () => {
    const rejected = getRejectedHandler();
    const err = axiosError({ code: 'ECONNABORTED', message: 'timeout of 20000ms exceeded' });

    await expect(rejected(err)).rejects.toBe(err);

    expect(err.errorCode).toBe('TIMEOUT');
    expect(err.userMessage).toMatch(/took too long/i);
    expect(toastEvents).toHaveLength(0);
  });

  it('a 404 with a real backend message never shows the outage toast', async () => {
    const rejected = getRejectedHandler();
    const err = axiosError({ response: { status: 404, data: { message: 'Vehicle not found' } } });

    await expect(rejected(err)).rejects.toBe(err);

    expect(err.userMessage).toBe('Vehicle not found');
    expect(toastEvents).toHaveLength(0);
  });

  it('a 409 conflict surfaces the backend business message, not a generic outage', async () => {
    const rejected = getRejectedHandler();
    const err = axiosError({ response: { status: 409, data: { message: 'This vehicle already has an active maintenance order.' } } });

    await expect(rejected(err)).rejects.toBe(err);

    expect(err.userMessage).toBe('This vehicle already has an active maintenance order.');
    expect(toastEvents).toHaveLength(0);
  });

  it('does not toast when the browser itself is offline (BackendHealthGate owns that state)', async () => {
    const rejected = getRejectedHandler();
    const originalOnLine = Object.getOwnPropertyDescriptor(window.navigator, 'onLine');
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });

    try {
      await expect(rejected(axiosError({ code: 'ERR_NETWORK' }))).rejects.toBeTruthy();
      await expect(rejected(axiosError({ code: 'ERR_NETWORK' }))).rejects.toBeTruthy();
      expect(toastEvents).toHaveLength(0);
    } finally {
      if (originalOnLine) Object.defineProperty(window.navigator, 'onLine', originalOnLine);
    }
  });
});

describe('isCancelledError', () => {
  it('recognizes the ERR_CANCELED code axios assigns when an AbortSignal fires', () => {
    const err = axiosError({ code: 'ERR_CANCELED', message: 'canceled' });
    expect(isCancelledError(err)).toBe(true);
  });

  it('recognizes axios.isCancel-shaped legacy CancelToken errors', () => {
    const err = axiosError({ __CANCEL__: true, message: 'canceled' });
    expect(axios.isCancel(err)).toBe(true);
    expect(isCancelledError(err)).toBe(true);
  });

  it('does not misclassify an ordinary network error as cancelled', () => {
    const err = axiosError({ code: 'ERR_NETWORK' });
    expect(isCancelledError(err)).toBe(false);
  });
});
