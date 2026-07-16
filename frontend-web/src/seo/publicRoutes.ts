import routes from './public-routes.json';

/**
 * Single source of truth for URLs that belong in sitemap.xml. Backed by
 * public-routes.json so scripts/generate-sitemap.mjs (plain Node, no TS
 * loader available) and this TS module both read the exact same data —
 * routes and the sitemap can't drift out of sync.
 *
 * IMPORTANT — as of this writing, this list is intentionally EMPTY. The app
 * is entirely HashRouter-based (see src/main.tsx), so every real URL path
 * (the part before "#") returns the identical index.html shell — there is
 * no server-renderable, crawlable, hash-free public page to list yet. The
 * one genuinely public page today, /contact (src/pages/PublicContact.tsx),
 * only exists at https://innovacar.app/#/contact, and a sitemap must never
 * contain a "#/" fragment (Google can't treat it as a distinct document).
 *
 * To add a page here, it must first be served at a clean, hash-free path
 * that returns real content with a 200 status — i.e. migrated off
 * HashRouter (see docs/seo-route-inventory.md, "Part 3" follow-up).
 */
export interface PublicRouteEntry {
  /** Path relative to the canonical origin, e.g. "/contact". Must NOT contain "#". */
  path: string;
  /** ISO date (YYYY-MM-DD) of the last real content change. Omit if unknown — never invent one. */
  lastmod?: string;
}

export const PUBLIC_SITEMAP_ROUTES: PublicRouteEntry[] = routes;
