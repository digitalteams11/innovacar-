import { API_BASE_URL, API_ORIGIN } from '../lib/api';

/**
 * Resolves a relative uploads path or partial URL to a full backend URL.
 *
 * Priority:
 *  1. null / empty               → null
 *  2. Already absolute (http/https) → return as-is
 *  3. Starts with /uploads or uploads → prefix backend origin
 */
export function resolveMediaUrl(pathOrUrl: string | null | undefined): string | null {
  if (!pathOrUrl || !pathOrUrl.trim()) return null;
  if (pathOrUrl.startsWith('http://') || pathOrUrl.startsWith('https://')) return pathOrUrl;
  const normalized = pathOrUrl.startsWith('/') ? pathOrUrl : `/${pathOrUrl}`;
  return `${API_ORIGIN}${normalized}`;
}

export { API_ORIGIN as BACKEND_ORIGIN, API_BASE_URL };
