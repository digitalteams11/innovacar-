const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  // Fires once the main process catches an `innovacar://oauth-callback?...`
  // deep link (see electron/main.cjs) — lets Login.tsx react to a completed
  // Google sign-in the same way it already reacts to the web app's
  // `?oauth2code=...` query param on refresh.
  onOAuthCallback: (callback) => {
    const subscription = (_event, url) => callback(url);
    ipcRenderer.on('oauth-callback', subscription);
    return () => ipcRenderer.removeListener('oauth-callback', subscription);
  },
});
