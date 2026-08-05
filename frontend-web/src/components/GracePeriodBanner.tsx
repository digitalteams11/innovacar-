import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useSubscription } from '../hooks/useSubscription';

/**
 * Persistent (non-toast, non-dismissable) compact banner shown on every page
 * while the tenant is in GRACE_PERIOD — access stays FULL during grace (see
 * SubscriptionController#buildTypedStatus), this only informs the admin the
 * clock is running. Unlike TrialBanner this has no close button: the spec
 * explicitly forbids re-triggering it as a toast, but also never asks for it
 * to be dismissable — it should stay visible for as long as the state is
 * true, same as GracePeriodEnd's real deadline.
 */
export default function GracePeriodBanner() {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
  const { status: subscription, isGracePeriod } = useSubscription();

  if (!subscription || !isGracePeriod) return null;

  const deadline = subscription.gracePeriodEnd
    ? new Date(subscription.gracePeriodEnd).toLocaleDateString(i18n.resolvedLanguage || i18n.language, {
        year: 'numeric', month: 'long', day: 'numeric',
      })
    : '—';

  return (
    <div
      role="status"
      style={{ background: 'rgba(245,158,11,0.1)', borderBottom: '1px solid rgba(245,158,11,0.3)' }}
    >
      <div className="flex items-center justify-between max-w-7xl mx-auto px-4 py-3 gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <AlertTriangle size={18} className="shrink-0" style={{ color: '#d97706' }} />
          <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
            {t('subscription.graceBanner.message', { date: deadline })}
          </p>
        </div>
        <button
          onClick={() => navigate('/subscription')}
          className="inline-flex items-center gap-1.5 text-sm font-semibold transition-colors underline underline-offset-2 whitespace-nowrap shrink-0"
          style={{ color: '#92400e' }}
        >
          {t('subscription.graceBanner.cta')} <ArrowRight size={14} />
        </button>
      </div>
    </div>
  );
}
