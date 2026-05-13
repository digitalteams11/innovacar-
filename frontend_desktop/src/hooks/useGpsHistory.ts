import { useState, useEffect } from 'react';
import api from '../api/axios';

export interface GpsHistoryPoint {
  latitude: number;
  longitude: number;
  speed: number;
  heading: number;
  timestamp: string;
  status: string;
  address: string;
}

export interface GpsTrip {
  vehicleId: number;
  vehicleName: string;
  startTime: string;
  endTime: string;
  startLatitude: number;
  startLongitude: number;
  endLatitude: number;
  endLongitude: number;
  distanceKm: number;
  maxSpeed: number;
  avgSpeed: number;
  durationMinutes: number;
  startAddress: string;
  endAddress: string;
}

export function useGpsHistory(vehicleId: number, from: string, to: string) {
  const [history, setHistory] = useState<GpsHistoryPoint[]>([]);
  const [trips, setTrips] = useState<GpsTrip[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!vehicleId || !from || !to) return;
    setLoading(true);
    Promise.all([
      api.get(`/gps/history/${vehicleId}?from=${from}&to=${to}`),
      api.get(`/gps/trips/${vehicleId}?from=${from}&to=${to}`),
    ])
      .then(([histRes, tripsRes]) => {
        setHistory(histRes.data);
        setTrips(tripsRes.data);
      })
      .catch(err => console.error('Failed to fetch history', err))
      .finally(() => setLoading(false));
  }, [vehicleId, from, to]);

  return { history, trips, loading };
}
