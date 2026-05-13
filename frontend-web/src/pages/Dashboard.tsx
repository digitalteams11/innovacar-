import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, AreaChart, Area } from 'recharts';
import { Users, Car, Calendar, CreditCard } from 'lucide-react';
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
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const { t } = useTranslation();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, vehiclesRes, reservationsRes] = await Promise.all([
          api.get('/dashboard'),
          api.get('/vehicles'),
          api.get('/reservations'),
        ]);
        setStats(statsRes.data);
        setVehicles(vehiclesRes.data);
        setReservations(reservationsRes.data);
      } catch (err) {
        console.error('Failed to fetch dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const totalVehicles = stats?.totalVehicles || 0;
  const rentedVehicles = stats?.rentedVehicles || 0;
  const totalRevenue = stats?.totalRevenue || 0;
  const availableVehicles = totalVehicles - rentedVehicles;

  const revenueData = [
    { name: 'Jan', revenue: 45000 },
    { name: 'Feb', revenue: 52000 },
    { name: 'Mar', revenue: 48000 },
    { name: 'Apr', revenue: 61000 },
    { name: 'May', revenue: 55000 },
    { name: 'Jun', revenue: 67000 },
  ];

  const reservationData = [
    { name: 'Mon', count: 12 },
    { name: 'Tue', count: 18 },
    { name: 'Wed', count: 15 },
    { name: 'Thu', count: 22 },
    { name: 'Fri', count: 30 },
    { name: 'Sat', count: 25 },
    { name: 'Sun', count: 14 },
  ];

  const distributionData = [
    { name: 'Economy', value: 45 },
    { name: 'SUV', value: 25 },
    { name: 'Luxury', value: 15 },
    { name: 'Compact', value: 15 },
  ];

  return (
    <div className="space-y-5 animate-fade">
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard title={t('dashboard.reservationsThisMonth')} value={reservations.length} icon={Calendar} trend="up" trendValue="12.5" iconBg="bg-brand-500" onClick={() => navigate('/reservations')} />
        <KpiCard title={t('dashboard.availableVehicles')} value={availableVehicles} icon={Car} trend="up" trendValue="8.3" iconBg="bg-success-500" onClick={() => navigate('/vehicles')} />
        <KpiCard title={t('dashboard.revenue')} value={`${totalRevenue} DH`} icon={CreditCard} trend="up" trendValue="15.7" iconBg="bg-accent-500" onClick={() => navigate('/payments')} />
        <KpiCard title={t('dashboard.activeClients')} value="312" icon={Users} trend="up" trendValue="10.1" iconBg="bg-[#1e293b]" onClick={() => navigate('/clients')} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-7 card-premium">
          <div className="flex items-center justify-between mb-5">
            <h3 className="text-base font-bold text-[#1e293b]">{t('dashboard.reservationOverview')}</h3>
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
          <div className="h-[220px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={reservationData}>
                <defs><linearGradient id="colorRes" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#1e3a5f" stopOpacity={0.12}/><stop offset="95%" stopColor="#1e3a5f" stopOpacity={0}/></linearGradient></defs>
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 11, fontWeight: 500}} dy={8} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#94a3b8', fontSize: 11, fontWeight: 500}} />
                <Tooltip contentStyle={{borderRadius: '12px', border: 'none', boxShadow: '0 8px 32px -8px rgba(0,0,0,0.12)', padding: '10px 14px'}} />
                <Area type="monotone" dataKey="count" stroke="#1e3a5f" strokeWidth={2.5} fillOpacity={1} fill="url(#colorRes)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="lg:col-span-5 card-premium">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-[#1e293b]">{t('dashboard.recentReservations')}</h3>
            <button onClick={() => navigate('/reservations')} className="text-brand-500 text-xs font-medium bg-brand-50 px-4 py-2 rounded-xl hover:bg-brand-500 hover:text-white active:scale-95 transition-all duration-200">{t('dashboard.seeAll')}</button>
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

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 pb-4">
        <div className="lg:col-span-8 card-premium overflow-hidden">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-[#1e293b]">{t('dashboard.vehicles')}</h3>
            <button onClick={() => navigate('/vehicles')} className="text-brand-500 text-xs font-medium bg-brand-50 px-4 py-2 rounded-xl hover:bg-brand-500 hover:text-white transition-all duration-200">{t('dashboard.seeAllVehicles')}</button>
          </div>
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em] border-b border-[#e8e6e1]">
                  <th className="pb-3 pr-4">{t('dashboard.vehicle')}</th>
                  <th className="pb-3 pr-4">{t('dashboard.status')}</th>
                  <th className="pb-3">{t('dashboard.availability')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/60">
                {vehicles.slice(0, 5).map((vehicle) => (
                  <tr key={vehicle.id} onClick={() => navigate('/vehicles')} className="hover:bg-[#f5f5f0]/60 transition-colors rounded-xl cursor-pointer group">
                    <td className="py-3.5 pr-4">
                      <div className="flex items-center gap-3">
                         <div className="w-9 h-9 rounded-lg bg-[#f5f5f0] flex items-center justify-center shrink-0 group-hover:bg-white group-hover:shadow-sm transition-all">
                            <Car size={15} className="text-slate-400 group-hover:text-brand-500 transition-colors" />
                         </div>
                         <span className="text-xs font-medium text-[#1e293b]">{vehicle.marque}</span>
                      </div>
                    </td>
                    <td className="py-3.5 pr-4">
                      <span className={`px-2 py-1 rounded-lg text-[10px] font-bold uppercase ${
                        vehicle.statut === 'AVAILABLE' ? 'bg-success-50 text-success-500' : vehicle.statut === 'RENTED' ? 'bg-warning-50 text-warning-500' : 'bg-danger-50 text-danger-500'
                      }`}>
                        {vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : vehicle.statut === 'RENTED' ? t('dashboard.rented') : t('dashboard.maintenance')}
                      </span>
                    </td>
                    <td className="py-3.5"><p className="text-[11px] font-medium text-[#1e293b]">{vehicle.statut === 'AVAILABLE' ? t('dashboard.available') : "Jusqu'au 25/05"}</p></td>
                  </tr>
                ))}
                {vehicles.length === 0 && (
                  <tr><td colSpan={3} className="py-4 text-center text-slate-400 text-sm">No vehicles found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="lg:col-span-4 space-y-4">
          <div className="card-premium hover:shadow-elevated transition-shadow duration-300">
            <h3 className="text-sm font-bold text-[#1e293b] mb-3">{t('dashboard.distribution')}</h3>
            <div className="h-36 w-full relative">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart><Pie data={distributionData} cx="50%" cy="50%" innerRadius={42} outerRadius={60} paddingAngle={4} dataKey="value">{distributionData.map((_e, i) => (<Cell key={`cell-${i}`} fill={COLORS[i % COLORS.length]} className="outline-none cursor-pointer hover:opacity-80 transition-opacity" />))}</Pie><Tooltip /></PieChart>
              </ResponsiveContainer>
            </div>
            <div className="space-y-2 mt-2">
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-brand-500"></div><span className="text-slate-500 tracking-tight">{t('dashboard.confirmed')}</span></div>
                  <span className="text-[#1e293b]">65% (83)</span>
               </div>
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-accent-400"></div><span className="text-slate-500 tracking-tight">{t('dashboard.pending')}</span></div>
                  <span className="text-[#1e293b]">20% (26)</span>
               </div>
               <div className="flex justify-between items-center text-[10px] font-medium hover:bg-[#f5f5f0] p-2 rounded-xl transition-colors cursor-pointer">
                  <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-danger-500"></div><span className="text-slate-500 tracking-tight">{t('dashboard.cancelled')}</span></div>
                  <span className="text-[#1e293b]">15% (19)</span>
               </div>
            </div>
          </div>

          <div className="card-premium hover:shadow-elevated transition-shadow duration-300">
            <div className="flex justify-between items-center mb-3">
               <div>
                  <p className="text-[10px] font-medium text-slate-400 mb-0.5">{t('dashboard.revenuePreview')}</p>
                  <h4 className="text-base font-bold text-[#1e293b]">{totalRevenue} DH <span className="text-success-500 text-[10px] ml-1 font-bold">↑ 15.7%</span></h4>
               </div>
               <select className="bg-[#f5f5f0] text-[10px] font-bold uppercase text-slate-400 rounded-lg px-2 py-1 outline-none border border-[#e8e6e1] hover:bg-[#ebe9e4] cursor-pointer">
                  <option>{t('dashboard.thisMonth')}</option>
                  <option>{t('dashboard.year')}</option>
               </select>
            </div>
            <div className="h-24 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={revenueData}><Bar dataKey="revenue" fill="#1e3a5f" radius={[5, 5, 0, 0]} barSize={10} className="cursor-pointer hover:opacity-80 transition-opacity" /><Tooltip /></BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
