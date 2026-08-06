import { Loader2 } from 'lucide-react';
import { Navigate } from 'react-router-dom';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import LockedFeatureCard from './LockedFeatureCard';

export default function FeatureGate({
  feature,
  children,
  onDenied = 'lockedCard',
  redirectTo = '/settings',
}: {
  feature: string;
  children: React.ReactNode;
  /** 'lockedCard' (default) shows an upsell card for features agencies can
   * reasonably discover and buy into (GPS, reports, contracts, etc).
   * 'redirect' silently sends them elsewhere instead — for entitlements
   * that shouldn't be advertised or explained on the page itself (e.g.
   * WHITE_LABEL, an enterprise-only feature never sold via self-serve
   * upsell — showing "upgrade to unlock" there would just confuse a
   * normal agency that reached the URL directly). */
  onDenied?: 'lockedCard' | 'redirect';
  redirectTo?: string;
}) {
  const { loading, getFeature, hasFeature } = useFeatureAccess();

  if (loading) {
    return <div className="min-h-[40vh] flex items-center justify-center"><Loader2 className="animate-spin text-brand-500" /></div>;
  }

  if (hasFeature(feature)) return <>{children}</>;

  if (onDenied === 'redirect') return <Navigate to={redirectTo} replace />;

  const access = getFeature(feature) || {
    code: feature,
    enabled: false,
    name: feature.replaceAll('_', ' '),
    requiredPlan: 'Premium',
  };
  return <LockedFeatureCard feature={access} />;
}
