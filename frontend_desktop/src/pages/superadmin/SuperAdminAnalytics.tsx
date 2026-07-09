import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  TrendingUp, Users, Building2, Calendar,
  ArrowUpRight, CreditCard, DollarSign, PieChart as PieChartIcon
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, LineChart, Line
} from 'recharts';
import SafeChartContainer from '../../components/shared/SafeChartContainer';

const COLORS = ['#0a0f2c', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];

export default function SuperAdminAnalytics() {
  const { t } = useTranslation();
  const [growth, setGrowth] = useState<any>(null);
  const [agencyStats, setAgencyStats] = useState<any>(null);
  const [reservationStats, setReservationStats] = useState<any>(null);
  const [revenue, setRevenue] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [growthRes, agencyRes, resRes, revRes] = await Promise.all([
          superAdminApi.getGrowthAnalytics(),
          superAdminApi.getAgencyAnalytics(),
          superAdminApi.getReservationAnalytics(),
          superAdminApi.getRevenueStats(),
        ]);
        setGrowth(growthRes.data);
        setAgencyStats(agencyRes.data);
        setReservationStats(resRes.data);
        setRevenue(revRes.data);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const statusData = agencyStats?.byStatus ? Object.entries(agencyStats.byStatus).map(([name, value]) => ({ name, value })) : [];
  const planData = agencyStats?.byPlan ? Object.entries(agencyStats.byPlan).map(([name, value]) => ({ name, value })) : [];
  const planRevenueData = revenue?.revenueByPlan ? revenue.revenueByPlan.map((p: any) => ({ name: p.planName, value: p.revenue })) : [];

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] tracking-tight">{t('superAdmin.analytics.title')}</h1>
          <p className="text-slate-500 text-sm mt-1">{t('superAdmin.analytics.subtitle')}</p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 flex items-center justify-center">
              <Building2 size={18} className="text-blue-600" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">{t('superAdmin.analytics.totalAgencies')}</p>
              <p className="text-xl font-bold text-[#1e293b]">{growth?.totalAgencies || 0}</p>
            </div>
          </div>
          <div className="flex items-center gap-1 text-xs text-emerald-600">
            <ArrowUpRight size={12} />
            <span>{growth?.newAgenciesThisMonth || 0} {t('superAdmin.analytics.thisMonth')}</span>
          </div>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center">
              <Users size={18} className="text-emerald-600" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">{t('superAdmin.analytics.activeAgencies')}</p>
              <p className="text-xl font-bold text-[#1e293b]">{growth?.activeAgencies || 0}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-[#0a0f2c]/5 flex items-center justify-center">
              <Calendar size={18} className="text-[#0a0f2c]" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">{t('superAdmin.analytics.totalReservations')}</p>
              <p className="text-xl font-bold text-[#1e293b]">{reservationStats?.totalReservations || 0}</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center">
              <TrendingUp size={18} className="text-amber-600" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">{t('superAdmin.analytics.totalRevenue')}</p>
              <p className="text-xl font-bold text-[#1e293b]">{revenue?.totalRevenue?.toLocaleString() || 0} MAD</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-violet-50 flex items-center justify-center">
              <CreditCard size={18} className="text-violet-600" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">MRR</p>
              <p className="text-xl font-bold text-[#1e293b]">{revenue?.monthlyRevenue?.toLocaleString() || 0} MAD</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-rose-50 flex items-center justify-center">
              <DollarSign size={18} className="text-rose-600" />
            </div>
            <div>
              <p className="text-slate-500 text-xs font-medium">ARPU</p>
              <p className="text-xl font-bold text-[#1e293b]">{revenue?.avgRevenuePerAgency?.toLocaleString() || 0} MAD</p>
            </div>
          </div>
        </div>
      </div>

      {/* Subscription Metrics Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft lg:col-span-2">
          <h3 className="text-base font-bold text-[#1e293b] mb-4 flex items-center gap-2">
            <PieChartIcon size={18} className="text-violet-600" />
            Revenue by Plan
          </h3>
          <SafeChartContainer className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={planRevenueData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} formatter={(value: any) => [`${Number(value).toLocaleString()} MAD`, 'Revenue']} />
                <Bar dataKey="value" fill="#8b5cf6" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">Plan Performance</h3>
          <div className="space-y-4">
            {revenue?.revenueByPlan?.map((plan: any, idx: number) => {
              const total = revenue.revenueByPlan.reduce((sum: number, p: any) => sum + (p.revenue || 0), 0);
              const pct = total > 0 ? Math.round((plan.revenue / total) * 100) : 0;
              return (
                <div key={plan.planName}>
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-[#1e293b]">{plan.planName}</span>
                    <span className="text-sm text-slate-500">{plan.revenue?.toLocaleString()} MAD</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: COLORS[idx % COLORS.length] }} />
                    </div>
                    <span className="text-xs font-medium text-slate-500 w-8">{pct}%</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] mb-4">{t('superAdmin.analytics.agencyStatus')}</h3>
          <SafeChartContainer className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={statusData} cx="50%" cy="50%" innerRadius={60} outerRadius={90} paddingAngle={4} dataKey="value">
                  {statusData.map((_entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </SafeChartContainer>
          <div className="flex flex-wrap justify-center gap-3 mt-4">
            {statusData.map((item: any, idx: number) => (
              <div key={item.name} className="flex items-center gap-1.5">
                <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: COLORS[idx] }} />
                <span className="text-xs text-slate-500">{item.name}: <span className="font-semibold text-[#1e293b]">{item.value}</span></span>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.analytics.plansDistribution')}</h3>
          <SafeChartContainer className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={planData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                <Bar dataKey="value" fill="#0a0f2c" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] mb-4">{t('superAdmin.analytics.revenueTrend')}</h3>
          <SafeChartContainer className="h-[260px]">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={revenue?.monthlyTrend || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="month" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                <Line type="monotone" dataKey="revenue" stroke="#0a0f2c" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>

        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.analytics.countries')}</h3>
          <div className="space-y-3">
            {agencyStats?.byCountry && Object.entries(agencyStats.byCountry)
              .sort(([,a]: any, [,b]: any) => b - a)
              .slice(0, 8)
              .map(([country, count]: any, idx: number) => {
                const total = Object.values(agencyStats.byCountry as Record<string, number>).reduce((a: number, b: number) => a + b, 0);
                const pct = total > 0 ? Math.round((count / total) * 100) : 0;
                return (
                  <div key={country} className="flex items-center gap-3">
                    <span className="text-sm text-slate-500 w-24 truncate">{country}</span>
                    <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: COLORS[idx % COLORS.length] }} />
                    </div>
                    <span className="text-sm font-medium text-[#1e293b] w-8 text-right">{count}</span>
                  </div>
                );
              })}
          </div>
        </div>
      </div>
    </div>
  );
}
