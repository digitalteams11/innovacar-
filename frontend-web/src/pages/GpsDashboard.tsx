import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import {
  MapPin, Navigation, Car, AlertTriangle, Clock,
  Wifi, WifiOff, Loader2, RefreshCw, Satellite,
  Search, X, Gauge, Settings2, List, Map as MapIcon,
  ExternalLink, Bell, ChevronRight, AlertCircle,
  TriangleAlert, Eye,
} from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

// @ts-ignore
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({ iconRetinaUrl: markerIcon2x, iconUrl: markerIcon, shadowUrl: markerShadow });

// ── Types ────────────────────────────────────────────────────────────────────

interface GpsVehicle {
  id: number;
  marque: string;
  vehicleName?: string; // alias
  plate: string;
  gpsDeviceId?: string;
  gpsImei?: string;
  lastLatitude: number;
  lastLongitude: number;
  lastGpsUpdate?: string;
  gpsStatus: 'ONLINE' | 'OFFLINE' | 'MOVING' | 'STOPPED' | 'IDLE';
  lastSpeed?: number;
  gpsEnabled: boolean;
  category?: string;
  imageUrl?: string;
  outOfZone?: boolean;
}

interface GpsStats {
  totalTracked: number;
  online: number;
  offline: number;
  moving: number;
  stopped: number;
  idle: number;
  outOfZone: number;
  activeAlerts: number;
  alertsToday: number;
}

interface SyncedDevice {
  id: number;
  providerDeviceId: string;
  name?: string;
  imei?: string;
  plateNumber?: string;
  status?: string;
  latitude?: number;
  longitude?: number;
  speed?: number;
  vehicleId?: number | null;
  lastSyncedAt?: string;
}

// ── Status configuration ──────────────────────────────────────────────────────

const STATUS: Record<string, { label: string; color: string; bg: string; border: string; markerColor: string; Icon: any }> = {
  MOVING:  { label: 'Moving',    color: 'text-blue-600',    bg: 'bg-blue-50',    border: 'border-blue-200',    markerColor: '#2563eb', Icon: Navigation },
  ONLINE:  { label: 'Online',    color: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-200', markerColor: '#10b981', Icon: Wifi },
  STOPPED: { label: 'Stopped',   color: 'text-amber-600',   bg: 'bg-amber-50',   border: 'border-amber-200',   markerColor: '#d97706', Icon: Clock },
  IDLE:    { label: 'Idle',      color: 'text-orange-500',  bg: 'bg-orange-50',  border: 'border-orange-200',  markerColor: '#f97316', Icon: Clock },
  OFFLINE: { label: 'Offline',   color: 'text-slate-400',   bg: 'bg-slate-50',   border: 'border-slate-200',   markerColor: '#94a3b8', Icon: WifiOff },
};

const OOZ_MARKER_COLOR = '#dc2626';

function createMarkerIcon(color: string, isSelected = false) {
  const size = isSelected ? 40 : 32;
  const border = isSelected ? 4 : 3;
  return L.divIcon({
    className: '',
    html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${color};border:${border}px solid white;box-shadow:0 2px 10px rgba(0,0,0,0.3);display:flex;align-items:center;justify-content:center;">
      <svg width="${isSelected ? 20 : 16}" height="${isSelected ? 20 : 16}" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/>
        <circle cx="7" cy="17" r="2"/><circle cx="17" cy="17" r="2"/>
      </svg>
    </div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -(size / 2 + 4)],
  });
}

function MapFlyTo({ vehicle }: { vehicle: GpsVehicle | null }) {
  const map = useMap();
  useEffect(() => {
    if (vehicle?.lastLatitude && vehicle?.lastLongitude) {
      map.flyTo([vehicle.lastLatitude, vehicle.lastLongitude], 15, { duration: 1.2 });
    }
  }, [vehicle, map]);
  return null;
}

function MapFitBounds({ vehicles }: { vehicles: GpsVehicle[] }) {
  const map = useMap();
  const fitted = useRef(false);
  useEffect(() => {
    if (fitted.current) return;
    const pts = vehicles.filter(v => v.lastLatitude && v.lastLongitude);
    if (pts.length > 1) {
      const bounds = L.latLngBounds(pts.map(v => [v.lastLatitude, v.lastLongitude]));
      map.fitBounds(bounds, { padding: [40, 40] });
      fitted.current = true;
    }
  }, [vehicles, map]);
  return null;
}

// ── Helper ────────────────────────────────────────────────────────────────────

function timeAgo(date: string | null | undefined): string {
  if (!date) return '—';
  const diff = Math.floor((Date.now() - new Date(date).getTime()) / 1000);
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return new Date(date).toLocaleDateString();
}

type Filter = 'ALL' | 'ONLINE' | 'OFFLINE' | 'MOVING' | 'STOPPED' | 'OUT_OF_ZONE' | 'ALERTS';

// ── Main Component ────────────────────────────────────────────────────────────

export default function GpsDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [vehicles, setVehicles] = useState<GpsVehicle[]>([]);
  const [stats, setStats] = useState<GpsStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<GpsVehicle | null>(null);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [showVehicleList, setShowVehicleList] = useState(true);
  const [gpsConfigured, setGpsConfigured] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [secondsAgo, setSecondsAgo] = useState(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const inFlightRef = useRef(false);
  const syntheticRef = useRef<GpsVehicle[]>([]);

  // `/gps/settings` and `/gps/devices` describe configuration, not live
  // position — they only need to be (re-)fetched on mount and on an
  // explicit manual refresh, not on every 30s tracking tick. Polling them
  // that often just doubles request volume for data that hasn't changed.
  const fetchAll = useCallback(async (isManual = false, includeConfig = false) => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    if (isManual) setRefreshing(true);

    try {
      const requests: Promise<any>[] = [api.get('/gps/tracked'), api.get('/gps/stats')];
      if (includeConfig) requests.push(api.get('/gps/settings'), api.get('/gps/devices'));

      const [vehiclesRes, statsRes, settingsRes, devicesRes] = await Promise.allSettled(requests);

      if (includeConfig) {
        if (settingsRes?.status === 'fulfilled') {
          const d = settingsRes.value.data;
          setGpsConfigured(!!(d?.hasCredentials || d?.enabled));
        } else {
          setGpsConfigured(false);
        }
      }

      let tracked: GpsVehicle[] = [];
      if (vehiclesRes.status === 'fulfilled') {
        const raw = vehiclesRes.value.data;
        tracked = Array.isArray(raw) ? raw : (raw?.data ?? []);
      }

      // Merge unlinked synced devices (no vehicleId) as virtual map entries.
      // Only refetched with the config batch; on tracking-only ticks we
      // reuse the last computed list so they don't disappear from the map.
      if (includeConfig && devicesRes?.status === 'fulfilled') {
        const devList: SyncedDevice[] = devicesRes.value.data?.data?.devices ?? [];
        const linkedIds = new Set(tracked.map(v => v.id));
        syntheticRef.current = devList
          .filter(d => !d.vehicleId && d.latitude && d.longitude && !linkedIds.has(d.id))
          .map(d => ({
            id: d.id,
            marque: d.name || `Device ${d.providerDeviceId}`,
            plate: d.plateNumber || d.imei || d.providerDeviceId,
            gpsDeviceId: d.providerDeviceId,
            gpsImei: d.imei,
            lastLatitude: d.latitude ?? 0,
            lastLongitude: d.longitude ?? 0,
            lastGpsUpdate: d.lastSyncedAt,
            gpsStatus: (d.status as GpsVehicle['gpsStatus']) || 'OFFLINE',
            lastSpeed: d.speed,
            gpsEnabled: true,
          }));
      }
      setVehicles([...tracked, ...syntheticRef.current]);

      if (statsRes.status === 'fulfilled') {
        const raw = statsRes.value.data;
        setStats(raw?.data ?? raw);
      }

      setLastUpdated(new Date());
      setSecondsAgo(0);
    } finally {
      inFlightRef.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    fetchAll(false, true);
  }, [fetchAll]);

  // Auto-poll every 30 seconds — tracking data only, config is re-fetched
  // separately (initial load + manual refresh).
  useEffect(() => {
    pollRef.current = setInterval(() => fetchAll(false, false), 30_000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [fetchAll]);

  // Seconds-ago ticker
  useEffect(() => {
    const t = setInterval(() => setSecondsAgo(s => s + 1), 1_000);
    return () => clearInterval(t);
  }, []);

  const vehicleName = (v: GpsVehicle) => v.marque || v.vehicleName || 'Unknown';

  const filteredVehicles = useMemo(() => {
    let list = vehicles;
    if (filter === 'ONLINE')       list = list.filter(v => v.gpsStatus === 'ONLINE' || v.gpsStatus === 'MOVING');
    else if (filter === 'OFFLINE') list = list.filter(v => v.gpsStatus === 'OFFLINE');
    else if (filter === 'MOVING')  list = list.filter(v => v.gpsStatus === 'MOVING');
    else if (filter === 'STOPPED') list = list.filter(v => v.gpsStatus === 'STOPPED' || v.gpsStatus === 'IDLE');
    else if (filter === 'OUT_OF_ZONE') list = list.filter(v => v.outOfZone);
    else if (filter === 'ALERTS')  list = list.filter(v => v.outOfZone || v.gpsStatus === 'OFFLINE');

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(v =>
        vehicleName(v).toLowerCase().includes(q) ||
        v.plate?.toLowerCase().includes(q) ||
        v.gpsDeviceId?.toLowerCase().includes(q)
      );
    }
    return list;
  }, [vehicles, filter, searchQuery]);

  const mapCenter = useMemo<[number, number]>(() => {
    const pts = vehicles.filter(v => v.lastLatitude && v.lastLongitude);
    if (pts.length > 0) {
      const lat = pts.reduce((s, v) => s + v.lastLatitude, 0) / pts.length;
      const lng = pts.reduce((s, v) => s + v.lastLongitude, 0) / pts.length;
      return [lat, lng];
    }
    return [31.7917, -7.0926]; // Morocco default
  }, [vehicles]);

  // ── Loading / no-config states ──────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (!gpsConfigured) {
    return (
      <div className="space-y-6 animate-fade max-w-4xl mx-auto w-full">
        <GlassPageHeader title={t('gps.liveTracking')} subtitle={t('gps.realTimeMonitoring')} icon={Satellite} />
        <div className="data-surface flex flex-col items-center justify-center py-20 text-center">
          <div className="w-20 h-20 rounded-2xl bg-brand-50 flex items-center justify-center mb-5">
            <Satellite size={40} className="text-brand-500" />
          </div>
          <h3 className="text-xl font-bold text-[#1e293b] mb-2">{t('gps.notConfigured')}</h3>
          <p className="text-sm text-slate-500 max-w-md mb-6">
            {t('gps.notConfiguredDescription')}
          </p>
          <button
            onClick={() => navigate('/gps-settings')}
            className="flex items-center gap-2 px-6 py-3 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all shadow-sm"
          >
            <Settings2 size={16} />
            {t('gps.openSettings')}
          </button>
        </div>
      </div>
    );
  }

  // ── KPI cards config ──────────────────────────────────────────────────────

  const kpis = [
    { label: 'Tracked',     value: stats?.totalTracked ?? 0,  Icon: Car,           color: 'text-brand-600',    bg: 'bg-brand-50',    filterKey: 'ALL'         },
    { label: 'Online',      value: stats?.online ?? 0,        Icon: Wifi,          color: 'text-emerald-600',  bg: 'bg-emerald-50',  filterKey: 'ONLINE'      },
    { label: 'Moving',      value: stats?.moving ?? 0,        Icon: Navigation,    color: 'text-blue-600',     bg: 'bg-blue-50',     filterKey: 'MOVING'      },
    { label: 'Stopped',     value: stats?.stopped ?? 0,       Icon: Clock,         color: 'text-amber-600',    bg: 'bg-amber-50',    filterKey: 'STOPPED'     },
    { label: 'Offline',     value: stats?.offline ?? 0,       Icon: WifiOff,       color: 'text-slate-400',    bg: 'bg-slate-50',    filterKey: 'OFFLINE'     },
    { label: 'Out of Zone', value: stats?.outOfZone ?? 0,     Icon: TriangleAlert, color: 'text-rose-600',     bg: 'bg-rose-50',     filterKey: 'OUT_OF_ZONE' },
    { label: 'Alerts Today',value: stats?.alertsToday ?? 0,   Icon: Bell,          color: 'text-orange-600',   bg: 'bg-orange-50',   filterKey: 'ALERTS'      },
  ] as const;

  const FILTERS: { key: Filter; label: string; count?: number }[] = [
    { key: 'ALL',         label: 'All',         count: stats?.totalTracked },
    { key: 'ONLINE',      label: 'Online',      count: stats?.online },
    { key: 'MOVING',      label: 'Moving',      count: stats?.moving },
    { key: 'STOPPED',     label: 'Stopped',     count: stats?.stopped },
    { key: 'OFFLINE',     label: 'Offline',     count: stats?.offline },
    { key: 'OUT_OF_ZONE', label: 'Out of Zone', count: stats?.outOfZone },
  ];

  return (
    <div className="space-y-4 animate-fade">
      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <GlassPageHeader
        title={t('gps.liveTracking')}
        subtitle={t('gps.realTimeMonitoring')}
        icon={Satellite}
        actions={
          <div className="flex items-center gap-3">
            {lastUpdated && (
              <span className="text-xs text-slate-400 hidden sm:block">
                Updated {secondsAgo < 5 ? 'just now' : `${secondsAgo}s ago`}
              </span>
            )}
            <button
              onClick={() => navigate('/gps-alerts')}
              className="surface-control flex items-center gap-2 h-10 px-3 text-sm font-medium"
            >
              <Bell size={15} />
              <span className="hidden sm:inline">Alerts</span>
              {(stats?.activeAlerts ?? 0) > 0 && (
                <span className="px-1.5 py-0.5 rounded-full bg-rose-500 text-white text-[10px] font-bold leading-none">
                  {stats!.activeAlerts}
                </span>
              )}
            </button>
            <button
              onClick={() => fetchAll(true, true)}
              disabled={refreshing}
              className="surface-control flex items-center gap-2 h-10 px-4 text-sm font-medium disabled:opacity-50"
            >
              <RefreshCw size={15} className={refreshing ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        }
      />

      {/* ── KPI row ────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-7 gap-2 sm:gap-3">
        {kpis.map(({ label, value, Icon, color, bg, filterKey }) => (
          <button
            key={label}
            onClick={() => setFilter(filterKey as Filter)}
            className={`metric-surface text-left transition-all hover:scale-[1.02] ${
              filter === filterKey ? 'ring-2 ring-brand-300' : ''
            }`}
          >
            <div className={`w-7 h-7 rounded-lg ${bg} flex items-center justify-center mb-2`}>
              <Icon size={14} className={color} />
            </div>
            <p className="text-lg font-bold text-[#1e293b] leading-none">{value}</p>
            <p className="text-[10px] text-slate-400 font-medium mt-0.5 leading-tight">{label}</p>
          </button>
        ))}
      </div>

      {/* ── Main: Map + Sidebar ──────────────────────────────────────────── */}
      <div
        className="grid grid-cols-1 lg:grid-cols-4 gap-3"
        style={{ height: 'calc(100vh - 320px)', minHeight: 520 }}
      >
        {/* Map */}
        <div className="lg:col-span-3 relative rounded-xl overflow-hidden border border-[var(--border-subtle)] shadow-[var(--shadow-card)]">
          <MapContainer
            center={mapCenter}
            zoom={vehicles.length > 0 ? 12 : 6}
            style={{ height: '100%', width: '100%' }}
            scrollWheelZoom
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapFlyTo vehicle={selectedVehicle} />
            <MapFitBounds vehicles={filteredVehicles} />

            {filteredVehicles.map(vehicle => {
              if (!vehicle.lastLatitude || !vehicle.lastLongitude) return null;
              const st = STATUS[vehicle.gpsStatus] || STATUS.OFFLINE;
              const markerColor = vehicle.outOfZone ? OOZ_MARKER_COLOR : st.markerColor;
              const isSelected = selectedVehicle?.id === vehicle.id;
              const name = vehicleName(vehicle);

              return (
                <Marker
                  key={vehicle.id}
                  position={[vehicle.lastLatitude, vehicle.lastLongitude]}
                  icon={createMarkerIcon(markerColor, isSelected)}
                  eventHandlers={{ click: () => setSelectedVehicle(vehicle) }}
                >
                  <Popup closeButton={false} className="vehicle-popup">
                    <div className="min-w-[240px] p-1">
                      <div className="flex items-center gap-2.5 mb-3">
                        <div className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                          style={{ background: markerColor + '20' }}>
                          <Car size={18} style={{ color: markerColor }} />
                        </div>
                        <div>
                          <p className="font-bold text-[#1e293b] text-sm">{name}</p>
                          <p className="text-xs text-slate-400">{vehicle.plate || '—'}</p>
                        </div>
                        <span className={`ml-auto px-2 py-0.5 rounded-full text-[10px] font-semibold ${st.bg} ${st.color}`}>
                          {vehicle.outOfZone ? '⚠ Out of Zone' : st.label}
                        </span>
                      </div>

                      <div className="grid grid-cols-2 gap-1.5 mb-3">
                        {[
                          { label: 'Speed',    value: vehicle.lastSpeed ? `${Math.round(vehicle.lastSpeed)} km/h` : '—' },
                          { label: 'Updated',  value: timeAgo(vehicle.lastGpsUpdate) },
                          { label: 'Lat',      value: vehicle.lastLatitude.toFixed(5) },
                          { label: 'Lng',      value: vehicle.lastLongitude.toFixed(5) },
                        ].map(({ label, value }) => (
                          <div key={label} className="bg-slate-50 rounded-lg p-2">
                            <p className="text-[10px] text-slate-400 mb-0.5">{label}</p>
                            <p className="text-xs font-semibold text-[#1e293b] font-mono">{value}</p>
                          </div>
                        ))}
                      </div>

                      <div className="flex gap-2">
                        <button
                          onClick={() => setSelectedVehicle(vehicle)}
                          className="flex-1 py-2 bg-brand-500 text-white rounded-lg text-xs font-medium hover:bg-brand-600 transition-all flex items-center justify-center gap-1.5"
                        >
                          <Eye size={12} /> Details
                        </button>
                        <a
                          href={`https://maps.google.com/?q=${vehicle.lastLatitude},${vehicle.lastLongitude}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex-1 py-2 bg-slate-100 text-[#1e293b] rounded-lg text-xs font-medium hover:bg-slate-200 transition-all flex items-center justify-center gap-1.5"
                        >
                          <ExternalLink size={12} /> Maps
                        </a>
                      </div>
                    </div>
                  </Popup>
                </Marker>
              );
            })}
          </MapContainer>

          {/* Map overlay: mobile toggle */}
          <button
            onClick={() => setShowVehicleList(v => !v)}
            className="lg:hidden absolute top-3 end-3 z-[1000] flex items-center gap-2 px-3 py-2 bg-white rounded-xl shadow-lg text-sm font-medium text-[#1e293b] border border-[#e8e6e1]"
          >
            {showVehicleList ? <MapIcon size={14} /> : <List size={14} />}
            {showVehicleList ? 'Map' : 'List'}
          </button>

          {/* Map overlay: no vehicles found */}
          {vehicles.length === 0 && (
            <div className="absolute inset-0 z-[500] flex items-center justify-center bg-white/70 backdrop-blur-sm">
              <div className="text-center p-8">
                <Satellite size={40} className="text-slate-300 mx-auto mb-3" />
                <p className="font-semibold text-[#1e293b] mb-1">No GPS devices found</p>
                <p className="text-sm text-slate-400 mb-4">Go to GPS Settings and click Sync Devices to import your fleet.</p>
                <button
                  onClick={() => navigate('/gps-settings')}
                  className="flex items-center gap-2 px-4 py-2 bg-brand-500 text-white rounded-xl text-sm font-medium mx-auto hover:bg-brand-600 transition-all"
                >
                  <Settings2 size={14} /> GPS Settings
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className={`lg:col-span-1 flex flex-col gap-2 overflow-hidden ${showVehicleList ? '' : 'hidden lg:flex'}`}
          style={{ minHeight: 0 }}>

          {/* Search */}
          <div className="relative shrink-0">
            <Search size={13} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search vehicles..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full ps-8 pe-8 py-2.5 bg-white border border-[var(--border-subtle)] rounded-xl text-sm text-[#1e293b] placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-200"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')}
                className="absolute end-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                <X size={13} />
              </button>
            )}
          </div>

          {/* Filter chips */}
          <div className="flex flex-wrap gap-1 shrink-0">
            {FILTERS.map(f => (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className={`px-2 py-1 rounded-lg text-[11px] font-medium transition-all ${
                  filter === f.key
                    ? 'bg-brand-500 text-white'
                    : 'bg-white text-slate-500 hover:text-[#1e293b] border border-[var(--border-subtle)]'
                }`}
              >
                {f.label}{f.count != null ? ` ${f.count}` : ''}
              </button>
            ))}
          </div>

          {/* Vehicle list */}
          <div className="flex-1 overflow-y-auto space-y-1.5 min-h-0">
            {filteredVehicles.length === 0 ? (
              <div className="data-surface py-10 text-center">
                <Car size={28} className="text-slate-300 mx-auto mb-2" />
                <p className="text-sm text-slate-400">
                  {vehicles.length === 0 ? 'No GPS devices found. Sync in GPS Settings.' : 'No vehicles match filter'}
                </p>
              </div>
            ) : filteredVehicles.map(vehicle => {
              const st = STATUS[vehicle.gpsStatus] || STATUS.OFFLINE;
              const isSelected = selectedVehicle?.id === vehicle.id;
              const name = vehicleName(vehicle);
              return (
                <button
                  key={vehicle.id}
                  onClick={() => setSelectedVehicle(isSelected ? null : vehicle)}
                  className={`w-full text-left data-surface p-3 cursor-pointer transition-all hover:shadow-sm ${
                    isSelected ? 'ring-2 ring-brand-200 bg-brand-50/20' : ''
                  }`}
                >
                  <div className="flex items-start gap-2.5">
                    <div className={`w-8 h-8 rounded-lg ${st.bg} flex items-center justify-center shrink-0 mt-0.5`}>
                      <st.Icon size={14} className={st.color} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5 mb-0.5">
                        <p className="text-sm font-semibold text-[#1e293b] truncate">{name}</p>
                        {vehicle.outOfZone && (
                          <TriangleAlert size={11} className="text-rose-500 shrink-0" />
                        )}
                      </div>
                      <p className="text-[11px] text-slate-400 truncate">{vehicle.plate || '—'}</p>
                      <div className="flex items-center gap-2 mt-1.5">
                        <span className={`text-[10px] font-semibold ${st.color}`}>{st.label}</span>
                        {vehicle.lastSpeed != null && vehicle.lastSpeed > 0 && (
                          <>
                            <span className="text-slate-300">·</span>
                            <span className="text-[10px] text-slate-500 flex items-center gap-0.5">
                              <Gauge size={9} /> {Math.round(vehicle.lastSpeed)} km/h
                            </span>
                          </>
                        )}
                        {vehicle.lastGpsUpdate && (
                          <>
                            <span className="text-slate-300">·</span>
                            <span className="text-[10px] text-slate-400">{timeAgo(vehicle.lastGpsUpdate)}</span>
                          </>
                        )}
                      </div>
                    </div>
                    <ChevronRight size={13} className={`text-slate-300 mt-1 transition-transform ${isSelected ? 'rotate-90' : ''}`} />
                  </div>

                  {/* Coordinates row */}
                  {vehicle.lastLatitude && vehicle.lastLongitude && (
                    <div className="mt-2 pt-2 border-t border-[var(--border-subtle)]/50 flex items-center gap-1 text-[10px] text-slate-400 font-mono">
                      <MapPin size={9} />
                      {vehicle.lastLatitude.toFixed(4)}, {vehicle.lastLongitude.toFixed(4)}
                    </div>
                  )}
                </button>
              );
            })}
          </div>

          {/* Selected vehicle detail panel */}
          {selectedVehicle && (() => {
            const v = selectedVehicle;
            const st = STATUS[v.gpsStatus] || STATUS.OFFLINE;
            const name = vehicleName(v);
            return (
              <div className="shrink-0 data-surface p-3 space-y-3 border-t-2 border-brand-200">
                {/* Header */}
                <div className="flex items-center gap-2">
                  <div className={`w-9 h-9 rounded-xl ${st.bg} flex items-center justify-center shrink-0`}>
                    <Car size={16} className={st.color} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold text-[#1e293b] truncate">{name}</p>
                    <p className="text-[11px] text-slate-400">{v.plate || '—'}</p>
                  </div>
                  <button onClick={() => setSelectedVehicle(null)}
                    className="text-slate-300 hover:text-slate-500 ml-auto">
                    <X size={14} />
                  </button>
                </div>

                {/* Out-of-zone warning */}
                {v.outOfZone && (
                  <div className="flex items-center gap-2 p-2 bg-rose-50 rounded-lg border border-rose-100">
                    <AlertCircle size={13} className="text-rose-500 shrink-0" />
                    <p className="text-[11px] font-medium text-rose-600">Vehicle is outside the allowed city zone</p>
                  </div>
                )}

                {/* Stats grid */}
                <div className="grid grid-cols-2 gap-1.5">
                  {[
                    { label: 'Status',  value: st.label },
                    { label: 'Speed',   value: v.lastSpeed ? `${Math.round(v.lastSpeed)} km/h` : '—' },
                    { label: 'Device',  value: v.gpsDeviceId || '—' },
                    { label: 'Updated', value: timeAgo(v.lastGpsUpdate) },
                  ].map(({ label, value }) => (
                    <div key={label} className="bg-slate-50 rounded-lg p-2">
                      <p className="text-[10px] text-slate-400">{label}</p>
                      <p className="text-xs font-semibold text-[#1e293b] truncate">{value}</p>
                    </div>
                  ))}
                </div>

                {/* Actions */}
                <div className="flex flex-col gap-1.5">
                  {v.lastLatitude && v.lastLongitude && (
                    <a
                      href={`https://maps.google.com/?q=${v.lastLatitude},${v.lastLongitude}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center gap-1.5 py-2 bg-brand-500 text-white rounded-lg text-xs font-medium hover:bg-brand-600 transition-all"
                    >
                      <ExternalLink size={12} /> Open in Google Maps
                    </a>
                  )}
                  <button
                    onClick={() => navigate('/gps-alerts')}
                    className="flex items-center justify-center gap-1.5 py-2 bg-slate-100 text-[#1e293b] rounded-lg text-xs font-medium hover:bg-slate-200 transition-all"
                  >
                    <AlertTriangle size={12} /> View Alerts
                  </button>
                </div>
              </div>
            );
          })()}
        </div>
      </div>
    </div>
  );
}
