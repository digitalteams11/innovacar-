// Rejects a configured API URL that points at a non-production host — most
// commonly a leftover Vercel preview deployment URL (*.vercel.app) pasted
// into the dashboard env var before the real api.innovacar.app backend
// existed. That string would otherwise get inlined verbatim into the
// production bundle by Vite's static import.meta.env substitution.
const UNSAFE_API_HOST_PATTERN = /(^|\.)vercel\.app$/i;
function sanitizeConfiguredApiUrl(raw: string | undefined): string | undefined {
  const trimmed = raw?.trim();
  if (!trimmed) return undefined;
  try {
    const { hostname } = new URL(trimmed);
    if (UNSAFE_API_HOST_PATTERN.test(hostname)) return undefined;
  } catch {
    return undefined;
  }
  return trimmed;
}

const configuredApiUrl = sanitizeConfiguredApiUrl(import.meta.env.VITE_API_URL);
const browserHost =
  typeof window !== 'undefined' && window.location.hostname
    ? window.location.hostname
    : 'localhost';

// Production builds must always have VITE_API_URL baked in at build time
// (set in Vercel's project env vars). This is still logged loudly when it's
// missing so the misconfiguration is impossible to miss, but production no
// longer falls back to a LAN-dev URL in that case (see PROD_DEFAULT_API_URL
// below) — a missing env var must never send production traffic to
// http://innovacar.app:8082, which is both the wrong host and blocked as
// mixed content, and is exactly what previously surfaced as "Backend server
// is not running on port 8082" in the Email Center.
if (import.meta.env.PROD && !configuredApiUrl) {
  // Deliberately generic wording here (never spell out the literal rejected
  // domain suffix in a shipped string) — see sanitizeConfiguredApiUrl above
  // for what's actually rejected and why.
  console.error(
    '[API] VITE_API_URL is not set, or points at a non-production preview ' +
    'host, in this production build. ' +
    'Set it in Vercel → Project Settings → Environment Variables ' +
    '(e.g. VITE_API_URL=https://api.innovacar.app) and redeploy.'
  );
}

// Guards against the single most common production misconfiguration: Vercel's
// dashboard env var set to the bare API origin (e.g. https://api.innovacar.app)
// instead of the origin + /api path the backend actually serves under. Without
// this, every request silently drops the /api prefix, misses every permitAll
// matcher in SecurityConfig, and falls through to anyRequest().authenticated() —
// surfacing as a 401 "session expired" even on public routes like /auth/register.
function ensureApiSuffix(url: string): string {
  const trimmed = url.replace(/\/+$/, '');
  return /\/api$/.test(trimmed) ? trimmed : `${trimmed}/api`;
}

// The one hardcoded production fallback — only used if VITE_API_URL is
// somehow missing from the Vercel build. Never a LAN/localhost address:
// production must never construct a :8082 URL under any circumstance.
const PROD_DEFAULT_API_URL = 'https://api.innovacar.app';

export const API_BASE_URL = configuredApiUrl
  ? ensureApiSuffix(configuredApiUrl)
  : import.meta.env.PROD
    ? ensureApiSuffix(PROD_DEFAULT_API_URL)
    : `http://${browserHost}:8082/api`;

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
