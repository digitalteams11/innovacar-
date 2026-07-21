import routes from './public-routes.json';

/**
 * Single source of truth for URLs that belong in sitemap.xml. Backed by
 * public-routes.json so scripts/generate-sitemap.mjs (plain Node, no TS
 * loader available) and this TS module both read the exact same data —
 * routes and the sitemap can't drift out of sync.
 *
 * The rest of the app (dashboard, login, contract-sign, etc.) is still
 * entirely HashRouter-based (see src/main.tsx) and stays off this list —
 * a sitemap must never contain a "#/" fragment. These three routes are the
 * exception: they're the public marketing site (src/marketing/pages.tsx),
 * served at real, hash-free paths and prerendered to static HTML at build
 * time by scripts/prerender-marketing.mjs. See docs/seo-route-inventory.md.
 */
export interface PublicRouteEntry {
  /** Path relative to the canonical origin, e.g. "/contact". Must NOT contain "#". */
  path: string;
  /** ISO date (YYYY-MM-DD) of the last real content change. Omit if unknown — never invent one. */
  lastmod?: string;
}

export const PUBLIC_SITEMAP_ROUTES: PublicRouteEntry[] = routes;
