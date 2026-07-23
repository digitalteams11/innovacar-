/**
 * Anti-flash theme bootstrap — applies the resolved theme to <html> before
 * any CSS/React paints, so the page never flashes light-then-dark or
 * dark-then-light. Served as a same-origin static file (not an inline
 * <script>) specifically so it satisfies a strict CSP `script-src 'self'`
 * with no 'unsafe-inline' — an inline version of this exact script is what
 * triggered the "Executing inline script violates Content Security Policy"
 * warning in production.
 *
 * Contains no API request and no sensitive data: it only ever reads the
 * locally-stored theme preference and matchMedia. Must never throw —
 * localStorage/matchMedia can be unavailable (private browsing, storage
 * disabled) or the stored value can be corrupt/stale/legacy, and any
 * failure here must fall back to the app's default (light), never break
 * page load.
 */
(function () {
  'use strict';
  try {
    var STORAGE_KEY = 'rentcar_appearance';
    var mode = 'light';

    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        // Normalizes the same way ThemeContext.tsx's normalizeThemePreference()
        // does: accepts light/dark/system plus the legacy "auto" value and any
        // casing, everything else (including a missing/corrupt mode) is light.
        var normalized = String((parsed && parsed.mode) || '').trim().toLowerCase();
        if (normalized === 'light' || normalized === 'dark') {
          mode = normalized;
        } else if (normalized === 'system' || normalized === 'auto') {
          mode = 'system';
        }
      }
    } catch (e) {
      /* storage unavailable or corrupt JSON — keep default light */
    }

    var resolved = mode;
    if (mode === 'system') {
      try {
        resolved = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      } catch (e) {
        resolved = 'light';
      }
    }

    var root = document.documentElement;
    if (resolved === 'dark') root.classList.add('dark');
    else root.classList.remove('dark');
    root.style.colorScheme = resolved;

    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', resolved === 'dark' ? '#0F172A' : '#F8FAFC');
  } catch (e) {
    /* never block page load on theme resolution */
  }
})();
