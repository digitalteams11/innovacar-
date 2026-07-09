import { Loader2 } from 'lucide-react';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import LockedFeatureCard from './LockedFeatureCard';

export default function FeatureGate({
  feature,
  children,
}: {
  feature: string;
  children: React.ReactNode;
}) {
  const { loading, getFeature, hasFeature } = useFeatureAccess();

  if (loading) {
    return <div className="min-h-[40vh] flex items-center justify-center"><Loader2 className="animate-spin text-brand-500" /></div>;
  }

  const access = getFeature(feature) || {
    code: feature,
    enabled: false,
    name: feature.replaceAll('_', ' '),
    requiredPlan: 'Premium',
  };

  return hasFeature(feature) ? <>{children}</> : <LockedFeatureCard feature={access} />;
}
