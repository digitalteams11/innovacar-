import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../context/PermissionContext';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import api from '../api/axios';
import { CreditCard, Download, CheckCircle2, Clock, Loader2, RefreshCcw, XCircle, LockKeyhole, ArrowUpRight } from 'lucide-react';
import ApiErrorState from '../components/ApiErrorState';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';

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
  const [loadError, setLoadError] = useState('');
  const [statsUnavailable, setStatsUnavailable] = useState(false);
  const [exportingStatements, setExportingStatements] = useState(false);
  const { showToast } = useToast();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasPermission, loading: permLoading, isAgencyAdmin } = usePermissions();
  const { hasFeature, getFeature, planName, loading: featureLoading } = useFeatureAccess();

  // Role/permission: an agency owner/admin always has full access to their own
  // agency's data — this is a role bypass, not a plan bypass.
  const canViewPayments = isAgencyAdmin || hasPermission('PAYMENT_VIEW');
  const canViewStats = isAgencyAdmin || hasPermission('PAYMENT_STATS_VIEW');
  // Plan/feature: whether the subscription includes Payments at all. This must
  // NOT bypass for the owner — the backend's FeatureAccessInterceptor enforces
  // the real plan restriction for every role including the owner, so bypassing
  // it here only meant the owner always attempted (and always got a 403 from)
  // a call the server was always going to reject. The backend gates the whole
  // /api/payments/** prefix on a single "PAYMENTS" feature — stats and
  // monthly-revenue share it, there's no separate plan tier for stats alone.
  const hasPlanPayments = hasFeature('PAYMENTS');
  const paymentsFeature = getFeature('PAYMENTS');
  const requiredPlan = paymentsFeature?.requiredPlan || paymentsFeature?.requiredPlans?.[0];

  const fetchPayments = async (signal?: AbortSignal) => {
    setLoading(true);
    setLoadError('');
    setStatsUnavailable(false);

    const wantsStats = canViewStats && hasPlanPayments;
    const [paymentsResult, statsResult, revenueResult] = await Promise.allSettled([
      api.get('/payments', { signal }),
      wantsStats ? api.get('/payments/stats', { signal }) : Promise.resolve(null),
      wantsStats ? api.get('/payments/monthly-revenue', { signal }) : Promise.resolve(null),
    ]);
    if (signal?.aborted) return;

    if (paymentsResult.status === 'fulfilled') {
      setPayments(paymentsResult.value?.data || []);
    } else {
      const err = paymentsResult.reason;
      const errorCode = err?.errorCode || err?.response?.data?.errorCode;
      const msg = err?.userMessage || err?.response?.data?.message;
      if (errorCode === 'PERMISSION_DENIED' || errorCode === 'ACCESS_DENIED') {
        setLoadError('You do not have permission to view payments.');
      } else if (errorCode === 'FEATURE_NOT_INCLUDED') {
        // The client-side plan gate below should already have caught this
        // before ever calling the API — reaching here means the cached
        // feature list was stale. Refresh so the gate screen takes over
        // instead of showing a raw error.
        setLoadError('Payments are not included in your current plan. Please upgrade.');
      } else {
        setLoadError(msg || 'Unable to load payment information. Please try again later.');
      }
      setLoading(false);
      return;
    }

    // Stats/monthly-revenue are supplementary — a failure there degrades the
    // page (metrics show as 0/empty, a small notice appears) but never hides
    // the payment list that already loaded successfully.
    if (statsResult.status === 'fulfilled' && statsResult.value) {
      setStats(statsResult.value.data || {});
    } else if (wantsStats) {
      setStatsUnavailable(true);
    }
    if (revenueResult.status === 'fulfilled' && revenueResult.value) {
      setMonthlyRevenue(revenueResult.value.data || []);
    }

    setLoading(false);
  };

  useEffect(() => {
    if (permLoading || featureLoading) return;
    if (!canViewPayments) {
      if (import.meta.env.DEV) console.log('[PERMISSION_FETCH_GUARD] endpoint=/api/payments requiredPermission=PAYMENT_VIEW allowed=false component=Payments');
      setLoading(false);
      return;
    }
    if (!hasPlanPayments) {
      if (import.meta.env.DEV) console.log('[PERMISSION_FETCH_GUARD] endpoint=/api/payments requiredFeature=PAYMENTS hasPlanFeature=false component=Payments');
      setLoading(false);
      return;
    }
    // Cancel any in-flight request from a prior mount/dependency change, and
    // abort on unmount — prevents a slow response from setting state on an
    // unmounted component and prevents two overlapping fetches from ever
    // racing (the "duplicate simultaneous requests" this page used to send).
    const controller = new AbortController();
    fetchPayments(controller.signal);
    return () => controller.abort();
  }, [canViewPayments, hasPlanPayments, permLoading, featureLoading]); // eslint-disable-line react-hooks/exhaustive-deps

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

  /**
   * Real server-side PDF — replaces the old client-side comma-join that
   * produced a mislabeled, mojibake-prone "payments.csv" (no BOM, no
   * quoting). PDF is now the primary/default export action for normal
   * agency users; CSV/XLSX remain available as an advanced/secondary
   * action elsewhere (Vehicles page), not duplicated here.
   */
  const exportStatements = async () => {
    if (exportingStatements) return;
    setExportingStatements(true);
    try {
      const response = await api.get('/payments/export/pdf', { responseType: 'blob' });
      const contentType = String(response.headers['content-type'] || '');
      if (contentType.includes('application/json')) {
        const text = await (response.data as Blob).text();
        const parsed = JSON.parse(text);
        throw new Error(parsed.message || t('payments.exportFailed', 'Failed to generate the PDF report.'));
      }
      const disposition = String(response.headers['content-disposition'] || '');
      const match = disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)"?/i);
      const filename = match ? decodeURIComponent(match[1]) : 'innovacar-payments.pdf';

      const blob = new Blob([response.data], { type: contentType || 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast(t('toast.statementsExported'));
    } catch (err: any) {
      let message = t('payments.exportFailed', 'Failed to generate the PDF report.');
      const data = err?.response?.data;
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text());
          if (parsed.errorCode === 'EXPORT_NO_DATA') message = t('payments.exportNoData', 'No payments match the selected filters.');
          else if (parsed.message) message = parsed.message;
        } catch {
          /* body wasn't JSON either — fall back to the generic message above */
        }
      } else if (err?.message) {
        message = err.message;
      }
      showToast(message, 'error');
    } finally {
      setExportingStatements(false);
    }
  };

  const markAsPaid = async (reservationId?: number) => {
    if (!reservationId) return;
    try {
      await api.patch(`/payments/reservation/${reservationId}/pay`);
      await fetchPayments();
      showToast(t('toast.success', { action: 'Payment marked as paid' }));
    } catch {
      showToast('Unable to complete the payment. Please try again.', 'error');
    }
  };

  const chartMax = Math.max(...monthlyRevenue.map((item) => Number(item.revenue || 0)), 1);

  // Permission gate — user lacks PAYMENT_VIEW
  if (!permLoading && !featureLoading && !canViewPayments) {
    return (
      <div className="m-6 p-8 border border-[#e8e6e1] bg-white rounded-lg text-center dark:border-white/10 dark:bg-[#1a2332]">
        <LockKeyhole size={28} className="mx-auto text-slate-400" />
        <h2 className="mt-3 text-base font-bold text-[#1e293b] dark:text-white">{t('payments.accessRestricted')}</h2>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{t('payments.accessRestrictedDesc')}</p>
      </div>
    );
  }

  // Plan gate — PAYMENTS feature not included in current plan. This checks the
  // same feature list the backend enforces (FeatureAccessInterceptor), so it
  // shows once and never attempts the API call the server would reject anyway
  // — no wasted request, no generic error screen, no retry button to loop on.
  if (!permLoading && !featureLoading && canViewPayments && !hasPlanPayments) {
    return (
      <div className="m-6 p-8 border border-amber-100 bg-amber-50 rounded-lg text-center dark:border-amber-500/20 dark:bg-amber-500/10">
        <ArrowUpRight size={28} className="mx-auto text-amber-500" />
        <h2 className="mt-3 text-base font-bold text-[#1e293b] dark:text-white">{t('payments.upgradeRequired')}</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          {t('payments.upgradeRequiredDesc')}
        </p>
        {(planName || requiredPlan) && (
          <p className="mt-3 text-xs font-semibold text-slate-500 dark:text-slate-400">
            {planName && <>{t('payments.currentPlan', 'Current plan')}: <span className="text-slate-700 dark:text-slate-200">{planName}</span></>}
            {planName && requiredPlan && <span className="mx-2 text-slate-300">·</span>}
            {requiredPlan && <>{t('payments.requiredPlan', 'Required plan')}: <span className="text-slate-700 dark:text-slate-200">{requiredPlan}</span></>}
          </p>
        )}
        <button
          type="button"
          onClick={() => navigate('/settings?tab=billing')}
          className="mt-5 inline-flex items-center gap-2 rounded-xl bg-amber-500 hover:bg-amber-600 px-5 py-2.5 text-sm font-bold text-white transition-colors"
        >
          <ArrowUpRight size={15} /> {t('payments.viewPlans', 'View plans / Upgrade')}
        </button>
      </div>
    );
  }

  if (loadError) {
    // ApiErrorState's button calls onRetry directly as an onClick handler, which
    // would otherwise pass the click MouseEvent through as fetchPayments' signal
    // argument — wrap it so a manual retry always starts a fresh, real request.
    return <div className="p-3 sm:p-4 lg:p-6"><ApiErrorState message={loadError} onRetry={() => fetchPayments()} /></div>;
  }

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title={t('payments.title')}
        subtitle={t('payments.subtitle')}
        icon={CreditCard}
        actions={(
          <button
            onClick={exportStatements}
            disabled={exportingStatements}
            className="surface-control flex items-center gap-2 h-10 px-4 font-medium text-xs sm:text-sm active:scale-95 disabled:opacity-60 disabled:cursor-wait"
          >
            {exportingStatements ? <Loader2 size={16} className="animate-spin" /> : <Download size={16} />}
            <span className="hidden sm:inline">{t('payments.exportStatements')}</span>
          </button>
        )}
      />

      {statsUnavailable && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2.5 text-xs font-semibold text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300">
          {t('payments.statsUnavailable', 'Payment statistics are temporarily unavailable — the list below is up to date.')}
        </div>
      )}

      {/* grid-cols-2 from the base (not sm:) — these must read as compact 2-up
          cards on a phone, not full-width stacked cards each taking a whole
          screen's worth of vertical space. */}
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-3 sm:gap-4">
        {[
          [t('payments.totalRevenueMetric'), money(stats.totalRevenue)],
          [t('payments.monthlyRevenueMetric'), money(stats.monthlyRevenue)],
          [t('payments.pendingPayments'), money(stats.pendingAmount), `${stats.pendingCount || 0} ${t('payments.transactions')}`],
          [t('payments.paidInvoices'), stats.paidInvoices || 0],
          [t('payments.overdueInvoices'), stats.overdueInvoices || 0],
          [t('payments.refunds'), money(stats.refundAmount), `${stats.refundCount || 0} ${t('payments.transactions')}`],
        ].map(([label, value, caption]) => (
          <div key={label} className="metric-surface min-h-[112px] sm:min-h-0">
            <p className="text-[var(--text-muted)] text-[10px] uppercase font-semibold mb-2 tracking-[0.12em]">{label}</p>
            <h3 className="text-xl font-semibold text-[var(--text-primary)]">{value}</h3>
            {caption && <p className="text-[10px] font-bold text-slate-400 mt-1">{caption}</p>}
          </div>
        ))}
      </div>

      <div className="data-surface p-4 sm:p-5">
        <h3 className="text-sm sm:text-base font-bold text-[#1e293b] mb-4">{t('payments.revenueCharts')}</h3>
        <div className="h-40 flex items-end gap-3">
          {monthlyRevenue.length === 0 ? (
            <div className="w-full text-center text-slate-400 text-sm">{t('payments.noRevenueData')}</div>
          ) : monthlyRevenue.map((item) => (
            <div key={`${item.month}-${item.year}`} className="flex-1 flex flex-col items-center gap-2 min-w-0">
              <div className="w-full bg-[var(--brand-primary)] rounded-t-sm min-h-[4px] opacity-80 hover:opacity-100 transition-opacity" style={{ height: `${Math.max((Number(item.revenue || 0) / chartMax) * 120, 4)}px` }} />
              <span className="text-[10px] font-bold text-slate-400 truncate">{item.month}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="data-surface">
        <div className="p-4 sm:p-5 border-b border-[#e8e6e1]/50 flex flex-col sm:flex-row justify-between gap-3">
          <h3 className="text-sm sm:text-base font-bold text-[#1e293b]">{t('payments.paymentHistory')}</h3>
          <SearchInput className="w-full sm:w-72" placeholder={t('payments.searchPlaceholder')} value={searchQuery} onChange={setSearchQuery} />
        </div>
        {loading ? (
          <div className="px-5 py-8 text-center"><Loader2 size={24} className="animate-spin text-brand-500 mx-auto" /></div>
        ) : (
        <ResponsiveDataView
          mobile={
            filteredPayments.length === 0 ? (
              <div className="px-5 py-8 text-center text-slate-400 text-sm">{t('payments.noPaymentsFound')}</div>
            ) : (
              <div className="divide-y divide-[#e8e6e1]/50 dark:divide-white/5">
                {filteredPayments.map((payment) => (
                  <div key={payment.id} className="p-4 space-y-2.5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <span className="font-mono text-xs font-bold text-slate-400">{payment.paymentNumber || `PAY-${payment.id}`}</span>
                        <h3 className="mt-0.5 truncate text-sm font-semibold text-[#1e293b] dark:text-white">{payment.clientName || 'N/A'}</h3>
                        <p className="mt-0.5 truncate text-xs text-slate-400">{payment.vehicleLabel || 'N/A'}</p>
                      </div>
                      <span className="shrink-0 text-sm font-bold text-[#1e293b] dark:text-white">{money(payment.amount)}</span>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider ${statusClass(payment.status)}`}>
                        {payment.status === 'PAID' ? <CheckCircle2 size={12} /> : ['FAILED', 'CANCELLED', 'EXPIRED'].includes(payment.status) ? <XCircle size={12} /> : <Clock size={12} />}
                        {payment.status.replace('_', ' ')}
                      </span>
                      <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{payment.paymentMethod || 'N/A'}</span>
                      <span className="text-[10px] text-slate-400">
                        {payment.paymentDate ? new Date(payment.paymentDate).toLocaleDateString() : 'N/A'}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400">
                      {payment.reservationLabel || (payment.reservationId ? `RES-${payment.reservationId}` : null)}
                      {payment.contractNumber && ` · ${payment.contractNumber}`}
                    </p>
                    {payment.reservationId && payment.status !== 'PAID' && (
                      <button
                        onClick={() => markAsPaid(payment.reservationId)}
                        className="flex min-h-11 w-full items-center justify-center gap-1.5 rounded-lg bg-brand-50 text-xs font-bold uppercase tracking-wider text-brand-500 hover:bg-brand-500 hover:text-white transition-all"
                      >
                        <RefreshCcw size={13} /> {t('payments.markPaid')}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )
          }
          desktop={
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left min-w-[980px]">
            <thead>
              <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                {[t('payments.paymentId'), t('payments.client'), t('payments.vehicle'), t('payments.reservation'), t('payments.contract'), t('payments.amount'), t('payments.method'), t('payments.status'), t('payments.date'), t('payments.actions')].map((heading) => (
                  <th key={heading} className="px-3 sm:px-5 py-3 sm:py-4">{heading}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e8e6e1]/50">
              {filteredPayments.length > 0 ? filteredPayments.map((payment) => (
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
                        <RefreshCcw size={12} /> {t('payments.markPaid')}
                      </button>
                    )}
                  </td>
                </tr>
              )) : (
                <tr><td colSpan={10} className="px-5 py-8 text-center text-slate-400 text-sm">{t('payments.noPaymentsFound')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
          }
        />
        )}
      </div>
    </div>
  );
}
