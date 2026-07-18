/**
 * Recovers from a stale-chunk failure: a browser tab left open (or a cached
 * index.html) across a new deployment still references JS chunk hashes that
 * the new deployment no longer serves. Vite's client emits `vite:preloadError`
 * on `window` specifically for this — a failed dynamic `import()` for a
 * lazy-loaded route. The fix is a single reload, which fetches the current
 * index.html (and therefore the current chunk hashes); a sessionStorage
 * marker caps it at one attempt per tab session so a genuinely broken
 * deployment can't reload-loop the page forever.
 */
const RELOAD_MARKER = 'rentcar_chunk_reload_once';

export const CHUNK_RELOAD_MARKER = RELOAD_MARKER;

export function installLazyLoadRecovery() {
  window.addEventListener('vite:preloadError', (event) => {
    // Stop the unhandled-rejection noise in the console — we're handling it.
    event.preventDefault();

    if (sessionStorage.getItem(RELOAD_MARKER)) {
      // Already reloaded once this session and the failure recurred — a real,
      // persistent problem (not a one-off stale cache). Let it surface via
      // ErrorBoundary/Suspense instead of reloading forever.
      return;
    }
    sessionStorage.setItem(RELOAD_MARKER, '1');
    window.location.reload();
  });
}
