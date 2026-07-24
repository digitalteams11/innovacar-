import { useTranslation } from 'react-i18next';
import { LogIn } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

// Single controlled session-expired notice — replaces the old repeated
// bottom-toast + immediate hard redirect. AuthContext dedupes the trigger
// (one 401 storm produces at most one of these), and the redirect only
// happens when the user clicks through, preserving their place in the app.
export default function SessionExpiredModal() {
  const { sessionExpired, signInAgain } = useAuth();
  const { t } = useTranslation();

  if (!sessionExpired) return null;

  return (
    <div
      className="fixed inset-0 z-[260] flex items-center justify-center p-4"
      style={{ background: 'rgba(15, 23, 42, 0.55)', backdropFilter: 'blur(2px)' }}
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="session-expired-title"
      aria-describedby="session-expired-desc"
    >
      <div
        className="w-full max-w-[340px] rounded-2xl p-5 text-center"
        style={{ background: 'var(--bg-card-solid)', boxShadow: 'var(--shadow-elevated)', border: '1px solid var(--border-subtle)' }}
      >
        <div
          className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full"
          style={{ background: 'rgba(239,68,68,0.1)' }}
        >
          <LogIn size={20} style={{ color: '#ef4444' }} />
        </div>
        <h2 id="session-expired-title" className="text-base font-bold mb-1" style={{ color: 'var(--text-primary)' }}>
          {t('session.expiredTitle', 'Session expired')}
        </h2>
        <p id="session-expired-desc" className="text-sm mb-4" style={{ color: 'var(--text-secondary)' }}>
          {t('session.expiredMessage', 'Your session has ended. Sign in again to continue.')}
        </p>
        <button
          type="button"
          onClick={signInAgain}
          className="w-full rounded-xl py-2.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
          style={{ background: 'var(--brand-primary)' }}
          autoFocus
        >
          {t('session.signInAgain', 'Sign in again')}
        </button>
      </div>
    </div>
  );
}
