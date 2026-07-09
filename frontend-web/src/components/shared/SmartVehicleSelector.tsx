import { useCallback, useEffect, useRef, useState } from 'react';
import { Car, Fuel, Gauge, AlertCircle, Check, Loader2, RefreshCw } from 'lucide-react';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';

export interface Vehicle {
  id: number;
  marque: string;
  brand?: string;
  model?: string;
  category: string;
  plate: string;
  plateNumber?: string;
  fuel: string;
  transmission: string;
  year: number;
  color: string;
  prixJour: number;
  dailyPrice?: number;
  prixSemaine?: number;
  prixMois?: number;
  depositAmount?: number;
  insuranceFees?: number;
  deliveryFees?: number;
  extraMileageCost?: number;
  statut: string;
  imageUrl?: string;
  gpsEnabled?: boolean;
}

interface SmartVehicleSelectorProps {
  startDate: string;
  startTime?: string;
  endDate: string;
  endTime?: string;
  value?: number;
  onSelect: (vehicle: Vehicle) => void;
  /** Called when the currently selected vehicle drops out of the available list for the new dates. */
  onUnavailable?: () => void;
}

function extractVehicleList(payload: unknown): Vehicle[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return normalizeVehicles(payload);
  if (payload && typeof payload === 'object') {
    const response = payload as Record<string, unknown>;
    if (Array.isArray(response.data)) return normalizeVehicles(response.data);
    if (response.data && typeof response.data === 'object') {
      const nested = response.data as Record<string, unknown>;
      if (Array.isArray(nested.content)) return normalizeVehicles(nested.content);
      if (Array.isArray(nested.vehicles)) return normalizeVehicles(nested.vehicles);
      if (Array.isArray(nested.data)) return normalizeVehicles(nested.data);
    }
    if (Array.isArray(response.content)) return normalizeVehicles(response.content);
    if (Array.isArray(response.vehicles)) return normalizeVehicles(response.vehicles);
  }
  return [];
}

function normalizeVehicles(rows: unknown[]): Vehicle[] {
  return rows
    .filter((row): row is Record<string, unknown> => !!row && typeof row === 'object')
    .map((row) => {
      const brand = String(row.brand ?? '').trim();
      const model = String(row.model ?? '').trim();
      const marque = String(row.marque ?? [brand, model].filter(Boolean).join(' ')).trim();
      return {
        id: Number(row.id),
        marque,
        brand,
        model,
        category: String(row.category ?? ''),
        plate: String(row.plate ?? row.plateNumber ?? ''),
        plateNumber: String(row.plateNumber ?? row.plate ?? ''),
        fuel: String(row.fuel ?? ''),
        transmission: String(row.transmission ?? ''),
        year: Number(row.year ?? 0),
        color: String(row.color ?? ''),
        prixJour: Number(row.prixJour ?? row.dailyPrice ?? 0),
        dailyPrice: Number(row.dailyPrice ?? row.prixJour ?? 0),
        prixSemaine: row.prixSemaine == null ? undefined : Number(row.prixSemaine),
        prixMois: row.prixMois == null ? undefined : Number(row.prixMois),
        depositAmount: row.depositAmount == null ? undefined : Number(row.depositAmount),
        insuranceFees: row.insuranceFees == null ? undefined : Number(row.insuranceFees),
        deliveryFees: row.deliveryFees == null ? undefined : Number(row.deliveryFees),
        extraMileageCost: row.extraMileageCost == null ? undefined : Number(row.extraMileageCost),
        statut: String(row.statut ?? row.status ?? 'AVAILABLE'),
        imageUrl: row.imageUrl == null ? undefined : String(row.imageUrl),
        gpsEnabled: Boolean(row.gpsEnabled),
      };
    })
    .filter((vehicle) => Number.isFinite(vehicle.id) && vehicle.id > 0);
}

function normalizeDateParam(value: string): string {
  const trimmed = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  const usDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (usDate) {
    const [, month, day, year] = usDate;
    return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
  }
  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? trimmed : parsed.toISOString().slice(0, 10);
}

function normalizeTimeParam(value: string): string {
  const trimmed = value.trim();
  const twelveHour = trimmed.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/i);
  if (twelveHour) {
    const [, rawHour, minute, meridiem] = twelveHour;
    let hour = Number(rawHour);
    if (meridiem.toUpperCase() === 'PM' && hour !== 12) hour += 12;
    if (meridiem.toUpperCase() === 'AM' && hour === 12) hour = 0;
    return `${String(hour).padStart(2, '0')}:${minute}`;
  }
  const twentyFourHour = trimmed.match(/^(\d{1,2}):(\d{2})/);
  if (twentyFourHour) {
    const [, hour, minute] = twentyFourHour;
    return `${hour.padStart(2, '0')}:${minute}`;
  }
  return trimmed;
}

function currentAgencyId() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}') as Record<string, unknown>;
    return user.agencyId ?? user.tenantId ?? null;
  } catch {
    return null;
  }
}

function requestErrorMessage(error: unknown) {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 401) return 'Session expired. Please sign in again.';
  if (status === 403) return 'You do not have permission to view available vehicles.';
  if (error && typeof error === 'object' && 'userMessage' in error) {
    const value = (error as { userMessage?: unknown }).userMessage;
    if (typeof value === 'string' && value.trim()) return value;
  }
  return 'Available vehicles could not be loaded. Please retry.';
}

export default function SmartVehicleSelector({
  startDate, startTime = '09:00', endDate, endTime = '18:00', value, onSelect, onUnavailable
}: SmartVehicleSelectorProps) {
  const { isAuthenticated, loading: authLoading } = useAuth();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<Vehicle | null>(null);
  const [loadError, setLoadError] = useState('');
  const [emptyMessage, setEmptyMessage] = useState('No available vehicles for selected dates');
  const requestVersionRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  const fetchAvailableVehicles = useCallback(async (signal?: AbortSignal) => {
    // Never call the availability endpoint before auth has settled and a
    // token actually exists — calling it during the brief window right
    // after a refresh/logout transition is what produces spurious 401s.
    if (authLoading || !isAuthenticated || !localStorage.getItem('token')) {
      return;
    }
    const requestVersion = ++requestVersionRef.current;
    const requestedStartDate = normalizeDateParam(startDate);
    const requestedEndDate = normalizeDateParam(endDate);
    const requestedStartTime = normalizeTimeParam(startTime);
    const requestedEndTime = normalizeTimeParam(endTime);
    const agencyId = currentAgencyId();
    setLoading(true);
    setLoadError('');
    setEmptyMessage('No available vehicles for selected dates');
    try {
      const { data } = await api.get<unknown>('/availability/vehicles', {
        params: {
          startDate: requestedStartDate,
          endDate: requestedEndDate,
          startTime: requestedStartTime,
          endTime: requestedEndTime,
        },
        signal,
      });
      console.log('[availability]', {
        requestedStartDate,
        requestedEndDate,
        requestedStartTime,
        requestedEndTime,
        agencyId,
        responseData: data,
      });
      const availableVehicles = extractVehicleList(data);
      if (requestVersion === requestVersionRef.current) {
        setVehicles(availableVehicles);
        const response = data && typeof data === 'object' ? data as Record<string, unknown> : {};
        if (response.success === false && typeof response.message === 'string') {
          setEmptyMessage(response.message);
        } else if (availableVehicles.length === 0 && typeof response.message === 'string') {
          setEmptyMessage(response.message);
        }
        // The previously selected vehicle no longer appears for the new date
        // range (someone else booked it, or the dates changed) — clear it
        // instead of silently submitting a contract for an unavailable vehicle.
        if (value != null && !availableVehicles.some((vehicle) => vehicle.id === value)) {
          setSelectedVehicle(null);
          onUnavailable?.();
        }
      }
    } catch (error) {
      if ((error as { code?: string })?.code === 'ERR_CANCELED') return;
      console.error(error);
      if (requestVersion === requestVersionRef.current) {
        setVehicles([]);
        setLoadError(requestErrorMessage(error));
      }
    } finally {
      if (requestVersion === requestVersionRef.current) {
        setLoading(false);
      }
    }
  }, [authLoading, endDate, endTime, isAuthenticated, startDate, startTime]);

  useEffect(() => {
    if (!startDate || !endDate || authLoading || !isAuthenticated) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const timer = window.setTimeout(() => void fetchAvailableVehicles(controller.signal), 500);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [authLoading, endDate, endTime, fetchAvailableVehicles, isAuthenticated, startDate, startTime]);

  const activeVehicle = vehicles.find((vehicle) => vehicle.id === value) || selectedVehicle;

  const handleSelect = (vehicle: Vehicle) => {
    setSelectedVehicle(vehicle);
    onSelect(vehicle);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 size={24} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (!startDate || !endDate) {
    return (
      <div className="flex items-center gap-2 p-4 bg-warning-50 text-warning-600 rounded-xl text-sm">
        <AlertCircle size={16} />
        <span>Please select rental dates first to see available vehicles</span>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-200">
        <div className="flex items-start gap-2">
          <AlertCircle size={17} className="mt-0.5 shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold">Vehicle availability is unavailable</p>
            <p className="mt-1 text-xs leading-5">{loadError}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            abortRef.current?.abort();
            const controller = new AbortController();
            abortRef.current = controller;
            void fetchAvailableVehicles(controller.signal);
          }}
          className="mt-3 inline-flex items-center gap-2 rounded-lg border border-rose-200 bg-white px-3 py-2 text-xs font-semibold hover:bg-rose-100 dark:border-rose-500/20 dark:bg-white/5 dark:hover:bg-white/10"
        >
          <RefreshCw size={13} /> Retry availability
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-[320px] overflow-y-auto pr-1">
        {vehicles.map((vehicle) => {
          const isSelected = activeVehicle?.id === vehicle.id;
          return (
            <button
              key={`${vehicle.id}-${vehicle.plateNumber || vehicle.plate || vehicle.marque || vehicle.model || 'vehicle'}`}
              onClick={() => handleSelect(vehicle)}
              className={`relative flex items-start gap-3 p-4 rounded-2xl border-2 text-left transition-all ${
                isSelected
                  ? 'border-brand-400 bg-brand-50/50 shadow-sm'
                  : 'border-slate-100 bg-white hover:border-slate-200 hover:shadow-sm'
              }`}
            >
              {isSelected && (
                <div className="absolute top-2 right-2 w-6 h-6 bg-brand-500 rounded-full flex items-center justify-center">
                  <Check size={14} className="text-white" />
                </div>
              )}
              <div className="w-12 h-12 bg-slate-100 rounded-xl flex items-center justify-center text-slate-400 shrink-0">
                {vehicle.imageUrl ? (
                  <img src={vehicle.imageUrl} alt="" className="w-full h-full object-cover rounded-xl" />
                ) : (
                  <Car size={22} />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-[#1e293b]">{vehicle.marque}</p>
                <p className="text-[11px] text-slate-400 mt-0.5">{vehicle.category} • {vehicle.year} • {vehicle.color}</p>
                <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1.5">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1"><Fuel size={9} /> {vehicle.fuel}</span>
                  <span className="text-[10px] text-slate-400 flex items-center gap-1"><Gauge size={9} /> {vehicle.transmission}</span>
                  {vehicle.gpsEnabled && <span className="text-[10px] text-brand-400 font-bold">GPS</span>}
                </div>
                <div className="flex items-baseline gap-1.5 mt-2">
                  <span className="text-lg font-black text-brand-500">{vehicle.prixJour}</span>
                  <span className="text-[10px] text-slate-400 font-medium">MAD/day</span>
                </div>
              </div>
            </button>
          );
        })}
      </div>
      {vehicles.length === 0 && (
        <div className="text-center py-6 text-slate-400 text-sm">
          {emptyMessage}
        </div>
      )}
    </div>
  );
}
