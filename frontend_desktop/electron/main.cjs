const { app, BrowserWindow, shell } = require('electron');
const path = require('path');
const http = require('http');

const isDev = process.env.NODE_ENV === 'development';

// Custom protocol for the Google OAuth round-trip: the backend's OAuth2
// success handler needs to be configured to redirect desktop-originated
// logins to `innovacar://oauth-callback?oauth2code=...` (a separate,
// backend-side change — file:// has no origin the backend can redirect to
// directly, unlike the web app's real https origin). Until that redirect_uri
// is configured server-side, Google sign-in opens correctly in the system
// browser but the user lands on the web app afterward, not back in this app.
const OAUTH_PROTOCOL = 'innovacar';
let pendingOAuthUrl = null;
let mainWindowRef = null;

function forwardOAuthCallback(url) {
  if (!url || !url.startsWith(`${OAUTH_PROTOCOL}://`)) return;
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
}

async function findDevServer() {
  const ports = [5173, 5174, 5175, 5176, 5177];
  for (const port of ports) {
    try {
      await new Promise((resolve, reject) => {
        const req = http.get(`http://localhost:${port}`, (res) => {
          if (res.statusCode === 200 || res.statusCode === 204) {
            resolve(port);
          } else {
            reject(new Error(`Status ${res.statusCode}`));
          }
        });
        req.on('error', reject);
        req.setTimeout(500, () => reject(new Error('Timeout')));
      });
      return port;
    } catch {
      // try next port
    }
  }
  return 5173; // fallback
}

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1200,
    minHeight: 700,
    title: 'Innovacar Desktop',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false,
    },
  });

  // External links (help center, support, OAuth, etc.) must open in the
  // user's real browser, not navigate the app window away from the SPA.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // Google's OAuth button does a top-level `window.location.assign(...)`
  // (see GoogleAuthButton.tsx), not window.open — that's a same-window
  // navigation, not a new-window request, so it doesn't hit
  // setWindowOpenHandler above. Catch it here instead: any attempt to
  // navigate this window away from its own local dist/dev-server origin
  // is redirected to the system browser and cancelled in-app, so the
  // packaged app's window can never end up stuck on a live https page.
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const target = new URL(url);
    const isAppOrigin = isDev
      ? target.hostname === 'localhost' || target.hostname === '127.0.0.1'
      : target.protocol === 'file:';
    if (!isAppOrigin) {
      event.preventDefault();
      shell.openExternal(url);
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
    findDevServer().then((port) => {
      mainWindow.loadURL(`http://localhost:${port}`);
      mainWindow.webContents.openDevTools();
    });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
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
