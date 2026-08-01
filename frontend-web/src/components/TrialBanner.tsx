import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, ArrowRight, X } from 'lucide-react';
import { useSubscription } from '../hooks/useSubscription';
import { useTranslation } from 'react-i18next';

export default function TrialBanner() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { status: subscription } = useSubscription();
  const [dismissed, setDismissed] = useState(false);

  if (!subscription || dismissed) return null;

  if (!subscription.isTrial) return null;

  const secondsRemaining = subscription.trialSecondsRemaining;
  const daysRemaining = subscription.trialDaysRemaining;
  const isExpired = secondsRemaining <= 0;

  // Three-tier countdown color per spec: green with more than 7 days left,
  // orange under 7 days, red under 3 days — never just a single "urgent" cutoff.
  const dayFraction = secondsRemaining / 86400;
  const tier: 'green' | 'orange' | 'red' = dayFraction < 3 ? 'red' : dayFraction < 7 ? 'orange' : 'green';

  const styles = {
    green: {
      background: 'rgba(16,185,129,0.08)', borderBottom: '1px solid rgba(16,185,129,0.25)',
      iconColor: '#059669', buttonColor: '#047857', closeColor: '#059669',
    },
    orange: {
      background: 'rgba(245,158,11,0.1)', borderBottom: '1px solid rgba(245,158,11,0.3)',
      iconColor: '#d97706', buttonColor: '#92400e', closeColor: '#d97706',
    },
    red: {
      background: 'rgba(239,68,68,0.1)', borderBottom: '1px solid rgba(239,68,68,0.3)',
      iconColor: '#dc2626', buttonColor: '#991b1b', closeColor: '#dc2626',
    },
  } as const;

  const s = styles[tier];

  // Backend trialSecondsRemaining is the exact-instant source of truth, so once a
  // trial has less than a day left this switches to hours/minutes instead of
  // ever showing a misleading "0 days remaining".
  let messageKey: string;
  let messageCount: number;
  if (isExpired) {
    messageKey = 'trialBanner.trialExpired';
    messageCount = 0;
  } else if (secondsRemaining < 3600) {
    const minutes = Math.max(1, Math.floor(secondsRemaining / 60));
    messageKey = minutes === 1 ? 'trialBanner.trialEndsInMinute' : 'trialBanner.trialEndsInMinutePlural';
    messageCount = minutes;
  } else if (secondsRemaining < 86400) {
    const hours = Math.floor(secondsRemaining / 3600);
    messageKey = hours === 1 ? 'trialBanner.trialEndsInHour' : 'trialBanner.trialEndsInHourPlural';
    messageCount = hours;
  } else {
    messageKey = daysRemaining === 1 ? 'trialBanner.trialEndsIn' : 'trialBanner.trialEndsInPlural';
    messageCount = daysRemaining;
  }

  return (
    <div style={{ background: s.background, borderBottom: s.borderBottom }}>
      <div className="flex items-center justify-between max-w-7xl mx-auto px-4 py-3 gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <Clock size={18} className="shrink-0" style={{ color: s.iconColor }} />
          <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
            {t(messageKey, { count: messageCount })}
          </p>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => navigate('/subscription')}
            className="inline-flex items-center gap-1.5 text-sm font-semibold transition-colors underline underline-offset-2 whitespace-nowrap"
            style={{ color: s.buttonColor }}
          >
            {t('trialBanner.viewPlans')} <ArrowRight size={14} />
          </button>
          <button
            onClick={() => setDismissed(true)}
            className="transition-opacity opacity-60 hover:opacity-100"
            style={{ color: s.closeColor }}
            aria-label={t('common.close')}
          >
            <X size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
