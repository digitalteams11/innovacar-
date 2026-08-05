import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Clock, CreditCard, LifeBuoy, LogOut, Lock, Check, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { useSubscription } from '../hooks/useSubscription';
import api from '../api/axios';

interface CompletePlan {
  id: number;
  code: string;
  name: string;
  monthlyPrice: number;
  yearlyPrice: number;
  currency?: string;
  billingCycleAllowedMonthly?: boolean;
  billingCycleAllowedYearly?: boolean;
  whopCheckoutUrlMonthly?: string;
  whopCheckoutUrlYearly?: string;
  featuresJson?: string;
}

// One shared full-screen lock screen for every reason a user can be kept out
// of the app: a Super-Admin block/suspend, a trial that ran out, or a
// suspended (billing-lifecycle) subscription. It renders as a route (see
// App.tsx's ProtectedRoute), not a dismissable modal, so there is no
// X/Escape/backdrop-close to bypass and a browser refresh or manual URL
// change lands right back here for as long as accountAccess.canUsePlatform
// or the live accessMode from /subscriptions/status stays blocking.
export default function AccountSuspended() {
  const { user, logout } = useAuth();
  const { status: subscription } = useSubscription();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [plan, setPlan] = useState<CompletePlan | null>(null);
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [redirecting, setRedirecting] = useState(false);

  useEffect(() => {
    api.get('/subscriptions/plans')
      .then(({ data }) => {
        const complete = (data ?? []).find((p: any) => String(p?.code ?? '').toUpperCase() === 'COMPLETE');
        setPlan(complete ?? null);
      })
      .catch(() => setPlan(null));
  }, []);

  // A Super-Admin manual block/suspend (accountAccess.blockedReason
  // AGENCY_BLOCKED/AGENCY_SUSPENDED) reads very differently from the
  // billing-lifecycle TRIAL_EXPIRED/SUSPENDED states below — never conflate
  // "Innovax blocked you" with "your trial/payment lapsed".
  const manualBlockReason = user?.accountAccess?.blockedReason;
  const isManualBlock = manualBlockReason === 'AGENCY_BLOCKED' || manualBlockReason === 'AGENCY_SUSPENDED';

  const statusEnum = subscription?.statusEnum || (subscription?.status as any) || null;
  const isTrialExpired = !isManualBlock && (
    statusEnum === 'TRIAL_EXPIRED' || (Boolean(user?.wasTrial) && subscription?.status === 'EXPIRED')
  );
  const isBillingSuspended = !isManualBlock && !isTrialExpired;

  const features = [
    t('subscription.blocked.feature.vehicles'),
    t('subscription.blocked.feature.employees'),
    t('subscription.blocked.feature.contracts'),
    t('subscription.blocked.feature.support'),
  ];

  const currency = plan?.currency || 'MAD';
  const yearlyAllowed = plan?.billingCycleAllowedYearly !== false;
  const price = billingCycle === 'yearly' ? plan?.yearlyPrice : plan?.monthlyPrice;
  const activeCheckoutUrl = billingCycle === 'yearly' ? plan?.whopCheckoutUrlYearly : plan?.whopCheckoutUrlMonthly;

  const handleActivate = () => {
    if (!activeCheckoutUrl) {
      navigate('/subscription');
      return;
    }
    setRedirecting(true);
    window.location.href = activeCheckoutUrl;
  };

  let title: string;
  let description: string;
  let Icon = ShieldAlert;
  let iconClass = 'bg-rose-500/10';
  let iconColor = 'text-rose-500';

  if (isTrialExpired) {
    title = t('subscription.blocked.trialExpired.title');
    description = t('subscription.blocked.trialExpired.body');
    Icon = Clock;
    iconClass = 'bg-amber-500/10';
    iconColor = 'text-amber-500';
  } else if (isBillingSuspended) {
    title = t('subscription.blocked.suspended.title');
    description = t('subscription.blocked.suspended.body');
    Icon = ShieldAlert;
    iconClass = 'bg-rose-500/10';
    iconColor = 'text-rose-500';
  } else {
    const isBlocked = manualBlockReason === 'AGENCY_BLOCKED';
    title = t('accountSuspended.title', 'Account suspended');
    description = t('accountSuspended.description', 'Your agency account is currently {{status}} by Innovax Technologies.', {
      status: isBlocked ? t('accountSuspended.blocked', 'blocked') : t('accountSuspended.suspended', 'suspended'),
    });
  }

  const showCheckoutCard = isTrialExpired || isBillingSuspended;

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--bg-page)] p-4 py-8">
      <div className="max-w-md w-full bg-[var(--bg-card)] rounded-2xl border border-[var(--border-subtle)] shadow-soft p-6 sm:p-8 text-center">
        <div className={`w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-5 ${iconClass}`}>
          <Icon size={28} className={iconColor} />
        </div>
        <h1 className="text-xl font-bold text-[var(--text-primary)] mb-2">{title}</h1>
        <p className="text-sm text-[var(--text-muted)] mb-6">{description}</p>

        {showCheckoutCard && (
          <div className="bg-[var(--bg-hover)] rounded-xl p-4 mb-6 text-left">
            {plan && yearlyAllowed && (
              <div className="flex items-center justify-center gap-2 mb-4">
                <button
                  onClick={() => setBillingCycle('monthly')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${billingCycle === 'monthly' ? 'bg-[var(--brand-primary)] text-[var(--brand-primary-foreground)]' : 'text-[var(--text-muted)]'}`}
                >
                  {t('subscription.monthly')}
                </button>
                <button
                  onClick={() => setBillingCycle('yearly')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${billingCycle === 'yearly' ? 'bg-[var(--brand-primary)] text-[var(--brand-primary-foreground)]' : 'text-[var(--text-muted)]'}`}
                >
                  {t('subscription.yearly')}
                </button>
              </div>
            )}

            <div className="flex items-baseline justify-center gap-1 mb-4">
              {plan ? (
                <>
                  <span className="text-3xl font-bold text-[var(--text-primary)]">{price}</span>
                  <span className="text-sm text-[var(--text-muted)]">{currency}/{billingCycle === 'yearly' ? t('subscription.yearly') : t('subscription.perMonth')}</span>
                </>
              ) : (
                <Loader2 size={20} className="animate-spin text-[var(--text-muted)]" />
              )}
            </div>

            <ul className="space-y-1.5 mb-4">
              {features.map((f) => (
                <li key={f} className="flex items-center gap-2 text-xs text-[var(--text-secondary)]">
                  <Check size={13} className="text-emerald-500 shrink-0" />
                  {f}
                </li>
              ))}
            </ul>

            <div className="flex items-center gap-1.5 justify-center text-[11px] text-[var(--text-muted)]">
              <Lock size={11} />
              {t('subscription.blocked.secureCheckout')}
            </div>
          </div>
        )}

        <div className="space-y-2">
          <button
            onClick={showCheckoutCard ? handleActivate : () => navigate('/subscription')}
            disabled={redirecting}
            className="w-full flex items-center justify-center gap-2 bg-[var(--brand-primary)] hover:opacity-90 disabled:opacity-60 text-[var(--brand-primary-foreground)] py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <CreditCard size={16} />
            {redirecting
              ? t('subscription.processing')
              : isTrialExpired
              ? t('subscription.blocked.trialExpired.primaryCta')
              : isBillingSuspended
              ? t('subscription.blocked.suspended.primaryCta')
              : t('accountSuspended.openBilling', 'Open Billing')}
          </button>
          <button
            onClick={() => { navigate('/support'); window.dispatchEvent(new CustomEvent('open-help-center')); }}
            className="w-full flex items-center justify-center gap-2 bg-[var(--bg-hover)] hover:bg-[var(--bg-active)] text-[var(--text-primary)] py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <LifeBuoy size={16} />
            {showCheckoutCard
              ? t('subscription.blocked.contactSupport')
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
