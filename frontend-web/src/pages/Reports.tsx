import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import SafeChartContainer from '../components/shared/SafeChartContainer';
import { Car, Users, CreditCard, TrendingUp, Loader2 } from 'lucide-react';

const COLORS = ['#1e3a5f', '#c9a96e', '#be123c', '#10b981', '#6366f1'];

export default function Reports() {
  const [loading, setLoading] = useState(true);
  const [revenueData, setRevenueData] = useState<any[]>([]);
  const [vehicleUtilization, setVehicleUtilization] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);

  const { t } = useTranslation();

  useEffect(() => {
    const fetchReports = async () => {
      try {
        const [revenueRes, utilRes, dashboardRes] = await Promise.all([
          api.get('/reports/revenue'),
          api.get('/reports/vehicle-utilization'),
          api.get('/dashboard'),
        ]);
        setRevenueData(revenueRes.data || []);
        setVehicleUtilization(utilRes.data || []);
        setStats(dashboardRes.data);
      } catch (err) {
        console.error('Failed to fetch reports', err);
      } finally {
        setLoading(false);
      }
    };
    fetchReports();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  const totalVehicles = stats?.totalVehicles || 0;
  const rentedVehicles = stats?.rentedVehicles || 0;
  const totalRevenue = stats?.totalRevenue || 0;
  const availableVehicles = totalVehicles - rentedVehicles;

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div>
        <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('reports.title') || 'Reports'}</h1>
        <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('reports.subtitle') || 'Business analytics and insights'}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
        <div className="card-premium flex items-center gap-4 p-3 sm:p-5">
          <div className="w-12 h-12 rounded-xl bg-brand-500 flex items-center justify-center text-white">
            <Car size={22} />
          </div>
          <div className="min-w-0">
            <p className="text-slate-500 text-xs font-medium">Total Fleet</p>
            <h3 className="text-xl font-bold text-[#1e293b]">{totalVehicles}</h3>
          </div>
        </div>
        <div className="card-premium flex items-center gap-4 p-3 sm:p-5">
          <div className="w-12 h-12 rounded-xl bg-success-500 flex items-center justify-center text-white">
            <TrendingUp size={22} />
          </div>
          <div className="min-w-0">
            <p className="text-slate-500 text-xs font-medium">Active Rentals</p>
            <h3 className="text-xl font-bold text-[#1e293b]">{rentedVehicles}</h3>
          </div>
        </div>
        <div className="card-premium flex items-center gap-4 p-3 sm:p-5">
          <div className="w-12 h-12 rounded-xl bg-accent-500 flex items-center justify-center text-white">
            <CreditCard size={22} />
          </div>
          <div className="min-w-0">
            <p className="text-slate-500 text-xs font-medium">Total Revenue</p>
            <h3 className="text-xl font-bold text-[#1e293b]">{totalRevenue} DH</h3>
          </div>
        </div>
        <div className="card-premium flex items-center gap-4 p-3 sm:p-5">
          <div className="w-12 h-12 rounded-xl bg-[#1e293b] flex items-center justify-center text-white">
            <Users size={22} />
          </div>
          <div className="min-w-0">
            <p className="text-slate-500 text-xs font-medium">Available</p>
            <h3 className="text-xl font-bold text-[#1e293b]">{availableVehicles}</h3>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-4">
        <div className="card-premium p-3 sm:p-5">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">Revenue Overview</h3>
          <SafeChartContainer className="h-[250px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={revenueData.length > 0 ? revenueData : [
                { name: 'Jan', revenue: 0 }, { name: 'Feb', revenue: 0 }, { name: 'Mar', revenue: 0 },
                { name: 'Apr', revenue: 0 }, { name: 'May', revenue: 0 }, { name: 'Jun', revenue: 0 },
              ]}>
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} />
                <YAxis axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 11 }} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 8px 32px -8px rgba(0,0,0,0.12)' }} />
                <Bar dataKey="revenue" fill="#1e3a5f" radius={[5, 5, 0, 0]} barSize={30} />
              </BarChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>

        <div className="card-premium p-3 sm:p-5">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">Vehicle Utilization</h3>
          <SafeChartContainer className="h-[250px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={vehicleUtilization.length > 0 ? vehicleUtilization : [
                  { name: 'Available', value: availableVehicles || 1 },
                  { name: 'Rented', value: rentedVehicles || 1 },
                ]} cx="50%" cy="50%" innerRadius={60} outerRadius={90} paddingAngle={4} dataKey="value">
                  {(vehicleUtilization.length > 0 ? vehicleUtilization : [
                    { name: 'Available', value: availableVehicles || 1 },
                    { name: 'Rented', value: rentedVehicles || 1 },
                  ]).map((_e: any, i: number) => (
                    <Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>
      </div>
    </div>
  );
}
