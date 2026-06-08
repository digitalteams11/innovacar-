import axios from 'axios';

const api = axios.create({
  baseURL: `http://${window.location.hostname}:8082/api`,
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(callback: (token: string) => void) {
  refreshSubscribers.push(callback);
}

// Add a request interceptor to attach the JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Add a response interceptor to handle 401/403 and token expiry
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (!originalRequest) {
      return Promise.reject(error);
    }

    // Check for Token-Expired header (set by backend when JWT is expired)
    const tokenExpired = error.response?.headers?.['token-expired'] === 'true';
    const status = error.response?.status;

    // If the token is expired and we haven't retried yet
    if ((tokenExpired || status === 401) && !originalRequest._retry) {
      originalRequest._retry = true;

      if (isRefreshing) {
        // Wait for the refresh to complete
        return new Promise((resolve) => {
          addRefreshSubscriber((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            resolve(api(originalRequest));
          });
        });
      }

      isRefreshing = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const { data } = await axios.post(
          `${api.defaults.baseURL}/auth/refresh`,
          { refreshToken }
        );

        const newAccessToken = data.accessToken;
        localStorage.setItem('token', newAccessToken);
        if (data.refreshToken) {
          localStorage.setItem('refreshToken', data.refreshToken);
        }
        if (data.expiresIn) {
          const expiry = Date.now() + data.expiresIn * 1000;
          localStorage.setItem('tokenExpiry', expiry.toString());
        }

        api.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
        onTokenRefreshed(newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh failed — clear auth and signal logout (but not on public pages)
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        localStorage.removeItem('tokenExpiry');
        if (!window.location.hash.startsWith('#/contract-sign')) {
          window.dispatchEvent(new Event('auth-error'));
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    const featureLocked = status === 403 && error.response?.data?.error === 'FEATURE_NOT_AVAILABLE';

    // Only authentication failures should clear the session.
    if (status === 401) {
      // Only clear if it's not a refresh request itself and not on public pages
      if (!originalRequest.url?.includes('/auth/refresh')) {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        localStorage.removeItem('tokenExpiry');
        localStorage.removeItem('user_profile');
        if (!window.location.hash.startsWith('#/contract-sign')) {
          window.dispatchEvent(new Event('auth-error'));
        }
      }
    }

    // Normalize network errors for consistent handling
    if (!error.response) {
      error.userMessage = 'Server is offline. Please start the backend or check your connection.';
    } else if (error.response.data?.message) {
      error.userMessage = error.response.data.message;
    } else if (error.response.status === 401) {
      error.userMessage = 'Session expired. Please log in again.';
    } else if (featureLocked) {
      error.userMessage = error.response.data?.message || 'This feature is not included in your subscription plan.';
      error.feature = error.response.data?.feature;
    } else if (error.response.status === 403) {
      error.userMessage = 'You do not have permission to perform this action.';
    } else if (error.response.status >= 500) {
      error.userMessage = 'Server error. Please try again later.';
    } else {
      error.userMessage = `Request failed (${error.response.status})`;
    }

    return Promise.reject(error);
  }
);

export default api;
