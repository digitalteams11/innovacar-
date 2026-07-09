// Desktop-only override: lets a user point this installed app at a different
// backend (e.g. a LAN server) from Settings, without rebuilding or env vars.
// Electron's `file://` origin has no usable hostname, so this — and the
// VITE_API_URL build-time env — take priority over the browser-host guess.
const storedApiUrl = typeof localStorage !== 'undefined' ? localStorage.getItem('api_base_url')?.trim() : null;
const configuredApiUrl = import.meta.env.VITE_API_URL?.trim();
const browserHost =
  typeof window !== 'undefined' && window.location.hostname
    ? window.location.hostname
    : 'localhost';

export const API_BASE_URL = (
  storedApiUrl || configuredApiUrl || `http://${browserHost}:8082/api`
).replace(/\/+$/, '');

export const API_ORIGIN = API_BASE_URL.replace(/\/api$/, '');

if (import.meta.env.DEV) {
  console.info('[API] base URL:', API_BASE_URL);
}
