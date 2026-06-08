import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import { Search, Download, CheckCircle2, Clock, Loader2, RefreshCcw, XCircle } from 'lucide-react';

interface Payment {
  id: number;
  paymentNumber?: string;
  reservationId?: number;
  reservationLabel?: string;
  contractId?: number;
  contractNumber?: string;
  clientName?: string;
  vehicleLabel?: string;
  amount: number;
  paymentMethod?: string;
  status: string;
  paymentDate?: string;
  paid?: boolean;
}

interface PaymentStats {
  totalRevenue?: number;
  monthlyRevenue?: number;
  pendingAmount?: number;
  pendingCount?: number;
  paidInvoices?: number;
  overdueInvoices?: number;
  refundAmount?: number;
  refundCount?: number;
}

interface MonthlyRevenue {
  month: string;
  year: number;
  revenue: number;
}

const money = (value?: number) => `${Number(value || 0).toLocaleString()} DH`;

const statusClass = (status: string) => {
  if (status === 'PAID') return 'bg-success-50 text-success-500';
  if (status === 'PARTIALLY_PAID') return 'bg-brand-50 text-brand-500';
  if (status === 'REFUNDED') return 'bg-slate-100 text-slate-500';
  if (['FAILED', 'CANCELLED', 'EXPIRED'].includes(status)) return 'bg-danger-50 text-danger-500';
  return 'bg-warning-50 text-warning-500';
};

export default function Payments() {
  const [searchQuery, setSearchQuery] = useState('');
  const [payments, setPayments] = useState<Payment[]>([]);
  const [stats, setStats] = useState<PaymentStats>({});
  const [monthlyRevenue, setMonthlyRevenue] = useState<MonthlyRevenue[]>([]);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const { t } = useTranslation();

  const fetchPayments = async () => {
    try {
      const [paymentsRes, statsRes, revenueRes] = await Promise.all([
        api.get('/payments'),
        api.get('/payments/stats'),
        api.get('/payments/monthly-revenue'),
      ]);
      setPayments(paymentsRes.data || []);
      setStats(statsRes.data || {});
      setMonthlyRevenue(revenueRes.data || []);
    } catch (err) {
      console.error('Failed to fetch payments', err);
      showToast('Failed to load payments');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments();
  }, []);

  const filteredPayments = payments.filter((p) => {
    const q = searchQuery.toLowerCase();
    return [
      p.paymentNumber,
      p.clientName,
      p.vehicleLabel,
      p.reservationLabel,
      p.contractNumber,
      p.paymentMethod,
      p.status,
      String(p.id),
      String(p.reservationId || ''),
      String(p.contractId || ''),
    ].some((value) => value?.toLowerCase().includes(q));
  });

  const exportStatements = () => {
    const headers = ['Payment ID', 'Client', 'Vehicle', 'Reservation', 'Contract', 'Amount', 'Method', 'Status', 'Date'];
    const rows = filteredPayments.map((p) => [
      p.paymentNumber || `PAY-${p.id}`,
      p.clientName || '',
      p.vehicleLabel || '',
      p.reservationLabel || (p.reservationId ? `RES-${p.reservationId}` : ''),
      p.contractNumber || '',
      p.amount,
      p.paymentMethod || '',
      p.status,
      p.paymentDate || '',
    ]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'payments.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.statementsExported'));
  };

  const markAsPaid = async (reservationId?: number) => {
    if (!reservationId) return;
    try {
      await api.patch(`/payments/reservation/${reservationId}/pay`);
      await fetchPayments();
      showToast(t('toast.success', { action: 'Payment marked as paid' }));
    } catch (err) {
      showToast('Failed to mark payment as paid');
    }
  };

  const chartMax = Math.max(...monthlyRevenue.map((item) => Number(item.revenue || 0)), 1);

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="flex-1 min-w-0">
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('payments.title')}</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('payments.subtitle')}</p>
        </div>
        <button onClick={exportStatements} className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-xs sm:text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all w-fit">
          <Download size={17} />
          <span className="hidden sm:inline">{t('payments.exportStatements')}</span>
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-6 gap-3 sm:gap-4">
        {[
          ['Total Revenue', money(stats.totalRevenue)],
          ['Monthly Revenue', money(stats.monthlyRevenue)],
          ['Pending Payments', money(stats.pendingAmount), `${stats.pendingCount || 0} ${t('payments.transactions')}`],
          ['Paid Invoices', stats.paidInvoices || 0],
          ['Overdue Invoices', stats.overdueInvoices || 0],
          ['Refunds', money(stats.refundAmount), `${stats.refundCount || 0} ${t('payments.transactions')}`],
        ].map(([label, value, caption]) => (
          <div key={label} className="card-premium p-3 sm:p-4">
            <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{label}</p>
            <h3 className="text-lg font-bold text-[#1e293b]">{value}</h3>
            {caption && <p className="text-[10px] font-bold text-slate-400 mt-1">{caption}</p>}
          </div>
        ))}
      </div>

      <div className="card-premium p-4 sm:p-5">
        <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">Revenue Charts</h3>
        <div className="h-40 flex items-end gap-3">
          {monthlyRevenue.length === 0 ? (
            <div className="w-full text-center text-slate-400 text-sm">No revenue data yet</div>
          ) : monthlyRevenue.map((item) => (
            <div key={`${item.month}-${item.year}`} className="flex-1 flex flex-col items-center gap-2 min-w-0">
              <div className="w-full bg-brand-500 rounded-t-md min-h-[4px]" style={{ height: `${Math.max((Number(item.revenue || 0) / chartMax) * 120, 4)}px` }} />
              <span className="text-[10px] font-bold text-slate-400 truncate">{item.month}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card-premium overflow-hidden p-0">
        <div className="p-4 sm:p-5 border-b border-[#e8e6e1]/50 flex flex-col sm:flex-row justify-between gap-3">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b]">Payment History</h3>
          <div className="relative group w-full sm:w-auto">
            <Search size={17} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
            <input type="text" placeholder={t('payments.searchPlaceholder')} value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] outline-none w-full sm:w-72 focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
          </div>
        </div>
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left min-w-[980px]">
            <thead>
              <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                {['Payment ID', 'Client', 'Vehicle', 'Reservation', 'Contract', 'Amount', 'Method', 'Status', 'Date', 'Actions'].map((heading) => (
                  <th key={heading} className="px-3 sm:px-5 py-3 sm:py-4">{heading}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e8e6e1]/50">
              {loading ? (
                <tr><td colSpan={10} className="px-5 py-8 text-center"><Loader2 size={24} className="animate-spin text-brand-500 mx-auto" /></td></tr>
              ) : filteredPayments.length > 0 ? filteredPayments.map((payment) => (
                <tr key={payment.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                  <td className="px-3 sm:px-5 py-3 sm:py-4 font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500">{payment.paymentNumber || `PAY-${payment.id}`}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-sm font-medium text-[#1e293b]">{payment.clientName || 'N/A'}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-sm text-[#1e293b]">{payment.vehicleLabel || 'N/A'}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-sm text-[#1e293b]">{payment.reservationLabel || (payment.reservationId ? `RES-${payment.reservationId}` : 'N/A')}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-sm text-[#1e293b]">{payment.contractNumber || 'N/A'}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-sm font-bold text-[#1e293b]">{money(payment.amount)}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs font-bold text-slate-500">{payment.paymentMethod || 'N/A'}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider ${statusClass(payment.status)}`}>
                      {payment.status === 'PAID' ? <CheckCircle2 size={12} /> : ['FAILED', 'CANCELLED', 'EXPIRED'].includes(payment.status) ? <XCircle size={12} /> : <Clock size={12} />}
                      {payment.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs text-slate-500">{payment.paymentDate ? new Date(payment.paymentDate).toLocaleDateString() : 'N/A'}</td>
                  <td className="px-3 sm:px-5 py-3 sm:py-4">
                    {payment.reservationId && payment.status !== 'PAID' && (
                      <button onClick={() => markAsPaid(payment.reservationId)} className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brand-50 text-brand-500 rounded-lg text-[10px] font-bold uppercase tracking-wider hover:bg-brand-500 hover:text-white transition-all">
                        <RefreshCcw size={12} /> Mark Paid
                      </button>
                    )}
                  </td>
                </tr>
              )) : (
                <tr><td colSpan={10} className="px-5 py-8 text-center text-slate-400 text-sm">No payments found</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
