import { LockKeyhole, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useFeatureAccess } from '../context/FeatureAccessContext';

export default function FeatureGate({ feature, children }: { feature: string; children: React.ReactNode }) {
  const { loading, hasFeature, getFeature } = useFeatureAccess();
  const navigate = useNavigate();
  if (loading) return <div className="min-h-[40vh] flex items-center justify-center"><Loader2 className="animate-spin" /></div>;
  if (hasFeature(feature)) return <>{children}</>;

  const access = getFeature(feature) || { name: feature.replaceAll('_', ' '), requiredPlan: 'Premium' };
  return (
    <div className="min-h-[55vh] flex items-center justify-center p-4">
      <div className="bg-white border border-[#e8e6e1] p-8 max-w-xl text-center shadow-soft">
        <LockKeyhole size={26} className="mx-auto text-brand-500 mb-4" />
        <h1 className="text-xl font-bold text-[#1e293b]">Unlock {access.name}</h1>
        <p className="text-sm text-slate-500 mt-2">{access.benefits || access.description || `Enable ${access.name} for your agency.`}</p>
        <p className="text-xs font-semibold text-slate-400 uppercase mt-4">Available in {(access.requiredPlans || [access.requiredPlan]).join(' or ')} plans</p>
        <button onClick={() => navigate('/subscription')} className="mt-6 px-5 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-semibold">Upgrade Now</button>
      </div>
    </div>
  );
}
