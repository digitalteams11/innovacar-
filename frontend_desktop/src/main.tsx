// This app has no separate renderer source of its own — every page,
// component, context, hook and translation is imported straight from
// frontend-web/src so the desktop shell can never drift from the web app.
// Only electron/, this file, and index.html/vite config are desktop-only.
import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from '../../frontend-web/src/App';
import { AuthProvider } from '../../frontend-web/src/context/AuthContext';
import { installLazyLoadRecovery } from '../../frontend-web/src/lazyLoadRecovery';
import '../../frontend-web/src/index.css';
import '../../frontend-web/src/i18n';

installLazyLoadRecovery();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </HashRouter>
  </React.StrictMode>
);
