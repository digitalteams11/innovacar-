import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import UsageBar from '../UsageBar';
import { usePlanAccess } from '../../hooks/usePlanAccess';
import { trialBadgeClass, trialCountdownText } from '../../lib/trialDisplay';
import {
  CreditCard, Crown, Car, Users, MapPin, Calendar, Loader2, ArrowRight,
  Receipt, ShieldAlert, Download, ChevronDown, Tag, CheckCircle, XCircle,
  PartyPopper, RefreshCw, FileText, UserCheck,
} from 'lucide-react';

/** Two decimals, always — a bare `199` or `39.8` next to a proper `159.20` reads as a typo/bug on a pricing summary. */
function formatMoney(value: number | string | null | undefined): string {
  const n = typeof value === 'number' ? value : Number(value ?? 0);
  return Number.isFinite(n) ? n.toFixed(2) : '0.00';
}

interface PlanData {
  id: number;
  code: string;
  name: string;
  description?: string;
  monthlyPrice: number;
  yearlyPrice: number;
  maxVehicles: number;
  maxEmployees: number;
  maxGpsDevices: number;
  maxReservations?: number;
  contractLimit?: number;
  clientLimit?: number;
  storageLimitMb?: number;
  features?: string[];
  includedModules?: Record<string, boolean>;
  checkoutConfigured?: boolean;
  isFree?: boolean;
  highlighted?: boolean;
  billingCycleAllowedMonthly?: boolean;
  billingCycleAllowedYearly?: boolean;
  trialDays?: number;
}

interface InvoiceData {
  id: number;
  invoiceNumber: string;
  planName: string;
  billingCycle: string;
  total: number;
  currency: string;
  status: string;
  issuedAt: string;
}

export default function BillingTab() {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();
  const { data: planAccess } = usePlanAccess();
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [plansError, setPlansError] = useState<string | null>(null);
  const [status, setStatus] = useState<any>(null);
  const [plans, setPlans] = useState<PlanData[]>([]);
  const [invoices, setInvoices] = useState<InvoiceData[]>([]);
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [selectedPlan, setSelectedPlan] = useState<PlanData | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [showManage, setShowManage] = useState(true);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('TOO_EXPENSIVE');
  const [cancelFeedback, setCancelFeedback] = useState('');
  const [cancelConfirmed, setCancelConfirmed] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [pendingCancellation, setPendingCancellation] = useState<any>(null);
  const [paymentSuccess, setPaymentSuccess] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // When the user returns from Whop checkout with ?payment=success, poll refresh-status
  // to pick up the activated plan (activation comes via webhook, may take a few seconds).
  useEffect(() => {
    const param = searchParams.get('payment');
    if (param === 'success') {
      setPaymentSuccess(true);
      // Remove the query param to avoid re-triggering on navigation
      searchParams.delete('payment');
      setSearchParams(searchParams, { replace: true });
      handleRefreshStatus();
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRefreshStatus = async () => {
    setRefreshing(true);
    try {
      await api.post('/billing/refresh-status');
      await fetchData();
    } catch {
      // Silently ignore — fetchData still runs
      await fetchData();
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    setPlansError(null);

    if (import.meta.env.DEV) {
      console.info('[BILLING_DEBUG] plansEndpoint=/billing/plans');
    }

    const [statusResult, plansResult, invoicesResult, cancellationResult] = await Promise.allSettled([
      api.get('/subscriptions/status'),
      api.get('/billing/plans'),
      api.get('/subscriptions/invoices'),
      api.get('/subscriptions/cancellation-requests/mine'),
    ]);

    // Current subscription — failure here must NOT block plan rendering
    if (statusResult.status === 'fulfilled') {
      const d = statusResult.value.data;
      setStatus(d?.data ? { ...d, ...d.data } : d);
      if (import.meta.env.DEV) {
        console.info('[BILLING_DEBUG] currentSubscriptionStatus=', d?.data ?? d);
      }
    } else {
      console.warn('[BILLING_DEBUG] currentSubscription failed —', statusResult.reason?.message ?? statusResult.reason);
    }

    // Plans — core rendering depends on this
    if (plansResult.status === 'fulfilled') {
      const raw = plansResult.value.data;
      const normalized: PlanData[] = Array.isArray(raw) ? raw : Array.isArray(raw?.data) ? raw.data : [];
      if (import.meta.env.DEV) {
        console.info('[BILLING_DEBUG] rawPlansResponse=', raw);
        console.info('[BILLING_DEBUG] normalizedPlansCount=', normalized.length);
        console.info('[BILLING_DEBUG] activePlansCount=', normalized.filter((p) => p.code).length);
      }
      if (!Array.isArray(raw) && !Array.isArray(raw?.data)) {
        console.error('[BILLING_DEBUG] Unexpected plans response shape — expected array or {data:[]}', raw);
      }
      setPlans(normalized);
    } else {
      const msg = plansResult.reason?.response?.status
        ? `GET /billing/plans → ${plansResult.reason.response.status}`
        : plansResult.reason?.message ?? 'Unknown error';
      console.error('[BILLING_DEBUG] plans request failed —', msg);
      setPlansError(msg);
      setPlans([]);
      showToast(t('settings.billingTab.subscriptionPlansLoadFailed'), 'error');
    }

    // Invoices
    if (invoicesResult.status === 'fulfilled') {
      setInvoices(invoicesResult.value.data?.data || []);
    }

    // Cancellation
    if (cancellationResult.status === 'fulfilled') {
      const latestRequest = cancellationResult.value.data?.data;
      setPendingCancellation(latestRequest?.status === 'PENDING' ? latestRequest : null);
    }

    setLoading(false);
  };

  const openUpgrade = (plan: PlanData) => {
    setSelectedPlan(plan);
    setShowUpgradeModal(true);
  };

  const handleCheckout = async () => {
    if (!selectedPlan) return;
    if (selectedPlan.checkoutConfigured === false && !selectedPlan.isFree) {
      showToast(t('settings.billingTab.checkoutMissingAdmin'), 'error');
      return;
    }
    setCheckoutLoading(true);
    try {
      // Innovacar only ever sends the plan identity — no promo code, no
      // frontend-calculated price. Whop's own checkout page is the single
      // place a promo code is entered and applied; the provider is the only
      // authoritative source for the final discounted price.
      const { data } = await api.post('/billing/checkout', {
        planCode: selectedPlan.code,
        billingCycle: billingCycle.toUpperCase(),
      });
      if (data.success && data.checkoutUrl) {
        window.location.href = data.checkoutUrl;
      } else if (data.errorCode === 'PLAN_CHECKOUT_URL_MISSING' || data.errorCode === 'CHECKOUT_NOT_CONFIGURED') {
        showToast(data.message || 'Checkout link is missing for this plan. Please configure it in Super Admin → Subscriptions.', 'error');
      } else {
        showToast(data.message || 'Checkout is not configured for this plan. Please contact support.', 'error');
      }
    } catch (err: any) {
      const errCode = err?.response?.data?.errorCode;
      if (errCode === 'PLAN_CHECKOUT_URL_MISSING' || errCode === 'CHECKOUT_NOT_CONFIGURED') {
        showToast(err?.response?.data?.message || 'Checkout link is missing for this plan. Please configure it in Super Admin → Subscriptions.', 'error');
      } else {
        showToast(err?.userMessage || 'Failed to create checkout session. Please try again.', 'error');
      }
    } finally {
      setCheckoutLoading(false);
    }
  };

  const handleRequestCancellation = async () => {
    if (!cancelConfirmed) {
      showToast(t('settings.billingTab.confirmCancellationRequest'), 'warning');
      return;
    }
    setCancelling(true);
    try {
      const { data } = await api.post('/subscriptions/cancellation-requests', {
        reason: cancelReason,
        feedback: cancelFeedback.trim() || undefined,
        confirm: true,
      });
      showToast(data?.message || 'Your cancellation request has been submitted for review.', 'success');
      setShowCancelModal(false);
      setCancelFeedback('');
      setCancelConfirmed(false);
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to submit your cancellation request. Please try again later.', 'error');
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 size={28} className="animate-spin text-brand-500" />
      </div>
    );
  }

  const visiblePlans = plans.filter((plan) =>
    billingCycle === 'monthly'
      ? plan.billingCycleAllowedMonthly !== false || plan.isFree
      : plan.billingCycleAllowedYearly !== false || plan.isFree
  );

  if (import.meta.env.DEV) {
    console.info('[BILLING_DEBUG] selectedCycle=', billingCycle, 'renderPlansCount=', visiblePlans.length);
  }

  const locale = i18n.resolvedLanguage || i18n.language || undefined;
  const formatDate = (value?: string) => value ? new Date(value).toLocaleDateString(locale) : t('common.notAvailable', 'N/A');
  const formatPlanName = (planName?: string, planCode?: string) => {
    const key = (planCode || planName || 'trial').toLowerCase();
    return t(`subscription.planNames.${key}`, planName || t('subscription.planNames.trial'));
  };
  const formatStatus = (value?: string) => value ? t(`subscription.statuses.${value}`, value) : t('subscription.statuses.TRIAL');
  const formatCycle = (value?: string) => value ? t(`settings.billingTab.cycles.${value.toLowerCase()}`, value) : '';

  return (
    <div className="space-y-6">
      {/* Payment success banner — shown when user returns from Whop checkout */}
      {paymentSuccess && (
        <div className="flex items-start gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-200">
          <PartyPopper size={20} className="text-emerald-600 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-emerald-800">Payment received!</p>
            <p className="text-xs text-emerald-600 mt-0.5">
              Your subscription is being activated. This usually takes a few seconds after payment confirmation.
            </p>
          </div>
          <button
            onClick={handleRefreshStatus}
            disabled={refreshing}
            className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700 hover:text-emerald-900 disabled:opacity-50 shrink-0"
          >
            <RefreshCw size={13} className={refreshing ? 'animate-spin' : ''} />
            {refreshing ? 'Checking…' : 'Refresh'}
          </button>
        </div>
      )}

      {/* Current Plan */}
      <div className="card-premium p-4 sm:p-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-5">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-[#0a0f2c] flex items-center justify-center text-white">
              <Crown size={24} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-[#1e293b]">
                  {status?.isTrial ? t('subscription.trialCard.title') : formatPlanName(status?.planName, status?.planCode)}
                </h3>
                {status?.isTrial ? (
                  <span className={`px-2.5 py-0.5 rounded-lg text-xs font-semibold border ${trialBadgeClass(status?.trialDaysRemaining ?? 0)}`}>
                    {t('subscription.trialCard.active')}
                  </span>
                ) : status?.status === 'EXPIRED' ? (
                  <span className="px-2.5 py-0.5 rounded-lg bg-rose-50 text-rose-700 text-xs font-semibold border border-rose-200">
                    {t('subscription.trialCard.expired')}
                  </span>
                ) : (
                  <span className="px-2.5 py-0.5 rounded-lg bg-slate-50 text-slate-600 text-xs font-semibold border border-slate-200">
                    {formatStatus(status?.status)}
                  </span>
                )}
              </div>
              <p className="text-sm text-slate-500 mt-0.5">
                {status?.isTrial
                  ? trialCountdownText(status?.trialDaysRemaining ?? 0, status?.trialEndsAt, t, formatDate)
                  : status?.status === 'EXPIRED'
                    ? t('subscription.trialCard.expired')
                    : status?.subscriptionActive
                      ? t('settings.billingTab.renewsOn', { date: formatDate(status?.currentPeriodEnd) })
                      : t('settings.billingTab.subscriptionInactive')}
              </p>
            </div>
          </div>
          <button
            onClick={() => {
              const recommended = plans.find((p) => p.code?.toLowerCase() === 'premium') || plans[0];
              if (recommended) openUpgrade(recommended);
            }}
            className="bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center justify-center gap-2"
          >
            {status?.isTrial || status?.status === 'EXPIRED' ? t('subscription.trialCard.upgradePlan') : t('settings.billingTab.changePlan')} <ArrowRight size={16} />
          </button>
        </div>

        {/* Usage */}
        <div className="mt-6 pt-6 border-t border-[#e8e6e1]/60 grid grid-cols-1 md:grid-cols-2 gap-5">
          <UsageBar label={t('subscription.vehicles')} current={status?.vehicleCount ?? 0} max={status?.maxVehicles ?? 0} icon={<Car size={16} />} />
          <UsageBar label={t('subscription.employees')} current={status?.employeeCount ?? 0} max={status?.maxEmployees ?? 0} icon={<Users size={16} />} />
          <UsageBar label={t('subscription.gpsDevices')} current={status?.gpsCount ?? 0} max={status?.maxGpsDevices ?? 0} icon={<MapPin size={16} />} />
          <UsageBar label={t('subscription.reservations')} current={status?.reservationCount ?? 0} max={status?.maxReservations ?? 0} icon={<Calendar size={16} />} />
          <UsageBar label={t('subscription.clients')} current={planAccess?.limits.clients.used ?? 0} max={planAccess?.limits.clients.limit ?? 0} icon={<UserCheck size={16} />} />
          <UsageBar label={t('subscription.contracts')} current={planAccess?.limits.contracts.used ?? 0} max={planAccess?.limits.contracts.limit ?? 0} icon={<FileText size={16} />} />
        </div>
      </div>

      {/* Billing History */}
      <div className="card-premium p-4 sm:p-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Receipt size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">{t('settings.billingTab.history')}</h3>
            <p className="text-sm text-slate-400 font-normal">{t('settings.billingTab.invoicesIssued')}</p>
          </div>
        </div>

        {invoices.length === 0 ? (
          <p className="text-sm text-slate-400 text-center py-6">{t('settings.billingTab.noInvoicesYet')}</p>
        ) : (
          <div className="divide-y divide-[#e8e6e1]/40">
            {invoices.map((invoice) => (
              <div key={invoice.id} className="flex items-center justify-between py-3">
                <div>
                  <p className="text-sm font-semibold text-[#1e293b]">{invoice.invoiceNumber}</p>
                  <p className="text-xs text-slate-400">{formatPlanName(invoice.planName)} · {formatCycle(invoice.billingCycle)} · {invoice.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString(locale) : '—'}</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold text-[#1e293b]">{invoice.total} {invoice.currency}</span>
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-lg ${invoice.status === 'PAID' ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
                    {formatStatus(invoice.status)}
                  </span>
                  <button className="text-slate-400 hover:text-brand-500" title={t('settings.billingTab.downloadNotAvailable')}>
                    <Download size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Payment Method */}
      <div className="card-premium p-4 sm:p-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <CreditCard size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">{t('settings.billingTab.paymentMethod')}</h3>
            <p className="text-sm text-slate-400 font-normal">{t('settings.billingTab.paymentsProcessedBy')}</p>
          </div>
        </div>
        {status?.subscriptionActive && !status?.isTrial ? (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">{t('settings.billingTab.provider')}</span>
              <span className="text-sm font-medium text-[#1e293b]">Whop</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">{t('payments.status')}</span>
              <span className={`text-xs font-bold px-2 py-0.5 rounded-lg ${status.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                {formatStatus(status.status)}
              </span>
            </div>
            <p className="text-xs text-slate-400 pt-1">
              {t('settings.billingTab.paymentMethodManagedByWhop')}
            </p>
          </div>
        ) : (
          <p className="text-sm text-slate-500">
            {t('settings.billingTab.noActiveSubscriptionYet')}
          </p>
        )}
      </div>

      {/* Manage Subscription */}
      <div className="card-premium p-4 sm:p-6">
        <button onClick={() => setShowManage((v) => !v)} className="w-full flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-slate-50 flex items-center justify-center">
              <ShieldAlert size={20} className="text-slate-500" />
            </div>
            <div className="text-left">
              <h3 className="text-base font-bold text-[#1e293b]">{t('settings.billingTab.manageSubscription')}</h3>
              <p className="text-sm text-slate-400 font-normal">{t('settings.billingTab.manageSubscriptionDesc')}</p>
            </div>
          </div>
          <ChevronDown size={18} className={`text-slate-400 transition-transform ${showManage ? 'rotate-180' : ''}`} />
        </button>

        {showManage && (
          <div className="pt-5 mt-5 border-t border-[#e8e6e1]/60 space-y-4">
            <div className="flex items-center gap-3">
              <button
                onClick={() => setBillingCycle('monthly')}
                className={`px-4 py-2 rounded-lg text-sm font-medium ${billingCycle === 'monthly' ? 'bg-[#0a0f2c] text-white' : 'bg-slate-50 text-slate-500'}`}
              >
                {t('settings.billingTab.monthly')}
              </button>
              <button
                onClick={() => setBillingCycle('yearly')}
                className={`px-4 py-2 rounded-lg text-sm font-medium ${billingCycle === 'yearly' ? 'bg-[#0a0f2c] text-white' : 'bg-slate-50 text-slate-500'}`}
              >
                {t('settings.billingTab.yearly')}
              </button>
            </div>

            {plansError ? (
              <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                {t('settings.billingTab.plansLoadFailed', { error: plansError })}
              </div>
            ) : plans.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400">
                {t('settings.billingTab.noPlansFound')}
              </div>
            ) : visiblePlans.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400">
                {t('settings.billingTab.noCyclePlans', { cycle: formatCycle(billingCycle) })}
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {visiblePlans.map((plan) => {
                const isCurrent = status?.planName === plan.name || status?.planCode === plan.code;
                const price = billingCycle === 'yearly' ? plan.yearlyPrice : plan.monthlyPrice;
                const checkoutOk = plan.isFree || plan.checkoutConfigured !== false;
                const limitLabel = billingCycle === 'yearly' && !plan.billingCycleAllowedYearly
                  ? t('settings.billingTab.yearlyNotAvailable')
                  : billingCycle === 'monthly' && !plan.billingCycleAllowedMonthly
                  ? t('settings.billingTab.monthlyNotAvailable')
                  : null;
                return (
                  <div key={plan.id} className={`p-4 rounded-xl border flex flex-col ${isCurrent ? 'border-brand-500 bg-brand-50/40' : plan.highlighted ? 'border-amber-300 bg-amber-50/30' : 'border-[#e8e6e1]'}`}>
                    <div className="flex items-start justify-between gap-2 mb-1">
                      <p className="text-sm font-bold text-[#1e293b]">{formatPlanName(plan.name, plan.code)}</p>
                      <div className="flex gap-1 flex-wrap justify-end">
                        {isCurrent && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-brand-100 text-brand-700">{t('settings.billingTab.current')}</span>}
                        {plan.highlighted && !isCurrent && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">{t('settings.billingTab.popular')}</span>}
                      </div>
                    </div>
                    {plan.description && <p className="text-[11px] text-slate-500 mb-2 leading-snug">{plan.description}</p>}
                    <p className="text-base font-bold text-[#1e293b] mb-2">{price ? `${price} MAD` : t('settings.billingTab.free')}{price ? <span className="text-xs font-normal text-slate-400"> {billingCycle === 'yearly' ? t('settings.billingTab.perYear') : t('settings.billingTab.perMonth')}</span> : ''}</p>

                    {/* Limits */}
                    <div className="text-[11px] text-slate-500 space-y-0.5 mb-3">
                      {plan.maxVehicles != null && <div className="flex justify-between"><span>{t('subscription.vehicles')}</span><span className="font-semibold text-slate-700">{plan.maxVehicles === -1 ? '∞' : plan.maxVehicles}</span></div>}
                      {plan.maxEmployees != null && <div className="flex justify-between"><span>{t('subscription.employees')}</span><span className="font-semibold text-slate-700">{plan.maxEmployees === -1 ? '∞' : plan.maxEmployees}</span></div>}
                      {plan.maxGpsDevices != null && <div className="flex justify-between"><span>{t('subscription.gpsDevices')}</span><span className="font-semibold text-slate-700">{plan.maxGpsDevices === -1 ? '∞' : plan.maxGpsDevices}</span></div>}
                      {plan.maxReservations != null && <div className="flex justify-between"><span>{t('subscription.reservations')}</span><span className="font-semibold text-slate-700">{plan.maxReservations === -1 ? '∞' : plan.maxReservations}</span></div>}
                    </div>

                    {/* Features */}
                    {plan.features && plan.features.length > 0 && (
                      <ul className="text-[11px] space-y-0.5 mb-3 flex-1">
                        {plan.features.slice(0, 5).map((f) => (
                          <li key={f} className="flex items-center gap-1.5 text-slate-600">
                            <CheckCircle size={10} className="text-emerald-500 shrink-0" />
                            {f}
                          </li>
                        ))}
                        {plan.features.length > 5 && (
                          <li className="text-slate-400 ms-3.5">{t('settings.billingTab.moreFeatures', { count: plan.features.length - 5 })}</li>
                        )}
                      </ul>
                    )}

                    {/* Included modules if no features text */}
                    {(!plan.features || plan.features.length === 0) && plan.includedModules && (
                      <ul className="text-[11px] space-y-0.5 mb-3 flex-1">
                        {Object.entries(plan.includedModules).map(([mod, enabled]) => (
                          <li key={mod} className={`flex items-center gap-1.5 ${enabled ? 'text-slate-600' : 'text-slate-300'}`}>
                            {enabled
                              ? <CheckCircle size={10} className="text-emerald-500 shrink-0" />
                              : <XCircle size={10} className="shrink-0" />}
                            {mod.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
                          </li>
                        ))}
                      </ul>
                    )}

                    {!checkoutOk && (
                      <p className="text-[10px] text-amber-600 bg-amber-50 border border-amber-200 rounded px-2 py-1 mb-2">
                        {t('settings.billingTab.checkoutNotConfigured')}
                      </p>
                    )}
                    {limitLabel && (
                      <p className="text-[10px] text-slate-400 mb-2">{limitLabel}</p>
                    )}
                    <button
                      onClick={() => !isCurrent && checkoutOk && !limitLabel && openUpgrade(plan)}
                      disabled={isCurrent || !checkoutOk || !!limitLabel}
                      className={`mt-auto w-full py-2 rounded-lg text-xs font-semibold transition-colors ${
                        isCurrent ? 'bg-slate-100 text-slate-400 cursor-default'
                        : !checkoutOk || limitLabel ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
                        : plan.highlighted ? 'bg-amber-500 hover:bg-amber-600 text-white'
                        : 'bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90'}`}
                    >
                      {isCurrent ? t('settings.billingTab.currentPlanButton') : !checkoutOk ? t('settings.billingTab.notAvailable') : t('settings.billingTab.select')}
                    </button>
                  </div>
                );
              })}
              </div>
            )}

            <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
              {pendingCancellation ? (
                <p className="text-xs font-medium text-amber-600">
                  {t('settings.billingTab.cancellationPending')}
                </p>
              ) : (
                <button onClick={() => setShowCancelModal(true)} className="text-xs font-medium text-rose-500 hover:text-rose-600">
                  {t('settings.billingTab.cancelSubscription')}
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Upgrade/checkout confirm modal */}
      {showUpgradeModal && selectedPlan && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4" style={{ paddingBottom: 'max(1rem, env(safe-area-inset-bottom))' }}>
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowUpgradeModal(false)} />
          <div className="relative bg-white dark:bg-[#0f1428] rounded-2xl shadow-elevated w-full max-w-md max-h-[calc(100vh-2rem)] overflow-y-auto p-5 sm:p-6 space-y-4">
            <div>
              <h3 className="text-lg font-bold text-[#1e293b] dark:text-white">{t('settings.billingTab.confirmPlanChange')}</h3>
              <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                {t('settings.billingTab.subscribingTo', { plan: formatPlanName(selectedPlan.name, selectedPlan.code), cycle: formatCycle(billingCycle) })}
              </p>
            </div>

            {/* Trial + price summary — Innovacar shows only the plain plan
                price; Whop's own checkout page is the single place a promo
                code is entered and the only authoritative source for the
                final discounted price. */}
            <div className="bg-[#f5f5f0] dark:bg-white/5 rounded-xl p-3 space-y-1.5 text-sm">
              {(selectedPlan.trialDays ?? 0) > 0 && (
                <div className="flex justify-between text-slate-600 dark:text-slate-300">
                  <span>{t('settings.billingTab.freeTrial')}</span>
                  <span className="font-medium">{t('settings.billingTab.trialDays', { count: selectedPlan.trialDays })}</span>
                </div>
              )}
              <div className="flex justify-between text-slate-600 dark:text-slate-300">
                <span>{(selectedPlan.trialDays ?? 0) > 0 ? t('settings.billingTab.dueToday') : t('settings.billingTab.subtotal')}</span>
                <span className="font-medium">
                  {(selectedPlan.trialDays ?? 0) > 0 ? formatMoney(0) : formatMoney(billingCycle === 'yearly' ? selectedPlan.yearlyPrice : selectedPlan.monthlyPrice)} MAD
                </span>
              </div>
              <div className="flex justify-between font-bold text-[#1e293b] dark:text-white border-t border-[#e8e6e1] dark:border-white/10 pt-1.5 mt-1.5">
                <span>{(selectedPlan.trialDays ?? 0) > 0 ? t('settings.billingTab.afterTrial') : t('settings.billingTab.total')}</span>
                <span>
                  {formatMoney(billingCycle === 'yearly' ? selectedPlan.yearlyPrice : selectedPlan.monthlyPrice)} MAD
                  {billingCycle === 'yearly' ? t('settings.billingTab.perYearSuffix') : t('settings.billingTab.perMonthSuffix')}
                </span>
              </div>
            </div>

            <p className="text-xs text-slate-400 flex items-start gap-1.5">
              <Tag size={13} className="mt-0.5 shrink-0" />
              {t('settings.billingTab.promoOnWhopHint')}
            </p>

            <p className="text-xs text-slate-400">
              {t('settings.billingTab.redirectCheckoutHint')}
            </p>

            <div className="flex flex-col-reverse gap-2.5 sm:flex-row sm:gap-3">
              <button onClick={() => setShowUpgradeModal(false)} className="flex-1 min-h-[44px] bg-slate-100 dark:bg-white/10 hover:bg-slate-200 dark:hover:bg-white/15 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">
                {t('settings.billingTab.cancel')}
              </button>
              <button
                onClick={handleCheckout}
                disabled={checkoutLoading}
                className="flex-1 min-h-[44px] bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center justify-center gap-2"
              >
                {checkoutLoading ? <><Loader2 size={14} className="animate-spin" />{t('settings.billingTab.processing')}</> : t('settings.billingTab.continueToCheckout')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cancellation request modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowCancelModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">{t('settings.billingTab.requestCancellation')}</h3>
            <p className="text-sm text-slate-500 mb-4">
              {t('settings.billingTab.cancellationReviewHint')}
            </p>

            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.billingTab.reason')}</label>
            <select
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm mb-4 focus:outline-none focus:ring-2 ring-rose-100"
            >
              <option value="TOO_EXPENSIVE">{t('settings.billingTab.reasonTooExpensive')}</option>
              <option value="MISSING_FEATURES">{t('settings.billingTab.reasonMissingFeatures')}</option>
              <option value="SWITCHING_PROVIDER">{t('settings.billingTab.reasonSwitchingProvider')}</option>
              <option value="TEMPORARY_PAUSE">{t('settings.billingTab.reasonTemporaryPause')}</option>
              <option value="BUSINESS_CLOSED">{t('settings.billingTab.reasonBusinessClosed')}</option>
              <option value="TECHNICAL_ISSUES">{t('settings.billingTab.reasonTechnicalIssues')}</option>
              <option value="OTHER">{t('settings.billingTab.reasonOther')}</option>
            </select>

            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.billingTab.tellUsMore')}</label>
            <textarea
              value={cancelFeedback}
              onChange={(e) => setCancelFeedback(e.target.value)}
              rows={3}
              placeholder={t('settings.billingTab.feedbackPlaceholder')}
              className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm mb-4 focus:outline-none focus:ring-2 ring-rose-100 resize-none"
            />

            <label className="flex items-start gap-2 mb-4 cursor-pointer">
              <input
                type="checkbox"
                checked={cancelConfirmed}
                onChange={(e) => setCancelConfirmed(e.target.checked)}
                className="mt-0.5"
              />
              <span className="text-sm text-slate-600">{t('settings.billingTab.confirmCancellationReview')}</span>
            </label>

            <div className="flex gap-3">
              <button
                onClick={handleRequestCancellation}
                disabled={cancelling || !cancelConfirmed}
                className="flex-1 bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {cancelling ? t('settings.billingTab.submitting') : t('settings.billingTab.submitRequest')}
              </button>
              <button
                onClick={() => { setShowCancelModal(false); setCancelFeedback(''); setCancelConfirmed(false); }}
                className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {t('settings.billingTab.keepSubscription')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

