import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';

export interface FeatureAccess {
  code: string;
  enabled: boolean;
  name: string;
  description?: string;
  benefits?: string;
  category?: string;
  requiredPlan?: string;
  requiredPlans?: string[];
}

interface FeatureAccessState {
  loading: boolean;
  planName?: string;
  planCode?: string;
  features: Record<string, FeatureAccess>;
  hasFeature: (code: string) => boolean;
  getFeature: (code: string) => FeatureAccess | undefined;
  refresh: () => Promise<void>;
}

const FeatureAccessContext = createContext<FeatureAccessState | null>(null);

export function FeatureAccessProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isSuperAdmin } = useAuth();
  const [loading, setLoading] = useState(false);
  const [planName, setPlanName] = useState<string>();
  const [planCode, setPlanCode] = useState<string>();
  const [featureList, setFeatureList] = useState<FeatureAccess[]>([]);

  const refresh = async () => {
    if (!isAuthenticated || isSuperAdmin) {
      setFeatureList([]);
      return;
    }
    setLoading(true);
    try {
      const { data } = await api.get('/features/access');
      setPlanName(data.planName);
      setPlanCode(data.planCode);
      setFeatureList(data.features || []);
    } catch (error) {
      console.error('Failed to load feature access', error);
      setFeatureList([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [isAuthenticated, isSuperAdmin]);

  const features = useMemo(
    () => Object.fromEntries(featureList.map((feature) => [feature.code, feature])),
    [featureList],
  );

  const value = useMemo<FeatureAccessState>(() => ({
    loading,
    planName,
    planCode,
    features,
    hasFeature: (code) => Boolean(features[code]?.enabled),
    getFeature: (code) => features[code],
    refresh,
  }), [loading, planName, planCode, features]);

  return <FeatureAccessContext.Provider value={value}>{children}</FeatureAccessContext.Provider>;
}

export function useFeatureAccess() {
  const context = useContext(FeatureAccessContext);
  if (!context) throw new Error('useFeatureAccess must be used inside FeatureAccessProvider');
  return context;
}
