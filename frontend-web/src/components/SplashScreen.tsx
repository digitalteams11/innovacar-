import { useTranslation } from 'react-i18next';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';

// Theme-aware: uses the same CSS custom properties (--bg-page, --text-primary,
// --text-muted) the resolved app theme already sets before this ever paints —
// see public/theme-bootstrap.js (pre-React, before first paint) and
// ThemeContext's synchronous initial state + useLayoutEffect (post-mount).
// A fixed hardcoded background here would itself be a theme-mismatch flash
// (e.g. a dark-navy splash for a user on the light theme) — exactly the
// class of bug this component exists to prevent for the rest of the app.
export default function SplashScreen() {
  const { t } = useTranslation();
  return (
    <div
      className="fixed inset-0 z-[300] flex items-center justify-center"
      style={{ background: 'var(--bg-page)', minHeight: '100dvh' }}
      role="status"
      aria-live="polite"
    >
      <div className="text-center">
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-2xl bg-white shadow-xl overflow-hidden">
          <img src={INNOVACAR_LOGO_URL} alt="InnovaCar" className="h-full w-full object-contain p-1" />
        </div>
        <h1 className="mt-5 text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>InnovaCar</h1>
        <p className="mt-1 text-xs uppercase" style={{ color: 'var(--text-muted)' }}>{t('guidance.innovax')}</p>
        <div className="mx-auto mt-6 h-1 w-44 overflow-hidden rounded-full" style={{ background: 'var(--border-medium)' }}>
          <div className="splash-progress h-full bg-accent-400" />
        </div>
      </div>
    </div>
  );
}
