import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, AreaChart, Area } from 'recharts';
import SafeChartContainer from '../components/shared/SafeChartContainer';
import { StatCard } from '../components/StatCard';
import { GlassCard } from '../components/GlassCard';
import {
  Car, Calendar, CreditCard, Landmark, ArrowDownLeft, ArrowUpRight, Minus,
  Gauge, Plus, ArrowRight, Radio, CircleDollarSign
} from 'lucide-react';
import api from '../api/axios';
import ApiErrorState from '../components/ApiErrorState';
import PremiumLoader from '../components/PremiumLoader';
import { useAuth } from '../context/AuthContext';

// const COLORS = ['#1e3a5f', '#d4a853', '#ef4444'];
const PIE_COLORS = ['#10b981', '#f59e0b', '#ef4444'];

interface DashboardStats {
  totalVehicles?: number;
  rentedVehicles?: number;
  totalRevenue?: number;
  totalClients?: number;
  reservationsToday?: number;
  upcomingReservations?: number;
  activeContracts?: number;
  availableVehicles?: number;
  reservedVehicles?: number;
  totalDepositsHeld?: number;
  pendingReturns?: number;
  returnedDeposits?: number;
  depositDeductions?: number;
  monthlyRevenue?: number;
}

interface DashboardVehicle {
  id: number;
  marque: string;
  statut: string;
  imageUrl?: string;
}

interface DashboardReservation {
  id: number;
  dateStart: string;
  dateEnd: string;
  vehicleMarque?: string;
  totalPrice?: number;
  status?: string;
}

interface HealthScore {
  score?: number;
  label?: string;
  risk?: string;
}

const unwrapApiData = <T,>(payload: any, fallback: T): T => {
  const value = payload?.data && typeof payload.data === 'object' && !Array.isArray(payload.data)
    ? payload.data
    : payload;
  return (value ?? fallback) as T;
};

const unwrapApiArray = <T,>(payload: any): T[] => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.vehicles)) return payload.vehicles;
  if (Array.isArray(payload?.data?.vehicles)) return payload.data.vehicles;
  return [];
};

const hasPositiveValue = (items: Array<Record<string, any>>, key: string) =>
  items.some((item) => Number(item[key] || 0) > 0);

function ChartEmpty({ heightClass = 'h-[260px] min-h-[260px]' }: { heightClass?: string }) {
  const { t } = useTranslation();
  return (
    <div className={`${heightClass} w-full flex items-center justify-center rounded-xl border border-dashed border-[var(--border-subtle)] text-xs font-medium`} style={{ color: 'var(--text-muted)' }}>
      {t('dashboard.noChartData')}
    </div>
  );
}

/* ============================================
   HERO SECTION
   ============================================ */
function HeroSection({
  userName,
  onNewReservation,
  onAddVehicle,
  backgroundImage,
  totalVehicles,
  activeRentals,
  monthlyRevenue,
  reservations,
}: {
  userName: string;
  onNewReservation: () => void;
  onAddVehicle: () => void;
  backgroundImage?: string;
  totalVehicles: number;
  activeRentals: number;
  monthlyRevenue: number;
  reservations: number;
}) {
  const { t, i18n } = useTranslation();
  const now = new Date();
  const hour = now.getHours();
  const greeting = hour < 12 ? t('dashboard.greetingMorning') : hour < 17 ? t('dashboard.greetingAfternoon') : t('dashboard.greetingEvening');

  return (
    <motion.section
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="automotive-hero mb-6"
    >
      <img
        src={backgroundImage || 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&q=86&w=1800'}
        alt=""
        className="absolute inset-0 w-full h-full object-cover"
      />
      <div className="relative z-10 min-h-[300px] sm:min-h-[340px] p-5 sm:p-8 lg:p-10 flex flex-col justify-between">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 text-[10px] uppercase tracking-[0.2em] text-white/55">
            <Radio size={12} className="text-emerald-400" />
            {t('dashboard.liveAgencyCommand')}
          </div>
          <h1 className="mt-5 text-3xl sm:text-4xl lg:text-5xl font-semibold leading-[1.05] text-white">
            {greeting}, {userName}.
          </h1>
          <p className="mt-3 text-sm sm:text-base text-white/58 max-w-lg">
            {t('dashboard.heroSubtitle', { date: now.toLocaleDateString(i18n.language, { month: 'long', day: 'numeric' }) })}
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-2">
            <motion.button whileTap={{ scale: 0.97 }} onClick={onNewReservation} className="premium-action h-10 px-4 inline-flex items-center gap-2 text-sm font-semibold">
              <Plus size={16} /> {t('dashboard.newReservation')}
            </motion.button>
            <motion.button whileTap={{ scale: 0.97 }} onClick={onAddVehicle} className="h-10 px-4 rounded-lg border border-white/15 bg-white/[0.07] backdrop-blur-xl text-white inline-flex items-center gap-2 text-sm font-medium hover:bg-white/[0.12]">
              {t('dashboard.exploreFleet')} <ArrowRight size={15} />
            </motion.button>
          </div>
        </div>

        <div className="command-strip mt-8">
          {[
            { label: t('dashboard.fleet'), value: totalVehicles, icon: Car },
            { label: t('dashboard.activeRentals'), value: activeRentals, icon: Radio },
            { label: t('dashboard.reservationsLabel'), value: reservations, icon: Calendar },
            { label: t('dashboard.monthlyRevenue'), value: `${monthlyRevenue.toLocaleString()} MAD`, icon: CircleDollarSign },
          ].map((item) => (
            <div key={item.label} className="p-3 sm:p-4 min-w-0">
              <div className="flex items-center gap-2 text-white/42">
                <item.icon size={13} />
                <span className="text-[9px] uppercase tracking-[0.12em] truncate">{item.label}</span>
              </div>
              <strong className="block mt-1 text-base sm:text-lg text-white truncate">{item.value}</strong>
            </div>
          ))}
        </div>
      </div>
    </motion.section>
  );
}

/* ============================================
   HEALTH SCORE BAR
   ============================================ */
function HealthScoreBar({ health }: { health: HealthScore | null }) {
  const { t } = useTranslation();
  const score = Math.min(health?.score || 0, 100);
  const label = health?.label || 'Poor';
  const risk = health?.risk || 'LOADING';

  const getScoreColor = (s: number) => {
    if (s >= 80) return '#10b981';
    if (s >= 60) return '#f59e0b';
    if (s >= 40) return '#f97316';
    return '#ef4444';
  };

  const scoreColor = getScoreColor(score);

  return (
    <GlassCard padding="md" delay={400}>
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0" style={{ backgroundColor: `${scoreColor}15` }}>
          <Gauge size={22} style={{ color: scoreColor }} />
        </div>
        <div className="flex-1">
          <div className="flex items-center justify-between gap-3 mb-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>{t('dashboard.healthScoreTitle')}</p>
              <p className="text-lg font-bold mt-0.5" style={{ color: 'var(--text-primary)' }}>
                {score}/100 · <span style={{ color: scoreColor }}>{label}</span>
              </p>
            </div>
            <span
              className="text-xs font-bold px-3 py-1.5 rounded-lg uppercase tracking-wider"
              style={{ backgroundColor: `${scoreColor}15`, color: scoreColor }}
            >
              {risk === 'HEALTHY' ? t('dashboard.healthy') : risk.replaceAll('_', ' ')}
            </span>
          </div>
          <div className="h-2.5 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--bg-hover)' }}>
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${score}%` }}
              transition={{ duration: 1.2, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
              className="h-full rounded-full"
              style={{
                background: `linear-gradient(90deg, ${scoreColor}80, ${scoreColor})`,
                boxShadow: `0 0 12px ${scoreColor}40`,
              }}
            />
          </div>
        </div>
      </div>
    </GlassCard>
  );
}

/* ============================================
   MAIN DASHBOARD
   ============================================ */
export default function Dashboard() {
  const [selectedPeriod, setSelectedPeriod] = useState('This Month');
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [vehicles, setVehicles] = useState<DashboardVehicle[]>([]);
  const [reservations, setReservations] = useState<DashboardReservation[]>([]);
  const [health, setHealth] = useState<HealthScore | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [statsError, setStatsError] = useState(false);
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, loading: authLoading } = useAuth();

  const user = JSON.parse(localStorage.getItem('user') || '{}');
  const userName = user?.firstName || user?.email?.split('@')[0] || 'Admin';

  useEffect(() => {
    // Defensive guard: never call dashboard/vehicles/reservations/saas-health
    // before auth has resolved, even if this component were ever mounted
    // briefly during the auth bootstrap (e.g. a fresh LAN origin with no
    // session yet) — avoids 401 spam on the login screen.
    const isDashboardRoute = location.pathname === '/'
      || location.pathname === '/dashboard'
      || location.pathname === '/employee/dashboard';
    if (authLoading || !isAuthenticated || !isDashboardRoute) return;
    let active = true;
    const isUnauthorized = (result: PromiseSettledResult<unknown>) =>
      result.status === 'rejected' && (result.reason as any)?.response?.status === 401;
    const fetchData = async () => {
      try {
        setLoading(true);
        setLoadError('');
        const [statsRes, vehiclesRes, reservationsRes, healthRes] = await Promise.allSettled([
          api.get('/dashboard'),
          api.get('/vehicles'),
          api.get('/reservations'),
          api.get('/saas-health'),
        ]);
        if (!active) return;

        const results = [statsRes, vehiclesRes, reservationsRes, healthRes];
        if (results.some(isUnauthorized)) {
          setStats(null);
          setVehicles([]);
          setReservations([]);
          setHealth(null);
          return;
        }

        if (statsRes.status === 'fulfilled') {
          setStats(unwrapApiData<DashboardStats>(statsRes.value.data, {}));
          setStatsError(false);
        } else {
          console.error('Failed to fetch dashboard stats', statsRes.reason);
          setStats(null);
          setStatsError(true);
        }
        if (vehiclesRes.status === 'fulfilled') {
          setVehicles(unwrapApiArray<DashboardVehicle>(vehiclesRes.value.data));
        } else {
          console.error('Failed to fetch vehicles for dashboard', vehiclesRes.reason);
          setVehicles([]);
        }
        if (reservationsRes.status === 'fulfilled') {
          setReservations(unwrapApiArray<DashboardReservation>(reservationsRes.value.data));
        } else {
          console.error('Failed to fetch reservations for dashboard', reservationsRes.reason);
          setReservations([]);
        }
        if (healthRes.status === 'fulfilled') {
          setHealth(unwrapApiData<HealthScore>(healthRes.value.data, { score: 20, label: 'Getting Started', risk: 'GETTING_STARTED' }));
        } else {
          console.error('Failed to fetch SaaS health for dashboard', healthRes.reason);
          setHealth({ score: 20, label: 'Getting Started', risk: 'GETTING_STARTED' });
        }

        const allFailed = [statsRes, vehiclesRes, reservationsRes, healthRes].every((result) => result.status === 'rejected');
        if (allFailed) {
          setLoadError(t('dashboard.unableToLoad'));
        }
      } catch (err) {
        console.error('Failed to fetch dashboard data', err);
        setLoadError(t('dashboard.unableToLoad'));
      } finally {
        if (active) setLoading(false);
      }
    };
    fetchData();
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, isAuthenticated, location.pathname]);

  const totalVehicles = stats?.totalVehicles || 0;
  const rentedVehicles = stats?.rentedVehicles || 0;
  const totalRevenue = stats?.totalRevenue || 0;
  const availableVehicles = stats?.availableVehicles || 0;
  const reservedVehicles = stats?.reservedVehicles || 0;
  const activeContracts = stats?.activeContracts || 0;
  const reservationsToday = stats?.reservationsToday || 0;
  const upcomingReservations = stats?.upcomingReservations || 0;

  const depositStats = {
    totalHeld: stats?.totalDepositsHeld || 0,
    pendingReturns: stats?.pendingReturns || 0,
    returned: stats?.returnedDeposits || 0,
    deductions: stats?.depositDeductions || 0,
  };

  const revenueData = [
    { name: 'This Month', revenue: stats?.monthlyRevenue || 0 },
    { name: 'Total', revenue: totalRevenue },
  ];

  const reservationData = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((name, index) => ({
    name,
    count: reservations.filter((res) => res.dateStart && new Date(res.dateStart).getDay() === (index + 1) % 7).length,
  }));

  const distributionData = [
    { name: t('dashboard.available'), value: availableVehicles },
    { name: t('dashboard.rented'), value: rentedVehicles },
    { name: t('dashboard.maintenance'), value: vehicles.filter((v) => v.statut !== 'AVAILABLE' && v.statut !== 'RENTED').length },
  ].filter((item) => item.value > 0);

  const reservationStatusData = [
    { name: t('dashboard.confirmed'), count: reservations.filter((r) => r.status === 'CONFIRMED').length },
    { name: t('dashboard.pending'), count: reservations.filter((r) => r.status === 'PENDING').length },
    { name: t('dashboard.cancelled'), count: reservations.filter((r) => r.status === 'CANCELLED').length },
  ];
  const reservationStatusTotal = Math.max(reservationStatusData.reduce((sum, item) => sum + item.count, 0), 1);

  if (loadError) {
    return (
      <div className="p-3 sm:p-4 lg:p-6">
        <ApiErrorState message={loadError} onRetry={() => window.location.reload()} />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {loading && (
        <PremiumLoader />
      )}

      {/* Hero */}
      <HeroSection
        userName={userName}
        onNewReservation={() => navigate('/reservations')}
        onAddVehicle={() => navigate('/vehicles')}
        backgroundImage={vehicles.find((vehicle) => vehicle.imageUrl)?.imageUrl}
        totalVehicles={totalVehicles}
        activeRentals={activeContracts}
        monthlyRevenue={Number(stats?.monthlyRevenue || 0)}
        reservations={upcomingReservations}
      />

      {/* Live operating metrics */}
      {statsError && (
        <div className="flex items-center justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-2.5 text-sm text-amber-800">
          <span>{t('dashboard.unableToLoadStats')}</span>
          <button onClick={() => window.location.reload()} className="font-semibold underline whitespace-nowrap">
            {t('dashboard.retry')}
          </button>
        </div>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3 sm:gap-4">
        <StatCard
          title={t('dashboard.reservationsToday')}
          value={statsError ? '—' : reservationsToday}
          numericValue={statsError ? undefined : reservationsToday}
          icon={Calendar}
          iconBg="bg-[#477d91]"
          onClick={() => navigate('/reservations')}
          delay={0}
        />
        <StatCard
          title={t('dashboard.upcomingReservations')}
          value={statsError ? '—' : upcomingReservations}
          numericValue={statsError ? undefined : upcomingReservations}
          icon={Calendar}
          iconBg="bg-sky-600"
          onClick={() => navigate('/reservations')}
          delay={100}
        />
        <StatCard
          title={t('dashboard.activeContracts')}
          value={statsError ? '—' : activeContracts}
          numericValue={statsError ? undefined : activeContracts}
          icon={CreditCard}
          iconBg="bg-violet-600"
          onClick={() => navigate('/contracts')}
          delay={200}
        />
        <StatCard
          title={t('dashboard.availableVehicles')}
          value={statsError ? '—' : availableVehicles}
          numericValue={statsError ? undefined : availableVehicles}
          icon={Car}
          iconBg="bg-emerald-600"
          glow="success"
          onClick={() => navigate('/vehicles')}
          delay={300}
        />
        <StatCard
          title={t('dashboard.reservedVehicles')}
          value={statsError ? '—' : reservedVehicles}
          numericValue={statsError ? undefined : reservedVehicles}
          icon={Car}
          iconBg="bg-amber-600"
          onClick={() => navigate('/vehicles')}
          delay={400}
        />
        <StatCard
          title={t('dashboard.rentedVehicles')}
          value={statsError ? '—' : rentedVehicles}
          numericValue={statsError ? undefined : rentedVehicles}
          icon={Car}
          iconBg="bg-rose-600"
          onClick={() => navigate('/vehicles')}
          delay={500}
        />
      </div>

      {/* Health Score */}
      <HealthScoreBar health={health} />

      {/* Deposit KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <StatCard title={t('dashboard.depositsHeld')} value={`${depositStats.totalHeld} MAD`} numericValue={depositStats.totalHeld} icon={Landmark} iconBg="bg-amber-600" delay={400} />
        <StatCard title={t('dashboard.pendingReturns')} value={depositStats.pendingReturns} numericValue={depositStats.pendingReturns} icon={ArrowDownLeft} iconBg="bg-orange-600" delay={500} />
        <StatCard title={t('dashboard.returnedDeposits')} value={`${depositStats.returned} MAD`} numericValue={depositStats.returned} icon={ArrowUpRight} iconBg="bg-emerald-600" delay={600} />
        <StatCard title={t('dashboard.deductions')} value={`${depositStats.deductions} MAD`} numericValue={depositStats.deductions} icon={Minus} iconBg="bg-rose-600" delay={700} />
      </div>

      {/* Main Grid: Chart + Recent */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4">
        {/* Reservation Chart */}
        <GlassCard className="lg:col-span-7" padding="md" delay={500}>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-5 gap-3">
            <h3 className="text-sm sm:text-base font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.reservationOverview')}</h3>
            <select
              value={selectedPeriod}
              onChange={(e) => setSelectedPeriod(e.target.value)}
              className="text-xs font-medium rounded-xl px-3 py-2 outline-none cursor-pointer transition-colors"
              style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)', border: '1px solid var(--border-subtle)' }}
            >
              <option>{t('dashboard.thisMonth')}</option>
              <option>{t('dashboard.lastMonth')}</option>
            </select>
          </div>
          <div className="flex items-center gap-5 mb-4">
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: '#1e3a5f' }} />
              <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.reservationsLabel')}</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: 'var(--text-muted)' }} />
              <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>{t('dashboard.cancellations')}</span>
            </div>
          </div>
          {hasPositiveValue(reservationData, 'count') ? (
            <SafeChartContainer className="h-[260px] min-h-[260px]" minHeight={260}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={reservationData}>
                  <defs>
                    <linearGradient id="colorRes" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#1e3a5f" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#1e3a5f" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 11, fontWeight: 500 }} dy={8} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: 'var(--text-muted)', fontSize: 11, fontWeight: 500 }} />
                  <Tooltip
                    contentStyle={{
                      borderRadius: '14px',
                      border: 'none',
                      boxShadow: 'var(--shadow-elevated)',
                      padding: '12px 16px',
                      backgroundColor: 'var(--glass-bg)',
                      backdropFilter: 'blur(12px)',
                    }}
                  />
                  <Area type="monotone" dataKey="count" stroke="#1e3a5f" strokeWidth={2.5} fillOpacity={1} fill="url(#colorRes)" />
                </AreaChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          ) : (
            <ChartEmpty />
          )}
        </GlassCard>

        {/* Recent Reservations */}
        <GlassCard className="lg:col-span-5" padding="md" delay={600}>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
            <h3 className="text-sm sm:text-base font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.recentReservations')}</h3>
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => navigate('/reservations')}
              className="text-xs font-medium px-4 py-2 rounded-xl transition-all"
              style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--brand-primary)' }}
            >
              {t('dashboard.seeAll')}
            </motion.button>
          </div>
          <div className="space-y-2">
            {reservations.slice(0, 5).map((res, i) => (
              <motion.div
                key={res.id}
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.7 + i * 0.05 }}
                onClick={() => navigate('/reservations')}
                className="flex items-center justify-between group cursor-pointer p-3 rounded-xl transition-all duration-200 hover:bg-[var(--bg-hover)]"
                style={{ border: '1px solid transparent' }}
                whileHover={{ borderColor: 'var(--border-subtle)' }}
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg flex items-center justify-center transition-all group-hover:scale-105" style={{ backgroundColor: 'var(--bg-hover)' }}>
                    <Car size={16} style={{ color: 'var(--text-muted)' }} />
                  </div>
                  <div>
                    <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{res.vehicleMarque}</p>
                    <p className="text-[11px] font-normal" style={{ color: 'var(--text-muted)' }}>
                      {res.dateStart ? new Date(res.dateStart).toLocaleDateString() : t('dashboard.noStartDate')} - {res.dateEnd ? new Date(res.dateEnd).toLocaleDateString() : t('dashboard.noEndDate')}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{res.totalPrice} DH</p>
                </div>
              </motion.div>
            ))}
            {reservations.length === 0 && (
              <div className="text-center py-4" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noReservationsFound')}</div>
            )}
          </div>
        </GlassCard>
      </div>

      {/* Bottom Grid: Vehicles + Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4 pb-4">
        {/* Vehicles Table */}
        <GlassCard className="lg:col-span-8 overflow-hidden" padding="md" delay={700}>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
            <h3 className="text-sm sm:text-base font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.vehicles')}</h3>
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => navigate('/vehicles')}
              className="text-xs font-medium px-4 py-2 rounded-xl transition-all"
              style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--brand-primary)' }}
            >
              {t('dashboard.seeAllVehicles')}
            </motion.button>
          </div>
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-start min-w-[300px]">
              <thead>
                <tr className="text-[10px] font-bold uppercase tracking-[0.08em]" style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                  <th className="pb-3 pe-2 sm:pe-4">{t('dashboard.vehicle')}</th>
                  <th className="pb-3 pe-2 sm:pe-4">{t('dashboard.status')}</th>
                  <th className="pb-3">{t('dashboard.availability')}</th>
                </tr>
              </thead>
              <tbody className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
                {vehicles.slice(0, 5).map((vehicle) => (
                  <motion.tr
                    key={vehicle.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    onClick={() => navigate('/vehicles')}
                    className="hover:bg-[var(--bg-hover)] transition-colors rounded-xl cursor-pointer group"
                    whileHover={{ x: 2 }}
                  >
                    <td className="py-3 pe-2 sm:pe-4">
                      <div className="flex items-center gap-2 sm:gap-3">
                        <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-lg flex items-center justify-center shrink-0 transition-all" style={{ backgroundColor: 'var(--bg-hover)' }}>
                          <Car size={14} className="sm:hidden" style={{ color: 'var(--text-muted)' }} />
                          <Car size={15} className="hidden sm:block" style={{ color: 'var(--text-muted)' }} />
                        </div>
                        <span className="text-[11px] sm:text-xs font-medium" style={{ color: 'var(--text-primary)' }}>{vehicle.marque}</span>
                      </div>
                    </td>
                    <td className="py-3 pe-2 sm:pe-4">
                      <span
                        className="px-2 py-1 rounded-lg text-[9px] sm:text-[10px] font-bold uppercase"
                        style={{
                          backgroundColor: vehicle.statut === 'AVAILABLE' ? 'rgba(16,185,129,0.1)' : vehicle.statut === 'RENTED' ? 'rgba(245,158,11,0.1)' : 'rgba(239,68,68,0.1)',
                          color: vehicle.statut === 'AVAILABLE' ? '#10b981' : vehicle.statut === 'RENTED' ? '#f59e0b' : '#ef4444',
                        }}
                      >
                        {vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : vehicle.statut === 'RENTED' ? t('dashboard.rented') : t('dashboard.maintenance')}
                      </span>
                    </td>
                    <td className="py-3">
                      <p className="text-[10px] sm:text-[11px] font-medium" style={{ color: 'var(--text-primary)' }}>
                        {vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : (vehicle.statut || 'UNKNOWN').replaceAll('_', ' ')}
                      </p>
                    </td>
                  </motion.tr>
                ))}
                {vehicles.length === 0 && (
                  <tr><td colSpan={3} className="py-4 text-center text-xs sm:text-sm" style={{ color: 'var(--text-muted)' }}>{t('dashboard.noVehiclesFound')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </GlassCard>

        {/* Right Column: Pie + Revenue */}
        <div className="lg:col-span-4 space-y-4">
          <GlassCard delay={800}>
            <h3 className="text-xs sm:text-sm font-bold mb-3" style={{ color: 'var(--text-primary)' }}>{t('dashboard.distribution')}</h3>
            {distributionData.length > 0 ? (
              <SafeChartContainer className="h-36 min-h-36" minHeight={144}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={distributionData} cx="50%" cy="50%" innerRadius={42} outerRadius={60} paddingAngle={4} dataKey="value">
                      {distributionData.map((_e, i) => (
                        <Cell key={`cell-${i}`} fill={PIE_COLORS[i % PIE_COLORS.length]} className="outline-none cursor-pointer hover:opacity-80 transition-opacity" />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ borderRadius: '14px', border: 'none', boxShadow: 'var(--shadow-elevated)', backgroundColor: 'var(--glass-bg)', backdropFilter: 'blur(12px)' }} />
                  </PieChart>
                </ResponsiveContainer>
              </SafeChartContainer>
            ) : (
              <ChartEmpty heightClass="h-36 min-h-36" />
            )}
            <div className="space-y-2 mt-2">
              {reservationStatusData.map((item, i) => (
                <div key={item.name} className="flex justify-between items-center text-[10px] font-medium p-2 rounded-xl transition-colors cursor-pointer hover:bg-[var(--bg-hover)]">
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: PIE_COLORS[i] }} />
                    <span style={{ color: 'var(--text-muted)' }}>{item.name}</span>
                  </div>
                  <span style={{ color: 'var(--text-primary)' }}>{Math.round((item.count / reservationStatusTotal) * 100)}% ({item.count})</span>
                </div>
              ))}
            </div>
          </GlassCard>

          <GlassCard delay={900}>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-3 gap-3">
              <div>
                <p className="text-[10px] font-medium mb-0.5" style={{ color: 'var(--text-muted)' }}>{t('dashboard.revenuePreview')}</p>
                <h4 className="text-sm sm:text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                  {totalRevenue} DH
                </h4>
              </div>
              <select
                className="text-[10px] font-bold uppercase rounded-lg px-2 py-1 outline-none cursor-pointer transition-colors"
                style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)', border: '1px solid var(--border-subtle)' }}
              >
                <option>{t('dashboard.thisMonth')}</option>
                <option>{t('dashboard.year')}</option>
              </select>
            </div>
            {hasPositiveValue(revenueData, 'revenue') ? (
              <SafeChartContainer className="h-24 min-h-24" minHeight={96}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={revenueData}>
                    <Bar dataKey="revenue" fill="#1e3a5f" radius={[5, 5, 0, 0]} barSize={10} className="cursor-pointer hover:opacity-80 transition-opacity" />
                    <Tooltip contentStyle={{ borderRadius: '14px', border: 'none', boxShadow: 'var(--shadow-elevated)', backgroundColor: 'var(--glass-bg)', backdropFilter: 'blur(12px)' }} />
                  </BarChart>
                </ResponsiveContainer>
              </SafeChartContainer>
            ) : (
              <ChartEmpty heightClass="h-24 min-h-24" />
            )}
          </GlassCard>
        </div>
      </div>
    </div>
  );
}
