import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import { Search, Download, ArrowUpRight, MoreHorizontal, CheckCircle2, Clock, XCircle, Loader2 } from 'lucide-react';

interface Payment {
  id: number;
  reservationId: number;
  amount: number;
  paid: boolean;
}

export default function Payments() {
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    const fetchPayments = async () => {
      try {
        // Fetch all reservations and their payments
        const { data: reservations } = await api.get('/reservations');
        const payments: Payment[] = [];
        for (const res of reservations) {
          try {
            const { data: payment } = await api.get(`/payments/reservation/${res.id}`);
            payments.push({
              id: payment.id,
              reservationId: res.id,
              amount: payment.amount,
              paid: payment.paid,
            });
          } catch (e) {
            // Some reservations may not have payments yet
          }
        }
        setData(payments);
      } catch (err) {
        console.error('Failed to fetch payments', err);
      } finally {
        setLoading(false);
      }
    };
    fetchPayments();
  }, []);

  const filteredPayments = data.filter((p) => {
    const q = searchQuery.toLowerCase();
    return String(p.id).includes(q) || String(p.reservationId).includes(q);
  });

  const totalRevenue = data.filter((p) => p.paid).reduce((sum, p) => sum + p.amount, 0);
  const totalPending = data.filter((p) => !p.paid).reduce((sum, p) => sum + p.amount, 0);
  const successRate = data.length > 0 ? Math.round((data.filter((p) => p.paid).length / data.length) * 100) : 0;

  const exportStatements = () => {
    const headers = ['ID', 'Reservation ID', 'Amount', 'Status'];
    const rows = filteredPayments.map((p) => [p.id, p.reservationId, p.amount, p.paid ? 'Paid' : 'Pending']);
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

  const markAsPaid = async (reservationId: number) => {
    try {
      await api.patch(`/payments/reservation/${reservationId}/pay`);
      setData((prev) => prev.map((p) => (p.reservationId === reservationId ? { ...p, paid: true } : p)));
      showToast(t('toast.success', { action: 'Payment marked as paid' }));
    } catch (err) {
      showToast('Failed to mark payment as paid');
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('payments.title')}</h1>
          <p className="text-slate-500 font-normal text-sm mt-0.5">{t('payments.subtitle')}</p>
        </div>
        <button
          onClick={exportStatements}
          className="flex items-center gap-2 px-5 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all"
        >
          <Download size={18} />
          {t('payments.exportStatements')}
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('payments.totalRevenue')}</p>
          <div className="flex items-end justify-between">
            <h3 className="text-xl font-bold text-[#1e293b]">{totalRevenue.toLocaleString()} DH</h3>
            <div className="flex items-center gap-1 text-success-500 font-bold text-[10px] bg-success-50 px-2 py-1 rounded-lg">
              <ArrowUpRight size={13} />
              {successRate}%
            </div>
          </div>
        </div>
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('payments.pendingPayments')}</p>
          <div className="flex items-end justify-between">
            <h3 className="text-xl font-bold text-[#1e293b]">{totalPending.toLocaleString()} DH</h3>
            <div className="text-warning-500 font-bold text-[10px] bg-warning-50 px-2 py-1 rounded-lg">
              {data.filter((p) => !p.paid).length} {t('payments.transactions')}
            </div>
          </div>
        </div>
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('payments.successRate')}</p>
          <div className="flex items-end justify-between">
            <h3 className="text-xl font-bold text-[#1e293b]">{successRate}%</h3>
            <div className="text-slate-400 font-bold text-[10px] bg-[#f5f5f0] px-2 py-1 rounded-lg">
              {t('payments.last30Days')}
            </div>
          </div>
        </div>
      </div>

      <div className="card-premium overflow-hidden p-0">
        <div className="p-5 border-b border-[#e8e6e1]/50 flex flex-col md:flex-row justify-between gap-4">
          <h3 className="text-base font-bold text-[#1e293b]">{t('payments.history')}</h3>
          <div className="flex gap-2">
            <div className="relative group">
              <Search size={17} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
              <input
                type="text"
                placeholder={t('payments.searchPlaceholder')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] outline-none w-full md:w-64 focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
            </div>
          </div>
        </div>
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                <th className="px-5 py-4">{t('payments.transactionId')}</th>
                <th className="px-5 py-4">Reservation</th>
                <th className="px-5 py-4">{t('payments.amount')}</th>
                <th className="px-5 py-4">{t('payments.status')}</th>
                <th className="px-5 py-4 text-right">{t('payments.action')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e8e6e1]/50">
              {loading ? (
                <tr><td colSpan={5} className="px-5 py-8 text-center"><Loader2 size={24} className="animate-spin text-brand-500 mx-auto" /></td></tr>
              ) : filteredPayments.length > 0 ? (
                filteredPayments.map((payment) => (
                  <tr key={payment.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4 font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">PAY-{payment.id}</td>
                    <td className="px-5 py-4 font-medium text-[#1e293b]">#RES-{payment.reservationId}</td>
                    <td className="px-5 py-4 font-bold text-[#1e293b]">{payment.amount} DH</td>
                    <td className="px-5 py-4">
                      <span className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider w-fit ${
                        payment.paid ? 'bg-success-50 text-success-500' : 'bg-warning-50 text-warning-500'
                      }`}>
                        {payment.paid ? <CheckCircle2 size={12} /> : <Clock size={12} />}
                        {payment.paid ? t('payments.completed') : t('payments.pending')}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {!payment.paid && (
                          <button
                            onClick={() => markAsPaid(payment.reservationId)}
                            className="px-3 py-1.5 bg-brand-50 text-brand-500 rounded-lg text-[10px] font-bold uppercase tracking-wider hover:bg-brand-500 hover:text-white transition-all"
                          >
                            Mark Paid
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-slate-400 text-sm">No payments found</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
