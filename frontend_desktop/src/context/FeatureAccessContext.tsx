import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';

const FeatureAccessContext = createContext<any>(null);

export function FeatureAccessProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<any[]>([]);

  useEffect(() => {
    if (!isAuthenticated) {
      setList([]);
      return;
    }
    setLoading(true);
    api.get('/features/access')
      .then(({ data }) => setList(data.features || []))
      .catch(() => setList([]))
      .finally(() => setLoading(false));
  }, [isAuthenticated]);

  const features = useMemo(() => Object.fromEntries(list.map((feature) => [feature.code, feature])), [list]);
  return (
    <FeatureAccessContext.Provider value={{
      loading,
      getFeature: (code: string) => features[code],
      hasFeature: (code: string) => Boolean(features[code]?.enabled),
    }}>
      {children}
    </FeatureAccessContext.Provider>
  );
}

export function useFeatureAccess() {
  const context = useContext(FeatureAccessContext);
  if (!context) throw new Error('FeatureAccessProvider is required');
  return context;
}
