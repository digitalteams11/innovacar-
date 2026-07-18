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
  return /\/auth\/(login|signup|register|google|refresh|logout|phone\/verify-otp|forgot-password|verify-reset-code|reset-password)/.test(url);
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
  'AGENCY_SUSPENDED', 'AGENCY_BLOCKED', 'SUBSCRIPTION_EXPIRED', 'SUBSCRIPTION_SUSPENDED', 'PLAN_LIMIT_REACHED',
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
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (!originalRequest) return Promise.reject(error);

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
      // Network error — no response from server
      error.networkError = true;
      error.userMessage = te('errors.NETWORK_ERROR', 'API server unavailable. Please check your connection and try again.');
      notifyNetworkFailure();
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

// Offline detection
window.addEventListener('offline', notifyNetworkFailure);

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

