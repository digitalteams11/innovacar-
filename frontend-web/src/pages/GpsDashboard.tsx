import { useState, useEffect, useMemo } from 'react';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import {
  MapPin, Navigation, Car, AlertTriangle, Clock,
  Wifi, WifiOff, Loader2,
  ChevronRight, RefreshCw, Satellite, Circle,
  Search, X, Eye, Gauge, Route, Settings2, List, Map as MapIcon
} from 'lucide-react';

// Leaflet imports
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix Leaflet default icon paths for Vite
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

// @ts-ignore
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

interface GpsVehicle {
  id: number;
  vehicleName: string;
  plate: string;
  gpsDeviceId: string;
  gpsImei: string;
  lastLatitude: number;
  lastLongitude: number;
  lastGpsUpdate: string;
  gpsStatus: 'ONLINE' | 'OFFLINE' | 'MOVING' | 'STOPPED' | 'IDLE';
  lastSpeed: number;
  gpsEnabled: boolean;
  category: string;
  imageUrl?: string;
  fuel?: string;
  transmission?: string;
}

interface GpsStats {
  totalTracked: number;
  online: number;
  offline: number;
  moving: number;
  stopped: number;
  idle: number;
  activeAlerts: number;
  totalDistanceTodayKm: number;
}

interface GpsAlert {
  id: number;
  alertType: string;
  message: string;
  severity: string;
  vehicleName: string;
  createdAt: string;
  read: boolean;
}

const STATUS_STYLES: Record<string, { color: string; bg: string; icon: any; label: string; markerColor: string }> = {
  ONLINE: { color: 'text-emerald-600', bg: 'bg-emerald-50', icon: Wifi, label: 'Online', markerColor: '#10b981' },
  OFFLINE: { color: 'text-slate-500', bg: 'bg-slate-50', icon: WifiOff, label: 'Offline', markerColor: '#64748b' },
  MOVING: { color: 'text-blue-600', bg: 'bg-blue-50', icon: Navigation, label: 'Moving', markerColor: '#2563eb' },
  STOPPED: { color: 'text-amber-600', bg: 'bg-amber-50', icon: Circle, label: 'Stopped', markerColor: '#d97706' },
  IDLE: { color: 'text-orange-600', bg: 'bg-orange-50', icon: Clock, label: 'Idle', markerColor: '#ea580c' },
};

// Create colored marker icons for each status
function createMarkerIcon(color: string) {
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="
      width: 32px; height: 32px; border-radius: 50%;
      background: ${color}; border: 3px solid white;
      box-shadow: 0 2px 8px rgba(0,0,0,0.35);
      display: flex; align-items: center; justify-content: center;
    ">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/>
        <circle cx="7" cy="17" r="2"/>
        <circle cx="17" cy="17" r="2"/>
      </svg>
    </div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    popupAnchor: [0, -18],
  });
}

// Map controller to fly to selected vehicle
function MapController({ vehicle }: { vehicle: GpsVehicle | null }) {
  const map = useMap();
  useEffect(() => {
    if (vehicle && vehicle.lastLatitude && vehicle.lastLongitude) {
      map.flyTo([vehicle.lastLatitude, vehicle.lastLongitude], 16, { duration: 1 });
    }
  }, [vehicle, map]);
  return null;
}

export default function GpsDashboard() {
  const { showToast } = useToast();

  const [vehicles, setVehicles] = useState<GpsVehicle[]>([]);
  const [stats, setStats] = useState<GpsStats | null>(null);
  const [, setAlerts] = useState<GpsAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedVehicle, setSelectedVehicle] = useState<GpsVehicle | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'ONLINE' | 'OFFLINE' | 'MOVING'>('ALL');
  const [settingsConfigured, setSettingsConfigured] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [showVehicleList, setShowVehicleList] = useState(true);

  useEffect(() => {
    fetchAllData();
  }, []);

  const fetchAllData = async () => {
    try {
      setRefreshing(true);
      const [vehiclesRes, statsRes, alertsRes] = await Promise.all([
        api.get('/gps/tracked').catch(() => ({ data: [] })),
        api.get('/gps/stats').catch(() => ({ data: null })),
        api.get('/gps/alerts').catch(() => ({ data: [] })),
      ]);

      setVehicles(vehiclesRes.data || []);
      setStats(statsRes.data);
      setAlerts((alertsRes.data || []).slice(0, 5));

      try {
        const settingsRes = await api.get('/gps/settings');
        setSettingsConfigured(settingsRes.data?.hasCredentials || false);
      } catch {
        setSettingsConfigured(false);
      }
    } catch {
      showToast('Failed to load GPS data', 'error');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const filteredVehicles = useMemo(() => {
    let result = vehicles;
    if (filter !== 'ALL') {
      if (filter === 'ONLINE') result = result.filter(v => v.gpsStatus === 'ONLINE' || v.gpsStatus === 'MOVING');
      else if (filter === 'OFFLINE') result = result.filter(v => v.gpsStatus === 'OFFLINE');
      else if (filter === 'MOVING') result = result.filter(v => v.gpsStatus === 'MOVING');
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(v =>
        v.vehicleName.toLowerCase().includes(q) ||
        v.plate?.toLowerCase().includes(q) ||
        v.gpsDeviceId?.toLowerCase().includes(q) ||
        v.gpsImei?.toLowerCase().includes(q)
      );
    }
    return result;
  }, [vehicles, filter, searchQuery]);

  const mapCenter = useMemo<[number, number]>(() => {
    const withCoords = vehicles.filter(v => v.lastLatitude && v.lastLongitude);
    if (withCoords.length > 0) {
      const avgLat = withCoords.reduce((s, v) => s + v.lastLatitude, 0) / withCoords.length;
      const avgLng = withCoords.reduce((s, v) => s + v.lastLongitude, 0) / withCoords.length;
      return [avgLat, avgLng];
    }
    return [31.7917, -7.0926];
  }, [vehicles]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (!settingsConfigured) {
    return (
      <div className="space-y-6 animate-fade max-w-6xl mx-auto">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">GPS Live Tracking</h1>
          <p className="text-slate-500 font-normal text-sm mt-0.5">Real-time fleet monitoring and analytics</p>
        </div>
        <div className="card-premium flex flex-col items-center justify-center py-16 text-center">
          <div className="w-16 h-16 rounded-2xl bg-brand-50 flex items-center justify-center mb-4">
            <Satellite size={32} className="text-brand-500" />
          </div>
          <h3 className="text-lg font-bold text-[#1e293b] mb-2">GPS Not Configured</h3>
          <p className="text-sm text-slate-400 max-w-md mb-6">
            You haven't set up a GPS provider yet. Configure your tracking platform to see live vehicle data.
          </p>
          <a
            href="#/gps-settings"
            className="flex items-center gap-2 px-6 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all"
          >
            <ChevronRight size={16} />
            Go to GPS Settings
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-3 animate-fade">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">GPS Live Tracking</h1>
          <p className="text-slate-500 font-normal text-sm mt-0.5">Real-time fleet monitoring and analytics</p>
        </div>
        <button
          onClick={fetchAllData}
          disabled={refreshing}
          className="flex items-center gap-2 px-4 py-2 bg-white border border-[#e8e6e1] text-[#1e293b] rounded-xl text-sm font-medium hover:bg-[#f5f5f0] transition-all disabled:opacity-50"
        >
          <RefreshCw size={16} className={refreshing ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="grid grid-cols-3 md:grid-cols-6 gap-3">
          {[
            { label: 'Tracked', value: stats.totalTracked, icon: Car, color: 'text-brand-600', bg: 'bg-brand-50' },
            { label: 'Online', value: stats.online, icon: Wifi, color: 'text-emerald-600', bg: 'bg-emerald-50' },
            { label: 'Offline', value: stats.offline, icon: WifiOff, color: 'text-slate-500', bg: 'bg-slate-50' },
            { label: 'Moving', value: stats.moving, icon: Navigation, color: 'text-blue-600', bg: 'bg-blue-50' },
            { label: 'Stopped', value: stats.stopped, icon: Circle, color: 'text-amber-600', bg: 'bg-amber-50' },
            { label: 'Alerts', value: stats.activeAlerts, icon: AlertTriangle, color: 'text-rose-600', bg: 'bg-rose-50' },
          ].map((stat) => (
            <div key={stat.label} className="card-premium p-3">
              <div className={`w-8 h-8 rounded-lg ${stat.bg} flex items-center justify-center mb-2`}>
                <stat.icon size={16} className={stat.color} />
              </div>
              <p className="text-xl font-bold text-[#1e293b]">{stat.value}</p>
              <p className="text-xs text-slate-400 font-medium mt-0.5">{stat.label}</p>
            </div>
          ))}
        </div>
      )}

      {/* Main Content: Map + Vehicle List */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4" style={{ minHeight: '520px', height: 'calc(100vh - 280px)' }}>
        {/* Map */}
        <div className="lg:col-span-3 relative rounded-2xl overflow-hidden border border-[#e8e6e1] bg-white shadow-sm">
          <MapContainer
            center={mapCenter}
            zoom={vehicles.length > 0 ? 12 : 5}
            style={{ height: '100%', width: '100%' }}
            scrollWheelZoom={true}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapController vehicle={selectedVehicle} />
            {filteredVehicles.map((vehicle) => {
              if (!vehicle.lastLatitude || !vehicle.lastLongitude) return null;
              const status = STATUS_STYLES[vehicle.gpsStatus] || STATUS_STYLES.OFFLINE;
              return (
                <Marker
                  key={vehicle.id}
                  position={[vehicle.lastLatitude, vehicle.lastLongitude]}
                  icon={createMarkerIcon(status.markerColor)}
                  eventHandlers={{
                    click: () => setSelectedVehicle(vehicle),
                  }}
                >
                  <Popup className="vehicle-popup" closeButton={false}>
                    <div className="min-w-[260px] p-1">
                      {/* Header */}
                      <div className="flex items-center gap-3 mb-3">
                        <div className={`w-10 h-10 rounded-xl ${status.bg} flex items-center justify-center shrink-0`}>
                          <Car size={20} className={status.color} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <h3 className="text-sm font-bold text-[#1e293b] truncate">{vehicle.vehicleName}</h3>
                          <p className="text-xs text-slate-400">{vehicle.plate || 'No plate'}</p>
                        </div>
                        <div className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${status.bg} ${status.color}`}>
                          {status.label}
                        </div>
                      </div>

                      {/* Info Grid */}
                      <div className="grid grid-cols-2 gap-2 mb-3">
                        <div className="bg-[#f5f5f0] rounded-lg p-2">
                          <div className="flex items-center gap-1.5 text-slate-400 mb-0.5">
                            <Gauge size={10} />
                            <span className="text-[10px] font-medium">Speed</span>
                          </div>
                          <p className="text-sm font-bold text-[#1e293b]">{vehicle.lastSpeed ? `${Math.round(vehicle.lastSpeed)} km/h` : '—'}</p>
                        </div>
                        <div className="bg-[#f5f5f0] rounded-lg p-2">
                          <div className="flex items-center gap-1.5 text-slate-400 mb-0.5">
                            <Clock size={10} />
                            <span className="text-[10px] font-medium">Updated</span>
                          </div>
                          <p className="text-sm font-bold text-[#1e293b]">
                            {vehicle.lastGpsUpdate
                              ? new Date(vehicle.lastGpsUpdate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                              : '—'}
                          </p>
                        </div>
                      </div>

                      {/* Coordinates & Details */}
                      <div className="space-y-1.5 mb-3">
                        <div className="flex items-center gap-2 text-xs">
                          <MapPin size={12} className="text-slate-400 shrink-0" />
                          <span className="text-slate-500 font-mono">{vehicle.lastLatitude.toFixed(6)}, {vehicle.lastLongitude.toFixed(6)}</span>
                        </div>
                        <div className="flex items-center gap-2 text-xs">
                          <Settings2 size={12} className="text-slate-400 shrink-0" />
                          <span className="text-slate-500 font-mono">{vehicle.gpsDeviceId || '—'}</span>
                        </div>
                        {vehicle.gpsImei && (
                          <div className="flex items-center gap-2 text-xs">
                            <Route size={12} className="text-slate-400 shrink-0" />
                            <span className="text-slate-500 font-mono">{vehicle.gpsImei}</span>
                          </div>
                        )}
                      </div>

                      {/* Actions */}
                      <div className="flex gap-2">
                        <button
                          onClick={() => setSelectedVehicle(vehicle)}
                          className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-brand-500 text-white rounded-lg text-xs font-medium hover:bg-brand-600 transition-all"
                        >
                          <Eye size={12} />
                          Details
                        </button>
                        <button
                          onClick={() => {
                            setSelectedVehicle(vehicle);
                          }}
                          className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-[#f5f5f0] text-[#1e293b] rounded-lg text-xs font-medium hover:bg-[#e8e6e1] transition-all"
                        >
                          <Route size={12} />
                          History
                        </button>
                      </div>
                    </div>
                  </Popup>
                </Marker>
              );
            })}
          </MapContainer>

          {/* Toggle vehicle list button (mobile) */}
          <button
            onClick={() => setShowVehicleList(!showVehicleList)}
            className="lg:hidden absolute top-3 left-3 z-[1000] flex items-center gap-2 px-3 py-2 bg-white rounded-xl shadow-md text-sm font-medium text-[#1e293b] border border-[#e8e6e1]"
          >
            {showVehicleList ? <MapIcon size={14} /> : <List size={14} />}
            {showVehicleList ? 'Map' : 'List'}
          </button>
        </div>

        {/* Vehicle List Sidebar */}
        <div className={`lg:col-span-1 space-y-3 overflow-hidden flex flex-col ${showVehicleList ? '' : 'hidden lg:flex'}`} style={{ minHeight: 0 }}>
          {/* Search */}
          <div className="relative shrink-0">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search vehicles..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-8 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-sm text-[#1e293b] placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-200"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Filters */}
          <div className="flex gap-1.5 shrink-0">
            {([
              { key: 'ALL', label: 'All', count: stats?.totalTracked || 0 },
              { key: 'ONLINE', label: 'On', count: stats?.online || 0 },
              { key: 'OFFLINE', label: 'Off', count: stats?.offline || 0 },
              { key: 'MOVING', label: 'Move', count: stats?.moving || 0 },
            ] as const).map((f) => (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium transition-all whitespace-nowrap ${
                  filter === f.key
                    ? 'bg-brand-500 text-white shadow-sm'
                    : 'bg-white text-slate-500 hover:text-[#1e293b] hover:bg-[#f5f5f0] border border-[#e8e6e1]'
                }`}
              >
                {f.label} {f.count}
              </button>
            ))}
          </div>

          {/* Vehicle Cards List */}
          <div className="flex-1 overflow-y-auto space-y-2 pr-1 min-h-0">
            {filteredVehicles.length === 0 ? (
              <div className="card-premium py-10 text-center">
                <Car size={32} className="text-slate-300 mx-auto mb-3" />
                <p className="text-sm text-slate-400">No vehicles</p>
                <p className="text-xs text-slate-300 mt-1">Sync devices from GPS settings</p>
              </div>
            ) : (
              filteredVehicles.map((vehicle) => {
                const status = STATUS_STYLES[vehicle.gpsStatus] || STATUS_STYLES.OFFLINE;
                const StatusIcon = status.icon;
                const isSelected = selectedVehicle?.id === vehicle.id;
                return (
                  <div
                    key={vehicle.id}
                    onClick={() => setSelectedVehicle(vehicle)}
                    className={`card-premium p-3 cursor-pointer transition-all hover:shadow-md ${
                      isSelected ? 'ring-2 ring-brand-200 bg-brand-50/30' : ''
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-9 h-9 rounded-lg ${status.bg} flex items-center justify-center shrink-0`}>
                        <StatusIcon size={16} className={status.color} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold text-[#1e293b] truncate">{vehicle.vehicleName}</p>
                        <p className="text-[11px] text-slate-400">{vehicle.plate || 'No plate'} • {vehicle.category || '—'}</p>
                      </div>
                      <div className="text-right shrink-0">
                        {vehicle.lastSpeed !== null && vehicle.lastSpeed > 0 && (
                          <p className="text-sm font-bold text-[#1e293b]">{Math.round(vehicle.lastSpeed)} <span className="text-[10px] font-normal text-slate-400">km/h</span></p>
                        )}
                        <p className={`text-[10px] font-semibold ${status.color}`}>{status.label}</p>
                      </div>
                    </div>

                    {vehicle.lastLatitude && vehicle.lastLongitude && (
                      <div className="mt-2 pt-2 border-t border-[#e8e6e1]/60 flex items-center gap-3 text-[11px] text-slate-400">
                        <div className="flex items-center gap-1">
                          <MapPin size={10} />
                          <span>{vehicle.lastLatitude.toFixed(4)}, {vehicle.lastLongitude.toFixed(4)}</span>
                        </div>
                        {vehicle.lastGpsUpdate && (
                          <div className="flex items-center gap-1">
                            <Clock size={10} />
                            <span>{new Date(vehicle.lastGpsUpdate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {/* Selected Vehicle Detail (mini panel in sidebar) */}
          {selectedVehicle && (
            <div className="card-premium p-3 space-y-2 shrink-0 border-t-2 border-brand-200">
              <div className="flex items-center gap-2 pb-2 border-b border-[#e8e6e1]/60">
                <div className="w-8 h-8 rounded-lg bg-brand-50 flex items-center justify-center">
                  <Car size={16} className="text-brand-500" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-bold text-[#1e293b] truncate">{selectedVehicle.vehicleName}</h3>
                  <p className="text-[11px] text-slate-400">{selectedVehicle.plate || 'No plate'}</p>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-[#f5f5f0] rounded-lg p-2">
                  <p className="text-slate-400 text-[10px]">Status</p>
                  <p className="font-semibold text-[#1e293b]">{selectedVehicle.gpsStatus}</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-lg p-2">
                  <p className="text-slate-400 text-[10px]">Speed</p>
                  <p className="font-semibold text-[#1e293b]">{selectedVehicle.lastSpeed ? `${Math.round(selectedVehicle.lastSpeed)} km/h` : '—'}</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-lg p-2">
                  <p className="text-slate-400 text-[10px]">Lat</p>
                  <p className="font-mono text-[#1e293b]">{selectedVehicle.lastLatitude?.toFixed(5) || '—'}</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-lg p-2">
                  <p className="text-slate-400 text-[10px]">Lng</p>
                  <p className="font-mono text-[#1e293b]">{selectedVehicle.lastLongitude?.toFixed(5) || '—'}</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
