/**
 * Anti-flash route bootstrap — runs synchronously before first paint, same
 * as theme-bootstrap.js, and for the same CSP reason (script-src 'self',
 * no 'unsafe-inline' — see vercel.json). This is a separate concern from
 * theme resolution: it marks the document as an "app route" (any URL
 * carrying a "#/..." fragment) so a stale prerendered marketing-page body
 * baked into dist/index.html at build time — see scripts/prerender-marketing.mjs
 * — is never actually painted for it.
 *
 * Why this exists: this is a HashRouter SPA. Every authenticated route
 * (#/dashboard, #/contracts, ...) and the marketing homepage (/) are all the
 * *same* server request (a URL fragment is never sent to the server), so
 * they are served the exact same dist/index.html. The marketing prerender
 * step bakes real landing-page markup into that file's #root for SEO/crawlers
 * — without this guard, refreshing on any authenticated hash route would
 * briefly paint that landing-page HTML before main.tsx's module script loads
 * and React replaces it. Setting this attribute before paint, combined with
 * the CSS in index.html's <head> that reacts to it, keeps that markup hidden
 * for hash routes and shows the neutral static boot shell instead.
 *
 * Must never throw — a corrupt/unavailable `location` object must still let
 * the page load normally (falls through to showing the marketing/root markup,
 * which is only ever an issue for the narrow hash-route case this guards).
 */
(function () {
  'use strict';
  try {
    if (window.location.hash && window.location.hash.length > 1) {
      document.documentElement.setAttribute('data-app-route', 'true');
    }
  } catch (e) {
    /* never block page load */
  }
})();
