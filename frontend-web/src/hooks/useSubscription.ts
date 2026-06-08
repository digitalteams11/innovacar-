import { useState, useEffect } from 'react';
import api from '../api/axios';

interface SubscriptionStatus {
  planName: string;
  status: string;
  subscriptionActive: boolean;
  subscriptionEndDate: string;
  trialEndDate: string;
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

export function useSubscription() {
  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchStatus = async () => {
    try {
      const { data } = await api.get('/subscriptions/status');
      setStatus(data);
    } catch (err) {
      console.error('Failed to fetch subscription status', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
  }, []);

  const canCreateVehicle = status ? status.vehicleCount < status.maxVehicles : true;
  const canCreateEmployee = status ? status.employeeCount < status.maxEmployees : true;
  const canCreateReservation = status ? status.reservationCount < status.maxReservations : true;
  const canAddGps = status ? status.gpsCount < status.maxGpsDevices : true;
  const isTrial = status?.inTrial ?? false;
  const isExpired = status ? !status.subscriptionActive && !status.inTrial : false;

  return {
    status,
    loading,
    refresh: fetchStatus,
    canCreateVehicle,
    canCreateEmployee,
    canCreateReservation,
    canAddGps,
    isTrial,
    isExpired,
  };
}
