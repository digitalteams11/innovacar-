import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { 
  Car, 
  TrendingUp, 
  CheckCircle2, 
  AlertCircle,
  ArrowRight
} from 'lucide-react';

interface Stats {
  totalVehicles: number;
  rentedVehicles: number;
  totalRevenue: number;
}

const StatCard = ({ title, value, icon: Icon, color, prefix = '' }: any) => (
  <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow">
    <div className="flex items-center justify-between mb-4">
      <div className={`p-3 rounded-xl ${color}`}>
        <Icon size={24} />
      </div>
    </div>
    <div>
      <p className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-1">{title}</p>
      <h3 className="text-2xl font-bold text-gray-900">{prefix}{typeof value === 'number' ? value.toLocaleString() : value}</h3>
    </div>
  </div>
);

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const { data } = await api.get('/dashboard');
        setStats(data);
      } catch (err) {
        console.error('Failed to fetch stats', err);
      } finally {
        setLoading(false);
      }
    };
    fetchStats();
  }, []);

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
    </div>
  );

  const utilizationRate = stats ? Math.round((stats.rentedVehicles / stats.totalVehicles) * 100) || 0 : 0;

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">Fleet Overview</h2>
        <p className="text-gray-500 font-medium">Real-time performance metrics for your rental business</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <StatCard 
          title="Total Fleet" 
          value={stats?.totalVehicles} 
          icon={Car} 
          color="bg-blue-50 text-blue-600" 
        />
        <StatCard 
          title="Active Rentals" 
          value={stats?.rentedVehicles} 
          icon={CheckCircle2} 
          color="bg-green-50 text-green-600" 
        />
        <StatCard 
          title="Total Revenue" 
          value={stats?.totalRevenue} 
          icon={TrendingUp} 
          color="bg-purple-50 text-purple-600" 
          prefix="$"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white p-8 rounded-2xl border border-gray-100 shadow-sm">
          <div className="flex items-center justify-between mb-8">
            <h3 className="text-lg font-bold text-gray-900">Fleet Utilization</h3>
            <span className="text-sm font-bold text-blue-600 bg-blue-50 px-3 py-1 rounded-lg">{utilizationRate}%</span>
          </div>
          <div className="relative pt-1">
            <div className="overflow-hidden h-4 mb-4 text-xs flex rounded-full bg-gray-100">
              <div 
                style={{ width: `${utilizationRate}%` }}
                className="shadow-none flex flex-col text-center whitespace-nowrap text-white justify-center bg-blue-500 transition-all duration-1000"
              ></div>
            </div>
            <div className="flex text-xs font-bold text-gray-500 uppercase tracking-wider">
              <div className="flex-1">0%</div>
              <div className="flex-1 text-right">100%</div>
            </div>
          </div>
          <div className="mt-8 space-y-4">
            <div className="flex items-center justify-between text-sm font-medium">
              <span className="text-gray-500">Available Vehicles</span>
              <span className="text-gray-900">{(stats?.totalVehicles || 0) - (stats?.rentedVehicles || 0)}</span>
            </div>
            <div className="flex items-center justify-between text-sm font-medium">
              <span className="text-gray-500">Rented Vehicles</span>
              <span className="text-gray-900">{stats?.rentedVehicles}</span>
            </div>
          </div>
        </div>

        <div className="bg-blue-600 p-8 rounded-2xl shadow-xl shadow-blue-500/20 text-white flex flex-col justify-between overflow-hidden relative group cursor-pointer">
          <div className="absolute -right-12 -top-12 w-48 h-48 bg-white/10 rounded-full blur-3xl transition-transform duration-500 group-hover:scale-125"></div>
          <div>
            <h3 className="text-2xl font-bold mb-2">New Reservation</h3>
            <p className="text-blue-100 font-medium">Instantly book a vehicle from your dashboard</p>
          </div>
          <div className="mt-8 flex items-center gap-2 font-bold group-hover:translate-x-2 transition-transform">
            Get Started <ArrowRight size={20} />
          </div>
        </div>
      </div>
    </div>
  );
}
