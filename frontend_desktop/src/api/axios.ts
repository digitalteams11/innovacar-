import axios from 'axios';

// Allow overriding the API base URL via localStorage (useful for desktop app users)
const getBaseURL = () => {
  const stored = localStorage.getItem('api_base_url');
  if (stored) return stored;
  // Default backend URL
  return 'http://localhost:8082/api';
};

const api = axios.create({
  baseURL: getBaseURL(),
  headers: {
    'Content-Type': 'application/json',
  },
});

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

// Add a response interceptor to handle authentication failures.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const featureLocked = error.response?.status === 403
      && error.response?.data?.error === 'FEATURE_NOT_AVAILABLE';
    if (error.response?.status === 401) {
      // Clear token but don't redirect on public contract signing pages
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (!window.location.hash.startsWith('#/contract-sign')) {
        window.dispatchEvent(new Event('auth-error'));
      }
    }
    if (featureLocked) {
      error.userMessage = error.response?.data?.message;
      error.feature = error.response?.data?.feature;
    }
    return Promise.reject(error);
  }
);

export default api;
