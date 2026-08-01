import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Clock, CreditCard, LifeBuoy, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';

// One shared full-screen lock screen for every reason a user can be kept out
// of the app: a Super-Admin block/suspend, or the free trial simply running
// out. It renders as a route (see App.tsx's ProtectedRoute), not a dismissable
// modal, so there is no X/Escape/backdrop-close to bypass and a browser
// refresh or manual URL change lands right back here for as long as
// accountAccess.canUsePlatform stays false.
export default function AccountSuspended() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const status = user?.subscriptionStatus || user?.agencyStatus || 'SUSPENDED';
  const isBlocked = status === 'BLOCKED';
  // A trial that ran out (wasTrial + status EXPIRED) reads very differently
  // from a Super Admin deliberately blocking/suspending the agency — the
  // former is an expected, self-serve-recoverable state; the latter implies
  // contacting support. Never conflate the two copy sets.
  const isTrialExpired = Boolean(user?.wasTrial) && status === 'EXPIRED';

  const title = isTrialExpired
    ? t('trialExpiredScreen.title', 'Your free trial has ended')
    : t('accountSuspended.title', 'Account suspended');
  const description = isTrialExpired
    ? t('trialExpiredScreen.description', 'Choose a subscription to continue using Innovacar.')
    : t('accountSuspended.description', 'Your agency account is currently {{status}} by Innovax Technologies.', {
        status: isBlocked ? t('accountSuspended.blocked', 'blocked') : t('accountSuspended.suspended', 'suspended'),
      });

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--bg-page)] p-4">
      <div className="max-w-md w-full bg-[var(--bg-card)] rounded-2xl border border-[var(--border-subtle)] shadow-soft p-8 text-center">
        <div
          className={`w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-5 ${
            isTrialExpired ? 'bg-amber-500/10' : 'bg-rose-500/10'
          }`}
        >
          {isTrialExpired ? (
            <Clock size={28} className="text-amber-500" />
          ) : (
            <ShieldAlert size={28} className="text-rose-500" />
          )}
        </div>
        <h1 className="text-xl font-bold text-[var(--text-primary)] mb-2">{title}</h1>
        <p className="text-sm text-[var(--text-muted)] mb-6">{description}</p>

        <div className="bg-[var(--bg-hover)] rounded-xl p-4 mb-6 text-left space-y-1.5">
          <div className="flex justify-between text-sm">
            <span className="text-[var(--text-muted)]">{t('accountSuspended.plan', 'Plan')}</span>
            <span className="font-semibold text-[var(--text-primary)]">{user?.planName || '-'}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-[var(--text-muted)]">{t('accountSuspended.status', 'Status')}</span>
            <span className={`font-semibold ${isTrialExpired ? 'text-amber-600' : 'text-rose-600'}`}>{status}</span>
          </div>
        </div>

        <div className="space-y-2">
          <button
            onClick={() => navigate('/subscription')}
            className="w-full flex items-center justify-center gap-2 bg-[var(--brand-primary)] hover:opacity-90 text-[var(--brand-primary-foreground)] py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <CreditCard size={16} />
            {isTrialExpired
              ? t('trialExpiredScreen.upgradeNow', 'Upgrade now')
              : t('accountSuspended.openBilling', 'Open Billing')}
          </button>
          <button
            onClick={() => window.dispatchEvent(new CustomEvent('open-help-center'))}
            className="w-full flex items-center justify-center gap-2 bg-[var(--bg-hover)] hover:bg-[var(--bg-active)] text-[var(--text-primary)] py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <LifeBuoy size={16} />
            {isTrialExpired
              ? t('trialExpiredScreen.contactSupport', 'Contact support')
              : t('accountSuspended.contactSupport', 'Contact Support')}
          </button>
          <button
            onClick={logout}
            className="w-full flex items-center justify-center gap-2 text-rose-500 hover:bg-rose-500/10 py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <LogOut size={16} />
            {t('accountSuspended.logout', 'Logout')}
          </button>
        </div>
      </div>
    </div>
  );
}
