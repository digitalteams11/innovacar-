// Single source of truth for the production public-facing origin used in
// canonical URLs, Open Graph tags, structured data, and public share links.
//
// Deliberately never trusts a configured value blindly: a leftover Vercel
// preview URL (*.vercel.app) pasted into the env var before the real domain
// existed would otherwise get inlined verbatim into the production bundle
// (see scripts/verify-seo-dist.mjs, which fails the build if that ever
// happens again). An unsafe or malformed value falls back to the real
// production domain instead of being used as-is.
const UNSAFE_PUBLIC_HOST_PATTERN = /(^|\.)vercel\.app$/i;

function sanitizePublicAppUrl(raw: string | undefined): string | undefined {
  const trimmed = raw?.trim().replace(/\/+$/, '');
  if (!trimmed) return undefined;
  try {
    const { hostname, protocol } = new URL(trimmed);
    if (protocol !== 'https:') return undefined;
    if (UNSAFE_PUBLIC_HOST_PATTERN.test(hostname)) return undefined;
  } catch {
    return undefined;
  }
  return trimmed;
}

/** e.g. "https://innovacar.app" — no trailing slash. */
export const PUBLIC_APP_URL =
  sanitizePublicAppUrl(import.meta.env.VITE_PUBLIC_APP_URL as string | undefined)
  || 'https://innovacar.app';
