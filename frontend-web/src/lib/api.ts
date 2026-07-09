const configuredApiUrl = import.meta.env.VITE_API_URL?.trim();
const browserHost =
  typeof window !== 'undefined' && window.location.hostname
    ? window.location.hostname
    : 'localhost';

export const API_BASE_URL = (
  configuredApiUrl || `http://${browserHost}:8082/api`
).replace(/\/+$/, '');

export const API_ORIGIN = API_BASE_URL.replace(/\/api$/, '');

if (import.meta.env.DEV) {
  console.info('[API] base URL:', API_BASE_URL);
}

/**
 * Pings the backend's health endpoint directly (not through the shared
 * `api` instance) so this check never gets entangled with the 401/refresh
 * interceptor chain — it only cares whether the server is reachable at all.
 * Returns false on any network failure (connection refused, timeout, etc.)
 * and true for any HTTP response at all, including non-2xx, since that
 * still proves the backend process is up and answering requests.
 */
export async function checkHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/health`, { method: 'GET', signal: AbortSignal.timeout(3000) });
    return response.ok;
  } catch {
    return false;
  }
}
