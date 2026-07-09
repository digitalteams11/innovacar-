import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/axios';
import { useAuth } from './AuthContext';

interface OnboardingStatus {
  welcomeDismissed: boolean;
  wizardSkipped: boolean;
  completed: boolean;
  tourCompleted: boolean;
  steps: Record<string, boolean>;
  counts: Record<string, number>;
}

interface OnboardingValue {
  status: OnboardingStatus | null;
  loading: boolean;
  refresh: () => Promise<void>;
  update: (values: Partial<Pick<OnboardingStatus, 'welcomeDismissed' | 'wizardSkipped' | 'completed' | 'tourCompleted'>>) => Promise<void>;
}

const OnboardingContext = createContext<OnboardingValue | null>(null);

const defaultOnboardingStatus: OnboardingStatus = {
  welcomeDismissed: false,
  wizardSkipped: false,
  completed: false,
  tourCompleted: false,
  steps: {
    agency: true,
    business: false,
    vehicle: false,
    client: false,
    reservation: false,
    contract: false,
    gps: false,
  },
  counts: {
    vehicles: 0,
    clients: 0,
    reservations: 0,
    contracts: 0,
  },
};

const ONBOARDING_FALLBACK_KEY = 'rentcar:onboarding-fallback';

function readFallbackStatus(): OnboardingStatus {
  try {
    const stored = localStorage.getItem(ONBOARDING_FALLBACK_KEY);
    return stored
      ? { ...defaultOnboardingStatus, ...JSON.parse(stored) }
      : defaultOnboardingStatus;
  } catch {
    return defaultOnboardingStatus;
  }
}

function unwrapStatus(payload: any): OnboardingStatus {
  if (!payload || typeof payload !== 'object') return readFallbackStatus();

  // The onboarding API keeps legacy state fields at the top level while its
  // standard response data contains the progress checklist. Prefer the full
  // top-level state so dismissal flags are not reset after a successful PATCH.
  const data = payload.success !== undefined && payload.data && typeof payload.data === 'object'
    ? payload.data
    : payload;
  const state = payload.success !== undefined ? payload : data;

  return {
    ...defaultOnboardingStatus,
    completed: state.completed ?? data.completed ?? false,
    welcomeDismissed: state.welcomeDismissed ?? data.welcomeDismissed ?? false,
    wizardSkipped: state.wizardSkipped ?? data.wizardSkipped ?? false,
    tourCompleted: state.tourCompleted ?? data.tourCompleted ?? false,
    steps: state.steps && !Array.isArray(state.steps)
      ? { ...defaultOnboardingStatus.steps, ...state.steps }
      : defaultOnboardingStatus.steps,
    counts: state.counts && typeof state.counts === 'object'
      ? { ...defaultOnboardingStatus.counts, ...state.counts }
      : defaultOnboardingStatus.counts,
  };
}

export function OnboardingProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isSuperAdmin } = useAuth();
  const [status, setStatus] = useState<OnboardingStatus | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!isAuthenticated || isSuperAdmin) {
      setStatus(null);
      setLoading(false);
      return;
    }
    try {
      const { data } = await api.get('/onboarding');
      const nextStatus = unwrapStatus(data);
      setStatus(nextStatus);
      localStorage.setItem(ONBOARDING_FALLBACK_KEY, JSON.stringify(nextStatus));
    } catch {
      setStatus(readFallbackStatus());
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated, isSuperAdmin]);

  useEffect(() => { refresh(); }, [refresh]);

  const update = useCallback(async (values: Record<string, boolean>) => {
    if (!isAuthenticated || isSuperAdmin) return;
    let optimisticStatus: OnboardingStatus = readFallbackStatus();
    setStatus(current => {
      optimisticStatus = { ...(current || readFallbackStatus()), ...values };
      localStorage.setItem(ONBOARDING_FALLBACK_KEY, JSON.stringify(optimisticStatus));
      return optimisticStatus;
    });

    try {
      const { data } = await api.patch('/onboarding', values);
      const savedStatus = unwrapStatus(data);
      setStatus(savedStatus);
      localStorage.setItem(ONBOARDING_FALLBACK_KEY, JSON.stringify(savedStatus));
    } catch {
      // Onboarding is optional guidance. Keep the optimistic local state so
      // a temporary backend failure never blocks or traps the user.
      setStatus(optimisticStatus);
    }
  }, [isAuthenticated, isSuperAdmin]);

  const value = useMemo(() => ({ status, loading, refresh, update }), [status, loading, refresh, update]);
  return <OnboardingContext.Provider value={value}>{children}</OnboardingContext.Provider>;
}

export function useOnboarding() {
  const context = useContext(OnboardingContext);
  if (!context) throw new Error('useOnboarding must be used within OnboardingProvider');
  return context;
}
