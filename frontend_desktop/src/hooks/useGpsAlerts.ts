import { useState, useEffect, useCallback } from 'react';
import api from '../api/axios';

export interface GpsAlert {
  id: number;
  alertType: string;
  message: string;
  severity: string;
  read: boolean;
  vehicleId: number;
  vehicleName: string;
  latitude: number | null;
  longitude: number | null;
  speed: number | null;
  createdAt: string;
}

export function useGpsAlerts() {
  const [alerts, setAlerts] = useState<GpsAlert[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);

  const fetchAlerts = useCallback(async () => {
    try {
      const [alertsRes, countRes] = await Promise.all([
        api.get('/gps/alerts'),
        api.get('/gps/alerts/count'),
      ]);
      setAlerts(alertsRes.data);
      setUnreadCount(countRes.data.count);
    } catch (err) {
      console.error('Failed to fetch alerts', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAlerts();
    const interval = setInterval(fetchAlerts, 30000);
    return () => clearInterval(interval);
  }, [fetchAlerts]);

  const markAsRead = async (id: number) => {
    await api.patch(`/gps/alerts/${id}/read`);
    setAlerts(prev => prev.map(a => a.id === id ? { ...a, read: true } : a));
    setUnreadCount(prev => Math.max(0, prev - 1));
  };

  const markAllAsRead = async () => {
    await api.patch('/gps/alerts/read-all');
    setAlerts(prev => prev.map(a => ({ ...a, read: true })));
    setUnreadCount(0);
  };

  return { alerts, unreadCount, loading, markAsRead, markAllAsRead, refetch: fetchAlerts };
}
