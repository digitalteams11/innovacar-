import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../lib/api';

export type ApiSeverity = 'success' | 'warning' | 'error' | 'info';

export interface NormalizedApiError extends AxiosError {
  userMessage: string;
  severity: ApiSeverity;
  requestId?: string;
  feature?: string;
  networkError?: boolean;
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
      message: 'Backend server is not running on port 8082.',
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

      const token = localStorage.getItem('token');
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
    const authEntryRequest = /\/auth\/(login|signup|register|google|refresh|logout|phone\/verify-otp)/.test(
      originalRequest.url || ''
    );

    const hasSessionMarker = Boolean(
      localStorage.getItem('token')
      || localStorage.getItem('refreshToken')
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
          refreshToken: localStorage.getItem('refreshToken'),
        }, {
          withCredentials: true,
          headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
          },
        });
        const payload = refreshResponse.data?.data || refreshResponse.data;
        if (payload?.accessToken) {
          localStorage.setItem('token', payload.accessToken);
          if (originalRequest.headers) {
            originalRequest.headers['Authorization'] = `Bearer ${payload.accessToken}`;
          }
        }
        if (payload?.refreshToken) {
          localStorage.setItem('refreshToken', payload.refreshToken);
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
          localStorage.removeItem('user');
          localStorage.removeItem('token');
          localStorage.removeItem('refreshToken');
          localStorage.removeItem('tokenExpiry');
          dispatchAuthErrorOnce();
        }
        (refreshError as NormalizedApiError).userMessage = 'Session expired. Please sign in again.';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    const responseDataObj = (responseData && typeof responseData === 'object') ? responseData as Record<string, any> : {};
    const featureLocked = status === 403 && responseDataObj.error === 'FEATURE_NOT_AVAILABLE';
    error.requestId = responseDataObj.requestId;
    error.severity = responseDataObj.severity === 'warning' ? 'warning' : 'error';

    if (ACCOUNT_BLOCK_ERROR_CODES.has(responseDataObj.errorCode)) {
      error.userMessage = isSafeBusinessMessage(responseDataObj.message)
        ? responseDataObj.message
        : 'Your agency account is suspended. Please contact Innovax Technologies or update your subscription.';
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
      error.userMessage = 'Backend server is not running on port 8082.';
      notifyNetworkFailure();
    } else if (status === 401) {
      error.userMessage = 'Your session has expired. Please sign in again.';
    } else if (featureLocked) {
      error.userMessage = isSafeBusinessMessage(responseDataObj.message)
        ? responseDataObj.message
        : 'This feature is not included in your subscription plan.';
      error.feature = responseDataObj.feature;
      error.severity = 'warning';
    } else if (status === 403) {
      error.userMessage = isSafeBusinessMessage(responseDataObj.message)
        ? responseDataObj.message
        : 'You do not have permission to perform this action.';
    } else if (status && status >= 500) {
      // Prefer the backend's own message (e.g. a controller-level "please
      // check backend logs" or a specific failure reason) over the generic
      // fallback — the generic text should only show when the backend gave
      // no usable message at all.
      error.userMessage = isSafeBusinessMessage(responseDataObj.message)
        ? responseDataObj.message
        : 'Server error. Please check backend logs.';
    } else if (isSafeBusinessMessage(responseDataObj.message)) {
      // Safe business message from backend — show it
      error.userMessage = responseDataObj.message;
    } else {
      // Unsafe or missing message — generic fallback
      error.userMessage = 'We could not complete this request. Please try again.';
    }

    logClientFailure(error);
    return Promise.reject(error);
  }
);

// Offline detection
window.addEventListener('offline', notifyNetworkFailure);

export default api;
