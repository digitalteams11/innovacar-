import { useState, useEffect, useCallback } from 'react';
import api from '../api/axios';

export interface GpsVehicle {
  id: number;
  marque: string;
  prixJour: number;
  category: string;
  plate: string;
  fuel: string;
  transmission: string;
  imageUrl: string;
  gpsDeviceId: string | null;
  gpsImei: string | null;
  lastLatitude: number | null;
  lastLongitude: number | null;
  lastGpsUpdate: string | null;
  gpsStatus: 'ONLINE' | 'OFFLINE' | 'MOVING' | 'STOPPED' | 'IDLE' | 'MAINTENANCE' | null;
  lastSpeed: number | null;
  gpsEnabled: boolean;
  tenantId: number;
  statusLabel: string;
}

export interface GpsStats {
  totalTracked: number;
  online: number;
  offline: number;
  moving: number;
  stopped: number;
  idle: number;
  activeAlerts: number;
  totalDistanceTodayKm: number;
}

export function useGpsLive() {
  const [vehicles, setVehicles] = useState<GpsVehicle[]>([]);
  const [stats, setStats] = useState<GpsStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [vehRes, statsRes] = await Promise.all([
        api.get('/gps/vehicles'),
        api.get('/gps/stats'),
      ]);
      setVehicles(vehRes.data);
      setStats(statsRes.data);
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to fetch GPS data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000); // Poll every 30s
    return () => clearInterval(interval);
  }, [fetchData]);

  return { vehicles, stats, loading, error, refetch: fetchData };
}

export function useGpsVehicle(vehicleId: number) {
  const [vehicle, setVehicle] = useState<GpsVehicle | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get(`/gps/vehicles/${vehicleId}`)
      .then(res => setVehicle(res.data))
      .finally(() => setLoading(false));
  }, [vehicleId]);

  return { vehicle, loading };
}
