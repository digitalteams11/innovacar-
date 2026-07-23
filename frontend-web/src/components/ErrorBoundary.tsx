import React from 'react';
import i18n from '../i18n';
import { CHUNK_RELOAD_MARKER } from '../lazyLoadRecovery';

// Matches the errors browsers throw for a stale/missing chunk after a new
// deployment replaced it: "Failed to fetch dynamically imported module",
// "Failed to load module script... MIME type text/html", "error loading
// dynamically imported module", "Importing a module script failed".
const CHUNK_LOAD_ERROR_PATTERN = /(fetch|load(ing)?)\s+dynamically imported module|failed to load module script|importing a module script failed/i;

// Matches "Cannot read properties of undefined (reading 'background')" and
// the same shape for any other theme-token key — the signature of a stale/
// legacy/malformed `rentcar_appearance.mode` value reaching a preset lookup
// as an invalid key. ThemeContext.tsx's normalizeThemePreference()/
// resolveTheme() close the actual gap; this is the last-resort recovery
// for anyone who still hits it (e.g. a deploy that shipped between the bug
// and the fix, or any future token this class of bug could recur on).
const THEME_TOKEN_READ_ERROR_PATTERN = /cannot read propert(?:y|ies) of undefined \(reading '(background|surface|sidebar|foreground)'\)/i;
const THEME_STORAGE_KEY = 'rentcar_appearance';

/** Best-effort: drops the persisted appearance blob only if it's missing or
 * doesn't parse to an object with a recognizable mode — never touches
 * auth/user/language/tenant/reservation data, all of which live under
 * different keys. Never throws. */
function clearInvalidThemeCache() {
  try {
    const raw = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    const mode = String(parsed?.mode ?? '').trim().toLowerCase();
    if (typeof parsed !== 'object' || parsed === null || !['light', 'dark', 'system', 'auto'].includes(mode)) {
      window.localStorage.removeItem(THEME_STORAGE_KEY);
    }
  } catch {
    // Malformed JSON is exactly the case we're guarding against — drop it.
    try { window.localStorage.removeItem(THEME_STORAGE_KEY); } catch { /* non-fatal */ }
  }
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export default class ErrorBoundary extends React.Component<React.PropsWithChildren, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: unknown) {
    console.error('Route render failed', error);

    // "X must be used within a <Y>Provider" from a component that is
    // genuinely nested under that provider in the JSX tree is the
    // signature of a stale-module state — most commonly a broken Vite HMR
    // WebSocket (e.g. dev server reached via a LAN IP with no reachable
    // hmr.host) leaving the page running two different instances of the
    // same context module. No amount of re-rendering in place fixes that;
    // only reloading the page re-fetches a consistent module graph. Guard
    // with a one-shot sessionStorage flag so a *genuine* recurring bug
    // still falls through to the normal retry UI instead of reload-looping.
    if (error instanceof Error && /must be used within/i.test(error.message)) {
      const key = 'rentcar_error_boundary_reload_once';
      if (!sessionStorage.getItem(key)) {
        sessionStorage.setItem(key, '1');
        window.location.reload();
      }
      return;
    }

    // Belt-and-suspenders for stale-chunk failures that surface as a thrown
    // error during render (e.g. React.lazy()'s import() rejection) rather
    // than as the `vite:preloadError` window event lazyLoadRecovery.ts
    // already handles for most cases. Same one-shot-per-session guard.
    if (error instanceof Error && CHUNK_LOAD_ERROR_PATTERN.test(error.message)) {
      if (!sessionStorage.getItem(CHUNK_RELOAD_MARKER)) {
        sessionStorage.setItem(CHUNK_RELOAD_MARKER, '1');
        window.location.reload();
      }
      return;
    }

    // A theme-token property read off `undefined` — a stale/legacy/invalid
    // persisted theme preference reached a preset lookup as an invalid key
    // (see ThemeContext.tsx's normalizeThemePreference()/resolveTheme()).
    // Reset ONLY the invalid theme cache, never auth/user/language/tenant/
    // reservation data, then reload once so the next boot picks up the
    // now-clean (defaults to light) preference. Same one-shot guard as the
    // other recovery branches above, so a genuinely different recurring bug
    // still falls through to the normal Retry UI instead of reload-looping.
    if (error instanceof Error && THEME_TOKEN_READ_ERROR_PATTERN.test(error.message)) {
      console.error('[ErrorBoundary] Theme token read on undefined — resetting persisted theme preference to light.');
      const key = 'rentcar_theme_error_reload_once';
      if (!sessionStorage.getItem(key)) {
        sessionStorage.setItem(key, '1');
        clearInvalidThemeCache();
        window.location.reload();
      }
    }
  }

  private retry = () => {
    // Restore a valid theme state before re-attempting the route — if the
    // crash that got us here was theme-related, retrying without this would
    // just crash again on the same invalid persisted value.
    clearInvalidThemeCache();
    this.setState({ hasError: false });
  };

  private goDashboard = () => {
    window.location.hash = '#/';
    this.setState({ hasError: false });
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="min-h-screen bg-[#f7f7f4] px-4 py-10 text-[#242722] dark:bg-[#101418] dark:text-white">
        <div className="mx-auto max-w-lg rounded-2xl border border-rose-100 bg-white p-6 text-center shadow-soft dark:border-white/10 dark:bg-[#1a2332]">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600">
            !
          </div>
          <h1 className="text-xl font-bold">{i18n.t('common.unableToLoadPage', 'Unable to load this page.')}</h1>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-300">
            {i18n.t('common.unableToLoadPageDesc', 'Something went wrong while loading this page. You can retry or return to the dashboard.')}
          </p>
          <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <button
              type="button"
              onClick={this.retry}
              className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700"
            >
              {i18n.t('common.retry', 'Retry')}
            </button>
            <button
              type="button"
              onClick={this.goDashboard}
              className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50 dark:border-white/10 dark:text-white dark:hover:bg-white/5"
            >
              {i18n.t('common.goToDashboard', 'Go to Dashboard')}
            </button>
          </div>
        </div>
      </div>
    );
  }
}
