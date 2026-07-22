import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertCircle, Car, Check, Fuel, Gauge, Loader2, RefreshCw, Search } from 'lucide-react';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { resolveMediaUrl } from '../../lib/utils';
import { translateFuelType, translateTransmission, translateVehicleCategory } from '../../utils/statusLabels';

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

function requestErrorMessage(error: unknown, t: any) {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 401) return t('vehicleSelector.sessionExpired');
  if (status === 403) return t('vehicleSelector.permissionDenied');
  if (error && typeof error === 'object' && 'userMessage' in error) {
    const value = (error as { userMessage?: unknown }).userMessage;
    if (typeof value === 'string' && value.trim()) return value;
  }
  return t('vehicleSelector.loadFailed');
}

function transmissionKey(value: string) {
  const normalized = value.trim().toLowerCase();
  if (['manual', 'manuelle'].includes(normalized)) return 'manual';
  if (['automatic', 'automatique', 'auto'].includes(normalized)) return 'automatic';
  return normalized;
}

function rentalDays(startDate: string, endDate: string) {
  if (!startDate || !endDate) return 0;
  const start = new Date(startDate);
  const end = new Date(endDate);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return 0;
  return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)));
}

export default function SmartVehicleSelector({
  startDate, startTime = '09:00', endDate, endTime = '18:00', value, onSelect, onUnavailable
}: SmartVehicleSelectorProps) {
  const { t, i18n } = useTranslation();
  const { isAuthenticated, loading: authLoading } = useAuth();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<Vehicle | null>(null);
  const [loadError, setLoadError] = useState('');
  const [emptyMessage, setEmptyMessage] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [transmissionFilter, setTransmissionFilter] = useState('');
  const requestVersionRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);
  const days = rentalDays(startDate, endDate);

  const fetchAvailableVehicles = useCallback(async (signal?: AbortSignal) => {
    if (authLoading || !isAuthenticated || !localStorage.getItem('token')) return;

    const requestVersion = ++requestVersionRef.current;
    const requestedStartDate = normalizeDateParam(startDate);
    const requestedEndDate = normalizeDateParam(endDate);
    const requestedStartTime = normalizeTimeParam(startTime);
    const requestedEndTime = normalizeTimeParam(endTime);
    const agencyId = currentAgencyId();
    setLoading(true);
    setLoadError('');
    setEmptyMessage('');
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
        setLoadError(requestErrorMessage(error, t));
      }
    } finally {
      if (requestVersion === requestVersionRef.current) setLoading(false);
    }
  }, [authLoading, endDate, endTime, isAuthenticated, onUnavailable, startDate, startTime, t, value]);

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

  useEffect(() => {
    setSearchQuery('');
    setCategoryFilter('');
    setTransmissionFilter('');
  }, [startDate, startTime, endDate, endTime]);

  const activeVehicle = vehicles.find((vehicle) => vehicle.id === value) || selectedVehicle;

  const categoryOptions = useMemo(() => {
    return Array.from(new Set(vehicles.map((vehicle) => vehicle.category).filter(Boolean))).sort((a, b) => a.localeCompare(b));
  }, [vehicles]);

  const filteredVehicles = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return vehicles
      .filter((vehicle) => {
        const matchesSearch = !q || [
          vehicle.marque,
          vehicle.brand,
          vehicle.model,
          vehicle.plate,
          vehicle.plateNumber,
        ].some((field) => String(field || '').toLowerCase().includes(q));
        const matchesCategory = !categoryFilter || vehicle.category === categoryFilter;
        const matchesTransmission = !transmissionFilter || transmissionKey(vehicle.transmission) === transmissionFilter;
        return matchesSearch && matchesCategory && matchesTransmission;
      });
  }, [categoryFilter, searchQuery, transmissionFilter, vehicles]);

  const hasActiveFilters = Boolean(searchQuery.trim() || categoryFilter || transmissionFilter);
  const periodLabel = startDate && endDate
    ? t('vehicleSelector.period', {
      startDate: new Date(startDate).toLocaleDateString(i18n.language),
      startTime,
      endDate: new Date(endDate).toLocaleDateString(i18n.language),
      endTime,
    })
    : '';

  const resetFilters = () => {
    setSearchQuery('');
    setCategoryFilter('');
    setTransmissionFilter('');
  };

  const handleSelect = (vehicle: Vehicle) => {
    setSelectedVehicle(vehicle);
    onSelect(vehicle);
  };

  if (!startDate || !endDate) {
    return (
      <div className="flex items-center gap-2 rounded-xl bg-warning-50 p-4 text-sm text-warning-600">
        <AlertCircle size={16} />
        <span>{t('vehicleSelector.selectPeriodFirst')}</span>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="rounded-2xl border border-slate-100 bg-white p-4">
        <div className="flex items-center gap-2 text-sm font-semibold text-slate-600">
          <Loader2 size={16} className="animate-spin text-brand-500" />
          {t('vehicleSelector.loading')}
        </div>
        <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
          {[0, 1, 2, 3].map((item) => (
            <div key={item} className="h-28 animate-pulse rounded-2xl bg-slate-100" />
          ))}
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-200">
        <div className="flex items-start gap-2">
          <AlertCircle size={17} className="mt-0.5 shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold">{t('vehicleSelector.unavailableTitle')}</p>
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
          <RefreshCw size={13} /> {t('vehicleSelector.retry')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="rounded-2xl border border-slate-100 bg-white p-3 shadow-sm">
        <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-sm font-bold text-[#1e293b]">
              {t('vehicleSelector.availableCount', { count: vehicles.length })}
            </p>
            <p className="text-[11px] text-slate-400">{periodLabel}</p>
          </div>
          {hasActiveFilters && (
            <button type="button" onClick={resetFilters} className="text-xs font-semibold text-brand-500 hover:text-brand-600">
              {t('vehicleSelector.resetFilters')}
            </button>
          )}
        </div>

        {vehicles.length > 0 && (
          <div className="mt-3 grid grid-cols-1 gap-2 md:grid-cols-[minmax(180px,1fr)_150px_150px]">
            <label className="relative block">
              <Search size={14} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder={t('vehicleSelector.searchPlaceholder')}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pe-3 ps-9 text-xs text-[#1e293b] outline-none transition-all focus:border-brand-300 focus:bg-white focus:ring-2 focus:ring-brand-100"
              />
            </label>
            <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)} className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-xs text-[#1e293b] outline-none focus:border-brand-300 focus:bg-white focus:ring-2 focus:ring-brand-100">
              <option value="">{t('vehicleSelector.allCategories')}</option>
              {categoryOptions.map((category) => <option key={category} value={category}>{translateVehicleCategory(category)}</option>)}
            </select>
            <select value={transmissionFilter} onChange={(event) => setTransmissionFilter(event.target.value)} className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-xs text-[#1e293b] outline-none focus:border-brand-300 focus:bg-white focus:ring-2 focus:ring-brand-100">
              <option value="">{t('vehicleSelector.allTransmissions')}</option>
              <option value="manual">{t('vehicleSelector.manual')}</option>
              <option value="automatic">{t('vehicleSelector.automatic')}</option>
            </select>
          </div>
        )}
      </div>

      {vehicles.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-6 text-center">
          <p className="text-sm font-semibold text-slate-600">
            {emptyMessage || t('vehicleSelector.noAvailable')}
          </p>
          <p className="mt-1 text-xs text-slate-400">
            {t('vehicleSelector.changePeriodHint')}
          </p>
        </div>
      ) : filteredVehicles.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-6 text-center">
          <p className="text-sm font-semibold text-slate-600">
            {t('vehicleSelector.noMatches')}
          </p>
          <button type="button" onClick={resetFilters} className="mt-3 rounded-xl bg-white px-4 py-2 text-xs font-semibold text-brand-500 shadow-sm hover:bg-brand-50">
            {t('vehicleSelector.resetFilters')}
          </button>
        </div>
      ) : (
        <div className="grid max-h-[360px] grid-cols-1 gap-3 overflow-y-auto pe-1 md:grid-cols-2">
          {filteredVehicles.map((vehicle) => {
            const isSelected = activeVehicle?.id === vehicle.id;
            const imageSrc = resolveMediaUrl(vehicle.imageUrl);
            const estimatedTotal = days > 0 ? (vehicle.prixJour || 0) * days : 0;
            const displayName = vehicle.marque || `${vehicle.brand || ''} ${vehicle.model || ''}`.trim();
            const categoryLabel = translateVehicleCategory(vehicle.category);
            const fuelLabel = translateFuelType(vehicle.fuel);
            const transmissionLabel = translateTransmission(vehicle.transmission);
            return (
              <button
                key={`${vehicle.id}-${vehicle.plateNumber || vehicle.plate || displayName || 'vehicle'}`}
                onClick={() => handleSelect(vehicle)}
                className={`relative flex min-h-[132px] items-start gap-3 rounded-2xl border-2 p-3 text-left transition-all ${
                  isSelected
                    ? 'border-brand-400 bg-brand-50/60 shadow-sm ring-2 ring-brand-100'
                    : 'border-slate-100 bg-white hover:border-slate-200 hover:shadow-sm'
                }`}
              >
                {isSelected && (
                  <div className="absolute end-2 top-2 flex items-center gap-1 rounded-full bg-brand-500 px-2 py-1 text-[10px] font-bold text-white">
                    <Check size={14} />
                    {t('vehicleSelector.selected')}
                  </div>
                )}
                <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-slate-100 text-slate-400">
                  {imageSrc ? (
                    <img src={imageSrc} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <Car size={22} />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate pe-20 text-sm font-bold text-[#1e293b]">{displayName || '-'}</p>
                  <p className="mt-0.5 font-mono text-[11px] text-slate-500">{vehicle.plateNumber || vehicle.plate || '-'}</p>
                  <p className="mt-1 text-[11px] text-slate-400">
                    {[categoryLabel, fuelLabel, transmissionLabel].filter(Boolean).join(' - ')}
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5">
                    {vehicle.fuel && <span className="flex items-center gap-1 text-[10px] text-slate-400"><Fuel size={9} /> {fuelLabel}</span>}
                    {vehicle.transmission && <span className="flex items-center gap-1 text-[10px] text-slate-400"><Gauge size={9} /> {transmissionLabel}</span>}
                    {vehicle.gpsEnabled && <span className="text-[10px] font-bold text-brand-400">GPS</span>}
                  </div>
                  <div className="mt-2 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                    <span className="text-lg font-black text-brand-500">{(vehicle.prixJour || 0).toLocaleString()}</span>
                    <span className="text-[10px] font-medium text-slate-400">{t('vehicleSelector.madPerDay')}</span>
                    {estimatedTotal > 0 && (
                      <span className="basis-full text-[11px] font-semibold text-slate-500">
                        {t('vehicleSelector.estimatedTotal', { amount: estimatedTotal.toLocaleString() })}
                      </span>
                    )}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
