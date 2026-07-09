import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { usePermissions } from '../context/PermissionContext';
import api from '../api/axios';
import {
  Bell, AlertTriangle, Wifi, WifiOff, Navigation,
  TriangleAlert, CheckCircle, Loader2, RefreshCw,
  Check, MapPin, ChevronDown, Car, AlertCircle,
  Zap, BellOff,
} from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';

// ── Types ─────────────────────────────────────────────────────────────────────

interface GpsAlert {
  id: number;
  alertType: string;
  message: string;
  severity: string;
  read: boolean;
  vehicleId?: number;
  vehicleName?: string;
  latitude?: number;
  longitude?: number;
  speed?: number;
  createdAt: string;
}

// ── Alert display config ──────────────────────────────────────────────────────

const ALERT_CONFIG: Record<string, { label: string; Icon: any; color: string; bg: string; border: string }> = {
  GEOFENCE_EXIT:          { label: 'Out of Zone',      Icon: TriangleAlert,   color: 'text-rose-600',    bg: 'bg-rose-50',     border: 'border-rose-200' },
  GEOFENCE_ENTER:         { label: 'Back in Zone',     Icon: MapPin,          color: 'text-emerald-600', bg: 'bg-emerald-50',  border: 'border-emerald-200' },
  OFFLINE:                { label: 'Device Offline',   Icon: WifiOff,         color: 'text-slate-500',   bg: 'bg-slate-50',    border: 'border-slate-200' },
  VEHICLE_STARTED_MOVING: { label: 'Started Moving',  Icon: Navigation,      color: 'text-blue-600',    bg: 'bg-blue-50',     border: 'border-blue-200' },
  OVERSPEED:              { label: 'Speed Alert',      Icon: Zap,             color: 'text-amber-600',   bg: 'bg-amber-50',    border: 'border-amber-200' },
  DEVICE_DISCONNECT:      { label: 'Disconnected',     Icon: AlertCircle,     color: 'text-orange-600',  bg: 'bg-orange-50',   border: 'border-orange-200' },
  GPS_SYNC_ERROR:         { label: 'Sync Error',       Icon: AlertTriangle,   color: 'text-rose-600',    bg: 'bg-rose-50',     border: 'border-rose-200' },
  LOW_BATTERY:            { label: 'Low Battery',      Icon: AlertCircle,     color: 'text-amber-600',   bg: 'bg-amber-50',    border: 'border-amber-200' },
  TOWING:                 { label: 'Towing Detected',  Icon: Car,             color: 'text-rose-600',    bg: 'bg-rose-50',     border: 'border-rose-200' },
  HARD_BRAKING:           { label: 'Hard Braking',     Icon: AlertTriangle,   color: 'text-orange-600',  bg: 'bg-orange-50',   border: 'border-orange-200' },
  HARD_ACCELERATION:      { label: 'Hard Acceleration',Icon: Zap,             color: 'text-orange-600',  bg: 'bg-orange-50',   border: 'border-orange-200' },
  IDLING_TOO_LONG:        { label: 'Idling Too Long',  Icon: AlertCircle,     color: 'text-amber-500',   bg: 'bg-amber-50',    border: 'border-amber-200' },
};

const SEVERITY_BADGE: Record<string, string> = {
  CRITICAL: 'bg-rose-100 text-rose-700 border border-rose-200',
  HIGH:     'bg-orange-100 text-orange-700 border border-orange-200',
  MEDIUM:   'bg-amber-100 text-amber-700 border border-amber-200',
  LOW:      'bg-blue-100 text-blue-600 border border-blue-200',
};

function formatRelative(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'Just now';
  if (min < 60) return `${min}m ago`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h ago`;
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function GpsAlerts() {
  const { t } = useTranslation();
  const { hasPermission, loading: permLoading } = usePermissions();
  const canViewGps = hasPermission('GPS_ACCESS');
  const [alerts, setAlerts] = useState<GpsAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [typeFilter, setTypeFilter] = useState<string>('ALL');
  const [readFilter, setReadFilter] = useState<'ALL' | 'UNREAD'>('ALL');
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const fetchAlerts = useCallback(async (manual = false) => {
    if (manual) setRefreshing(true);
    try {
      const { data } = await api.get('/gps/alerts');
      const list: GpsAlert[] = Array.isArray(data) ? data : (data?.data ?? []);
      setAlerts(list);
    } catch {
      // show empty state
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (permLoading) return;
    if (!canViewGps) {
      if (import.meta.env.DEV) console.log('[PERMISSION_FETCH_GUARD] endpoint=/api/gps/alerts requiredPermission=GPS_ACCESS allowed=false component=GpsAlerts');
      setLoading(false);
      return;
    }
    fetchAlerts();
  }, [canViewGps, permLoading, fetchAlerts]);

  const markRead = async (id: number) => {
    try {
      await api.patch(`/gps/alerts/${id}/read`);
      setAlerts(prev => prev.map(a => a.id === id ? { ...a, read: true } : a));
    } catch {/* ignore */}
  };

  const markAllRead = async () => {
    setMarkingAll(true);
    try {
      await api.patch('/gps/alerts/read-all');
      setAlerts(prev => prev.map(a => ({ ...a, read: true })));
    } catch {/* ignore */}
    finally { setMarkingAll(false); }
  };

  // Distinct alert types found in data
  const alertTypes = ['ALL', ...Array.from(new Set(alerts.map(a => a.alertType)))];

  const filtered = alerts.filter(a => {
    if (typeFilter !== 'ALL' && a.alertType !== typeFilter) return false;
    if (readFilter === 'UNREAD' && a.read) return false;
    return true;
  });

  const unreadCount = alerts.filter(a => !a.read).length;

  // ── Loading ─────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  return (
    <div className="space-y-4 animate-fade">
      {/* Header */}
      <GlassPageHeader
        title={t('gps.alerts', 'GPS Alerts')}
        subtitle={t('gps.alertsSubtitle', 'Fleet events and geofence notifications')}
        icon={Bell}
        actions={
          <div className="flex items-center gap-2">
            {unreadCount > 0 && (
              <button
                onClick={markAllRead}
                disabled={markingAll}
                className="surface-control flex items-center gap-2 h-10 px-3 text-sm font-medium disabled:opacity-50"
              >
                {markingAll ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
                <span className="hidden sm:inline">Mark all read</span>
                <span className="sm:hidden">All read</span>
              </button>
            )}
            <button
              onClick={() => fetchAlerts(true)}
              disabled={refreshing}
              className="surface-control flex items-center gap-2 h-10 px-4 text-sm font-medium disabled:opacity-50"
            >
              <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        }
      />

      {/* KPI strip */}
      <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
        {[
          { label: 'Total',    value: alerts.length,  Icon: Bell,         color: 'text-brand-600',   bg: 'bg-brand-50' },
          { label: 'Unread',   value: unreadCount,    Icon: AlertTriangle,color: 'text-rose-600',    bg: 'bg-rose-50' },
          { label: 'Today',    value: alerts.filter(a => new Date(a.createdAt) > new Date(Date.now() - 86400000)).length,
            Icon: Wifi, color: 'text-blue-600', bg: 'bg-blue-50' },
          { label: 'Critical', value: alerts.filter(a => a.severity === 'CRITICAL' || a.severity === 'HIGH').length,
            Icon: AlertCircle, color: 'text-orange-600', bg: 'bg-orange-50' },
        ].map(({ label, value, Icon, color, bg }) => (
          <div key={label} className="metric-surface">
            <div className={`w-7 h-7 rounded-lg ${bg} flex items-center justify-center mb-2`}>
              <Icon size={14} className={color} />
            </div>
            <p className="text-lg font-bold text-[#1e293b] leading-none">{value}</p>
            <p className="text-[10px] text-slate-400 font-medium mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="data-surface p-3">
        <div className="flex flex-wrap gap-2 items-center">
          {/* Read filter */}
          <div className="flex bg-slate-100 rounded-lg p-0.5 shrink-0">
            {(['ALL', 'UNREAD'] as const).map(v => (
              <button
                key={v}
                onClick={() => setReadFilter(v)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  readFilter === v ? 'bg-white text-[#1e293b] shadow-sm' : 'text-slate-500'
                }`}
              >
                {v === 'ALL' ? 'All' : `Unread${unreadCount ? ` (${unreadCount})` : ''}`}
              </button>
            ))}
          </div>

          {/* Type filter */}
          <div className="flex flex-wrap gap-1 flex-1">
            {alertTypes.map(type => {
              const cfg = ALERT_CONFIG[type];
              return (
                <button
                  key={type}
                  onClick={() => setTypeFilter(type)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition-all ${
                    typeFilter === type
                      ? 'bg-brand-500 text-white'
                      : 'bg-white border border-[var(--border-subtle)] text-slate-500 hover:text-[#1e293b]'
                  }`}
                >
                  {type === 'ALL' ? 'All types' : (cfg?.label ?? type)}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Alert list */}
      {filtered.length === 0 ? (
        <div className="data-surface flex flex-col items-center justify-center py-20 text-center">
          <div className="w-16 h-16 rounded-2xl bg-emerald-50 flex items-center justify-center mb-4">
            <BellOff size={32} className="text-emerald-400" />
          </div>
          <h3 className="text-base font-bold text-[#1e293b] mb-1">
            {alerts.length === 0 ? 'No GPS alerts yet' : 'No alerts match this filter'}
          </h3>
          <p className="text-sm text-slate-400 max-w-sm">
            {alerts.length === 0
              ? 'Alerts will appear here when your GPS provider detects important fleet events.'
              : 'Try a different filter to see more alerts.'}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map(alert => {
            const cfg = ALERT_CONFIG[alert.alertType] ?? {
              label: alert.alertType,
              Icon: AlertTriangle,
              color: 'text-slate-500',
              bg: 'bg-slate-50',
              border: 'border-slate-200',
            };
            const AlertIcon = cfg.Icon;
            const isExpanded = expandedId === alert.id;
            const severityClass = SEVERITY_BADGE[alert.severity] ?? SEVERITY_BADGE.LOW;

            return (
              <div
                key={alert.id}
                className={`data-surface transition-all ${!alert.read ? 'border-l-4 border-l-brand-400' : ''}`}
              >
                <div
                  className="flex items-start gap-3 p-4 cursor-pointer"
                  onClick={() => {
                    setExpandedId(isExpanded ? null : alert.id);
                    if (!alert.read) markRead(alert.id);
                  }}
                >
                  {/* Icon */}
                  <div className={`w-9 h-9 rounded-xl ${cfg.bg} ${cfg.border} border flex items-center justify-center shrink-0 mt-0.5`}>
                    <AlertIcon size={16} className={cfg.color} />
                  </div>

                  {/* Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-0.5">
                      <p className="text-sm font-semibold text-[#1e293b]">{cfg.label}</p>
                      {!alert.read && (
                        <span className="w-2 h-2 rounded-full bg-brand-500 shrink-0" />
                      )}
                      <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${severityClass}`}>
                        {alert.severity}
                      </span>
                    </div>
                    <p className="text-sm text-slate-500 leading-snug">{alert.message}</p>
                    <div className="flex items-center gap-2 mt-1.5 text-[11px] text-slate-400">
                      {alert.vehicleName && (
                        <>
                          <Car size={10} />
                          <span>{alert.vehicleName}</span>
                          <span>·</span>
                        </>
                      )}
                      <span title={formatDate(alert.createdAt)}>{formatRelative(alert.createdAt)}</span>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 shrink-0">
                    {!alert.read && (
                      <button
                        onClick={e => { e.stopPropagation(); markRead(alert.id); }}
                        title="Mark as read"
                        className="w-7 h-7 rounded-lg bg-brand-50 flex items-center justify-center hover:bg-brand-100 transition-all"
                      >
                        <CheckCircle size={13} className="text-brand-500" />
                      </button>
                    )}
                    <ChevronDown
                      size={14}
                      className={`text-slate-300 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                    />
                  </div>
                </div>

                {/* Expanded details */}
                {isExpanded && (
                  <div className="px-4 pb-4 border-t border-[var(--border-subtle)] pt-3">
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                      {alert.latitude != null && (
                        <div className="bg-slate-50 rounded-lg p-2">
                          <p className="text-[10px] text-slate-400 mb-0.5">Latitude</p>
                          <p className="text-xs font-semibold font-mono">{alert.latitude.toFixed(6)}</p>
                        </div>
                      )}
                      {alert.longitude != null && (
                        <div className="bg-slate-50 rounded-lg p-2">
                          <p className="text-[10px] text-slate-400 mb-0.5">Longitude</p>
                          <p className="text-xs font-semibold font-mono">{alert.longitude.toFixed(6)}</p>
                        </div>
                      )}
                      {alert.speed != null && (
                        <div className="bg-slate-50 rounded-lg p-2">
                          <p className="text-[10px] text-slate-400 mb-0.5">Speed</p>
                          <p className="text-xs font-semibold">{Math.round(alert.speed)} km/h</p>
                        </div>
                      )}
                      <div className="bg-slate-50 rounded-lg p-2">
                        <p className="text-[10px] text-slate-400 mb-0.5">Time</p>
                        <p className="text-xs font-semibold">{formatDate(alert.createdAt)}</p>
                      </div>
                    </div>

                    {alert.latitude != null && alert.longitude != null && (
                      <a
                        href={`https://maps.google.com/?q=${alert.latitude},${alert.longitude}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="mt-2 inline-flex items-center gap-1.5 text-xs text-brand-600 hover:text-brand-700 font-medium"
                      >
                        <MapPin size={11} /> View location on Google Maps
                      </a>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
