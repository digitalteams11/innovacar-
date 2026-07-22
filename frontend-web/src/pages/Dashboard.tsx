import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line,
} from 'recharts';
import SafeChartContainer from '../components/shared/SafeChartContainer';
import {
  Car, Calendar, CreditCard, Users, TrendingUp, TrendingDown,
  ChevronLeft, ChevronRight, FileText, DollarSign, BarChart3,
  Wrench, CheckCircle, Clock, Activity, Gauge,
  ArrowRight, MapPin, Bell, AlertTriangle, Info, XCircle,
  LayoutDashboard, Settings2, Zap, MapPinOff, CheckSquare,
  RefreshCw, ChevronDown,
} from 'lucide-react';
import api from '../api/axios';
import ApiErrorState from '../components/ApiErrorState';
import PremiumLoader from '../components/PremiumLoader';
import { useAuth } from '../context/AuthContext';
import AiAssistantButton from '../components/shared/AiAssistantButton';
import DashboardVehicleCard, { type VehicleCardData } from '../components/shared/DashboardVehicleCard';
import ContractReturnModal from '../components/shared/ContractReturnModal';
import DashboardCustomizeModal from '../components/dashboard/DashboardCustomizeModal';
import { useDashboardLayout } from '../hooks/useDashboardLayout';
import { formatMonthYear, formatShortDate, getWeekdayLabels, resolveLocale } from '../utils/dateFormat';
import { translateReservationStatus, translateFleetHealthStatus } from '../utils/statusLabels';
import type {
  DashboardStats, DashboardVehicle, DashboardReservation,
  ClientItem, MaintenanceItem, HealthScore,
} from '../types/dashboard';

/* ─── constants ──────────────────────────────────────────────────── */
const STATUS_COLORS: Record<string, string> = {
  CONFIRMED: '#10b981',
  PENDING:   '#f59e0b',
  CANCELLED: '#ef4444',
  ACTIVE:    '#3b82f6',
  COMPLETED: '#8b5cf6',
};
const CALENDAR_DOT_COLORS: Record<string, string> = {
  CONFIRMED: '#10b981',
  ACTIVE:    '#3b82f6',
  RENTED:    '#3b82f6',
  PENDING:   '#f59e0b',
  CANCELLED: '#ef4444',
};

/* ─── helpers ────────────────────────────────────────────────────── */
const unwrapApiData = <T,>(payload: unknown, fallback: T): T => {
  const v =
    payload != null &&
    typeof payload === 'object' &&
    'data' in payload &&
    payload.data != null &&
    typeof payload.data === 'object' &&
    !Array.isArray(payload.data)
      ? (payload as { data: T }).data
      : (payload as T);
  return (v ?? fallback) as T;
};
const unwrapApiArray = <T,>(payload: unknown): T[] => {
  if (Array.isArray(payload)) return payload;
  if (payload != null && typeof payload === 'object') {
    const p = payload as Record<string, unknown>;
    if (Array.isArray(p.data))     return p.data as T[];
    if (Array.isArray(p.vehicles)) return p.vehicles as T[];
    if (Array.isArray((p.data as Record<string, unknown>)?.vehicles))
      return (p.data as Record<string, unknown>).vehicles as T[];
  }
  return [];
};
const fmt = (n: number | undefined) => (n ?? 0).toLocaleString('fr-MA');

/* ─── Shimmer ─────────────────────────────────────────────────────── */
function Shimmer({ className = '' }: { className?: string }) {
  return <div className={`shimmer rounded-lg ${className}`} />;
}

/* ─── DCard ──────────────────────────────────────────────────────── */
function DCard({
  children, className = '', delay = 0, onClick, noPad = false,
}: {
  children: React.ReactNode; className?: string; delay?: number;
  onClick?: () => void; noPad?: boolean;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay: delay / 1000, ease: [0.16, 1, 0.3, 1] }}
      onClick={onClick}
      className={`rounded-2xl border overflow-hidden ${noPad ? '' : 'p-5'} ${onClick ? 'cursor-pointer' : ''} ${className}`}
      style={{
        backgroundColor: 'var(--bg-card)',
        borderColor: 'var(--border-subtle)',
        boxShadow: 'var(--shadow-card)',
        backdropFilter: 'blur(20px)',
      }}
    >
      {children}
    </motion.div>
  );
}

/* ─── TopStat ────────────────────────────────────────────────────── */
function TopStat({
  title, value, icon: Icon, bgClass, iconClass, trend, trendText,
  loading = false, delay = 0, onClick, sparkData,
}: {
  title: string; value: string | number; icon: React.ElementType; bgClass: string;
  iconClass: string; trend?: 'up' | 'down'; trendText?: string;
  loading?: boolean; delay?: number; onClick?: () => void; sparkData?: number[];
}) {
  const [displayed, setDisplayed] = useState(0);
  const num = useMemo(() => {
    const n = parseFloat(String(value).replace(/[^0-9.]/g, ''));
    return isNaN(n) ? null : n;
  }, [value]);

  useEffect(() => {
    if (num === null) return;
    let raf: number;
    const t = setTimeout(() => {
      const start = performance.now();
      const dur = 900;
      const tick = (now: number) => {
        const p = Math.min((now - start) / dur, 1);
        setDisplayed(num * (1 - Math.pow(1 - p, 3)));
        if (p < 1) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
    }, delay + 100);
    return () => { clearTimeout(t); cancelAnimationFrame(raf); };
  }, [num, delay]);

  if (loading) {
    return (
      <div className="rounded-2xl border p-5" style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)', backdropFilter: 'blur(20px)' }}>
        <div className="flex justify-between items-start mb-3">
          <div className="space-y-2 flex-1">
            <Shimmer className="h-3 w-24" />
            <Shimmer className="h-8 w-20" />
            <Shimmer className="h-3 w-28" />
          </div>
          <Shimmer className="w-12 h-12 rounded-xl" />
        </div>
      </div>
    );
  }

  const trendColor = trend === 'up' ? '#10b981' : '#ef4444';
  const TI = trend === 'up' ? TrendingUp : TrendingDown;
  const points = sparkData ?? [0, 0, 0, 0, 0];

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay: delay / 1000, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -2, transition: { duration: 0.15 } }}
      onClick={onClick}
      className={`rounded-2xl border p-5 group ${onClick ? 'cursor-pointer' : ''}`}
      style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)', boxShadow: 'var(--shadow-card)', backdropFilter: 'blur(20px)' }}
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <p className="text-xs font-semibold tracking-wide" style={{ color: 'var(--text-muted)' }}>{title}</p>
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-transform duration-300 group-hover:scale-110 ${bgClass}`}>
          <Icon size={20} className={iconClass} />
        </div>
      </div>
      <div className="flex items-end justify-between gap-2">
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight mb-1.5" style={{ color: 'var(--text-primary)' }}>
            {num !== null ? displayed.toLocaleString('en-US', { maximumFractionDigits: 0 }) : value}
          </h2>
          {trendText && trend && (
            <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full"
              style={{ backgroundColor: `${trendColor}18`, color: trendColor }}>
              <TI size={10} />
              {trendText}
            </span>
          )}
        </div>
        {points.some(v => v > 0) && (
          <div className="w-20 h-10 shrink-0">
            <SafeChartContainer minHeight={40} style={{ height: 40 }}>
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points.map((v, i) => ({ v, i }))}>
                  <Line type="monotone" dataKey="v" stroke={trendColor} strokeWidth={1.5} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          </div>
        )}
      </div>
    </motion.div>
  );
}

/* ─── VStatusBadge ───────────────────────────────────────────────── */
function VStatusBadge({ status }: { status: string }) {
  const { t } = useTranslation();
  const cfg: Record<string, { label: string; cls: string }> = {
    AVAILABLE:      { label: t('common.available'),   cls: 'bg-emerald-500/10 text-emerald-500' },
    RENTED:         { label: t('common.rented'),      cls: 'bg-blue-500/10 text-blue-500' },
    RESERVED:       { label: t('common.reserved'),    cls: 'bg-amber-500/10 text-amber-500' },
    MAINTENANCE:    { label: t('common.maintenance'), cls: 'bg-red-500/10 text-red-500' },
    IN_MAINTENANCE: { label: t('common.maintenance'), cls: 'bg-red-500/10 text-red-500' },
    OUT_OF_SERVICE: { label: t('common.outOfService'),cls: 'bg-gray-500/10 text-gray-400' },
    SOLD:           { label: t('common.sold'),        cls: 'bg-gray-500/10 text-gray-400' },
    ARCHIVED:       { label: t('common.archived'),    cls: 'bg-gray-500/10 text-gray-400' },
  };
  const c = cfg[status] ?? { label: status, cls: 'bg-gray-500/10 text-gray-400' };
  return <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${c.cls}`}>{c.label}</span>;
}

/* ─── AlertIcon ──────────────────────────────────────────────────── */
function AlertIcon({ severity }: { severity: string }) {
  if (severity === 'danger')  return <XCircle   size={15} className="text-rose-500 shrink-0" />;
  if (severity === 'warning') return <AlertTriangle size={15} className="text-amber-500 shrink-0" />;
  return <Info size={15} className="text-blue-500 shrink-0" />;
}

/* ─── Avatar ─────────────────────────────────────────────────────── */
function Avatar({ name, gender }: { name: string; gender?: string }) {
  const initials = name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase() || '?';
  const g = (gender || '').toLowerCase();
  const bg = g === 'male' || g === 'm'
    ? 'from-blue-500 to-blue-600'
    : g === 'female' || g === 'f'
    ? 'from-pink-500 to-rose-500'
    : 'from-violet-500 to-purple-600';
  return (
    <div className={`w-10 h-10 rounded-full bg-gradient-to-br ${bg} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
      {initials}
    </div>
  );
}

/* ─── ChartEmpty ─────────────────────────────────────────────────── */
function ChartEmpty({ h = 'h-[200px]' }: { h?: string }) {
  const { t } = useTranslation();
  return (
    <div className={`${h} flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed`}
      style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-muted)' }}>
      <BarChart3 size={22} className="opacity-40" />
      <span className="text-xs">{t('dashboard.noChartData', 'No data yet')}</span>
    </div>
  );
}

/* ─── StatusLegend ───────────────────────────────────────────────── */
function StatusLegend({ data, total }: { data: { name: string; count: number; color: string }[]; total: number }) {
  return (
    <div className="space-y-2 mt-3">
      {data.map(item => (
        <div key={item.name} className="flex items-center justify-between text-[11px]">
          <div className="flex items-center gap-2">
            <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
            <span style={{ color: 'var(--text-secondary)' }}>{translateReservationStatus(item.name)}</span>
          </div>
          <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
            {item.count}&nbsp;
            <span style={{ color: 'var(--text-muted)' }}>
              ({total > 0 ? Math.round(item.count / total * 100) : 0}%)
            </span>
          </span>
        </div>
      ))}
    </div>
  );
}

/* ─── EnhancedCalendar ───────────────────────────────────────────── */
function EnhancedCalendar({ reservations }: { reservations: DashboardReservation[] }) {
  const { t } = useTranslation();
  const [view, setView] = useState(new Date());
  const [selectedDay, setSelectedDay] = useState<number | null>(null);
  const today = new Date();
  const year = view.getFullYear();
  const month = view.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const monthLabel = formatMonthYear(view);
  const weekdayLetters = useMemo(() => getWeekdayLabels('narrow'), [t]);

  // Map date → list of reservation statuses for that day
  const dayMap = useMemo(() => {
    const map: Record<number, string[]> = {};
    reservations.forEach(r => {
      [r.dateStart, r.dateEnd].forEach((ds, idx) => {
        if (!ds) return;
        const d = new Date(ds);
        if (d.getFullYear() !== year || d.getMonth() !== month) return;
        const day = d.getDate();
        const statusKey = idx === 0
          ? (r.status ?? 'PENDING')
          : 'RETURN';
        if (!map[day]) map[day] = [];
        map[day].push(statusKey);
      });
    });
    return map;
  }, [reservations, year, month]);

  // Reservations for the selected day
  const dayAgenda = useMemo(() => {
    if (!selectedDay) return [];
    return reservations.filter(r => {
      const start = r.dateStart ? new Date(r.dateStart) : null;
      const end   = r.dateEnd   ? new Date(r.dateEnd)   : null;
      const match = (d: Date | null) =>
        d && d.getFullYear() === year && d.getMonth() === month && d.getDate() === selectedDay;
      return match(start) || match(end);
    });
  }, [selectedDay, reservations, year, month]);

  const cells: (number | null)[] = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let i = 1; i <= daysInMonth; i++) cells.push(i);

  const getDotColors = (statuses: string[]) => {
    const unique = [...new Set(statuses.slice(0, 3))];
    return unique.map(s =>
      s === 'RETURN' ? '#f97316'
      : CALENDAR_DOT_COLORS[s] ?? '#94a3b8'
    );
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
          {t('dashboard.reservationsCalendar')}
        </h3>
        <div className="flex items-center gap-0.5">
          <button
            onClick={() => { setView(new Date(year, month - 1, 1)); setSelectedDay(null); }}
            className="w-6 h-6 rounded-md flex items-center justify-center hover:bg-[var(--bg-hover)] transition-colors"
            style={{ color: 'var(--text-muted)' }}
          >
            <ChevronLeft size={13} />
          </button>
          <span className="text-[11px] font-semibold px-2 min-w-[110px] text-center" style={{ color: 'var(--text-secondary)' }}>
            {monthLabel}
          </span>
          <button
            onClick={() => { setView(new Date(year, month + 1, 1)); setSelectedDay(null); }}
            className="w-6 h-6 rounded-md flex items-center justify-center hover:bg-[var(--bg-hover)] transition-colors"
            style={{ color: 'var(--text-muted)' }}
          >
            <ChevronRight size={13} />
          </button>
          <button
            onClick={() => { setView(new Date()); setSelectedDay(null); }}
            className="ms-1 text-[9px] font-bold px-2 py-0.5 rounded-full"
            style={{ backgroundColor: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)', opacity: 0.85 }}
          >
            {t('calendar.today')}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-7">
        {weekdayLetters.map((d, i) => (
          <div key={i} className="text-center text-[10px] font-bold uppercase pb-2" style={{ color: 'var(--text-muted)' }}>
            {d}
          </div>
        ))}
        {cells.map((day, i) => {
          if (!day) return <div key={`e-${i}`} />;
          const isToday    = day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
          const isSelected = day === selectedDay;
          const statuses   = dayMap[day] ?? [];
          const dotColors  = getDotColors(statuses);
          const hasEvents  = statuses.length > 0;
          const manyEvents = statuses.length > 3;
          return (
            <div key={day} className="flex flex-col items-center py-0.5">
              <button
                onClick={() => setSelectedDay(prev => prev === day ? null : day)}
                className={`relative w-7 h-7 flex items-center justify-center rounded-full text-[11px] font-medium transition-colors
                  ${isToday ? 'text-white' : isSelected ? 'text-white' : 'hover:bg-[var(--bg-hover)]'}`}
                style={
                  isToday
                    ? { backgroundColor: 'var(--brand-primary)' }
                    : isSelected
                    ? { backgroundColor: 'var(--brand-primary)', opacity: 0.7 }
                    : { color: 'var(--text-secondary)' }
                }
              >
                {day}
                {/* Count bubble for high-density days */}
                {manyEvents && !isToday && !isSelected && (
                  <span
                    className="absolute -top-0.5 -end-0.5 min-w-[14px] h-3.5 px-0.5 rounded-full text-[8px] font-bold flex items-center justify-center text-white"
                    style={{ backgroundColor: dotColors[0] ?? '#94a3b8' }}
                  >
                    {statuses.length}
                  </span>
                )}
              </button>
              <div className="flex gap-0.5 h-1.5 mt-0.5">
                {hasEvents && !manyEvents && dotColors.slice(0, 3).map((color, ci) => (
                  <div key={ci} className="w-1 h-1 rounded-full" style={{ backgroundColor: color }} />
                ))}
                {manyEvents && (
                  <div className="w-3 h-1 rounded-full" style={{ backgroundColor: dotColors[0] ?? '#94a3b8' }} />
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Day Agenda */}
      <AnimatePresence>
        {selectedDay && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div className="pt-2 pb-1 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
              <p className="text-[10px] font-bold uppercase tracking-wide mb-2" style={{ color: 'var(--text-muted)' }}>
                {new Intl.DateTimeFormat(resolveLocale(), { weekday: 'long', month: 'short', day: 'numeric' }).format(new Date(year, month, selectedDay))}
              </p>
              {dayAgenda.length === 0 ? (
                <p className="text-xs italic" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noReservationsThisDay')}</p>
              ) : (
                <div className="space-y-1.5">
                  {dayAgenda.map(r => {
                    const isPickup = r.dateStart && new Date(r.dateStart).getDate() === selectedDay;
                    const color = CALENDAR_DOT_COLORS[r.status ?? ''] ?? '#94a3b8';
                    return (
                      <div key={r.id} className="flex items-center gap-2 text-[11px]">
                        <div className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: color }} />
                        <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                          {isPickup ? `↑ ${t('dashboard.pickupLabel')}` : `↓ ${t('dashboard.returnLabel')}`}
                        </span>
                        <span className="truncate" style={{ color: 'var(--text-secondary)' }}>
                          {r.vehicleMarque ?? '—'}
                          {r.clientName ? ` · ${r.clientName}` : r.clientFirstName ? ` · ${r.clientFirstName}` : ''}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Legend */}
      <div className="flex flex-wrap items-center gap-3 pt-2 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
        {[
          { label: t('dashboard.confirmed'), color: '#10b981' },
          { label: t('dashboard.pending'),   color: '#f59e0b' },
          { label: t('dashboard.cancelled'), color: '#ef4444' },
          { label: t('dashboard.returnLabel'), color: '#f97316' },
        ].map(({ label, color }) => (
          <div key={label} className="flex items-center gap-1 text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>
            <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
            {label}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── SetupChecklist ─────────────────────────────────────────────── */
interface ChecklistStep {
  id: string; label: string; description: string; done: boolean; route: string; icon: React.ElementType;
}
function SetupChecklist({ steps, navigate }: { steps: ChecklistStep[]; navigate: (p: string) => void }) {
  const { t } = useTranslation();
  const done  = steps.filter(s => s.done).length;
  const total = steps.length;
  const pct   = Math.round((done / total) * 100);
  const [collapsed, setCollapsed] = useState(false);

  if (done === total) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl border p-4 flex items-center gap-4"
        style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)', backdropFilter: 'blur(20px)' }}
      >
        <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 bg-emerald-500/15">
          <CheckCircle size={20} className="text-emerald-500" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.setup.allSetTitle')}</p>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>{t('dashboard.setup.allSetSubtitle')}</p>
        </div>
        <div className="hidden sm:flex items-center gap-1.5 shrink-0">
          {steps.map(s => (
            <div key={s.id} className="w-2 h-2 rounded-full bg-emerald-500" />
          ))}
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-2xl border overflow-hidden"
      style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)', backdropFilter: 'blur(20px)' }}
    >
      {/* Header */}
      <button
        onClick={() => setCollapsed(c => !c)}
        className="w-full flex items-center justify-between px-5 py-4 hover:bg-[var(--bg-hover)] transition-colors"
      >
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center bg-[var(--brand-primary)]/10">
            <CheckSquare size={17} style={{ color: 'var(--brand-primary)' }} />
          </div>
          <div className="text-left">
            <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
              {t('dashboard.setup.title')}
            </p>
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              {t('dashboard.setup.stepsComplete', { done, total, pct })}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {/* Progress bar */}
          <div className="hidden sm:flex items-center gap-2">
            <div className="w-24 h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
              <motion.div
                initial={{ width: 0 }}
                animate={{ width: `${pct}%` }}
                transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                className="h-full rounded-full"
                style={{ backgroundColor: 'var(--brand-primary)' }}
              />
            </div>
            <span className="text-xs font-semibold" style={{ color: 'var(--brand-primary)' }}>{pct}%</span>
          </div>
          <ChevronDown
            size={16}
            style={{ color: 'var(--text-muted)', transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)', transition: 'transform 0.2s' }}
          />
        </div>
      </button>

      <AnimatePresence>
        {!collapsed && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="overflow-hidden"
          >
            <div
              className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 px-5 pb-5 pt-1 border-t"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              {steps.map((step, idx) => (
                <motion.button
                  key={step.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.04 }}
                  onClick={() => !step.done && navigate(step.route)}
                  className={`flex items-start gap-3 p-3 rounded-xl border text-left transition-all
                    ${step.done ? 'opacity-60' : 'hover:border-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/5'}`}
                  style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--bg-hover)' }}
                >
                  <div
                    className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5"
                    style={{
                      backgroundColor: step.done ? '#10b981' + '20' : 'var(--bg-card)',
                      border: `1px solid ${step.done ? '#10b981' : 'var(--border-subtle)'}`,
                    }}
                  >
                    {step.done
                      ? <CheckCircle size={14} className="text-emerald-500" />
                      : <step.icon size={14} style={{ color: 'var(--text-muted)' }} />
                    }
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold" style={{ color: step.done ? 'var(--text-muted)' : 'var(--text-primary)' }}>
                      {step.label}
                    </p>
                    <p className="text-[10px] mt-0.5 leading-snug" style={{ color: 'var(--text-muted)' }}>
                      {step.description}
                    </p>
                  </div>
                </motion.button>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

/* ─── PremiumFleetEmpty ──────────────────────────────────────────── */
function PremiumFleetEmpty({ navigate }: { navigate: (p: string) => void }) {
  const { t } = useTranslation();
  const steps = [
    { n: 1, icon: Car,      label: t('dashboard.addVehiclesStep'),  desc: t('dashboard.addVehiclesStepDesc'),  action: () => navigate('/vehicles') },
    { n: 2, icon: Users,    label: t('dashboard.addClientsStep'),   desc: t('dashboard.addClientsStepDesc'),   action: () => navigate('/clients') },
    { n: 3, icon: Calendar, label: t('dashboard.startBookingStep'), desc: t('dashboard.startBookingStepDesc'), action: () => navigate('/reservations') },
  ];
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-2xl border border-dashed p-8"
      style={{ borderColor: 'var(--border-subtle)' }}
    >
      <div className="max-w-lg mx-auto text-center mb-8">
        <div className="w-16 h-16 rounded-2xl mx-auto mb-4 flex items-center justify-center bg-blue-500/10">
          <Car size={32} className="text-blue-500" />
        </div>
        <h3 className="text-base font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
          {t('dashboard.buildYourFleet')}
        </h3>
        <p className="text-sm leading-relaxed" style={{ color: 'var(--text-muted)' }}>
          {t('dashboard.buildYourFleetDesc')}
        </p>
        <button
          onClick={() => navigate('/vehicles')}
          className="mt-5 flex items-center gap-2 text-sm font-semibold px-5 py-2.5 rounded-xl text-white transition-all hover:opacity-90 active:scale-95 mx-auto"
          style={{ backgroundColor: 'var(--brand-primary)' }}
        >
          <Car size={15} />
          {t('dashboard.addFirstVehicle')}
        </button>
      </div>

      {/* 3-step guide */}
      <div className="flex items-start justify-center gap-0 mb-8 max-w-sm mx-auto">
        {steps.map(({ n, icon: Icon, label, desc, action }, idx) => (
          <div key={n} className="flex items-start">
            <motion.button
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.1 }}
              onClick={action}
              className="flex flex-col items-center text-center w-24 group"
            >
              <div
                className="w-10 h-10 rounded-full border-2 flex items-center justify-center mb-2 group-hover:scale-110 transition-transform"
                style={{ borderColor: 'var(--brand-primary)', backgroundColor: 'var(--bg-card)' }}
              >
                <Icon size={16} style={{ color: 'var(--brand-primary)' }} />
              </div>
              <span className="text-[9px] font-bold uppercase tracking-wide" style={{ color: 'var(--brand-primary)' }}>
                Step {n}
              </span>
              <p className="text-[10px] font-semibold mt-0.5" style={{ color: 'var(--text-primary)' }}>{label}</p>
              <p className="text-[9px] mt-0.5 leading-snug" style={{ color: 'var(--text-muted)' }}>{desc}</p>
            </motion.button>
            {idx < steps.length - 1 && (
              <div className="flex-1 h-px mt-5 mx-1" style={{ backgroundColor: 'var(--border-subtle)' }} />
            )}
          </div>
        ))}
      </div>

      {/* Skeleton preview cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 opacity-25 pointer-events-none select-none">
        {['AVAILABLE','RENTED','RESERVED','MAINTENANCE'].map((status, i) => (
          <div
            key={i}
            className="rounded-xl border p-4 space-y-3"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)' }}
          >
            <div className="flex items-center gap-2">
              <div className="w-10 h-10 rounded-xl shimmer" />
              <div className="flex-1 space-y-1.5">
                <div className="h-3 w-20 shimmer rounded" />
                <div className="h-2.5 w-14 shimmer rounded" />
              </div>
            </div>
            <div className="h-2 w-full shimmer rounded-full" />
            <div className="flex items-center justify-between">
              <div className="h-3 w-16 shimmer rounded" />
              <VStatusBadge status={status} />
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
}

/* ─── GPSStatusWidget ────────────────────────────────────────────── */
function GPSStatusWidget({
  navigate,
  data,
}: {
  navigate: (p: string) => void;
  data: { configured: boolean; online?: number; offline?: number; total?: number } | null;
}) {
  const { t } = useTranslation();
  const isEmpty = data?.configured === false || (data?.configured && (data?.total ?? 0) === 0);

  if (!data) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="w-8 h-8 shimmer rounded-xl" />
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-center gap-3 py-4">
        <div className="w-12 h-12 rounded-xl flex items-center justify-center" style={{ backgroundColor: 'var(--bg-hover)' }}>
          <MapPinOff size={22} style={{ color: 'var(--text-muted)' }} />
        </div>
        <div>
          <p className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.gpsNotConfigured')}</p>
          <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>{t('dashboard.gpsNotConfiguredDesc')}</p>
        </div>
        <button
          onClick={() => navigate('/gps-settings')}
          className="text-xs font-semibold px-3 py-1.5 rounded-lg text-white"
          style={{ backgroundColor: 'var(--brand-primary)' }}
        >
          {t('gps.configure')}
        </button>
      </div>
    );
  }

  const onlineCount  = data.online  ?? 0;
  const offlineCount = data.offline ?? 0;
  const totalCount   = data.total   ?? 0;

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-3 gap-2 text-center">
        {/* Online — with pulse indicator when > 0 */}
        <div className="p-2.5 rounded-xl relative" style={{ backgroundColor: 'var(--bg-hover)' }}>
          <div className="flex items-center justify-center gap-1.5 mb-0.5">
            <p className="text-base font-bold" style={{ color: '#10b981' }}>{onlineCount}</p>
            {onlineCount > 0 && (
              <span className="relative flex h-2 w-2 shrink-0">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
              </span>
            )}
          </div>
          <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.online')}</p>
        </div>
        <div className="p-2.5 rounded-xl" style={{ backgroundColor: 'var(--bg-hover)' }}>
          <p className="text-base font-bold mb-0.5" style={{ color: '#ef4444' }}>{offlineCount}</p>
          <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.offline')}</p>
        </div>
        <div className="p-2.5 rounded-xl" style={{ backgroundColor: 'var(--bg-hover)' }}>
          <p className="text-base font-bold mb-0.5" style={{ color: 'var(--text-primary)' }}>{totalCount}</p>
          <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.total')}</p>
        </div>
      </div>

      {/* Utilization bar */}
      {totalCount > 0 && (
        <div className="space-y-1">
          <div className="flex justify-between text-[10px]" style={{ color: 'var(--text-muted)' }}>
            <span>{Math.round((onlineCount / totalCount) * 100)}% online</span>
            <span>{totalCount} devices</span>
          </div>
          <div className="h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
            <div
              className="h-full rounded-full bg-emerald-500 transition-all duration-700"
              style={{ width: `${(onlineCount / totalCount) * 100}%` }}
            />
          </div>
        </div>
      )}

      <button
        onClick={() => navigate('/gps-tracking')}
        className="w-full flex items-center justify-center gap-1.5 text-xs font-medium py-2 rounded-xl border hover:bg-[var(--bg-hover)] transition-colors"
        style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
      >
        <MapPin size={12} />
        {t('dashboard.viewGpsMap')}
      </button>
    </div>
  );
}

/* ─── QuickActions ───────────────────────────────────────────────── */
function QuickActions({ navigate }: { navigate: (path: string) => void }) {
  const { t } = useTranslation();
  const actions = [
    { label: t('dashboard.newReservation', 'New Reservation'), icon: Calendar,   color: 'bg-blue-500/10 text-blue-500',   to: '/reservations', desc: t('dashboard.newReservationDesc') },
    { label: t('dashboard.addVehicle',     'Add Vehicle'),     icon: Car,         color: 'bg-emerald-500/10 text-emerald-500', to: '/vehicles', desc: t('dashboard.addVehicleDesc') },
    { label: t('dashboard.addClient',      'Add Client'),      icon: Users,       color: 'bg-violet-500/10 text-violet-500', to: '/clients', desc: t('dashboard.addClientDesc') },
    { label: t('dashboard.createContract', 'Create Contract'), icon: FileText,    color: 'bg-orange-500/10 text-orange-500', to: '/contracts', desc: t('dashboard.createContractDesc') },
    { label: t('dashboard.invoices',       'Invoices'),        icon: CreditCard,  color: 'bg-pink-500/10 text-pink-500',   to: '/invoices', desc: t('dashboard.invoicesDesc') },
    { label: t('dashboard.reports',        'Reports'),         icon: BarChart3,   color: 'bg-cyan-500/10 text-cyan-500',   to: '/reports', desc: t('dashboard.reportsDesc') },
    { label: t('dashboard.gpsDashboardAction'),                  icon: MapPin,      color: 'bg-teal-500/10 text-teal-500',   to: '/gps-tracking', desc: t('dashboard.gpsDashboardActionDesc') },
    { label: t('common.maintenance'),                            icon: Wrench,      color: 'bg-red-500/10 text-red-500',     to: '/maintenance', desc: t('dashboard.maintenanceActionDesc') },
  ];
  return (
    <div>
      <h3 className="text-sm font-bold mb-3" style={{ color: 'var(--text-primary)' }}>
        {t('dashboard.quickActions', 'Quick Actions')}
      </h3>
      <div className="grid grid-cols-4 sm:grid-cols-8 gap-2">
        {actions.map(({ label, icon: Icon, color, to, desc }) => (
          <motion.button
            key={to}
            whileHover={{ y: -2 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => navigate(to)}
            title={desc}
            className={`flex flex-col items-center gap-1.5 p-2.5 sm:p-3 rounded-xl border transition-all text-center ${color}`}
            style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--bg-hover)' }}
          >
            <div className={`w-8 h-8 rounded-xl flex items-center justify-center ${color}`}>
              <Icon size={16} />
            </div>
            <span className="text-[9px] sm:text-[10px] font-semibold leading-tight" style={{ color: 'var(--text-secondary)' }}>
              {label}
            </span>
          </motion.button>
        ))}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════ */
/*  MAIN DASHBOARD                                                     */
/* ══════════════════════════════════════════════════════════════════ */
export default function Dashboard() {
  const [stats,          setStats]          = useState<DashboardStats | null>(null);
  const [vehicles,       setVehicles]       = useState<DashboardVehicle[]>([]);
  const [fleetVehicles,  setFleetVehicles]  = useState<VehicleCardData[]>([]);
  const [dashboardAlerts, setDashboardAlerts] = useState<Record<string, unknown>[]>([]);
  const [reservations,   setReservations]   = useState<DashboardReservation[]>([]);
  const [clients,        setClients]        = useState<ClientItem[]>([]);
  const [maintenance,    setMaintenance]    = useState<MaintenanceItem[]>([]);
  const [health,         setHealth]         = useState<HealthScore | null>(null);
  const [loading,        setLoading]        = useState(true);
  const [loadError,      setLoadError]      = useState('');
  const [statsError,     setStatsError]     = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState('thisMonth');
  const [returnContractId, setReturnContractId] = useState<number | null>(null);
  const [showCustomize,  setShowCustomize]  = useState(false);
  const [gpsStatus,      setGpsStatus]      = useState<{ configured: boolean; online?: number; offline?: number; total?: number } | null>(null);
  const [agencyConfigured, setAgencyConfigured] = useState(false);

  const { t, i18n: i18nInstance } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, loading: authLoading } = useAuth();

  const user = useMemo(() => {
    try { return JSON.parse(localStorage.getItem('user') || '{}') as Record<string, unknown>; }
    catch { return {}; }
  }, []);
  const userId = (user?.id as number | string) ?? 'default';
  const userName = (user?.firstName as string) || (user?.email as string)?.split('@')[0] || 'Admin';
  const { layout, reorder, reset, sortedVisibleIds } = useDashboardLayout(userId);

  const now  = new Date();
  const hour = now.getHours();
  const greeting =
    hour < 12 ? t('dashboard.greetingMorning',   'Good morning')
    : hour < 17 ? t('dashboard.greetingAfternoon', 'Good afternoon')
    :              t('dashboard.greetingEvening',   'Good evening');

  /* ── data loading ─────────────────────────────────────────────── */
  useEffect(() => {
    const isDashRoute =
      ['/', 'dashboard', '/employee/dashboard'].some(p => location.pathname === p || location.pathname.startsWith('/dashboard'));
    if (authLoading || !isAuthenticated || !isDashRoute) return;
    let active = true;

    const isUnauthorized = (r: PromiseSettledResult<unknown>) =>
      r.status === 'rejected' && (r.reason as { response?: { status?: number } })?.response?.status === 401;

    const fetchAll = async () => {
      try {
        setLoading(true);
        setLoadError('');
        const [statsRes, vehiclesRes, reservationsRes, healthRes, clientsRes, maintenanceRes, gpsRes, agencyRes] =
          await Promise.allSettled([
            api.get('/dashboard'),
            api.get('/vehicles'),
            api.get('/reservations'),
            api.get('/saas-health'),
            api.get('/clients'),
            api.get('/maintenance'),
            api.get('/gps/status'),
            api.get('/agency'),
          ]);
        if (!active) return;

        const core = [statsRes, vehiclesRes, reservationsRes, healthRes];
        if (core.some(isUnauthorized)) return;

        // ── vehicles from /vehicles (Vehicles page source of truth) ──────────
        let fallbackFleetVehicles: VehicleCardData[] = [];
        if (vehiclesRes.status === 'fulfilled') {
          const vArr = unwrapApiArray<DashboardVehicle>(vehiclesRes.value.data);
          setVehicles(vArr);
          // Cast is safe: VehicleResponse has marque/plate/statut/prixJour/fuel/imageUrl/category
          fallbackFleetVehicles = vArr as unknown as VehicleCardData[];
          console.log('[VEHICLES_PAGE_DATA_DEBUG] endpoint=GET /api/vehicles count=' + vArr.length +
            ' firstVehicle=' + (vArr[0] ? JSON.stringify({ id: vArr[0].id, marque: vArr[0].marque, statut: vArr[0].statut }) : 'none'));
        } else setVehicles([]);

        // ── dashboard stats + fleet vehicle cards ─────────────────────────────
        if (statsRes.status === 'fulfilled') {
          const raw = statsRes.value.data as Record<string, unknown>;
          const d = unwrapApiData<DashboardStats>(raw, {});
          setStats(d);
          setStatsError(false);
          const rawVehicles = (raw?.vehicles ?? (raw?.data as Record<string, unknown>)?.vehicles) as VehicleCardData[];
          const dashboardFleet = Array.isArray(rawVehicles) ? rawVehicles : [];
          console.log('[DASHBOARD_FLEET_DATA_DEBUG] endpoint=GET /api/dashboard count=' + dashboardFleet.length +
            ' firstVehicle=' + (dashboardFleet[0] ? JSON.stringify({ id: dashboardFleet[0].id, marque: dashboardFleet[0].marque, statut: dashboardFleet[0].statut }) : 'none') +
            ' filtersApplied=deleted=false_by_@SQLRestriction');
          // Use dashboard vehicles if returned; fall back to /api/vehicles data if not
          setFleetVehicles(dashboardFleet.length > 0 ? dashboardFleet : fallbackFleetVehicles);
          const rawAlerts = (raw?.alerts ?? (raw?.data as Record<string, unknown>)?.alerts) as Record<string, unknown>[];
          if (Array.isArray(rawAlerts)) setDashboardAlerts(rawAlerts);
        } else {
          setStats(null);
          setStatsError(true);
          // Dashboard endpoint failed — still show fleet vehicles from /api/vehicles
          if (fallbackFleetVehicles.length > 0) {
            console.log('[DASHBOARD_FLEET_DATA_DEBUG] endpoint=GET /api/dashboard FAILED — using fallback from /api/vehicles count=' + fallbackFleetVehicles.length);
            setFleetVehicles(fallbackFleetVehicles);
          }
        }

        if (reservationsRes.status === 'fulfilled')
          setReservations(unwrapApiArray<DashboardReservation>(reservationsRes.value.data));
        else setReservations([]);

        if (healthRes.status === 'fulfilled')
          setHealth(unwrapApiData<HealthScore>(healthRes.value.data, { score: 20, label: t('dashboard.gettingStarted'), risk: 'GETTING_STARTED' }));
        else setHealth({ score: 20, label: t('dashboard.gettingStarted'), risk: 'GETTING_STARTED' });

        if (clientsRes.status === 'fulfilled')
          setClients(unwrapApiArray<ClientItem>(clientsRes.value.data));
        else setClients([]);

        if (maintenanceRes.status === 'fulfilled')
          setMaintenance(unwrapApiArray<MaintenanceItem>(maintenanceRes.value.data));
        else setMaintenance([]);

        if (gpsRes.status === 'fulfilled') {
          const d = (gpsRes.value.data as Record<string, unknown>)?.data ?? gpsRes.value.data as Record<string, unknown>;
          if (d) setGpsStatus({ configured: true, online: (d as Record<string, number>).online ?? 0, offline: (d as Record<string, number>).offline ?? 0, total: (d as Record<string, number>).total ?? 0 });
        } else { setGpsStatus({ configured: false }); }

        if (agencyRes.status === 'fulfilled') {
          const ag = (agencyRes.value.data as Record<string, unknown>)?.data ?? agencyRes.value.data as Record<string, unknown>;
          setAgencyConfigured(!!(ag && ((ag as Record<string, unknown>).name || (ag as Record<string, unknown>).agencyName)));
        }

        if (core.every(r => r.status === 'rejected'))
          setLoadError(t('dashboard.unableToLoad', 'Unable to load dashboard'));
      } catch {
        setLoadError(t('dashboard.unableToLoad', 'Unable to load dashboard'));
      } finally {
        if (active) setLoading(false);
      }
    };
    fetchAll();
    return () => { active = false; };
  }, [authLoading, isAuthenticated, location.pathname, t]);

  /* ── derived values ─────────────────────────────────────────────── */
  const totalVehicles     = stats?.totalVehicles ?? stats?.fleet ?? 0;
  const availableVehicles = stats?.availableVehicles ?? 0;
  const rentedVehicles    = stats?.rentedVehicles ?? 0;
  const reservedVehicles  = stats?.reservedVehicles ?? 0;
  const activeContracts   = stats?.activeContracts ?? 0;
  const pendingContracts  = stats?.pendingContracts ?? 0;
  const signedContracts   = stats?.signedContracts ?? 0;
  const monthlyRevenue    = stats?.monthlyRevenue ?? 0;
  const paymentsToday     = stats?.paymentsToday ?? 0;
  const totalClients      = stats?.totalClients ?? 0;

  const bookingTrendData = useMemo(() => {
    // getWeekdayLabels() is Sunday-first; the chart itself starts on Monday,
    // so remap indices 0..6 (Mon..Sun) to the Sunday-first label array.
    const sundayFirstLabels = getWeekdayLabels('short');
    const mondayFirstOrder = [1, 2, 3, 4, 5, 6, 0];
    return mondayFirstOrder.map((sundayFirstIndex, i) => ({
      name: sundayFirstLabels[sundayFirstIndex],
      bookings: reservations.filter(r => r.dateStart && new Date(r.dateStart).getDay() === (i + 1) % 7).length,
      revenue:  reservations
        .filter(r => r.dateStart && new Date(r.dateStart).getDay() === (i + 1) % 7)
        .reduce((s, r) => s + (r.totalPrice || 0), 0),
    }));
  }, [reservations, i18nInstance.language]);

  const statusData = useMemo(() => {
    const counts: Record<string, number> = {};
    reservations.forEach(r => { if (r.status) counts[r.status] = (counts[r.status] || 0) + 1; });
    return Object.entries(counts).map(([name, count]) => ({ name, count, color: STATUS_COLORS[name] ?? '#94a3b8' }));
  }, [reservations]);
  const statusTotal = statusData.reduce((s, d) => s + d.count, 0);

  const vehicleDistData = useMemo(() => [
    { name: t('common.available'), value: availableVehicles, color: '#10b981' },
    { name: t('common.rented'),    value: rentedVehicles,    color: '#3b82f6' },
    { name: t('common.reserved'),  value: reservedVehicles,  color: '#f59e0b' },
    { name: t('common.other', 'Other'), value: Math.max(0, totalVehicles - availableVehicles - rentedVehicles - reservedVehicles), color: '#ef4444' },
  ].filter(d => d.value > 0), [availableVehicles, rentedVehicles, reservedVehicles, totalVehicles, t]);

  const upcoming7 = useMemo(() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const in7   = new Date(today); in7.setDate(today.getDate() + 7);
    return reservations.filter(r => {
      if (!r.dateStart) return false;
      const d = new Date(r.dateStart);
      return d >= today && d <= in7 && r.status !== 'CANCELLED';
    }).slice(0, 5);
  }, [reservations]);

  const upcomingReturns7 = useMemo(() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const in7   = new Date(today); in7.setDate(today.getDate() + 7);
    return reservations.filter(r => {
      if (!r.dateEnd) return false;
      const d = new Date(r.dateEnd);
      return d >= today && d <= in7 && r.status !== 'CANCELLED';
    }).slice(0, 5);
  }, [reservations]);

  const revenueSparkData = bookingTrendData.map(d => d.revenue);
  const healthScore = Math.min(health?.score ?? 0, 100);
  const healthColor = healthScore >= 80 ? '#10b981' : healthScore >= 60 ? '#f59e0b' : '#ef4444';
  // The backend's `label` field is free-text English prose (see
  // SaasHealthController) with no stable code — deriving the tier from the
  // score locally (using the same thresholds) avoids ever rendering that
  // untranslated backend string.
  const healthTierCode = healthScore <= 20 ? 'GETTING_STARTED' : healthScore <= 60 ? 'GROWING' : healthScore <= 80 ? 'GOOD' : 'EXCELLENT';

  /* Setup checklist steps (visible when DB is empty/new) */
  const checklistSteps: ChecklistStep[] = [
    { id: 'vehicle',     label: t('dashboard.setup.steps.vehicle.label'),     description: t('dashboard.setup.steps.vehicle.description'), done: totalVehicles > 0,    route: '/vehicles',     icon: Car },
    { id: 'client',      label: t('dashboard.setup.steps.client.label'),      description: t('dashboard.setup.steps.client.description'),     done: totalClients > 0,     route: '/clients',      icon: Users },
    { id: 'reservation', label: t('dashboard.setup.steps.reservation.label'),  description: t('dashboard.setup.steps.reservation.description'), done: reservations.length > 0, route: '/reservations', icon: Calendar },
    { id: 'contract',    label: t('dashboard.setup.steps.contract.label'),description: t('dashboard.setup.steps.contract.description'), done: (activeContracts + signedContracts) > 0, route: '/contracts', icon: FileText },
    { id: 'gps',         label: t('dashboard.setup.steps.gps.label'),              description: t('dashboard.setup.steps.gps.description'),       done: !!(gpsStatus?.configured && (gpsStatus?.total ?? 0) > 0), route: '/gps-settings',   icon: MapPin },
    { id: 'settings',    label: t('dashboard.setup.steps.settings.label'),  description: t('dashboard.setup.steps.settings.description'),   done: agencyConfigured,      route: '/agency',       icon: Settings2 },
  ];
  const checklistProgress = checklistSteps.filter(s => s.done).length;
  const showChecklist = checklistProgress < checklistSteps.length;

  /* ── error state ─────────────────────────────────────────────── */
  if (loadError) {
    return (
      <div className="p-4">
        <ApiErrorState message={loadError} onRetry={() => window.location.reload()} />
      </div>
    );
  }

  /* ══════════════════════════════════════════════════════════════ */
  /*  SECTION RENDER FUNCTIONS                                       */
  /* ══════════════════════════════════════════════════════════════ */

  const renderStats = () => (
    <div key="stats" className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
      <TopStat
        title={t('dashboard.totalFleet', 'Total Fleet')}
        value={totalVehicles}
        icon={Car}
        bgClass="bg-blue-500/15" iconClass="text-blue-500"
        trend="up" trendText={t('dashboard.trendMonth', 'this month')}
        loading={loading} delay={0}
        onClick={() => navigate('/vehicles')}
      />
      <TopStat
        title={t('dashboard.activeRentals', 'Active Rentals')}
        value={activeContracts}
        icon={Activity}
        bgClass="bg-emerald-500/15" iconClass="text-emerald-500"
        trend="up" trendText={t('dashboard.today', 'today')}
        loading={loading} delay={80}
        onClick={() => navigate('/contracts')}
        sparkData={bookingTrendData.map(d => d.bookings)}
      />
      <TopStat
        title={t('dashboard.todayRevenue', "Today's Revenue")}
        value={`${fmt(paymentsToday)} MAD`}
        icon={DollarSign}
        bgClass="bg-violet-500/15" iconClass="text-violet-500"
        trend={paymentsToday > 0 ? 'up' : undefined}
        trendText={paymentsToday > 0 ? t('dashboard.vsYesterday', 'vs yesterday') : undefined}
        loading={loading} delay={160}
        sparkData={revenueSparkData}
      />
      <TopStat
        title={t('dashboard.monthlyRevenue', 'Monthly Revenue')}
        value={`${fmt(monthlyRevenue)} MAD`}
        icon={BarChart3}
        bgClass="bg-orange-500/15" iconClass="text-orange-500"
        trend={monthlyRevenue > 0 ? 'up' : undefined}
        trendText={monthlyRevenue > 0 ? t('dashboard.vsLastMonth', 'vs last month') : undefined}
        loading={loading} delay={240}
        onClick={() => navigate('/payments')}
        sparkData={revenueSparkData}
      />
    </div>
  );

  const renderSetup = () =>
    showChecklist ? (
      <SetupChecklist key="setup" steps={checklistSteps} navigate={navigate} />
    ) : null;

  const renderActions = () => (
    <DCard key="actions" delay={150}>
      <QuickActions navigate={navigate} />
    </DCard>
  );

  const renderAlerts = () =>
    dashboardAlerts.length > 0 ? (
      <motion.div key="alerts" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, delay: 0.1 }}>
        <div className="flex items-center gap-2 mb-3">
          <Bell size={15} style={{ color: 'var(--text-muted)' }} />
          <h2 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('dashboard.alerts', 'Alerts')}
          </h2>
          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-500/10 text-rose-500">
            {dashboardAlerts.length}
          </span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          {dashboardAlerts.slice(0, 6).map((alert, idx) => (
            <motion.div
              key={(alert.id as string) ?? idx}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.25, delay: idx * 0.04 }}
              className="flex items-start gap-3 p-3 rounded-xl border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: alert.severity === 'danger' ? 'rgba(239,68,68,0.3)'
                  : alert.severity === 'warning' ? 'rgba(245,158,11,0.3)' : 'rgba(59,130,246,0.3)',
                backdropFilter: 'blur(12px)',
              }}
            >
              <AlertIcon severity={String(alert.severity ?? '')} />
              <div className="flex-1 min-w-0">
                <p className="text-[11px] font-bold leading-tight truncate" style={{ color: 'var(--text-primary)' }}>
                  {String(alert.title ?? '')}
                </p>
                <p className="text-[10px] mt-0.5 leading-snug" style={{ color: 'var(--text-muted)' }}>
                  {String(alert.message ?? '')}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      </motion.div>
    ) : null;

  const renderFleet = () => (
    <div key="fleet">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <Car size={15} style={{ color: 'var(--text-muted)' }} />
          <h2 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('dashboard.fleetOverview', 'Fleet Overview')}
          </h2>
          {[
            { label: t('common.available'), count: availableVehicles, color: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300' },
            { label: t('common.rented'),    count: rentedVehicles,    color: 'bg-blue-500/10 text-blue-600 dark:text-blue-300' },
            { label: t('common.reserved'),  count: reservedVehicles,  color: 'bg-amber-500/10 text-amber-600 dark:text-amber-300' },
          ].filter(p => p.count > 0).map(p => (
            <span key={p.label} className={`text-[10px] font-bold px-2 py-0.5 rounded-full hidden sm:inline ${p.color}`}>
              {p.count} {p.label}
            </span>
          ))}
        </div>
        <button onClick={() => navigate('/vehicles')}
          className="text-[11px] font-semibold hover:underline flex items-center gap-1"
          style={{ color: 'var(--brand-primary)' }}>
          {t('dashboard.viewAll', 'View All')} <ArrowRight size={12} />
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="rounded-2xl border p-4 space-y-3" style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)' }}>
              <div className="flex items-center gap-3">
                <div className="shimmer w-10 h-10 rounded-xl" />
                <div className="flex-1 space-y-2"><div className="shimmer h-3 w-28 rounded" /><div className="shimmer h-2.5 w-20 rounded" /></div>
              </div>
              <div className="shimmer h-2 rounded-full" />
              <div className="shimmer h-3 w-24 rounded" />
            </div>
          ))}
        </div>
      ) : fleetVehicles.length === 0 ? (
        <PremiumFleetEmpty navigate={navigate} />
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
            {fleetVehicles.slice(0, 8).map(v => (
              <DashboardVehicleCard
                key={v.id}
                v={v}
                onReturn={cid => setReturnContractId(cid)}
              />
            ))}
          </div>
          {fleetVehicles.length > 8 && (
            <div className="mt-3 text-center">
              <button onClick={() => navigate('/vehicles')}
                className="text-[11px] font-semibold hover:underline flex items-center gap-1 mx-auto"
                style={{ color: 'var(--brand-primary)' }}>
                {t('dashboard.seeAllVehicles', 'See all vehicles')} ({fleetVehicles.length}) <ArrowRight size={12} />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );

  const renderCharts = () => (
    <div key="charts" className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4">

      {/* Calendar (4 cols) */}
      <DCard className="lg:col-span-4" delay={200}>
        <EnhancedCalendar reservations={reservations} />
        <div className="grid grid-cols-3 gap-2 mt-4 pt-4 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
          {[
            { label: t('dashboard.pickupLabel'), value: upcoming7.length,        color: 'text-emerald-500' },
            { label: t('dashboard.returnLabel'), value: upcomingReturns7.length, color: 'text-orange-400' },
            { label: t('dashboard.total'),       value: reservations.length,     color: 'text-blue-500' },
          ].map(({ label, value, color }) => (
            <div key={label} className="text-center">
              <p className={`text-lg font-bold ${color}`}>{value}</p>
              <p className="text-[10px] font-medium mt-0.5" style={{ color: 'var(--text-muted)' }}>{label}</p>
            </div>
          ))}
        </div>
      </DCard>

      {/* Booking Trend + Status (5 cols) */}
      <div className="lg:col-span-5 space-y-4">
        <DCard delay={250}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
              {t('dashboard.bookingTrend', 'Booking Trend')}
            </h3>
            <select
              value={selectedPeriod}
              onChange={e => setSelectedPeriod(e.target.value)}
              className="text-[11px] font-semibold rounded-lg px-2.5 py-1.5 outline-none cursor-pointer"
              style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)', border: '1px solid var(--border-subtle)' }}
            >
              <option value="thisMonth">{t('dashboard.thisMonth')}</option>
              <option value="lastMonth">{t('dashboard.lastMonth')}</option>
            </select>
          </div>

          {reservations.length > 0 && (
            <div className="flex items-center gap-3 mb-4 p-3 rounded-xl" style={{ backgroundColor: 'var(--bg-hover)' }}>
              <div className="w-8 h-8 rounded-lg bg-blue-500/15 flex items-center justify-center">
                <BarChart3 size={15} className="text-blue-500" />
              </div>
              <div>
                <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.totalBookings')}</p>
                <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{reservations.length}</p>
              </div>
            </div>
          )}

          {bookingTrendData.some(d => d.bookings > 0) ? (
            <SafeChartContainer className="h-[180px] min-h-[180px]" minHeight={180}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={bookingTrendData} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
                  <defs>
                    <linearGradient id="gbookings" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#3b82f6" stopOpacity={0.2} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 10, fontWeight: 500 }} dy={6} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} />
                  <Tooltip
                    contentStyle={{ borderRadius: 12, border: 'none', backgroundColor: 'var(--glass-bg)', backdropFilter: 'blur(12px)', boxShadow: 'var(--shadow-elevated)', padding: '10px 14px' }}
                    labelStyle={{ color: 'var(--text-primary)', fontWeight: 600, fontSize: 12, marginBottom: 4 }}
                  />
                  <Area type="monotone" dataKey="bookings" stroke="#3b82f6" strokeWidth={2} fill="url(#gbookings)" name={t('dashboard.bookingsLabel')} />
                </AreaChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          ) : <ChartEmpty />}

          <div className="flex items-center gap-4 mt-3">
            <div className="flex items-center gap-1.5 text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>
              <div className="w-2 h-2 rounded-full bg-blue-500" /> {t('dashboard.bookingsLabel')}
            </div>
          </div>
        </DCard>

        {/* Reservation Status donut */}
        <DCard delay={320}>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.reservationStatus')}</h3>
            <span className="text-xs font-medium px-2 py-1 rounded-lg" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)' }}>
              {t('dashboard.totalCount', { count: statusTotal })}
            </span>
          </div>
          <div className="flex items-center gap-4">
            {statusData.length > 0 ? (
              <SafeChartContainer className="h-[120px] min-h-[120px] w-[120px] shrink-0" minHeight={120}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={statusData} cx="50%" cy="50%" innerRadius={36} outerRadius={52} paddingAngle={3} dataKey="count">
                      {statusData.map((d, i) => <Cell key={i} fill={d.color} className="outline-none" />)}
                    </Pie>
                    <Tooltip contentStyle={{ borderRadius: 10, border: 'none', backgroundColor: 'var(--glass-bg)', backdropFilter: 'blur(12px)' }} />
                  </PieChart>
                </ResponsiveContainer>
              </SafeChartContainer>
            ) : (
              <div className="w-[120px] h-[120px] shrink-0 rounded-full border-4 flex items-center justify-center" style={{ borderColor: 'var(--border-subtle)' }}>
                <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>0</span>
              </div>
            )}
            <div className="flex-1">
              <StatusLegend data={statusData} total={statusTotal} />
              {statusData.length === 0 && (
                <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noReservationsYet')}</p>
              )}
            </div>
          </div>
        </DCard>
      </div>

      {/* Contracts Summary + Fleet Health + GPS (3 cols) */}
      <div className="lg:col-span-3 space-y-4">
        <DCard delay={280}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.contractsSummary')}</h3>
            <button onClick={() => navigate('/contracts')} className="text-[11px] font-semibold hover:underline" style={{ color: 'var(--brand-primary)' }}>
              {t('dashboard.viewAll')}
            </button>
          </div>
          <div className="space-y-3">
            {[
              { label: t('dashboard.activeContracts'),      value: activeContracts,  icon: CheckCircle, color: 'text-emerald-500', bg: 'bg-emerald-500/10' },
              { label: t('dashboard.pendingSignatureLabel'), value: pendingContracts, icon: Clock,       color: 'text-amber-500',  bg: 'bg-amber-500/10'  },
              { label: t('dashboard.signedLabel'),           value: signedContracts,  icon: FileText,    color: 'text-blue-500',   bg: 'bg-blue-500/10'   },
              { label: t('dashboard.totalClientsLabel'),     value: totalClients,     icon: Users,       color: 'text-violet-500', bg: 'bg-violet-500/10' },
            ].map(({ label, value, icon: Icon, color, bg }) => (
              <div key={label} className="flex items-center gap-3 p-2.5 rounded-xl transition-colors hover:bg-[var(--bg-hover)]">
                <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${bg}`}>
                  <Icon size={16} className={color} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[11px] font-medium truncate" style={{ color: 'var(--text-muted)' }}>{label}</p>
                  {loading ? <Shimmer className="h-4 w-12 mt-1" /> : (
                    <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{value}</p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </DCard>

        {/* Fleet Health */}
        <DCard delay={350}>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.fleetHealth')}</h3>
            <Gauge size={16} style={{ color: healthColor }} />
          </div>
          <div className="text-center mb-3">
            <p className="text-3xl font-bold" style={{ color: healthColor }}>{healthScore}</p>
            <p className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>{health ? translateFleetHealthStatus(healthTierCode) : t('common.loading')}</p>
          </div>
          <div className="h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${healthScore}%` }}
              transition={{ duration: 1.2, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
              className="h-full rounded-full"
              style={{ backgroundColor: healthColor }}
            />
          </div>
          <div className="mt-4">
            <p className="text-[10px] font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--text-muted)' }}>
              {t('dashboard.fleetDistribution')}
            </p>
            {vehicleDistData.length > 0 ? (
              <div className="space-y-1.5">
                {vehicleDistData.map(d => (
                  <div key={d.name} className="flex items-center gap-2 text-[11px]">
                    <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: d.color }} />
                    <span className="flex-1 truncate" style={{ color: 'var(--text-secondary)' }}>{d.name}</span>
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>{d.value}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noVehiclesYet')}</p>
            )}
          </div>
        </DCard>
      </div>
    </div>
  );

  const renderLower = () => (
    <div key="lower" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
      {/* Vehicle Availability */}
      <DCard delay={400} noPad>
        <div className="p-5 pb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.vehicleAvailability')}</h3>
          <button onClick={() => navigate('/vehicles')} className="text-[11px] font-semibold hover:underline" style={{ color: 'var(--brand-primary)' }}>{t('dashboard.viewAll')}</button>
        </div>
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {vehicles.slice(0, 5).map(v => (
            <div key={v.id} className="flex items-center gap-3 px-5 py-3 hover:bg-[var(--bg-hover)] transition-colors group cursor-pointer" onClick={() => navigate('/vehicles')}>
              <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform" style={{ backgroundColor: 'var(--bg-hover)' }}>
                <Car size={18} style={{ color: 'var(--text-muted)' }} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>{v.marque}</p>
                {v.plate && <p className="text-[10px] font-mono mt-0.5" style={{ color: 'var(--text-muted)' }}>{v.plate}</p>}
              </div>
              <VStatusBadge status={v.statut} />
            </div>
          ))}
          {vehicles.length === 0 && (
            <div className="px-5 py-8 text-center">
              <Car size={28} className="mx-auto mb-2 opacity-30" style={{ color: 'var(--text-muted)' }} />
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noVehiclesFound')}</p>
            </div>
          )}
        </div>
        {vehicles.length > 5 && (
          <div className="p-3 border-t text-center" style={{ borderColor: 'var(--border-subtle)' }}>
            <button onClick={() => navigate('/vehicles')} className="text-[11px] font-medium flex items-center gap-1 mx-auto hover:gap-2 transition-all" style={{ color: 'var(--brand-primary)' }}>
              {t('dashboard.seeAll')} <ArrowRight size={12} />
            </button>
          </div>
        )}
      </DCard>

      {/* Recent Clients */}
      <DCard delay={460} noPad>
        <div className="p-5 pb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.recentClients')}</h3>
          <button onClick={() => navigate('/clients')} className="text-[11px] font-semibold hover:underline" style={{ color: 'var(--brand-primary)' }}>{t('dashboard.viewAll')}</button>
        </div>
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {clients.slice(0, 5).map(c => {
            const name = c.fullName || `${c.firstName || ''} ${c.lastName || ''}`.trim() || '—';
            return (
              <div key={c.id} className="flex items-center gap-3 px-5 py-3 hover:bg-[var(--bg-hover)] transition-colors cursor-pointer" onClick={() => navigate('/clients')}>
                <Avatar name={name} gender={c.gender} />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>{name}</p>
                  <p className="text-[10px] truncate mt-0.5" style={{ color: 'var(--text-muted)' }}>{c.email || c.phone || '—'}</p>
                </div>
                {(c.totalContracts ?? 0) > 0 && (
                  <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-blue-500/10 text-blue-500 shrink-0">
                    {t('dashboard.rentalsCount', { count: c.totalContracts ?? 0 })}
                  </span>
                )}
              </div>
            );
          })}
          {clients.length === 0 && (
            <div className="px-5 py-8 text-center">
              <Users size={28} className="mx-auto mb-2 opacity-30" style={{ color: 'var(--text-muted)' }} />
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noClientsYet')}</p>
            </div>
          )}
        </div>
        {clients.length > 5 && (
          <div className="p-3 border-t text-center" style={{ borderColor: 'var(--border-subtle)' }}>
            <button onClick={() => navigate('/clients')} className="text-[11px] font-medium flex items-center gap-1 mx-auto hover:gap-2 transition-all" style={{ color: 'var(--brand-primary)' }}>
              {t('dashboard.seeAll')} <ArrowRight size={12} />
            </button>
          </div>
        )}
      </DCard>

      {/* Maintenance Alerts */}
      <DCard delay={520} noPad>
        <div className="p-5 pb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.maintenanceAlerts')}</h3>
          <button onClick={() => navigate('/maintenance')} className="text-[11px] font-semibold hover:underline" style={{ color: 'var(--brand-primary)' }}>{t('dashboard.viewAll')}</button>
        </div>
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {maintenance.slice(0, 5).map(m => {
            const pri = (m.priority || '').toUpperCase();
            const priColor = pri === 'HIGH' || pri === 'CRITICAL' ? '#ef4444' : pri === 'MEDIUM' ? '#f59e0b' : '#3b82f6';
            const priLabel = pri === 'HIGH' || pri === 'CRITICAL' ? 'High' : pri === 'MEDIUM' ? 'Medium' : 'Low';
            return (
              <div key={m.id} className="flex items-center gap-3 px-5 py-3 hover:bg-[var(--bg-hover)] transition-colors cursor-pointer" onClick={() => navigate('/maintenance')}>
                <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0" style={{ backgroundColor: `${priColor}15` }}>
                  <Wrench size={17} style={{ color: priColor }} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                    {m.vehicle?.marque ?? '—'}{m.vehicle?.plate ? ` · ${m.vehicle.plate}` : ''}
                  </p>
                  <p className="text-[10px] truncate mt-0.5" style={{ color: 'var(--text-muted)' }}>
                    {m.description || m.type || 'Maintenance due'}
                  </p>
                </div>
                <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full shrink-0" style={{ backgroundColor: `${priColor}15`, color: priColor }}>
                  {priLabel}
                </span>
              </div>
            );
          })}
          {maintenance.length === 0 && (
            <div className="px-5 py-8 text-center">
              <CheckCircle size={28} className="mx-auto mb-2 opacity-30 text-emerald-500" />
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noMaintenanceAlerts')}</p>
            </div>
          )}
        </div>
      </DCard>
    </div>
  );

  const renderPickups = () => (
    <div key="pickups" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
      {/* Upcoming Pickups */}
      <DCard delay={580} noPad>
        <div className="p-5 pb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.upcomingPickups')}</h3>
          <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-500">
            {upcoming7.length}
          </span>
        </div>
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {upcoming7.map(r => (
            <div key={r.id} className="flex items-center gap-3 px-5 py-3 hover:bg-[var(--bg-hover)] transition-colors cursor-pointer" onClick={() => navigate('/reservations')}>
              <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 bg-emerald-500/10">
                <Car size={15} className="text-emerald-500" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                  {r.clientFirstName ? `${r.clientFirstName} ${r.clientLastName ?? ''}`.trim() : r.clientName ?? r.vehicleMarque ?? '—'}
                </p>
                <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  {r.vehicleMarque && <span>{r.vehicleMarque} · </span>}
                  {r.dateStart ? formatShortDate(r.dateStart) : '—'}
                </p>
              </div>
              {r.pickupLocation && (
                <div className="flex items-center gap-1 text-[10px] shrink-0" style={{ color: 'var(--text-muted)' }}>
                  <MapPin size={10} />
                  <span className="truncate max-w-[60px]">{r.pickupLocation}</span>
                </div>
              )}
            </div>
          ))}
          {upcoming7.length === 0 && (
            <div className="px-5 py-8 text-center">
              <Calendar size={28} className="mx-auto mb-2 opacity-30" style={{ color: 'var(--text-muted)' }} />
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noUpcomingPickups')}</p>
            </div>
          )}
        </div>
      </DCard>

      {/* Upcoming Returns */}
      <DCard delay={640} noPad>
        <div className="p-5 pb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.upcomingReturnsTitle')}</h3>
          <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-orange-500/10 text-orange-500">
            {upcomingReturns7.length}
          </span>
        </div>
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {upcomingReturns7.map(r => (
            <div key={r.id} className="flex items-center gap-3 px-5 py-3 hover:bg-[var(--bg-hover)] transition-colors cursor-pointer" onClick={() => navigate('/reservations')}>
              <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0 bg-orange-500/10">
                <ArrowRight size={15} className="text-orange-500" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                  {r.clientFirstName ? `${r.clientFirstName} ${r.clientLastName ?? ''}`.trim() : r.clientName ?? r.vehicleMarque ?? '—'}
                </p>
                <p className="text-[10px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  {r.vehicleMarque && <span>{r.vehicleMarque} · </span>}
                  {r.dateEnd ? formatShortDate(r.dateEnd) : '—'}
                </p>
              </div>
              {r.returnLocation && (
                <div className="flex items-center gap-1 text-[10px] shrink-0" style={{ color: 'var(--text-muted)' }}>
                  <MapPin size={10} />
                  <span className="truncate max-w-[60px]">{r.returnLocation}</span>
                </div>
              )}
            </div>
          ))}
          {upcomingReturns7.length === 0 && (
            <div className="px-5 py-8 text-center">
              <Clock size={28} className="mx-auto mb-2 opacity-30" style={{ color: 'var(--text-muted)' }} />
              <p className="text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noUpcomingReturns')}</p>
            </div>
          )}
        </div>
      </DCard>

      {/* Revenue Statistics */}
      <DCard delay={700}>
        <h3 className="text-sm font-bold mb-4" style={{ color: 'var(--text-primary)' }}>{t('dashboard.revenueStatistics')}</h3>
        {bookingTrendData.some(d => d.revenue > 0) ? (
          <SafeChartContainer className="h-[130px] min-h-[130px]" minHeight={130}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={bookingTrendData} margin={{ top: 0, right: 0, bottom: 0, left: -20 }} barSize={8}>
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 9 }} dy={5} />
                <YAxis axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 9 }} />
                <Tooltip contentStyle={{ borderRadius: 10, border: 'none', backgroundColor: 'var(--glass-bg)', backdropFilter: 'blur(12px)' }} />
                <Bar dataKey="revenue" fill="var(--brand-primary)" radius={[4, 4, 0, 0]} name={t('dashboard.revenue')} />
              </BarChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        ) : <ChartEmpty h="h-[130px]" />}

        <div className="mt-4 grid grid-cols-2 gap-2 pt-3 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
          {[
            { label: t('dashboard.depositsHeld'),      value: `${fmt(stats?.totalDepositsHeld)} MAD`, color: 'text-amber-500' },
            { label: t('dashboard.pendingReturns'),    value: stats?.pendingReturns ?? 0,             color: 'text-orange-500' },
            { label: t('dashboard.returnedDeposits'),  value: `${fmt(stats?.returnedDeposits)} MAD`,  color: 'text-emerald-500' },
            { label: t('dashboard.deductions'),        value: `${fmt(stats?.depositDeductions)} MAD`, color: 'text-red-500' },
          ].map(({ label, value, color }) => (
            <div key={label} className="p-2.5 rounded-xl" style={{ backgroundColor: 'var(--bg-hover)' }}>
              <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{label}</p>
              {loading ? <Shimmer className="h-4 w-14 mt-1" /> : (
                <p className={`text-sm font-bold mt-0.5 ${color}`}>{value}</p>
              )}
            </div>
          ))}
        </div>
      </DCard>
    </div>
  );

  const renderGPS = () => (
    <div key="gps" className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4">
      <DCard delay={760}>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <MapPin size={15} style={{ color: 'var(--text-muted)' }} />
            <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.gpsQuickStatus')}</h3>
          </div>
          <Zap size={14} style={{ color: 'var(--brand-primary)' }} />
        </div>
        <GPSStatusWidget navigate={navigate} data={gpsStatus} />
      </DCard>

      {/* Vehicle Availability Board */}
      <DCard delay={800}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.availabilityBoard')}</h3>
          <button onClick={() => navigate('/vehicles')} className="p-1.5 hover:bg-[var(--bg-hover)] rounded-lg transition-colors">
            <RefreshCw size={13} style={{ color: 'var(--text-muted)' }} />
          </button>
        </div>
        <div className="grid grid-cols-2 gap-2">
          {[
            { label: t('common.available'),    count: availableVehicles, color: '#10b981', bg: 'bg-emerald-500/10' },
            { label: t('common.rented'),       count: rentedVehicles,    color: '#3b82f6', bg: 'bg-blue-500/10'   },
            { label: t('common.reserved'),     count: reservedVehicles,  color: '#f59e0b', bg: 'bg-amber-500/10'  },
            { label: t('common.maintenance'),  count: Math.max(0, totalVehicles - availableVehicles - rentedVehicles - reservedVehicles), color: '#ef4444', bg: 'bg-red-500/10' },
          ].map(({ label, count, color, bg }) => (
            <div key={label} className={`flex items-center gap-3 p-3 rounded-xl ${bg} cursor-pointer hover:opacity-90 transition-opacity`}
              onClick={() => navigate('/vehicles')}>
              <div>
                <p className="text-xl font-bold" style={{ color }}>{loading ? '—' : count}</p>
                <p className="text-[10px] font-semibold" style={{ color }}>{label}</p>
              </div>
            </div>
          ))}
        </div>
        {totalVehicles > 0 && (
          <div className="mt-3 pt-3 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <div className="flex items-center gap-2">
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
                <div className="h-full flex">
                  {[
                    { value: availableVehicles, color: '#10b981' },
                    { value: rentedVehicles,    color: '#3b82f6' },
                    { value: reservedVehicles,  color: '#f59e0b' },
                  ].filter(d => d.value > 0).map((d, i) => (
                    <div key={i} className="h-full transition-all" style={{ width: `${(d.value / totalVehicles) * 100}%`, backgroundColor: d.color }} />
                  ))}
                </div>
              </div>
              <span className="text-[10px] font-semibold shrink-0" style={{ color: 'var(--text-muted)' }}>
                {totalVehicles} total
              </span>
            </div>
          </div>
        )}
      </DCard>
    </div>
  );

  /* Section map: id → render function */
  const sectionMap: Record<string, () => React.ReactNode | null> = {
    stats:   renderStats,
    setup:   renderSetup,
    actions: renderActions,
    alerts:  renderAlerts,
    fleet:   renderFleet,
    charts:  renderCharts,
    lower:   renderLower,
    pickups: renderPickups,
    gps:     renderGPS,
  };

  /* ══════════════════════════════════════════════════════════════ */
  /*  RENDER                                                         */
  /* ══════════════════════════════════════════════════════════════ */
  return (
    <div className="space-y-5 pb-6">
      {loading && <PremiumLoader />}

      {/* ══ PAGE HEADER ══════════════════════════════════════════ */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="flex flex-col sm:flex-row sm:items-center justify-between gap-3"
      >
        <div>
          <h1 className="text-[28px] sm:text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            {t('nav.dashboard', 'Dashboard')}
          </h1>
          <p className="text-sm mt-0.5 break-words" style={{ color: 'var(--text-muted)' }}>
            {greeting},{' '}
            <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>{userName}</span>
            {' — '}{new Intl.DateTimeFormat(resolveLocale(), { weekday: 'long', month: 'long', day: 'numeric' }).format(now)}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {statsError && (
            <button
              onClick={() => window.location.reload()}
              className="inline-flex items-center gap-2 text-xs font-medium px-3 py-2 rounded-xl border"
              style={{ color: 'var(--brand-primary)', borderColor: 'var(--border-subtle)', backgroundColor: 'var(--bg-hover)' }}
            >
              <RefreshCw size={13} /> {t('dashboard.retry')}
            </button>
          )}
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.97 }}
            onClick={() => setShowCustomize(true)}
            className="flex items-center gap-2 text-xs font-semibold px-3 py-2 rounded-xl border transition-all hover:bg-[var(--bg-hover)]"
            style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)', backgroundColor: 'var(--bg-card)' }}
          >
            <LayoutDashboard size={14} />
            {t('dashboard.customize')}
          </motion.button>
        </div>
      </motion.div>

      {/* ══ WIDGET SECTIONS (in user-defined order) ══════════════ */}
      {sortedVisibleIds.map(id => {
        const renderFn = sectionMap[id];
        if (!renderFn) return null;
        const content = renderFn();
        if (!content) return null;
        return <div key={id}>{content}</div>;
      })}

      <AiAssistantButton module="Dashboard" />

      {/* Return inspection modal */}
      {returnContractId != null && (
        <ContractReturnModal
          isOpen={returnContractId != null}
          contractId={returnContractId}
          onClose={() => setReturnContractId(null)}
          onSuccess={() => { setReturnContractId(null); window.location.reload(); }}
        />
      )}

      {/* Customize modal */}
      <AnimatePresence>
        {showCustomize && (
          <DashboardCustomizeModal
            layout={layout}
            onReorder={reorder}
            onReset={reset}
            onClose={() => setShowCustomize(false)}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
