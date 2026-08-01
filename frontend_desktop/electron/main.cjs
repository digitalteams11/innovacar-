const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path');
const http = require('http');

const isDev = process.env.NODE_ENV === 'development';

// Only these hosts may ever be opened in the system browser from this app —
// the Google OAuth authorization start URL (which itself 302s the *system*
// browser to accounts.google.com — Electron never navigates there directly)
// and the marketing/support surfaces linked from within the app. Blocks
// javascript:, file:, data:, and any renderer-controlled/unknown origin.
const ALLOWED_EXTERNAL_HOSTS = new Set([
  'api.innovacar.app',
  'innovacar.app',
  'www.innovacar.app',
]);
// Dev-only: frontend-web/src/lib/api.ts falls back to http://<host>:8082/api
// whenever VITE_API_URL isn't set, which is the normal case for
// `npm run electron:dev` — without allowing this too, the Google button
// would be silently blocked in every local dev run.
const DEV_ALLOWED_HOSTS = new Set(['localhost', '127.0.0.1']);
const DEV_ALLOWED_PORT = '8082';

function isAllowedExternalUrl(rawUrl) {
  try {
    const url = new URL(rawUrl);
    if (url.protocol === 'https:' && ALLOWED_EXTERNAL_HOSTS.has(url.hostname)) return true;
    if (isDev && url.protocol === 'http:' && DEV_ALLOWED_HOSTS.has(url.hostname) && url.port === DEV_ALLOWED_PORT) {
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

// Custom protocol for the Google OAuth round-trip: GoogleAuthButton.tsx
// appends ?desktop=true when running in Electron, DesktopOAuthOriginFilter
// (backend) marks the login as desktop-originated from that, and
// OAuth2LoginSuccessHandler/OAuth2LoginFailureHandler redirect the system
// browser to innovacar://auth/callback?code=... (or ?error=...) once Google's
// round trip completes. See DESKTOP_FEATURE_INVENTORY.md for the full flow.
const OAUTH_PROTOCOL = 'innovacar';
const OAUTH_CALLBACK_PREFIX = `${OAUTH_PROTOCOL}://auth/callback`;
let pendingOAuthUrl = null;
let mainWindowRef = null;

/** Only a well-formed innovacar://auth/callback?code=...|error=... URL is
 * ever forwarded to the renderer — anything else (a malformed/foreign deep
 * link, or another app registering the same scheme) is silently dropped. */
function isValidOAuthCallbackUrl(url) {
  if (typeof url !== 'string' || !url.startsWith(OAUTH_CALLBACK_PREFIX)) return false;
  try {
    const parsed = new URL(url);
    return Boolean(parsed.searchParams.get('code') || parsed.searchParams.get('error'));
  } catch {
    return false;
  }
}

function forwardOAuthCallback(url) {
  if (!isValidOAuthCallbackUrl(url)) {
    if (url) console.warn('[electron:oauth] rejected invalid callback URL', url);
    return;
  }
  if (mainWindowRef && !mainWindowRef.isDestroyed()) {
    mainWindowRef.webContents.send('oauth-callback', url);
  } else {
    pendingOAuthUrl = url;
  }
}

if (process.defaultApp) {
  if (process.argv.length >= 2) {
    app.setAsDefaultProtocolClient(OAUTH_PROTOCOL, process.execPath, [path.resolve(process.argv[1])]);
  }
} else {
  app.setAsDefaultProtocolClient(OAUTH_PROTOCOL);
}

// macOS delivers the deep link via this event.
app.on('open-url', (event, url) => {
  event.preventDefault();
  forwardOAuthCallback(url);
});

// Windows/Linux deliver it as an argv entry on the second-instance launch.
const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', (_event, argv) => {
    const deepLink = argv.find((arg) => arg.startsWith(`${OAUTH_PROTOCOL}://`));
    if (deepLink) forwardOAuthCallback(deepLink);
    if (mainWindowRef) {
      if (mainWindowRef.isMinimized()) mainWindowRef.restore();
      mainWindowRef.focus();
    }
  });
  // The app can also be launched cold *by* the deep link (Windows registers
  // the .exe as the protocol handler) — argv[1] carries it on this, the
  // first, instance rather than a second-instance event.
  const initialDeepLink = process.argv.find((arg) => arg.startsWith(`${OAUTH_PROTOCOL}://`));
  if (initialDeepLink) pendingOAuthUrl = initialDeepLink;
}

ipcMain.handle('open-external-url', (_event, url) => {
  if (!isAllowedExternalUrl(url)) {
    console.warn('[electron:open-external-url] blocked disallowed URL', url);
    return false;
  }
  shell.openExternal(url);
  return true;
});

async function probeDevServer(port) {
  return new Promise((resolve, reject) => {
    const req = http.get(`http://localhost:${port}`, (res) => {
      res.resume();
      if (res.statusCode && res.statusCode < 500) resolve(port);
      else reject(new Error(`Status ${res.statusCode}`));
    });
    req.on('error', reject);
    req.setTimeout(500, () => req.destroy(new Error('Timeout')));
  });
}

/** vite.config.ts pins strictPort:true on 5173 (see that file's comment) —
 * this is a bounded retry for the brief window before Vite finishes binding,
 * not a fallback across multiple ports, so Electron can never latch onto an
 * unrelated process answering on some other port. */
async function findDevServer() {
  const explicit = process.env.VITE_DEV_SERVER_URL;
  if (explicit) return explicit;
  const port = 5173;
  const attempts = 20;
  for (let i = 0; i < attempts; i++) {
    try {
      await probeDevServer(port);
      return `http://localhost:${port}`;
    } catch {
      await new Promise((r) => setTimeout(r, 300));
    }
  }
  throw new Error(`Vite dev server never answered on port ${port} after ${attempts} attempts`);
}

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1200,
    minHeight: 700,
    title: 'Innovacar Desktop',
    autoHideMenuBar: true,
    show: false,
    backgroundColor: '#0b1220',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });
  // ready-to-show fires once the renderer has painted its first frame — if
  // it never fires (a synchronous crash before first paint, or the load
  // itself failing), the window must still appear rather than staying
  // invisible forever with no way to see the error.
  const readyToShowTimeout = setTimeout(() => {
    if (!mainWindow.isDestroyed() && !mainWindow.isVisible()) {
      console.error('[electron:ready-to-show] timed out after 8s — showing window anyway so any error is visible');
      mainWindow.show();
    }
  }, 8000);
  mainWindow.once('ready-to-show', () => clearTimeout(readyToShowTimeout));

  // ── Diagnostics ──────────────────────────────────────────────────────────
  mainWindow.webContents.on('did-start-loading', () => {
    console.log('[electron:did-start-loading]');
  });
  mainWindow.webContents.on('did-finish-load', () => {
    console.log('[electron:did-finish-load]');
  });
  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
    console.error('[electron:did-fail-load]', { errorCode, errorDescription, validatedURL });
  });
  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    console.error('[electron:render-process-gone]', details);
  });
  mainWindow.webContents.on('unresponsive', () => {
    console.error('[electron:unresponsive] renderer stopped responding');
  });
  mainWindow.webContents.on('console-message', (_event, level, message, line, sourceId) => {
    // Forwards every renderer console.* call (including uncaught exceptions
    // React/Vite log via the default window.onerror handler) into this
    // process's own stdout, so `npm run electron:dev`'s terminal shows the
    // real failure without needing DevTools open.
    const levels = ['log', 'warning', 'error', 'debug'];
    console.log(`[renderer:${levels[level] || level}] ${message} (${sourceId}:${line})`);
  });

  // External links (help center, support, OAuth, etc.) must open in the
  // user's real browser, not navigate the app window away from the SPA.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAllowedExternalUrl(url)) shell.openExternal(url);
    else console.warn('[electron:setWindowOpenHandler] blocked disallowed URL', url);
    return { action: 'deny' };
  });

  // Google's OAuth button does a top-level `window.location.assign(...)`
  // fallback on platforms where the preload's openExternalAuthUrl bridge
  // is unavailable — that's a same-window navigation, not a new-window
  // request, so it doesn't hit setWindowOpenHandler above. Catch it here
  // instead: any attempt to navigate this window away from its own local
  // dist/dev-server origin is redirected to the system browser (if
  // allow-listed) and cancelled in-app, so the packaged app's window can
  // never end up stuck on a live https page.
  mainWindow.webContents.on('will-navigate', (event, url) => {
    let target;
    try {
      target = new URL(url);
    } catch {
      event.preventDefault();
      return;
    }
    const isAppOrigin = isDev
      ? target.hostname === 'localhost' || target.hostname === '127.0.0.1'
      : target.protocol === 'file:';
    if (!isAppOrigin) {
      event.preventDefault();
      if (isAllowedExternalUrl(url)) shell.openExternal(url);
      else console.warn('[electron:will-navigate] blocked disallowed navigation', url);
    }
  });

  mainWindowRef = mainWindow;
  mainWindow.webContents.once('did-finish-load', () => {
    if (pendingOAuthUrl) {
      forwardOAuthCallback(pendingOAuthUrl);
      pendingOAuthUrl = null;
    }
  });

  if (isDev) {
    if (process.env.OPEN_DEVTOOLS !== 'false') {
      mainWindow.webContents.openDevTools({ mode: 'detach' });
    }
    findDevServer()
      .then((url) => {
        if (mainWindow.isDestroyed()) return;
        return mainWindow.loadURL(`${url}/#/login`).catch((err) => {
          throw Object.assign(err, { stage: 'loadURL', url: `${url}/#/login` });
        });
      })
      .catch((err) => {
        if (mainWindow.isDestroyed()) return;
        const stage = err.stage === 'loadURL' ? 'loadURL' : 'findDevServer';
        console.error(`[electron:${stage}] failed to load the renderer`, err);
        mainWindow.loadURL(
          `data:text/html,${encodeURIComponent(
            `<body style="font-family:sans-serif;background:#0b1220;color:#fff;padding:2rem">` +
              `<h2>Innovacar Desktop — renderer failed to load (${stage})</h2>` +
              `<p>${String(err.message || err)}</p>` +
              `<p>Is <code>npm run dev</code> (Vite) running and bound to port 5173?</p></body>`,
          )}`,
        );
        mainWindow.show();
      });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'), { hash: '/login' });
  }
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', function () {
  if (process.platform !== 'darwin') app.quit();
});
