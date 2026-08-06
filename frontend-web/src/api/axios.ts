import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../lib/api';
import i18n from '../i18n';

// This module runs outside React component tree (request/response interceptors),
// so it can't use the useTranslation() hook — it calls the i18next singleton
// directly instead. errorCode-specific keys (errors.<CODE>) take priority over
// the generic fallback strings below, so any backend error code that has a
// translation entry is shown in the user's language automatically.
function te(key: string, fallback: string): string {
  return i18n.exists(key) ? i18n.t(key) : fallback;
}

export type ApiSeverity = 'success' | 'warning' | 'error' | 'info';

/**
 * Central error classification. `!error.response` alone used to be treated
 * as "the whole API server is unavailable" — but a cancelled request (an
 * AbortController-based typeahead/debounce cancelling its own previous
 * in-flight call, e.g. SmartVehicleSelector's `/availability/vehicles`
 * lookup on every date/filter change), a single slow request that hit the
 * client timeout, or one blip on an otherwise-healthy connection all share
 * that exact same shape. Only BACKEND_UNAVAILABLE/DNS_UNREACHABLE — and
 * only after the confirmation policy in the response interceptor below —
 * may ever surface the global "API unavailable" toast.
 */
export type ApiErrorCategory =
  | 'REQUEST_CANCELLED'
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'RATE_LIMITED'
  | 'SERVER_ERROR'
  | 'TIMEOUT'
  | 'NETWORK_CHANGED'
  | 'DNS_UNREACHABLE'
  | 'BACKEND_UNAVAILABLE'
  | 'UNKNOWN';

/** True for axios cancellation (CancelToken), fetch/DOM AbortError, and the ERR_CANCELED code axios assigns when an AbortSignal fires. */
export function isCancelledError(error: any): boolean {
  return axios.isCancel(error) || error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError' || error?.name === 'AbortError';
}

/** Pure classification — no side effects, safe to unit test directly with a synthetic error object. */
export function classifyApiError(error: any): ApiErrorCategory {
  if (isCancelledError(error)) return 'REQUEST_CANCELLED';

  const status = error?.response?.status;
  if (!error?.response) {
    const code = String(error?.code || '');
    const message = String(error?.message || '');
    if (code === 'ECONNABORTED' || /timeout/i.test(message)) return 'TIMEOUT';
    if (code === 'ERR_NETWORK_CHANGED') return 'NETWORK_CHANGED';
    if (/ERR_NAME_NOT_RESOLVED|ERR_ADDRESS_UNREACHABLE|ERR_INTERNET_DISCONNECTED/.test(code)
        || /ERR_NAME_NOT_RESOLVED|ERR_ADDRESS_UNREACHABLE|ERR_INTERNET_DISCONNECTED|dns/i.test(message)) {
      return 'DNS_UNREACHABLE';
    }
    return 'BACKEND_UNAVAILABLE';
  }

  if (status === 400 || status === 422) return 'VALIDATION_ERROR';
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 409) return 'CONFLICT';
  if (status === 429) return 'RATE_LIMITED';
  if (status === 502 || status === 503 || status === 504) return 'BACKEND_UNAVAILABLE';
  if (typeof status === 'number' && status >= 500) return 'SERVER_ERROR';
  return 'UNKNOWN';
}

export interface NormalizedApiError extends AxiosError {
  userMessage: string;
  severity: ApiSeverity;
  requestId?: string;
  feature?: string;
  networkError?: boolean;
  errorCode?: string;
  data?: unknown;
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
  withCredentials: true,
  timeout: 20000,
});

let isRefreshing = false;
let refreshSubscribers: ((error?: any) => void)[] = [];
let lastNetworkAlertAt = 0;
let authErrorDispatched = false;

function onTokenRefreshed(error?: any) {
  refreshSubscribers.forEach(callback => callback(error));
  refreshSubscribers = [];
}

function addRefreshSubscriber(callback: (error?: any) => void) {
  refreshSubscribers.push(callback);
}


function isAuthEntryRequest(url: string): boolean {
  return /\/auth\/(login|signup|register|google|oauth2\/exchange|refresh|logout|phone\/verify-otp|forgot-password|verify-reset-code|reset-password)/.test(url);
}

function isPublicRequest(url: string): boolean {
  return isAuthEntryRequest(url) || /\/(health|public\/|inspections\/token\/)/.test(url);
}

function getStoredAccessToken(): string | null {
  return localStorage.getItem('accessToken')
    || sessionStorage.getItem('accessToken')
    || localStorage.getItem('token')
    || sessionStorage.getItem('token');
}

function getStoredRefreshToken(): string | null {
  return localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');
}

function storeAccessToken(token: string) {
  localStorage.setItem('token', token);
  localStorage.setItem('accessToken', token);
}

function clearStoredAuth() {
  localStorage.removeItem('user');
  localStorage.removeItem('token');
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('tokenExpiry');
  sessionStorage.removeItem('token');
  sessionStorage.removeItem('accessToken');
  sessionStorage.removeItem('refreshToken');
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(window.atob(token.split('.')[1] || ''));
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}
function dispatchAuthErrorOnce() {
  if (authErrorDispatched) return;
  authErrorDispatched = true;
  if (!window.location.hash.startsWith('#/contract-sign') && !window.location.hash.startsWith('#/inspection')) {
    window.dispatchEvent(new Event('auth-error'));
  }
}

// Account-level blocks (suspended/blocked agency, expired/suspended
// subscription, plan limit reached) are a normal, recurring state — not a
// one-off failure — so every matching request would otherwise re-trigger the
// same toast/redirect. Dispatch the lock-state update once per occurrence of
// each code instead of on every single failed request.
const ACCOUNT_BLOCK_ERROR_CODES = new Set([
  'AGENCY_SUSPENDED', 'AGENCY_BLOCKED', 'AGENCY_ARCHIVED', 'SUBSCRIPTION_EXPIRED', 'SUBSCRIPTION_SUSPENDED', 'PLAN_LIMIT_REACHED',
]);
let lastDispatchedBlockCode: string | null = null;

function dispatchAccountBlockedOnce(errorCode: string, message: string, data: any) {
  if (lastDispatchedBlockCode === errorCode) return;
  lastDispatchedBlockCode = errorCode;
  window.dispatchEvent(new CustomEvent('account-blocked', { detail: { errorCode, message, data } }));
  window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'warning', message } }));
}

// Optional/best-effort endpoints whose failure must never force a full logout.
// They're fetched in the background by providers (notifications, sound
// settings, tenant appearance, agency branding) right after login; a single
// flaky/racing 401 on one of them must not tear down an otherwise-valid
// session. Only a confirmed failure on a core request (e.g. /me) should log
// the user out — see the response interceptor below.
const OPTIONAL_ENDPOINT_PATTERN = /\/(agency|tenant-settings|notifications\/unread|sound-settings|sse\/subscribe)(\/|$|\?)/;

function isOptionalEndpoint(url: string): boolean {
  return OPTIONAL_ENDPOINT_PATTERN.test(url);
}

/**
 * Check if a message is safe to show to end users.
 * Filters out technical/internal error details.
 */
function isSafeBusinessMessage(message: unknown): boolean {
  if (typeof message !== 'string' || !message.trim() || message.length > 220) return false;
  return !/(exception|stack|sql|jdbc|hibernate|nullpointer|null pointer|axios|fetch|database|constraint|at com\.|at java\.|at org\.|internal server|500|502|503|504)/i.test(message);
}

/**
 * Notify about network failure with throttling to prevent spam.
 * Callers must already have confirmed this is worth surfacing — see
 * `recordNetworkFailureAndShouldNotify` below — this function only adds the
 * "don't repeat the same toast" throttle on top of that decision.
 */
function notifyNetworkFailure() {
  const now = Date.now();
  if (now - lastNetworkAlertAt < 10000) return;
  lastNetworkAlertAt = now;
  window.dispatchEvent(new CustomEvent('app-toast', {
    detail: {
      type: 'error',
      message: te('errors.NETWORK_ERROR', 'API server unavailable. Please check your connection and try again.'),
    },
  }));
}

// ── "Confirm before declaring an outage" policy (spec: never after one failed
// request) ───────────────────────────────────────────────────────────────
// Mirrors BackendHealthGate's own multi-check confirmation, but at the
// per-request layer: a single BACKEND_UNAVAILABLE/DNS_UNREACHABLE-shaped
// failure only starts the clock. The global toast fires only once at least
// two such failures land within a short rolling window — one flaky request
// among many in-flight parallel calls must never alone read as "the whole
// API is down" (that's still true even while data on the current page is
// visibly loading fine from the requests that did succeed).
const recentNetworkFailureTimestamps: number[] = [];
const NETWORK_FAILURE_CONFIRM_WINDOW_MS = 4000;
const NETWORK_FAILURE_CONFIRM_COUNT = 2;

function recordNetworkFailureAndShouldNotify(): boolean {
  const now = Date.now();
  while (recentNetworkFailureTimestamps.length && now - recentNetworkFailureTimestamps[0] > NETWORK_FAILURE_CONFIRM_WINDOW_MS) {
    recentNetworkFailureTimestamps.shift();
  }
  recentNetworkFailureTimestamps.push(now);
  return recentNetworkFailureTimestamps.length >= NETWORK_FAILURE_CONFIRM_COUNT;
}

function isBrowserOnline(): boolean {
  return typeof navigator === 'undefined' || navigator.onLine !== false;
}

/** One-time brief wait before retrying a request that failed with ERR_NETWORK_CHANGED (spec §11: wait briefly, retry once, don't immediately show an outage toast). */
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Request interceptor: use bearer tokens stored for the current browser origin.
// localhost and 192.168.x.x are different origins, so each one logs in and
// persists its own token set.
api.interceptors.request.use(
  (config) => {
    config.withCredentials = true;
    if (config.headers) {
      config.headers['X-Requested-With'] = 'XMLHttpRequest';

      const requestUrl = config.url || '';
      const deviceId = localStorage.getItem('deviceFingerprint');
      if (deviceId) config.headers['X-Device-Id'] = deviceId;
      let token = getStoredAccessToken();
      if (token && isTokenExpired(token)) {
        clearStoredAuth();
        token = null;
        if (!isPublicRequest(requestUrl)) {
          dispatchAuthErrorOnce();
          return Promise.reject(new Error('Token expired'));
        }
      }
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
      if (config.data instanceof FormData) {
        delete config.headers['Content-Type'];
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Log client-side failures to the backend for admin review.
 * Never blocks or throws — fire-and-forget.
 */
function logClientFailure(error: NormalizedApiError) {
  if (!localStorage.getItem('user') || error.config?.url?.includes('/client-errors')) return;
  const payload = {
    message: error.message || 'API request failed',
    userMessage: error.userMessage,
    method: error.config?.method?.toUpperCase(),
    path: error.config?.url,
    status: error.response?.status,
    requestId: error.requestId,
    page: window.location.hash || window.location.pathname,
  };
  fetch(`${api.defaults.baseURL}/client-errors`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
    },
    credentials: 'include',
    body: JSON.stringify(payload),
    keepalive: true,
  }).catch(() => undefined);
}

// Response interceptor: handle errors, token refresh, normalization
api.interceptors.response.use(
  (response) => {
    const responseUrl = response.config?.url || '';
    if (/\/auth\/(login|refresh)/.test(responseUrl)) {
      authErrorDispatched = false;
      lastDispatchedBlockCode = null;
    }
    // Reactivation: once /me reports the platform is usable again, allow a
    // future re-suspension to toast/redirect again instead of staying
    // silenced by the earlier dispatch.
    if (/\/me(\?|$)/.test(responseUrl) && response.data?.accountAccess?.canUsePlatform === true) {
      lastDispatchedBlockCode = null;
    }
    // Check if response has a success message we should surface
    const data = response.data;
    if (data && typeof data === 'object' && data.success === true && data.message && typeof data.message === 'string') {
      // Backend sent an explicit success message — we could auto-toast here
      // But pages often handle their own success toasts, so we leave this optional
      // via a custom header: X-Auto-Notify: true
      if (response.headers['x-auto-notify'] === 'true') {
        const severity: ApiSeverity = data.severity || 'success';
        window.dispatchEvent(new CustomEvent('app-toast', {
          detail: { type: severity, message: data.message },
        }));
      }
    }
    return response;
  },
  async (rawError: AxiosError<any>) => {
    const error = rawError as NormalizedApiError;
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean; _networkChangeRetry?: boolean }) | undefined;
    if (!originalRequest) return Promise.reject(error);

    // Cancelled requests (AbortController.abort() on unmount/route-change/a
    // typeahead debounce superseding its own previous call, or an axios
    // CancelToken) are normal application behavior, not a failure — no
    // toast, no client-error log, no touching auth/session state. This must
    // be the very first check: a cancelled request has no `.response`, so
    // without this it would otherwise fall into the same bucket as a real
    // network outage below.
    if (isCancelledError(error)) {
      error.errorCode = 'REQUEST_CANCELLED';
      error.userMessage = '';
      return Promise.reject(error);
    }

    // ERR_NETWORK_CHANGED (wifi/cell handoff, VPN toggle mid-request) is
    // usually transient — wait briefly and retry the exact same request
    // once before treating it as anything worth telling the user about.
    if (classifyApiError(error) === 'NETWORK_CHANGED' && !originalRequest._networkChangeRetry) {
      originalRequest._networkChangeRetry = true;
      await delay(600);
      // Re-entering api(...) re-runs this same interceptor chain, so a
      // second failure is already fully classified/toasted (if warranted)
      // by that recursive pass — nothing more to do here than propagate it.
      return api(originalRequest);
    }

    const status = error.response?.status;
    const responseData = error.response?.data;
    const tokenExpired = error.response?.headers?.['token-expired'] === 'true';
    const authEntryRequest = isAuthEntryRequest(originalRequest.url || '');

    const hasSessionMarker = Boolean(
      getStoredAccessToken()
      || getStoredRefreshToken()
    );

    if ((tokenExpired || status === 401)
        && hasSessionMarker
        && !authEntryRequest
        && !originalRequest._retry) {
      originalRequest._retry = true;

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          addRefreshSubscriber((err: any) => {
            if (err) {
              reject(err);
            } else {
              resolve(api(originalRequest));
            }
          });
        });
      }
      isRefreshing = true;
      try {
        const refreshResponse = await axios.post(`${api.defaults.baseURL}/auth/refresh`, {
          refreshToken: getStoredRefreshToken(),
        }, {
          withCredentials: true,
          headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
          },
        });
        const payload = refreshResponse.data?.data || refreshResponse.data;
        if (payload?.accessToken) {
          storeAccessToken(payload.accessToken);
          if (originalRequest.headers) {
            originalRequest.headers['Authorization'] = `Bearer ${payload.accessToken}`;
          }
        }
        if (payload?.refreshToken) {
          localStorage.setItem('refreshToken', payload.refreshToken);
          sessionStorage.removeItem('refreshToken');
        }
        onTokenRefreshed();
        return api(originalRequest);
      } catch (refreshError) {
        onTokenRefreshed(refreshError);
        // Only a core/required request (e.g. /me) forces a full logout here.
        // Optional background calls (agency, tenant-settings, notifications,
        // sound-settings) must fail silently — the providers already swallow
        // their own errors — so one flaky 401 right after login can't tear
        // down an otherwise-valid session.
        if (!isOptionalEndpoint(originalRequest.url || '')) {
          clearStoredAuth();
          dispatchAuthErrorOnce();
        }
        (refreshError as NormalizedApiError).userMessage = 'Session expired. Please sign in again.';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    const responseDataObj = (responseData && typeof responseData === 'object') ? responseData as Record<string, any> : {};
    // FeatureAccessInterceptor (server) sends errorCode="FEATURE_NOT_INCLUDED" for a plan-gated
    // endpoint the tenant's current subscription doesn't include — this used to check a
    // nonexistent `error` field and could never actually match, so every plan-lock 403 (e.g.
    // Payments on a Basic/Trial plan) silently fell through to the generic 403 branch below.
    const featureLocked = status === 403 && responseDataObj.errorCode === 'FEATURE_NOT_INCLUDED';
    error.requestId = responseDataObj.requestId;
    error.severity = responseDataObj.severity === 'warning' ? 'warning' : 'error';
    error.errorCode = responseDataObj.errorCode;
    error.data = responseDataObj.data;

    // An errorCode-specific translation always wins over the generic
    // per-status fallback text below — this is what lets any backend error
    // code (VEHICLE_NOT_FOUND, INVALID_MAINTENANCE_STATUS_TRANSITION, etc.)
    // show up translated without every page having to know about it.
    const codeTranslation = responseDataObj.errorCode
      ? (i18n.exists(`errors.${responseDataObj.errorCode}`) ? i18n.t(`errors.${responseDataObj.errorCode}`) : null)
      : null;

    if (ACCOUNT_BLOCK_ERROR_CODES.has(responseDataObj.errorCode)) {
      error.userMessage = codeTranslation
        || (isSafeBusinessMessage(responseDataObj.message) ? responseDataObj.message : te('errors.ACCOUNT_SUSPENDED', 'Your agency account is suspended. Please contact Innovax Technologies or update your subscription.'));
      error.severity = 'warning';
      dispatchAccountBlockedOnce(responseDataObj.errorCode, error.userMessage, responseDataObj.data);
      // This is an expected, recurring account-state response, not a bug —
      // reporting it to /client-errors on every blocked request would just
      // spam the audit log with the same non-error over and over.
      return Promise.reject(error);
    }

    if (!error.response) {
      // No response reached the client at all — but that shape covers several
      // very different situations, only one of which is a real outage.
      const category = classifyApiError(error);
      error.errorCode = category;

      if (category === 'TIMEOUT') {
        // A single slow request, not a server-wide problem — no global toast.
        // Whatever section/action made this call decides how to show it.
        error.userMessage = te('errors.TIMEOUT', 'The request took too long. Please try again.');
      } else {
        error.networkError = true;
        error.userMessage = te('errors.NETWORK_ERROR', 'API server unavailable. Please check your connection and try again.');
        // Never declare an outage while the browser itself reports being
        // offline — BackendHealthGate's dedicated OfflineBanner already owns
        // that state, so this must not fire a second, conflicting message.
        // Otherwise, only surface the toast once at least two independent
        // requests have failed this way within a short window (never after
        // a single failed request, matching BackendHealthGate's own
        // multi-check confirmation policy one layer up).
        if (isBrowserOnline() && recordNetworkFailureAndShouldNotify()) {
          notifyNetworkFailure();
        }
      }
    } else if (status === 401) {
      error.userMessage = codeTranslation || te('errors.UNAUTHORIZED', 'Your session has expired. Please sign in again.');
    } else if (featureLocked) {
      error.userMessage = codeTranslation
        || (isSafeBusinessMessage(responseDataObj.message) ? responseDataObj.message : te('errors.FEATURE_NOT_INCLUDED', 'This feature is not included in your current plan.'));
      error.feature = responseDataObj.feature || responseDataObj.data?.feature;
      error.severity = 'warning';
    } else if (status === 403) {
      error.userMessage = codeTranslation
        || (isSafeBusinessMessage(responseDataObj.message) ? responseDataObj.message : te('errors.PERMISSION_DENIED', 'You do not have permission to perform this action.'));
    } else if (status && status >= 500) {
      // Prefer an errorCode translation, then the backend's own message
      // (e.g. a controller-level "please check backend logs" or a specific
      // failure reason), and only fall back to the generic text when the
      // backend gave no usable message at all.
      error.userMessage = codeTranslation
        || (isSafeBusinessMessage(responseDataObj.message) ? responseDataObj.message : te('errors.SERVER_ERROR', 'Server error. Please check backend logs.'));
    } else if (codeTranslation) {
      error.userMessage = codeTranslation;
    } else if (isSafeBusinessMessage(responseDataObj.message)) {
      // Safe business message from backend — show it. This still wins over the
      // status===404 branch below: a genuine "entity not found" 404 (e.g. a
      // deleted vehicle/contract) already carries a real backend message and
      // must keep showing it, not a generic "misconfigured" message.
      error.userMessage = responseDataObj.message;
    } else if (status === 404) {
      // No usable backend message at all on a 404 — this is the shape of a
      // request hitting a path the backend never mapped (a frontend/backend
      // route mismatch), not a normal "not found" business response.
      error.userMessage = te('errors.ENDPOINT_NOT_FOUND', 'This feature could not be reached. The app may be misconfigured — please contact support.');
    } else {
      // Unsafe or missing message — generic fallback
      error.userMessage = te('errors.REQUEST_FAILED', 'We could not complete this request. Please try again.');
    }

    logClientFailure(error);
    return Promise.reject(error);
  }
);

/**
 * Extracts the best available user-facing message from a caught API error.
 * Prefers the errorCode-translated message the response interceptor already
 * computed (`error.userMessage`) over a generic fallback, so a specific
 * backend errorCode (e.g. SMTP_NOT_CONFIGURED, INVALID_CODE) is never masked
 * behind a generic "service unavailable" string.
 */
export function translateApiError(error: unknown, t: (...args: any[]) => unknown): string {
  const err = error as NormalizedApiError;
  return err?.userMessage
    || (t('errors.REQUEST_FAILED', 'We could not complete this request. Please try again.') as string);
}

export default api;

