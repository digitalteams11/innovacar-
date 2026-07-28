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
    var PREFERENCE_KEY = 'innovacar-theme';
    // Superseded key names — see migrateLegacyThemeKeys() in ThemeContext.tsx,
    // which performs the actual one-time migration/cleanup once React mounts.
    // This script runs before that, so a device that hasn't been migrated
    // yet still needs to check these directly for a flash-free first paint.
    var LEGACY_KEYS = ['innovacar.theme.preference', 'theme', 'darkMode', 'selectedTheme', 'colorMode', 'app-theme'];
    var LEGACY_STORAGE_KEY = 'rentcar_appearance';
    var mode = 'light';

    // Normalizes the same way ThemeContext.tsx's normalizeThemePreference()
    // does: accepts light/dark/system plus the legacy "auto" value and any
    // casing, everything else (including a missing/corrupt mode) is light.
    function normalize(raw) {
      var normalized = String(raw || '').trim().toLowerCase();
      if (normalized === 'light' || normalized === 'dark') return normalized;
      if (normalized === 'system' || normalized === 'auto') return 'system';
      return null;
    }

    try {
      // The canonical preference key is authoritative when present — it's
      // written on every mode change (see ThemeContext.tsx) and is the only
      // thing this script needs to read for the common case.
      var fromKey = normalize(window.localStorage.getItem(PREFERENCE_KEY));
      if (fromKey) {
        mode = fromKey;
      } else {
        // Not migrated yet on this device — check legacy keys directly
        // (newest-first) so the very first paint still gets it right.
        for (var i = 0; i < LEGACY_KEYS.length && !fromKey; i++) {
          fromKey = normalize(window.localStorage.getItem(LEGACY_KEYS[i]));
        }
        if (fromKey) {
          mode = fromKey;
        } else {
          var raw = window.localStorage.getItem(LEGACY_STORAGE_KEY);
          if (raw) {
            var parsed = JSON.parse(raw);
            var legacy = normalize(parsed && parsed.mode);
            if (legacy) mode = legacy;
          }
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
    root.setAttribute('data-theme', resolved);

    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', resolved === 'dark' ? '#0F172A' : '#F8FAFC');
  } catch (e) {
    /* never block page load on theme resolution */
  }
})();
