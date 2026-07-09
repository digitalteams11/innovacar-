import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  Download, TrendingUp, CreditCard, RefreshCw,
  AlertCircle, CheckCircle2, Clock, FileText
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  AreaChart, Area
} from 'recharts';
import { useToast } from '../../context/ToastContext';
import SafeChartContainer from '../../components/shared/SafeChartContainer';

export default function SuperAdminPayments() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [revenue, setRevenue] = useState<any>(null);
  const [invoices, setInvoices] = useState<any[]>([]);
  const [, setPayments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [revRes, invRes, payRes] = await Promise.all([
        superAdminApi.getRevenueStats(),
        superAdminApi.getAllInvoices(),
        superAdminApi.getAllPayments(),
      ]);
      setRevenue(revRes.data);
      setInvoices(invRes.data);
      setPayments(payRes.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load payment data. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleExport = () => {
    const csv = [
      ['Invoice #', 'Client', 'Agency', 'Amount', 'Status', 'Date'].join(','),
      ...invoices.map(i => [
        i.invoiceNumber, i.clientName, i.tenantName,
        i.amount, i.status, i.issueDate
      ].join(','))
    ].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `invoices-export-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('CSV exported successfully', 'success');
  };

  const handleRetryPayment = async (invoiceId: number) => {
    try {
      await superAdminApi.retryPayment(invoiceId);
      showToast('Payment retry initiated', 'info');
      fetchData();
    } catch (err) {
      showToast('Unable to retry payment. Please try again later.', 'error');
    }
  };

  const monthlyData = revenue?.monthlyTrend || [];

  const statusStats = {
    paid: invoices.filter((i: any) => i.status === 'PAID').length,
    pending: invoices.filter((i: any) => i.status === 'PENDING').length,
    overdue: invoices.filter((i: any) => i.status === 'OVERDUE').length,
    cancelled: invoices.filter((i: any) => i.status === 'CANCELLED').length,
  };

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
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">{t('superAdmin.payments.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">{t('superAdmin.payments.subtitle')}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={fetchData}
            className="inline-flex items-center gap-2 bg-white dark:bg-[#1a2332]/70 hover:bg-slate-50 dark:hover:bg-white/5 text-[#1e293b] dark:text-white px-4 py-2.5 rounded-xl text-sm font-semibold border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft transition-colors"
          >
            <RefreshCw size={16} />
            Refresh
          </button>
          <button
            onClick={handleExport}
            className="inline-flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold shadow-soft transition-colors"
          >
            <Download size={16} /> {t('superAdmin.payments.export')}
          </button>
        </div>
      </div>

      {/* Revenue Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 flex items-center justify-center">
              <TrendingUp size={18} className="text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.payments.totalRevenue')}</p>
              <p className="text-xl font-bold text-[#1e293b] dark:text-white">{revenue?.totalRevenue?.toLocaleString() || 0} MAD</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center">
              <CreditCard size={18} className="text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.payments.monthlyRevenue')}</p>
              <p className="text-xl font-bold text-[#1e293b] dark:text-white">{revenue?.monthlyRevenue?.toLocaleString() || 0} MAD</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/10 flex items-center justify-center">
              <Clock size={18} className="text-amber-600 dark:text-amber-400" />
            </div>
            <div>
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.payments.pending')}</p>
              <p className="text-xl font-bold text-[#1e293b] dark:text-white">{statusStats.pending}</p>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 rounded-xl bg-rose-50 dark:bg-rose-500/10 flex items-center justify-center">
              <AlertCircle size={18} className="text-rose-600 dark:text-rose-400" />
            </div>
            <div>
              <p className="text-slate-500 dark:text-slate-400 text-xs font-medium">{t('superAdmin.payments.overdue')}</p>
              <p className="text-xl font-bold text-[#1e293b] dark:text-white">{statusStats.overdue}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6">
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.payments.revenueTrend')}</h3>
          <SafeChartContainer className="h-[260px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={monthlyData}>
                <defs>
                  <linearGradient id="colorPay" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#0a0f2c" stopOpacity={0.1}/>
                    <stop offset="95%" stopColor="#0a0f2c" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="month" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                <Area type="monotone" dataKey="revenue" stroke="#0a0f2c" strokeWidth={2} fillOpacity={1} fill="url(#colorPay)" />
              </AreaChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>

        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.payments.revenueByPlan')}</h3>
          <SafeChartContainer className="h-[260px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={revenue?.revenueByPlan || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="planName" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                <Bar dataKey="revenue" fill="#0a0f2c" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </SafeChartContainer>
        </div>
      </div>

      {/* Invoices Table */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        <div className="px-4 sm:px-6 py-3 sm:py-4 border-b border-[#e8e6e1]/60 dark:border-white/5 flex items-center justify-between">
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{t('superAdmin.payments.recentInvoices')}</h3>
          <span className="text-xs text-slate-400">{invoices.length} invoices</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">{t('superAdmin.payments.invoice')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">{t('superAdmin.payments.client')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">{t('superAdmin.payments.agency')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">{t('superAdmin.payments.amount')}</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">{t('superAdmin.payments.status')}</th>
                <th className="text-right text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.slice(0, 20).map((inv: any) => (
                <tr key={inv.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                  <td className="px-5 py-3 text-sm font-medium text-[#1e293b] dark:text-white">{inv.invoiceNumber}</td>
                  <td className="px-5 py-3 text-sm text-slate-500">{inv.clientName}</td>
                  <td className="px-5 py-3 text-sm text-slate-500">{inv.tenantName}</td>
                  <td className="px-5 py-3 text-sm font-medium text-[#1e293b] dark:text-white">{inv.amount?.toLocaleString()} MAD</td>
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-medium ${
                      inv.status === 'PAID' ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400' :
                      inv.status === 'OVERDUE' ? 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400' :
                      inv.status === 'PENDING' ? 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400' :
                      'bg-slate-50 dark:bg-slate-500/10 text-slate-600 dark:text-slate-400'
                    }`}>
                      {inv.status === 'PAID' && <CheckCircle2 size={12} />}
                      {inv.status === 'OVERDUE' && <AlertCircle size={12} />}
                      {inv.status === 'PENDING' && <Clock size={12} />}
                      {inv.status}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex items-center justify-end gap-2">
                      {inv.status === 'OVERDUE' && (
                        <button
                          onClick={() => handleRetryPayment(inv.id)}
                          className="flex items-center gap-1 px-2 py-1 rounded-lg text-xs font-medium bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/80 transition-colors"
                        >
                          <RefreshCw size={12} />
                          Retry
                        </button>
                      )}
                      <button
                        className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors"
                        title="View Details"
                      >
                        <FileText size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {invoices.length === 0 && (
                <tr><td colSpan={6} className="text-center py-8 text-slate-400">{t('superAdmin.payments.noInvoices')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
