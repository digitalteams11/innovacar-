import { useState, useEffect, useCallback } from 'react';
import api from '../api/axios';

export interface ResourceLimit {
  used: number;
  limit: number;
  percentage: number;
  isNearLimit: boolean;
  isAtLimit: boolean;
}

interface RawLimit {
  used: number;
  limit: number;
}

export interface PlanAccessData {
  currentPlan: string | null;
  currentPlanCode: string | null;
  subscriptionStatus: string;
  features: Record<string, boolean>;
  limits: {
    vehicles: ResourceLimit;
    employees: ResourceLimit;
    gpsDevices: ResourceLimit;
    reservations: ResourceLimit;
    clients: ResourceLimit;
    contracts: ResourceLimit;
  };
}

interface UsePlanAccessReturn {
  loading: boolean;
  error: boolean;
  data: PlanAccessData | null;
  hasFeature: (code: string) => boolean;
  getLimit: (resource: keyof PlanAccessData['limits']) => ResourceLimit | null;
  refresh: () => void;
}

const toResourceLimit = (raw: RawLimit | undefined): ResourceLimit => {
  const used = raw?.used ?? 0;
  const limit = raw?.limit ?? 0;
  const percentage = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0;
  return {
    used,
    limit,
    percentage,
    isNearLimit: limit > 0 && percentage >= 80,
    isAtLimit: limit > 0 && used >= limit,
  };
};

export function usePlanAccess(): UsePlanAccessReturn {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [data, setData] = useState<PlanAccessData | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    api.get<{ success: boolean; data: { currentPlan: string; currentPlanCode: string; subscriptionStatus: string; features: Record<string, boolean>; limits: Record<string, RawLimit> } }>('/billing/access')
      .then((res) => {
        if (cancelled) return;
        const raw = res.data.data;
        setData({
          currentPlan:        raw.currentPlan,
          currentPlanCode:    raw.currentPlanCode,
          subscriptionStatus: raw.subscriptionStatus,
          features:           raw.features ?? {},
          limits: {
            vehicles:     toResourceLimit(raw.limits?.vehicles),
            employees:    toResourceLimit(raw.limits?.employees),
            gpsDevices:   toResourceLimit(raw.limits?.gpsDevices),
            reservations: toResourceLimit(raw.limits?.reservations),
            clients:      toResourceLimit(raw.limits?.clients),
            contracts:    toResourceLimit(raw.limits?.contracts),
          },
        });
      })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tick]);

  const hasFeature = useCallback(
    (code: string) => data?.features[code] === true,
    [data],
  );

  const getLimit = useCallback(
    (resource: keyof PlanAccessData['limits']) => data?.limits[resource] ?? null,
    [data],
  );

  const refresh = useCallback(() => setTick((n) => n + 1), []);

  return { loading, error, data, hasFeature, getLimit, refresh };
}
