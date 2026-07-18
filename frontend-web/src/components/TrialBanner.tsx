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

  const daysRemaining = subscription.trialDaysRemaining;
  const isUrgent = daysRemaining <= 7;

  const urgentStyle = {
    background: 'rgba(245,158,11,0.1)',
    borderBottom: '1px solid rgba(245,158,11,0.3)',
    iconColor: '#d97706',
    textColor: '#92400e',
    buttonColor: '#92400e',
    closeColor: '#d97706',
  };
  const normalStyle = {
    background: 'rgba(59,130,246,0.08)',
    borderBottom: '1px solid rgba(59,130,246,0.2)',
    iconColor: '#3b82f6',
    textColor: '#1e40af',
    buttonColor: '#1e40af',
    closeColor: '#3b82f6',
  };

  const s = isUrgent ? urgentStyle : normalStyle;

  const messageKey = daysRemaining === 1 ? 'trialBanner.trialEndsIn' : 'trialBanner.trialEndsInPlural';

  return (
    <div style={{ background: s.background, borderBottom: s.borderBottom }}>
      <div className="flex items-center justify-between max-w-7xl mx-auto px-4 py-3 gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <Clock size={18} className="shrink-0" style={{ color: s.iconColor }} />
          <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
            {t(messageKey, { count: daysRemaining })}
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
