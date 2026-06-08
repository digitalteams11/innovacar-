import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, AreaChart, Area } from 'recharts';
import SafeChartContainer from '../components/shared/SafeChartContainer';
import { Users, Car, Calendar, CreditCard, Landmark, ArrowDownLeft, ArrowUpRight, Minus, Gauge } from 'lucide-react';
import api from '../api/axios';

const COLORS = ['#1e3a5f', '#c9a96e', '#be123c'];

const KpiCard = ({ title, value, icon: Icon, trend, trendValue, iconBg, onClick }: any) => (
  <div onClick={onClick} className="card-premium flex items-center gap-4 hover:-translate-y-0.5 transition-all duration-300 cursor-pointer group">
    <div className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 ${iconBg}`}>
      <Icon size={22} className="text-white" />
    </div>
    <div className="flex-1 min-w-0">
      <p className="text-slate-500 text-xs font-medium mb-0.5 tracking-wide">{title}</p>
      <h3 className="text-xl font-bold text-[#1e293b] group-hover:text-brand-500 transition-colors">{value}</h3>
      <div className="flex items-center gap-1.5 mt-1">
        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-md ${trend === 'up' ? 'bg-success-50 text-success-500' : 'bg-danger-50 text-danger-500'}`}>
          {trend === 'up' ? '↑' : '↓'} {trendValue}%
        </span>
        <span className="text-[10px] text-slate-400 font-normal">vs last month</span>
      </div>
    </div>
  </div>
);

export default function Dashboard() {
  const [selectedPeriod, setSelectedPeriod] = useState('This Month');
  const [stats, setStats] = useState<any>(null);
  const [vehicles, setVehicles] = useState<any[]>([]);
  const [reservations, setReservations] = useState<any[]>([]);
  const [health, setHealth] = useState<any>(null);
  const { showToast } = useToast();
  const { t } = useTranslation();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, vehiclesRes, reservationsRes, healthRes] = await Promise.all([
          api.get('/dashboard'),
          api.get('/vehicles'),
          api.get('/reservations'),
          api.get('/saas-health'),
        ]);
        setStats(statsRes.data);
        setVehicles(vehiclesRes.data);
        setReservations(reservationsRes.data);
        setHealth(healthRes.data);
      } catch (err) {
        console.error('Failed to fetch dashboard data', err);
      } finally {
        // loading state removed
      }
    };
    fetchData();
  }, []);

  const totalVehicles = stats?.totalVehicles || 0;
  const rentedVehicles = stats?.rentedVehicles || 0;
  const totalRevenue = stats?.totalRevenue || 0;
  const totalClients = stats?.totalClients || 0;
  const availableVehicles = totalVehicles - rentedVehicles;

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
    count: reservations.filter((res) => new Date(res.dateStart).getDay() === (index + 1) % 7).length,
  }));

  const distributionData = [
    { name: t('dashboard.available'), value: availableVehicles },
    { name: t('dashboard.rented'), value: rentedVehicles },
    { name: t('dashboard.maintenance'), value: vehicles.filter((vehicle) => vehicle.statut !== 'AVAILABLE' && vehicle.statut !== 'RENTED').length },
  ].filter((item) => item.value > 0);

  const reservationStatusData = [
    { name: t('dashboard.confirmed'), count: reservations.filter((res) => res.status === 'CONFIRMED').length },
    { name: t('dashboard.pending'), count: reservations.filter((res) => res.status === 'PENDING').length },
    { name: t('dashboard.cancelled'), count: reservations.filter((res) => res.status === 'CANCELLED').length },
  ];
  const reservationStatusTotal = Math.max(reservationStatusData.reduce((sum, item) => sum + item.count, 0), 1);

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
        <KpiCard title={t('dashboard.reservationsThisMonth')} value={reservations.length} icon={Calendar} trend="up" trendValue="12.5" iconBg="bg-brand-500" onClick={() => navigate('/reservations')} />
        <KpiCard title={t('dashboard.availableVehicles')} value={availableVehicles} icon={Car} trend="up" trendValue="8.3" iconBg="bg-success-500" onClick={() => navigate('/vehicles')} />
        <KpiCard title={t('dashboard.revenue')} value={`${totalRevenue} DH`} icon={CreditCard} trend="up" trendValue="15.7" iconBg="bg-accent-500" onClick={() => navigate('/payments')} />
        <KpiCard title={t('dashboard.activeClients')} value={totalClients} icon={Users} trend="up" trendValue="0" iconBg="bg-[#1e293b]" onClick={() => navigate('/clients')} />
      </div>

      <div className="bg-white border border-[#e8e6e1] p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="w-11 h-11 bg-brand-50 text-brand-500 flex items-center justify-center rounded-lg shrink-0">
          <Gauge size={21} />
        </div>
        <div className="flex-1">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase">SaaS Health Score</p>
              <p className="text-lg font-bold text-[#1e293b]">{health?.score || 0}/100 · {health?.label || 'Poor'}</p>
            </div>
            <span className="text-xs font-bold text-slate-500">{health?.risk === 'HEALTHY' ? 'Healthy' : health?.risk?.replaceAll('_', ' ') || 'Loading'}</span>
          </div>
          <div className="mt-3 h-2 bg-slate-100 rounded-full overflow-hidden">
            <div className="h-full bg-brand-500 transition-all" style={{ width: `${Math.min(health?.score || 0, 100)}%` }} />
          </div>
        </div>
      </div>

      {/* Deposit KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <KpiCard title="Deposits Held" value={`${depositStats.totalHeld} MAD`} icon={Landmark} trend="up" trendValue="5.2" iconBg="bg-amber-500" onClick={() => navigate('/contracts')} />
        <KpiCard title="Pending Returns" value={depositStats.pendingReturns} icon={ArrowDownLeft} trend="down" trendValue="2.1" iconBg="bg-orange-500" onClick={() => navigate('/contracts')} />
        <KpiCard title="Returned Deposits" value={`${depositStats.returned} MAD`} icon={ArrowUpRight} trend="up" trendValue="8.4" iconBg="bg-emerald-500" onClick={() => navigate('/contracts')} />
        <KpiCard title="Deductions" value={`${depositStats.deductions} MAD`} icon={Minus} trend="up" trendValue="3.7" iconBg="bg-rose-500" onClick={() => navigate('/contracts')} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4">
        <div className="lg:col-span-7 card-premium p-3 sm:p-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-5 gap-3">
            <h3 className="text-sm sm:text-base font-bold text-[#1e293b]">{t('dashboard.reservationOverview')}</h3>
            <select value={selectedPeriod} onChange={(e) => { setSelectedPeriod(e.target.value); showToast(t('toast.filterApplied', { action: e.target.value })); }}
              className="bg-[#f5f5f0] text-xs font-medium text-slate-500 rounded-xl px-3 py-2 outline-none cursor-pointer border border-[#e8e6e1] hover:bg-[#ebe9e4] transition-colors">
              <option>{t('dashboard.thisMonth')}</option>
              <option>{t('dashboard.lastMonth')}</option>
            </select>
          </div>
          <div className="flex items-center gap-5 mb-4">
             <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-brand-500"></div><span className="text-xs font-medium text-slate-500">{t('dashboard.reservationsLabel')}</span></div>
             <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-slate-300"></div><span className="text-xs font-medium text-slate-500">{t('dashboard.cancellations')}</span></div>
          </div>
          <SafeChartContainer className="h-[220px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={reservationData}>
                <defs><linearGradient id="colorRes" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#1e3a5f" stopOpacity={0.12}/><stop offset="95%" stopColor="#1e3a5f" stopOpacity={0}/></linearGradient></defs>
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 11, fontWeight: 500}} dy={8} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 11, fontWeight: 500}} />
                <Tooltip contentStyle={{borderRadius: '12px', border: 'none', boxShadow: '0 8px 32px -8px rgba(0,0,0,0.12)', padding: '10px 14px'}} />
                <Area type="monotone" dataKey="count" stroke="#1e3a5f" strokeWidth={2.5} fillOpacity={1} fill="url(#colorRes)" />
              </AreaChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>

        <div className="lg:col-span-5 card-premium p-3 sm:p-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
            <h3 className="text-sm sm:text-base font-bold text-[#1e293b]">{t('dashboard.recentReservations')}</h3>
            <button onClick={() => navigate('/reservations')} className="text-brand-500 text-xs font-medium bg-brand-50 px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl hover:bg-brand-500 hover:text-white active:scale-95 transition-all duration-200">{t('dashboard.seeAll')}</button>
          </div>
          <div className="space-y-2">
            {reservations.slice(0, 5).map((res) => (
              <div key={res.id} onClick={() => navigate('/reservations')} className="flex items-center justify-between group cursor-pointer hover:bg-[#f5f5f0] p-3 rounded-xl transition-all duration-200 border border-transparent hover:border-[#e8e6e1]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-[#f5f5f0] rounded-lg flex items-center justify-center border border-[#e8e6e1] group-hover:scale-105 transition-transform">
                    <Car size={16} className="text-slate-400" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-[#1e293b]">{res.vehicleMarque}</p>
                    <p className="text-[11px] font-normal text-slate-400">{new Date(res.dateStart).toLocaleDateString()} - {new Date(res.dateEnd).toLocaleDateString()}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-[#1e293b] mt-0.5">{res.totalPrice} DH</p>
                </div>
              </div>
            ))}
            {reservations.length === 0 && (
              <div className="text-center text-slate-400 text-sm py-4">No reservations found</div>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4 pb-4">
        <div className="lg:col-span-8 card-premium overflow-hidden p-3 sm:p-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
            <h3 className="text-sm sm:text-base font-bold text-[#1e293b]">{t('dashboard.vehicles')}</h3>
            <button onClick={() => navigate('/vehicles')} className="text-brand-500 text-xs font-medium bg-brand-50 px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl hover:bg-brand-500 hover:text-white transition-all duration-200">{t('dashboard.seeAllVehicles')}</button>
          </div>
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left min-w-[300px]">
              <thead>
                <tr className="text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em] border-b border-[#e8e6e1]">
                  <th className="pb-3 pr-2 sm:pr-4">{t('dashboard.vehicle')}</th>
                  <th className="pb-3 pr-2 sm:pr-4">{t('dashboard.status')}</th>
                  <th className="pb-3">{t('dashboard.availability')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/60">
                {vehicles.slice(0, 5).map((vehicle) => (
                  <tr key={vehicle.id} onClick={() => navigate('/vehicles')} className="hover:bg-[#f5f5f0]/60 transition-colors rounded-xl cursor-pointer group">
                    <td className="py-3 pr-2 sm:pr-4">
                      <div className="flex items-center gap-2 sm:gap-3">
                         <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-lg bg-[#f5f5f0] flex items-center justify-center shrink-0 group-hover:bg-white group-hover:shadow-sm transition-all">
                            <Car size={14} className="text-slate-400 group-hover:text-brand-500 transition-colors sm:hidden" />
                            <Car size={15} className="text-slate-400 group-hover:text-brand-500 transition-colors hidden sm:block" />
                         </div>
                         <span className="text-[11px] sm:text-xs font-medium text-[#1e293b]">{vehicle.marque}</span>
                      </div>
                    </td>
                    <td className="py-3 pr-2 sm:pr-4">
                      <span className={`px-1.5 sm:px-2 py-0.5 sm:py-1 rounded-lg text-[9px] sm:text-[10px] font-bold uppercase ${
                        vehicle.statut === 'AVAILABLE' ? 'bg-success-50 text-success-500' : vehicle.statut === 'RENTED' ? 'bg-warning-50 text-warning-500' : 'bg-danger-50 text-danger-500'
                      }`}>
                        {vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : vehicle.statut === 'RENTED' ? t('dashboard.rented') : t('dashboard.maintenance')}
                      </span>
                    </td>
                    <td className="py-3"><p className="text-[10px] sm:text-[11px] font-medium text-[#1e293b]">{vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : "Jusqu'au 25/05"}</p></td>
                  </tr>
                ))}
                {vehicles.length === 0 && (
                  <tr><td colSpan={3} className="py-4 text-center text-slate-400 text-xs sm:text-sm">No vehicles found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="lg:col-span-4 space-y-4">
          <div className="card-premium hover:shadow-elevated transition-shadow duration-300">
            <h3 className="text-xs sm:text-sm font-bold text-[#1e293b] mb-3">{t('dashboard.distribution')}</h3>
            <SafeChartContainer className="h-36" minHeight={144}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart><Pie data={distributionData} cx="50%" cy="50%" innerRadius={42} outerRadius={60} paddingAngle={4} dataKey="value">{distributionData.map((_e, i) => (<Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} className="outline-none cursor-pointer hover:opacity-80 transition-opacity" />))}</Pie><Tooltip /></PieChart>
              </ResponsiveContainer>
            </SafeChartContainer>
            <div className="space-y-2 mt-2">
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-brand-500"></div><span className="text-slate-500 tracking-tight">{t('dashboard.confirmed')}</span></div>
                  <span className="text-[#1e293b]">{Math.round((reservationStatusData[0].count / reservationStatusTotal) * 100)}% ({reservationStatusData[0].count})</span>
               </div>
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-accent-400"></div><span className="text-slate-500 tracking-tight">{t('dashboard.pending')}</span></div>
                  <span className="text-[#1e293b]">{Math.round((reservationStatusData[1].count / reservationStatusTotal) * 100)}% ({reservationStatusData[1].count})</span>
               </div>
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-danger-500"></div><span className="text-slate-500 tracking-tight">{t('dashboard.cancelled')}</span></div>
                  <span className="text-[#1e293b]">{Math.round((reservationStatusData[2].count / reservationStatusTotal) * 100)}% ({reservationStatusData[2].count})</span>
               </div>
            </div>
          </div>

          <div className="card-premium hover:shadow-elevated transition-shadow duration-300 p-3 sm:p-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-3 gap-3">
               <div>
                  <p className="text-[10px] font-medium text-slate-400 mb-0.5">{t('dashboard.revenuePreview')}</p>
                  <h4 className="text-sm sm:text-base font-bold text-[#1e293b]">{totalRevenue} DH <span className="text-success-500 text-[10px] ml-1 font-bold">↑ 15.7%</span></h4>
               </div>
               <select className="bg-[#f5f5f0] text-[10px] font-bold uppercase text-slate-400 rounded-lg px-2 py-1 outline-none border border-[#e8e6e1] hover:bg-[#ebe9e4] cursor-pointer">
                  <option>{t('dashboard.thisMonth')}</option>
                  <option>{t('dashboard.year')}</option>
               </select>
            </div>
            <SafeChartContainer className="h-24" minHeight={96}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={revenueData}><Bar dataKey="revenue" fill="#1e3a5f" radius={[5, 5, 0, 0]} barSize={10} className="cursor-pointer hover:opacity-80 transition-opacity" /><Tooltip /></BarChart>
              </ResponsiveContainer>
            </SafeChartContainer>
          </div>
        </div>
      </div>
    </div>
  );
}
