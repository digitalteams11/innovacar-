import { LockKeyhole, ArrowUpRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { FeatureAccess } from '../context/FeatureAccessContext';

export default function LockedFeatureCard({ feature }: { feature: FeatureAccess }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const plans = feature.requiredPlans?.length ? feature.requiredPlans.join(' or ') : feature.requiredPlan || t('subscription.premiumPlan', 'Premium');

  return (
    <div className="min-h-[55vh] flex items-center justify-center p-4">
      <div className="w-full max-w-xl bg-white border border-[#e8e6e1] shadow-soft p-6 sm:p-8 text-center">
        <div className="w-12 h-12 mx-auto mb-4 bg-brand-50 text-brand-500 flex items-center justify-center rounded-lg">
          <LockKeyhole size={24} />
        </div>
        <h1 className="text-xl font-bold text-[#1e293b]">{t('subscription.unlockFeature', 'Unlock {{feature}}', { feature: feature.name })}</h1>
        <p className="mt-2 text-sm text-slate-500 leading-6">
          {feature.benefits || feature.description || t('subscription.enableFeatureForAgency', 'Enable {{feature}} for your agency.', { feature: feature.name })}
        </p>
        <p className="mt-4 text-xs font-semibold text-slate-400 uppercase">
          {t('subscription.availableInPlans', 'Available in {{plans}} plans', { plans })}
        </p>
        <button
          onClick={() => navigate('/subscription')}
          className="mt-6 inline-flex items-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-semibold hover:bg-brand-600 transition-colors"
        >
          {t('subscription.upgradeNow')} <ArrowUpRight size={16} />
        </button>
      </div>
    </div>
  );
}
