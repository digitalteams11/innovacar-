import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import SafeChartContainer from '../components/shared/SafeChartContainer';
import { Car, Users, CreditCard, TrendingUp, Loader2 } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { StatCard } from '../components/StatCard';
import EmptyState from '../components/EmptyState';
import ApiErrorState from '../components/ApiErrorState';

const COLORS = ['#1e3a5f', '#c9a96e', '#be123c', '#10b981', '#6366f1'];

export default function Reports() {
  const [loading, setLoading] = useState(true);
  const [revenueData, setRevenueData] = useState<any[]>([]);
  const [vehicleUtilization, setVehicleUtilization] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [loadError, setLoadError] = useState('');

  const { t } = useTranslation();

  const fetchReports = async () => {
    try {
      setLoading(true);
      setLoadError('');
      const [revenueRes, utilRes, dashboardRes] = await Promise.allSettled([
        api.get('/reports/revenue'),
        api.get('/reports/vehicle-utilization'),
        api.get('/dashboard'),
      ]);

      if (revenueRes.status === 'fulfilled') {
        setRevenueData(Array.isArray(revenueRes.value.data) ? revenueRes.value.data : []);
      } else {
        setRevenueData([]);
      }

      if (utilRes.status === 'fulfilled') {
        setVehicleUtilization(Array.isArray(utilRes.value.data) ? utilRes.value.data : []);
      } else {
        setVehicleUtilization([]);
      }

      if (dashboardRes.status === 'fulfilled') {
        setStats(dashboardRes.value.data);
      } else {
        setStats(null);
      }

      const allFailed = [revenueRes, utilRes, dashboardRes].every(r => r.status === 'rejected');
      if (allFailed) {
        setLoadError('Unable to load reports. Please try again.');
      }
    } catch (err: any) {
      console.error('Failed to fetch reports', err);
      setLoadError('Unable to load reports. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (loadError) {
    return <ApiErrorState message={loadError} onRetry={fetchReports} />;
  }

  const totalVehicles = stats?.totalVehicles || 0;
  const rentedVehicles = stats?.rentedVehicles || 0;
  const totalRevenue = stats?.totalRevenue || 0;
  const availableVehicles = totalVehicles - rentedVehicles;

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader title={t('reports.title') || 'Reports'} subtitle={t('reports.subtitle') || 'Business analytics and insights'} icon={TrendingUp} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
        <StatCard title="Total Fleet" value={totalVehicles} numericValue={totalVehicles} icon={Car} iconBg="bg-stone-700" />
        <StatCard title="Active Rentals" value={rentedVehicles} numericValue={rentedVehicles} icon={TrendingUp} iconBg="bg-emerald-600" />
        <StatCard title="Total Revenue" value={`${totalRevenue} DH`} numericValue={totalRevenue} icon={CreditCard} iconBg="bg-[var(--brand-primary)]" iconColor="text-[var(--brand-primary-foreground,#171817)]" />
        <StatCard title="Available" value={availableVehicles} numericValue={availableVehicles} icon={Users} iconBg="bg-[#477d91]" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-4">
        <div className="data-surface p-4 sm:p-5">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">Revenue Overview</h3>
          {revenueData.length > 0 ? (
            <SafeChartContainer className="h-[250px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={revenueData}>
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} />
                  <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 8px 32px -8px rgba(0,0,0,0.12)' }} />
                  <Bar dataKey="revenue" fill="#1e3a5f" radius={[5, 5, 0, 0]} barSize={30} />
                </BarChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          ) : (
            <EmptyState
              size="sm"
              icon={TrendingUp}
              title="No revenue yet"
              description="Paid invoices will appear here as soon as revenue is recorded."
              className="min-h-[250px]"
            />
          )}
        </div>

        <div className="data-surface p-4 sm:p-5">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">Vehicle Utilization</h3>
          {vehicleUtilization.length > 0 ? (
            <SafeChartContainer className="h-[250px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={vehicleUtilization} cx="50%" cy="50%" innerRadius={60} outerRadius={90} paddingAngle={4} dataKey="value">
                    {vehicleUtilization.map((_e: any, i: number) => (
                      <Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          ) : (
            <EmptyState
              size="sm"
              icon={Car}
              title="No fleet utilization yet"
              description="Vehicles will appear here once fleet records exist."
              className="min-h-[250px]"
            />
          )}
        </div>
      </div>
    </div>
  );
}
