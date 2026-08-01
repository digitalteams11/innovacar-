// This app has no separate renderer source of its own — every page,
// component, context, hook and translation is imported straight from
// frontend-web/src so the desktop shell can never drift from the web app.
// Only electron/, this file, and index.html/vite config are desktop-only.
import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from '../../frontend-web/src/App';
import { AuthProvider } from '../../frontend-web/src/context/AuthContext';
import ErrorBoundary from '../../frontend-web/src/components/ErrorBoundary';
import { installLazyLoadRecovery } from '../../frontend-web/src/lazyLoadRecovery';
import '../../frontend-web/src/index.css';
import '../../frontend-web/src/i18n';

installLazyLoadRecovery();

// ErrorBoundary wraps *everything*, including AuthProvider — App.tsx already
// has its own internal boundary, but that one only covers what's inside
// App() itself. AuthProvider sits outside App() here (it needs to be, so
// useAuth() works inside App's own tree), so a synchronous crash during
// AuthProvider's render (a startup exception, not caught by anything else)
// would otherwise unmount the whole tree with no fallback UI at all — a
// blank white window with no way to see what went wrong.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <HashRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </HashRouter>
    </ErrorBoundary>
  </React.StrictMode>
);
