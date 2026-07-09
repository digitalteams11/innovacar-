import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Building2, Users, Car, Calendar, FileText,
  CreditCard, Satellite,
  Activity, ArrowRight, Zap, AlertTriangle,
  Server, Shield, Plus, Bell, Globe, HardDrive
} from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts';
import { StatCard } from '../../components/superadmin';

const COLORS = ['#0a0f2c', '#3b82f6', '#10b981', '#f59e0b', '#ef4444'];

const healthTone = (status: string) => {
  if (status === 'healthy') return 'bg-emerald-500';
  if (status === 'degraded' || status === 'not_configured') return 'bg-amber-500';
  if (status === 'checking') return 'bg-slate-400';
  return 'bg-rose-500';
};

const healthText = (status: string) => {
  if (status === 'healthy') return 'Operational';
  if (status === 'degraded') return 'Degraded';
  if (status === 'not_configured') return 'Not configured';
  if (status === 'checking') return 'Checking';
  return 'Down';
};

const healthTextTone = (status: string) => {
  if (status === 'healthy') return 'text-emerald-600 dark:text-emerald-400';
  if (status === 'degraded' || status === 'not_configured') return 'text-amber-600 dark:text-amber-400';
  if (status === 'checking') return 'text-slate-500 dark:text-slate-400';
  return 'text-rose-600 dark:text-rose-400';
};

const formatBytes = (bytes: number) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** index)).toFixed(index > 1 ? 1 : 0)} ${units[index]}`;
};

export default function SuperAdminDashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [stats, setStats] = useState<any>(null);
  const [activity, setActivity] = useState<any[]>([]);
  const [revenueData, setRevenueData] = useState<any[]>([]);
  const [health, setHealth] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, activityRes, revenueRes, healthRes] = await Promise.all([
          superAdminApi.getDashboardStats(),
          superAdminApi.getRecentActivity(),
          superAdminApi.getRevenueStats(),
          superAdminApi.getPlatformHealth().catch(() => ({ data: null })),
        ]);
        setStats(statsRes.data);
        setActivity(activityRes.data.slice(0, 10));
        setRevenueData(revenueRes.data.monthlyTrend || []);
        setHealth(healthRes.data);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const agencyStatusData = stats ? [
    { name: 'Active', value: stats.activeAgencies || 0 },
    { name: 'Trial', value: stats.trialAgencies || 0 },
    { name: 'Expired', value: stats.expiredAgencies || 0 },
    { name: 'Suspended', value: stats.suspendedAgencies || 0 },
    { name: 'Blocked', value: stats.blockedAgencies || 0 },
  ] : [];

  const criticalAlerts = [
    ...(stats?.trialAgencies > 0 ? [{ type: 'trial', message: `${stats.trialAgencies} agencies in trial period`, severity: 'info' as const }] : []),
    ...(stats?.openTickets > 0 ? [{ type: 'ticket', message: `${stats.openTickets} open support tickets`, severity: 'warning' as const }] : []),
    ...(stats?.failedPaymentsLast30Days > 0 ? [{ type: 'payment', message: `${stats.failedPaymentsLast30Days} failed payments (30d)`, severity: 'danger' as const }] : []),
  ];

  return (
    <div className="space-y-6 animate-fade">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">{t('superAdmin.dashboard.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">{t('superAdmin.dashboard.subtitle')}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-2 bg-white dark:bg-[#1a2332]/70 px-3 py-2 rounded-xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
            <Activity size={16} className="text-emerald-500" />
            <span className="text-sm font-medium text-[#1e293b] dark:text-white">{t('superAdmin.dashboard.live')}</span>
            <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse" />
          </div>
        </div>
      </div>

      {/* Critical Alerts Banner */}
      {criticalAlerts.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {criticalAlerts.map((alert, idx) => (
            <div key={idx} className={`flex items-center gap-3 px-4 py-3 rounded-xl border shadow-soft ${
              alert.severity === 'danger' ? 'bg-rose-50 dark:bg-rose-500/10 border-rose-200 dark:border-rose-500/20' :
              alert.severity === 'warning' ? 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/20' :
              'bg-blue-50 dark:bg-blue-500/10 border-blue-200 dark:border-blue-500/20'
            }`}>
              <AlertTriangle size={18} className={
                alert.severity === 'danger' ? 'text-rose-500' :
                alert.severity === 'warning' ? 'text-amber-500' : 'text-blue-500'
              } />
              <span className="text-sm font-medium text-[#1e293b] dark:text-white">{alert.message}</span>
            </div>
          ))}
        </div>
      )}

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title={t('superAdmin.dashboard.totalAgencies')}
          value={stats?.totalAgencies || 0}
          icon={Building2}
          tone="blue"
          onClick={() => navigate('/super-admin/agencies')}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.dashboard.activeUsers')}
          value={stats?.totalUsers || 0}
          icon={Users}
          tone="violet"
          onClick={() => navigate('/super-admin/users')}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.dashboard.totalVehicles')}
          value={stats?.totalVehicles || 0}
          icon={Car}
          tone="neutral"
          onClick={() => navigate('/super-admin/agencies')}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.dashboard.totalReservations')}
          value={stats?.totalReservations || 0}
          icon={Calendar}
          tone="amber"
          onClick={() => navigate('/super-admin/analytics')}
          loading={loading}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Monthly Recurring Revenue"
          value={`${stats?.monthlyRevenue?.toLocaleString() || 0} MAD`}
          icon={CreditCard}
          tone="emerald"
          onClick={() => navigate('/super-admin/payments')}
          loading={loading}
        />
        <StatCard
          title="Annual Recurring Revenue"
          value={`${stats?.yearlyRevenue?.toLocaleString() || 0} MAD`}
          icon={Zap}
          tone="emerald"
          onClick={() => navigate('/super-admin/payments')}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.dashboard.activeSubscriptions')}
          value={stats?.activeSubscriptions || 0}
          icon={FileText}
          tone="blue"
          onClick={() => navigate('/super-admin/subscriptions')}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.dashboard.activeGps')}
          value={stats?.activeGpsConnections || 0}
          icon={Satellite}
          tone="violet"
          onClick={() => navigate('/super-admin/gps')}
          loading={loading}
        />
      </div>

      {/* Platform Health + Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">Platform Health</h3>
          <div className="space-y-3">
            {[
              { label: 'API Server', status: health?.apiStatus || (loading ? 'checking' : 'down'), icon: Server },
              { label: 'Database', status: health?.dbStatus || (loading ? 'checking' : 'down'), icon: Globe },
              { label: 'Storage', status: health?.storageStatus || (loading ? 'checking' : 'down'), icon: HardDrive },
              { label: 'GPS Services', status: health?.gpsStatus || (loading ? 'checking' : 'not_configured'), icon: Satellite },
              { label: 'Email Service', status: health?.emailStatus || (loading ? 'checking' : 'not_configured'), icon: Bell },
            ].map((service) => (
              <div key={service.label} className="flex items-center justify-between py-2">
                <div className="flex items-center gap-3">
                  <service.icon size={16} className="text-slate-400" />
                  <span className="text-sm text-[#1e293b] dark:text-white">{service.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${healthTone(service.status)}`} />
                  <span className={`text-xs font-medium ${healthTextTone(service.status)}`}>{healthText(service.status)}</span>
                </div>
              </div>
            ))}
          </div>
          {health && (
            <div className="mt-4 grid grid-cols-2 gap-2 border-t border-[#e8e6e1]/70 pt-4 text-xs dark:border-white/5">
              <div className="rounded-lg bg-slate-50 p-2.5 dark:bg-white/[0.03]"><span className="block text-slate-400">DB latency</span><strong className="text-[#1e293b] dark:text-white">{health.dbLatencyMs ?? 0} ms</strong></div>
              <div className="rounded-lg bg-slate-50 p-2.5 dark:bg-white/[0.03]"><span className="block text-slate-400">App storage</span><strong className="text-[#1e293b] dark:text-white">{formatBytes(health.storageUsedBytes || 0)}</strong></div>
              <div className="rounded-lg bg-slate-50 p-2.5 dark:bg-white/[0.03]"><span className="block text-slate-400">Errors (24h)</span><strong className="text-[#1e293b] dark:text-white">{health.errorsLast24Hours || 0}</strong></div>
            </div>
          )}
        </div>

        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">Quick Actions</h3>
          <div className="grid grid-cols-2 gap-3">
            {[
              { label: 'Create Agency', icon: Plus, action: () => navigate('/super-admin/agencies') },
              { label: 'Send Email', icon: Bell, action: () => navigate('/super-admin/emails') },
              { label: 'View Alerts', icon: AlertTriangle, action: () => navigate('/super-admin/notifications') },
              { label: 'Security', icon: Shield, action: () => navigate('/super-admin/security') },
            ].map((action) => (
              <button
                key={action.label}
                onClick={action.action}
                className="flex flex-col items-center gap-2 p-4 rounded-xl border border-[#e8e6e1]/60 dark:border-white/5 hover:border-brand-300 dark:hover:border-brand-500/30 hover:bg-brand-50 dark:hover:bg-brand-500/5 transition-all"
              >
                <action.icon size={20} className="text-[#0a0f2c] dark:text-white" />
                <span className="text-xs font-medium text-[#1e293b] dark:text-white">{action.label}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">Recent Signups</h3>
          <div className="space-y-3">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 animate-pulse">
                  <div className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-slate-700" />
                  <div className="flex-1">
                    <div className="h-3 w-24 bg-slate-200 dark:bg-slate-700 rounded mb-1" />
                    <div className="h-2 w-16 bg-slate-200 dark:bg-slate-700 rounded" />
                  </div>
                </div>
              ))
            ) : activity.slice(0, 5).map((item: any) => (
              <div key={item.id} className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
                  <Building2 size={14} className="text-[#0a0f2c] dark:text-white/70" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-[#1e293b] dark:text-white truncate">{item.action}</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 truncate">{item.description}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">
        {/* Revenue Chart */}
        <div className="lg:col-span-2 bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-6">{t('superAdmin.dashboard.revenueTrend')}</h3>
          <div className="h-[280px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={revenueData}>
                <defs>
                  <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0a0f2c" stopOpacity={0.1}/>
                    <stop offset="95%" stopColor="#0a0f2c" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="month" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} tickFormatter={(v) => `${v}`} />
                <Tooltip
                  contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
                  formatter={(value: any) => [`${value?.toLocaleString()} MAD`, 'Revenue']}
                />
                <Area type="monotone" dataKey="revenue" stroke="#0a0f2c" strokeWidth={2} fillOpacity={1} fill="url(#colorRevenue)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Agency Status Distribution */}
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-6">{t('superAdmin.dashboard.agencyStatus')}</h3>
          <div className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={agencyStatusData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={90}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {agencyStatusData.map((_entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="grid grid-cols-2 gap-2 mt-4">
            {agencyStatusData.map((item: any, idx: number) => (
              <div key={item.name} className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[idx] }} />
                <span className="text-xs text-slate-500 dark:text-slate-400">{item.name}: <span className="font-semibold text-[#1e293b] dark:text-white">{item.value}</span></span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Activity Feed */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{t('superAdmin.dashboard.recentActivity')}</h3>
          <button
            onClick={() => navigate('/super-admin/security')}
            className="text-sm text-brand-600 hover:text-brand-700 font-medium flex items-center gap-1"
          >
            {t('superAdmin.dashboard.viewAll')} <ArrowRight size={14} />
          </button>
        </div>
        <div className="space-y-3">
          {activity.length === 0 && (
            <p className="text-slate-400 text-sm text-center py-8">{t('superAdmin.dashboard.noActivity')}</p>
          )}
          {activity.map((item: any) => (
            <div key={item.id} className="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 dark:hover:bg-white/5 transition-colors">
              <div className={`w-2 h-2 rounded-full mt-2 shrink-0 ${item.isSuccess ? 'bg-emerald-400' : 'bg-rose-400'}`} />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-[#1e293b] dark:text-white">{item.action}</p>
                <p className="text-xs text-slate-500 dark:text-slate-400 truncate">{item.description}</p>
              </div>
              <span className="text-xs text-slate-400 dark:text-slate-500 shrink-0">
                {item.timestamp ? new Date(item.timestamp).toLocaleDateString() : '-'}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
