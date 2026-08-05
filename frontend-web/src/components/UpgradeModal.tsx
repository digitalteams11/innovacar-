import { X, Sparkles, ArrowRight, Zap } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface UpgradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  featureName: string;
  featureDescription: string;
  requiredPlan: string;
  currentPlan: string;
  onUpgrade: () => void;
}

export default function UpgradeModal({
  isOpen,
  onClose,
  featureName,
  featureDescription,
  requiredPlan,
  currentPlan,
  onUpgrade,
}: UpgradeModalProps) {
  const { t } = useTranslation();
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[var(--z-modal-overlay)] flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      {/* Billing/upgrade surface — solid, never the translucent --bg-card glass token (spec: critical/billing surfaces must be opaque). */}
      <div className="relative flex w-full max-h-[100dvh] flex-col overflow-hidden rounded-t-2xl bg-[var(--bg-card-solid)] shadow-elevated animate-fade sm:max-h-[90dvh] sm:max-w-md sm:rounded-2xl">
        {/* Header — always brand-navy regardless of theme (fixed CTA identity, same as .premium-action) */}
        <div className="shrink-0 bg-[var(--brand-primary)] px-6 py-8 text-center relative overflow-hidden">
          <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/cubes.png')] opacity-5" />
          <div className="relative z-10">
            <div className="w-14 h-14 rounded-2xl bg-accent-400/20 flex items-center justify-center mx-auto mb-4">
              <Sparkles size={26} className="text-accent-400" />
            </div>
            <h3 className="text-xl font-bold text-[var(--brand-primary-foreground)]">{featureName}</h3>
            <p className="text-sm mt-1" style={{ color: 'var(--brand-primary-foreground)', opacity: 0.7 }}>{featureDescription}</p>
          </div>
          <button
            onClick={onClose}
            className="absolute top-4 end-4 p-1.5 hover:bg-white/10 rounded-lg transition-colors"
          >
            <X size={18} style={{ color: 'var(--brand-primary-foreground)', opacity: 0.7 }} />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-6 space-y-4">
          <div className="flex items-center gap-3 p-4 bg-[var(--bg-hover)] rounded-xl">
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 flex items-center justify-center shrink-0">
              <Zap size={18} className="text-amber-500" />
            </div>
            <div>
              <p className="text-sm font-medium text-[var(--text-primary)]">
                {t('subscription.availableInPlan', 'Available in')} <span className="text-brand-600 font-bold">{requiredPlan}</span>
              </p>
              <p className="text-xs text-[var(--text-muted)]">
                {t('subscription.yourCurrentPlan', 'Your current plan')}: {currentPlan || t('subscription.trial')}
              </p>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider">{t('subscription.whatYouGet', 'What you get:')}</p>
            <ul className="space-y-2">
              {[
                t('subscription.unlimitedAccessTo', 'Unlimited access to {{feature}}', { feature: featureName }),
                t('subscription.prioritySupportIncluded', 'Priority support included'),
                t('subscription.advancedAnalyticsReports', 'Advanced analytics & reports'),
                t('subscription.teamCollaborationTools', 'Team collaboration tools'),
              ].map((item, i) => (
                <li key={i} className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                  <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div
          className="shrink-0 flex gap-3 border-t p-6"
          style={{ borderColor: 'var(--border-subtle)', paddingBottom: 'max(1.5rem, env(safe-area-inset-bottom))' }}
        >
          <button
            onClick={onUpgrade}
            className="flex-1 bg-[var(--brand-primary)] hover:opacity-90 text-[var(--brand-primary-foreground)] py-3 rounded-xl text-sm font-semibold transition-colors flex items-center justify-center gap-2"
          >
            {t('subscription.upgradeNow')} <ArrowRight size={16} />
          </button>
          <button
            onClick={onClose}
            className="flex-1 bg-[var(--bg-hover)] hover:bg-[var(--bg-active)] text-[var(--text-primary)] py-3 rounded-xl text-sm font-semibold transition-colors"
          >
            {t('subscription.maybeLater')}
          </button>
        </div>
      </div>
    </div>
  );
}
