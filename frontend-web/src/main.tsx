import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { installLazyLoadRecovery } from './lazyLoadRecovery';
import { MARKETING_PATHS } from './marketing/pages';
import './index.css';
import './i18n';

installLazyLoadRecovery();

/**
 * Any URL carrying a "#/..." fragment always goes to the private HashRouter
 * app, unconditionally — this preserves every existing deep link, email
 * link, password-reset link, and bookmark exactly as before. Only a
 * hash-free navigation to a registered marketing path (see
 * src/marketing/pages.tsx), from a visitor with no auth token, renders the
 * public marketing site instead. A logged-in user who bookmarked "/" still
 * lands in the app, unchanged.
 */
function shouldRenderMarketingSite(): boolean {
  if (window.location.hash) return false;
  if (!MARKETING_PATHS.includes(window.location.pathname)) return false;
  const hasToken = Boolean(localStorage.getItem('token') || localStorage.getItem('accessToken'));
  return !hasToken;
}

const root = ReactDOM.createRoot(document.getElementById('root')!);

if (shouldRenderMarketingSite()) {
  const { default: MarketingApp } = await import('./marketing/MarketingApp');
  root.render(
    <React.StrictMode>
      <MarketingApp />
    </React.StrictMode>
  );
} else {
  root.render(
    <React.StrictMode>
      <HashRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </HashRouter>
    </React.StrictMode>
  );
}
