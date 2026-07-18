import { useState, useEffect } from 'react';
import api from '../api/axios';

interface SubscriptionStatus {
  planCode: string;
  planName: string;
  status: string;
  subscriptionStatus: string;
  isTrial: boolean;
  trialEndsAt: string | null;
  remainingTrialDays: number;
  trialDaysRemaining: number;
  trialStartDate: string | null;
  trialExpired: boolean;
  currentPeriodEnd: string | null;
  subscriptionActive: boolean;
  subscriptionEndDate: string | null;
  trialEndDate: string | null;
  inTrial: boolean;
  daysRemaining: number;
  maxVehicles: number;
  maxEmployees: number;
  maxGpsDevices: number;
  maxReservations: number;
  storageLimitMb: number;
  vehicleCount: number;
  employeeCount: number;
  reservationCount: number;
  gpsCount: number;
}

const unavailableSubscriptionStatus: SubscriptionStatus = {
  planCode: '',
  planName: '',
  status: 'UNKNOWN',
  subscriptionStatus: 'UNKNOWN',
  isTrial: false,
  trialEndsAt: null,
  remainingTrialDays: 0,
  trialDaysRemaining: 0,
  trialStartDate: null,
  trialExpired: false,
  currentPeriodEnd: null,
  subscriptionActive: false,
  subscriptionEndDate: null,
  trialEndDate: null,
  inTrial: false,
  daysRemaining: 0,
  maxVehicles: 4,
  maxEmployees: 5,
  maxGpsDevices: 5,
  maxReservations: 100,
  storageLimitMb: 1024,
  vehicleCount: 0,
  employeeCount: 0,
  reservationCount: 0,
  gpsCount: 0,
};

let cachedSubscriptionStatus: SubscriptionStatus | null = null;
let subscriptionRequest: Promise<SubscriptionStatus> | null = null;

function normalizeSubscriptionStatus(payload: any): SubscriptionStatus {
  const canonical = payload?.success !== undefined && payload?.data ? payload.data : payload;
  const source = { ...payload, ...canonical };
  const planCode = String(source.planCode || source.plan || source.planName || '').toUpperCase();
  // Gated on status alone — the backend's own subscriptionStatus/status field is the
  // single source of truth for "is this tenant on trial". Previously this also
  // required planCode to independently agree, which meant a tenant whose plan-code
  // lookup drifted (e.g. after a block/unblock cycle) still had status="TRIAL" but
  // showed the "Renews on ..." card instead of the trial countdown.
  const status = String(source.status || source.subscriptionStatus || 'UNKNOWN').toUpperCase();
  const isTrial = status === 'TRIAL' && source.isTrial === true;
  const trialDaysRemaining = isTrial
    ? Number(source.trialDaysRemaining ?? source.remainingTrialDays ?? source.remainingDays ?? 0)
    : 0;

  return {
    ...unavailableSubscriptionStatus,
    ...source,
    planCode,
    planName: source.planName || source.plan || planCode,
    status,
    subscriptionStatus: status,
    isTrial,
    inTrial: isTrial,
    trialEndsAt: isTrial ? (source.trialEndsAt || source.trialEndDate || null) : null,
    trialEndDate: isTrial ? (source.trialEndDate || source.trialEndsAt || null) : null,
    trialStartDate: source.trialStartDate || null,
    trialExpired: Boolean(source.trialExpired) || (status === 'EXPIRED'),
    remainingTrialDays: trialDaysRemaining,
    trialDaysRemaining,
    daysRemaining: Number(source.daysRemaining ?? source.remainingDays ?? 0),
    currentPeriodEnd: source.currentPeriodEnd || source.subscriptionEndDate || null,
  };
}

async function loadSubscriptionStatus(force = false): Promise<SubscriptionStatus> {
  if (!force && cachedSubscriptionStatus) return cachedSubscriptionStatus;
  if (!force && subscriptionRequest) return subscriptionRequest;

  subscriptionRequest = api.get('/subscriptions/status')
    .then(({ data }) => {
      cachedSubscriptionStatus = normalizeSubscriptionStatus(data);
      return cachedSubscriptionStatus;
    })
    .catch(() => {
      return unavailableSubscriptionStatus;
    })
    .finally(() => {
      subscriptionRequest = null;
    });

  return subscriptionRequest;
}

export function notifySubscriptionUpdated() {
  cachedSubscriptionStatus = null;
  subscriptionRequest = null;
  window.dispatchEvent(new Event('subscription-updated'));
}

export function useSubscription() {
  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchStatus = async (force = false) => {
    try {
      setStatus(await loadSubscriptionStatus(force));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
    const handleSubscriptionUpdated = () => fetchStatus(true);
    window.addEventListener('subscription-updated', handleSubscriptionUpdated);
    return () => window.removeEventListener('subscription-updated', handleSubscriptionUpdated);
  }, []);

  const canCreateVehicle = status ? status.vehicleCount < status.maxVehicles : true;
  const canCreateEmployee = status ? status.employeeCount < status.maxEmployees : true;
  const canCreateReservation = status ? status.reservationCount < status.maxReservations : true;
  const canAddGps = status ? status.gpsCount < status.maxGpsDevices : true;
  const isTrial = status?.isTrial === true;
  const isExpired = status ? status.status === 'EXPIRED' : false;

  return {
    status,
    loading,
    refresh: () => fetchStatus(true),
    canCreateVehicle,
    canCreateEmployee,
    canCreateReservation,
    canAddGps,
    isTrial,
    isExpired,
  };
}
