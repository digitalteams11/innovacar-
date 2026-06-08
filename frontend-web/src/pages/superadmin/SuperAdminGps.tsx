import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Satellite, MapPin, AlertTriangle,
  Activity, Radio, Server
} from 'lucide-react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';

const COLORS = ['#10b981', '#ef4444', '#f59e0b'];

export default function SuperAdminGps() {
  const { t } = useTranslation();
  const [stats, setStats] = useState<any>(null);
  const [alerts, setAlerts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsRes, alertsRes] = await Promise.all([
          superAdminApi.getGlobalGpsStats(),
          superAdminApi.getGlobalGpsAlerts(),
        ]);
        setStats(statsRes.data);
        setAlerts(alertsRes.data.slice(0, 20));
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const deviceStatusData = stats ? [
    { name: t('superAdmin.gps.online'), value: stats.onlineDevices || 0 },
    { name: t('superAdmin.gps.offline'), value: stats.offlineDevices || 0 },
  ] : [];

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
          <h1 className="text-2xl font-bold text-[#1e293b] tracking-tight">{t('superAdmin.gps.title')}</h1>
          <p className="text-slate-500 text-sm mt-1">{t('superAdmin.gps.subtitle')}</p>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center">
              <Server size={18} className="text-blue-600 dark:text-blue-400" />
            </div>
            <div className="min-w-0">
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.gps.totalProviders')}</p>
              <p className="text-lg sm:text-xl font-bold text-[#1e293b] dark:text-white">{stats?.totalProviders || 0}</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 flex items-center justify-center">
              <Radio size={18} className="text-emerald-600 dark:text-emerald-400" />
            </div>
            <div className="min-w-0">
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.gps.activeConnections')}</p>
              <p className="text-lg sm:text-xl font-bold text-[#1e293b] dark:text-white">{stats?.activeConnections || 0}</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
              <MapPin size={18} className="text-[#0a0f2c] dark:text-white/70" />
            </div>
            <div className="min-w-0">
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.gps.totalDevices')}</p>
              <p className="text-lg sm:text-xl font-bold text-[#1e293b] dark:text-white">{stats?.totalDevices || 0}</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/10 flex items-center justify-center">
              <AlertTriangle size={18} className="text-amber-600 dark:text-amber-400" />
            </div>
            <div className="min-w-0">
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.gps.activeAlerts')}</p>
              <p className="text-lg sm:text-xl font-bold text-[#1e293b] dark:text-white">{alerts.length}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6">
        {/* Provider Stats */}
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] mb-4">{t('superAdmin.gps.providers')}</h3>
          <div className="space-y-3">
            {stats?.providerStats?.map((provider: any, idx: number) => (
              <div key={idx} className="flex items-center justify-between p-3 rounded-xl bg-slate-50">
                <div className="flex items-center gap-3">
                  <div className={`w-2 h-2 rounded-full ${provider.connectionStatus === 'ACTIVE' ? 'bg-emerald-400' : 'bg-rose-400'}`} />
                  <div>
                    <p className="text-sm font-medium text-[#1e293b]">{provider.provider}</p>
                    <p className="text-xs text-slate-500">{provider.agencies} {t('superAdmin.gps.agencies')}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-[#1e293b]">{provider.activeDevices}</p>
                  <p className="text-xs text-slate-500">{t('superAdmin.gps.devices')}</p>
                </div>
              </div>
            ))}
            {(!stats?.providerStats || stats.providerStats.length === 0) && (
              <p className="text-slate-400 text-sm text-center py-8">{t('superAdmin.gps.noProviders')}</p>
            )}
          </div>
        </div>

        {/* Device Status */}
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] mb-4">{t('superAdmin.gps.deviceStatus')}</h3>
          <div className="h-[220px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={deviceStatusData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={90}
                  paddingAngle={4}
                  dataKey="value"
                >
                  {deviceStatusData.map((_entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex justify-center gap-4 mt-4">
            {deviceStatusData.map((item: any, idx: number) => (
              <div key={item.name} className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[idx] }} />
                <span className="text-xs text-slate-500">{item.name}: <span className="font-semibold text-[#1e293b]">{item.value}</span></span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Alerts Table */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.gps.recentAlerts')}</h3>
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">{t('superAdmin.gps.alertType')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">{t('superAdmin.gps.message')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">{t('superAdmin.gps.severity')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">{t('superAdmin.gps.vehicle')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">{t('superAdmin.gps.time')}</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert: any) => (
                <tr key={alert.id} className="border-b border-[#e8e6e1]/40 hover:bg-slate-50/50">
                  <td className="px-4 py-3">
                    <span className="inline-flex items-center gap-1.5 text-xs font-medium">
                      {alert.alertType === 'SPEEDING' ? <Activity size={12} className="text-rose-500" /> :
                       alert.alertType === 'GEOFENCE' ? <MapPin size={12} className="text-amber-500" /> :
                       <Satellite size={12} className="text-blue-500" />}
                      {alert.alertType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#1e293b]">{alert.message}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-lg text-xs font-medium ${
                      alert.severity === 'CRITICAL' ? 'bg-rose-50 text-rose-700' :
                      alert.severity === 'HIGH' ? 'bg-amber-50 text-amber-700' :
                      'bg-blue-50 text-blue-700'
                    }`}>
                      {alert.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-500">{alert.vehicleName || alert.vehicleId}</td>
                  <td className="px-4 py-3 text-xs text-slate-400">
                    {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : '-'}
                  </td>
                </tr>
              ))}
              {alerts.length === 0 && (
                <tr><td colSpan={5} className="text-center py-8 text-slate-400">{t('superAdmin.gps.noAlerts')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
