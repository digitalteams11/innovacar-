const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  // Opens a URL in the system's default browser instead of navigating this
  // window there — used for the Google OAuth start URL (main.cjs validates
  // it against a fixed host allowlist before calling shell.openExternal;
  // this bridge never exposes shell.openExternal itself, only this one
  // narrow, validated action). See GoogleAuthButton.tsx.
  openExternalUrl: (url) => ipcRenderer.invoke('open-external-url', url),
  // Fires once the main process catches an `innovacar://auth/callback?...`
  // deep link (see electron/main.cjs) — lets Login.tsx react to a completed
  // Google sign-in the same way it already reacts to the web app's
  // `?oauth2code=...` query param on refresh.
  onOAuthCallback: (callback) => {
    const subscription = (_event, url) => callback(url);
    ipcRenderer.on('oauth-callback', subscription);
    return () => ipcRenderer.removeListener('oauth-callback', subscription);
  },
});
