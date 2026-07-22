import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import UsageBar from '../components/UsageBar';
import UpgradeModal from '../components/UpgradeModal';
import {
  CreditCard, Check, Zap, Shield, Crown, Rocket, ArrowRight,
  Car, Users, MapPin, Calendar, HardDrive, BarChart3, FileSignature,
  QrCode, Globe, Headphones, CheckCircle2, Loader2, X, AlertTriangle,
  Clock, Receipt, RotateCcw, ChevronDown,
} from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { trialBadgeClass, trialCountdownText } from '../lib/trialDisplay';

const planIcons: Record<string, any> = {
  Trial: Zap,
  Basic: CreditCard,
  Standard: Shield,
  Premium: Crown,
  Enterprise: Rocket,
};

const features = [
  { key: 'maxVehicles', label: 'Max Vehicles', icon: Car },
  { key: 'maxEmployees', label: 'Max Employees', icon: Users },
  { key: 'maxGpsDevices', label: 'GPS Devices', icon: MapPin },
  { key: 'maxReservations', label: 'Reservations / Month', icon: Calendar },
  { key: 'storageLimitMb', label: 'Storage', icon: HardDrive },
  { key: 'analytics', label: 'Analytics & Reports', icon: BarChart3 },
  { key: 'digitalSignatures', label: 'Digital Signatures', icon: FileSignature },
  { key: 'qrContracts', label: 'QR Contracts', icon: QrCode },
  { key: 'whiteLabel', label: 'White Label', icon: Globe },
  { key: 'prioritySupport', label: 'Priority Support', icon: Headphones },
];

const CANCEL_REASONS = [
  { value: 'TOO_EXPENSIVE', label: 'Too expensive' },
  { value: 'MISSING_FEATURES', label: 'Missing features I need' },
  { value: 'SWITCHING_COMPETITOR', label: 'Switching to a competitor' },
  { value: 'NOT_USING_ENOUGH', label: 'Not using it enough' },
  { value: 'TECHNICAL_ISSUES', label: 'Too many technical issues' },
  { value: 'BUSINESS_CLOSED', label: 'Business closed or paused' },
  { value: 'OTHER', label: 'Other reason' },
];

export default function Subscription() {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [upgrading, setUpgrading] = useState(false);
  const [plans, setPlans] = useState<any[]>([]);
  const [status, setStatus] = useState<any>(null);
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [selectedPlan, setSelectedPlan] = useState<any>(null);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [checkoutUrlMissing, setCheckoutUrlMissing] = useState(false);
  const [upgradeFeature] = useState({ name: '', desc: '', requiredPlan: '' });

  // Cancellation state
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelFeedback, setCancelFeedback] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [undoing, setUndoing] = useState(false);

  // Billing history
  const [invoices, setInvoices] = useState<any[]>([]);
  const [invoicesLoading, setInvoicesLoading] = useState(false);
  const [showBillingHistory, setShowBillingHistory] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [plansRes, statusRes] = await Promise.all([
        api.get('/subscriptions/plans'),
        api.get('/subscriptions/status'),
      ]);
      setPlans(plansRes.data);
      setStatus(statusRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchInvoices = async () => {
    if (invoicesLoading) return;
    setInvoicesLoading(true);
    try {
      const res = await api.get('/subscriptions/invoices');
      setInvoices(res.data?.data ?? []);
    } catch {
      showToast('Failed to load billing history.', 'error');
    } finally {
      setInvoicesLoading(false);
    }
  };

  const toggleBillingHistory = () => {
    const next = !showBillingHistory;
    setShowBillingHistory(next);
    if (next && invoices.length === 0) fetchInvoices();
  };

  const getCheckoutUrl = (plan: any) =>
    billingCycle === 'yearly' ? plan.whopCheckoutUrlYearly : plan.whopCheckoutUrlMonthly;

  const handleUpgrade = () => {
    if (!selectedPlan) return;
    const checkoutUrl = getCheckoutUrl(selectedPlan);
    if (!checkoutUrl) {
      showToast('Checkout is not configured for this plan yet. Please contact support.', 'error');
      return;
    }
    setUpgrading(true);
    window.location.href = checkoutUrl;
  };

  const openUpgrade = (plan: any) => {
    setSelectedPlan(plan);
    setCheckoutUrlMissing(!getCheckoutUrl(plan));
    setShowConfirmModal(true);
  };

  const getPrice = (plan: any) => {
    if (plan.code === 'trial') return 0;
    return billingCycle === 'yearly' ? plan.yearlyPrice : plan.monthlyPrice;
  };

  const getYearlySavings = (plan: any) => {
    if (plan.code === 'trial' || !plan.monthlyPrice || !plan.yearlyPrice) return 0;
    const monthlyTotal = plan.monthlyPrice * 12;
    const savings = monthlyTotal - plan.yearlyPrice;
    return Math.round((savings / monthlyTotal) * 100);
  };

  const isCurrentPlan = (planName: string) => status?.planName === planName;

  const formatStorage = (mb: number) => {
    if (mb >= 1024) return `${mb / 1024} GB`;
    return `${mb} MB`;
  };

  const getFeatureValue = (plan: any, featureKey: string) => {
    if (featureKey === 'analytics') return plan.code !== 'trial' && plan.code !== 'basic';
    if (featureKey === 'digitalSignatures') return plan.code !== 'trial';
    if (featureKey === 'qrContracts') return plan.code !== 'trial';
    if (featureKey === 'whiteLabel') return plan.whiteLabel;
    if (featureKey === 'prioritySupport') return plan.prioritySupport;
    if (featureKey === 'maxVehicles') return plan.maxVehicles;
    if (featureKey === 'maxEmployees') return plan.maxEmployees;
    if (featureKey === 'maxGpsDevices') return plan.maxGpsDevices;
    if (featureKey === 'maxReservations') return plan.maxReservations >= 9999 ? 'Unlimited' : plan.maxReservations;
    if (featureKey === 'storageLimitMb') return formatStorage(plan.storageLimitMb);
    return '-';
  };

  const handleCancelSubmit = async () => {
    if (!cancelReason) {
      showToast('Please select a cancellation reason.', 'error');
      return;
    }
    setCancelling(true);
    try {
      const res = await api.post('/subscriptions/cancel', {
        reason: cancelReason,
        feedback: cancelFeedback || null,
        confirm: true,
      });
      if (res.data?.success) {
        showToast('Cancellation scheduled. Your access continues until end of billing period.', 'success');
        setShowCancelModal(false);
        setCancelReason('');
        setCancelFeedback('');
        await fetchData();
      } else {
        showToast(res.data?.message || 'Failed to schedule cancellation.', 'error');
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Failed to schedule cancellation.';
      showToast(msg, 'error');
    } finally {
      setCancelling(false);
    }
  };

  const handleUndoCancel = async () => {
    setUndoing(true);
    try {
      const res = await api.post('/subscriptions/undo-cancel');
      if (res.data?.success) {
        showToast('Cancellation reversed. Your subscription will renew as scheduled.', 'success');
        await fetchData();
      } else {
        showToast(res.data?.message || 'Failed to undo cancellation.', 'error');
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Failed to undo cancellation.';
      showToast(msg, 'error');
    } finally {
      setUndoing(false);
    }
  };

  const cancelEffectiveDateStr = () => {
    if (!status?.cancelEffectiveAt) return null;
    return new Date(status.cancelEffectiveAt).toLocaleDateString(undefined, {
      year: 'numeric', month: 'long', day: 'numeric',
    });
  };

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  const isCancelScheduled = status?.cancelScheduled === true || status?.status === 'CANCEL_SCHEDULED';
  const isPaidActive = !status?.isTrial && status?.subscriptionActive && !isCancelScheduled;
  const canCancelSubscription = isPaidActive;

  return (
    <div className="space-y-8 animate-fade max-w-7xl mx-auto w-full">
      <GlassPageHeader title="Subscription & Billing" subtitle="Manage your plan, usage, and billing preferences" icon={Crown} />

      {status?.hasFreeAccess && (
        <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 flex items-center gap-3">
          <Crown size={20} className="text-amber-600 shrink-0" />
          <div>
            <p className="text-sm font-bold text-amber-800">Special access by Innovax Technologies</p>
            <p className="text-xs text-amber-700 mt-0.5">
              {status.freeAccessReason || 'Your agency has complimentary access to this plan.'}
              {status.freeAccessUntil && ` Active until ${new Date(status.freeAccessUntil).toLocaleDateString()}.`}
            </p>
          </div>
        </div>
      )}

      {/* Cancellation Scheduled Banner */}
      {isCancelScheduled && (
        <div className="p-4 rounded-2xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-start gap-3">
            <Clock size={20} className="text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-bold text-amber-800 dark:text-amber-300">Cancellation Scheduled</p>
              <p className="text-xs text-amber-700 dark:text-amber-400 mt-0.5">
                Your subscription will remain <strong>fully active</strong> until{' '}
                <strong>{cancelEffectiveDateStr() || 'end of billing period'}</strong>.
                After that date your account switches to read-only mode.
              </p>
            </div>
          </div>
          <button
            onClick={handleUndoCancel}
            disabled={undoing}
            className="flex items-center gap-2 px-4 py-2 bg-amber-100 dark:bg-amber-500/20 hover:bg-amber-200 dark:hover:bg-amber-500/30 text-amber-800 dark:text-amber-300 rounded-xl text-sm font-semibold transition-colors shrink-0 disabled:opacity-50"
          >
            {undoing ? <Loader2 size={14} className="animate-spin" /> : <RotateCcw size={14} />}
            Undo Cancellation
          </button>
        </div>
      )}

      {/* Current Plan Overview */}
      {status && (
        <div className="data-surface p-4 sm:p-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center gap-4">
              <div className={`w-14 h-14 rounded-2xl bg-[#0a0f2c] flex items-center justify-center text-white`}>
                <Crown size={26} />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold text-[#1e293b]">
                    {status.isTrial ? t('subscription.trialCard.title') : (status.planName || 'Trial')}
                  </h2>
                  {status.isTrial && (
                    <span className={`px-2.5 py-0.5 rounded-lg text-xs font-semibold border ${trialBadgeClass(status.trialDaysRemaining ?? status.remainingTrialDays ?? 0)}`}>
                      {t('subscription.trialCard.active')}
                    </span>
                  )}
                  {!status.isTrial && status.status === 'EXPIRED' && (
                    <span className="px-2.5 py-0.5 rounded-lg bg-rose-50 text-rose-700 text-xs font-semibold border border-rose-200">
                      {t('subscription.trialCard.expired')}
                    </span>
                  )}
                  {isCancelScheduled && (
                    <span className="px-2.5 py-0.5 rounded-lg bg-amber-50 text-amber-700 text-xs font-semibold border border-amber-200 flex items-center gap-1">
                      <Clock size={10} /> Cancelling
                    </span>
                  )}
                  {isCurrentPlan('Standard') && !isCancelScheduled && (
                    <span className="px-2.5 py-0.5 rounded-lg bg-brand-50 text-brand-700 text-xs font-semibold border border-brand-200">
                      Most Popular
                    </span>
                  )}
                </div>
                <p className="text-sm text-slate-500 mt-0.5">
                  {status.isTrial
                    ? trialCountdownText(
                        status.trialDaysRemaining ?? status.remainingTrialDays ?? 0,
                        status.trialEndsAt,
                        t,
                        (value) => value ? new Date(value).toLocaleDateString(i18n.resolvedLanguage || i18n.language) : 'N/A',
                      )
                    : status.status === 'EXPIRED'
                    ? t('subscription.trialCard.expired')
                    : isCancelScheduled
                    ? `Access until ${cancelEffectiveDateStr() || 'end of period'} · Cancellation scheduled`
                    : status.subscriptionActive
                    ? `Active · Renews on ${status.currentPeriodEnd ? new Date(status.currentPeriodEnd).toLocaleDateString() : 'N/A'}`
                    : 'Subscription expired'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-4">
              {status.isTrial && (
                <div className={`text-center px-4 py-2 rounded-xl border ${trialBadgeClass(status.trialDaysRemaining ?? status.remainingTrialDays ?? 0)}`}>
                  <p className="text-2xl font-bold">{status.trialDaysRemaining ?? status.remainingTrialDays ?? 0}</p>
                  <p className="text-xs font-medium">{t('subscription.trialCard.daysLabel')}</p>
                </div>
              )}
              <button
                onClick={() => {
                  const premium = plans.find(p => p.code === 'premium');
                  if (premium) openUpgrade(premium);
                }}
                className="bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2"
              >
                {status.isTrial ? 'Upgrade Plan' : 'Manage Plan'} <ArrowRight size={16} />
              </button>
            </div>
          </div>

          {/* Usage Bars */}
          <div className="mt-6 pt-6 border-t border-[#e8e6e1]/60 grid grid-cols-1 md:grid-cols-2 gap-5">
            <UsageBar label="Vehicles" current={status.vehicleCount} max={status.maxVehicles} icon={<Car size={16} />} />
            <UsageBar label="Employees" current={status.employeeCount} max={status.maxEmployees} icon={<Users size={16} />} />
            <UsageBar label="GPS Devices" current={status.gpsCount} max={status.maxGpsDevices} icon={<MapPin size={16} />} />
            <UsageBar label="Reservations" current={status.reservationCount} max={status.maxReservations} icon={<Calendar size={16} />} />
          </div>

          {/* Cancel subscription link (only for active paid plans) */}
          {canCancelSubscription && (
            <div className="mt-4 pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
              <button
                onClick={() => setShowCancelModal(true)}
                className="text-xs text-rose-500 hover:text-rose-600 underline underline-offset-2 transition-colors"
              >
                Cancel subscription
              </button>
            </div>
          )}
        </div>
      )}

      {/* Billing Toggle */}
      <div className="flex items-center justify-center gap-4">
        <div className="bg-white p-1.5 rounded-xl border border-[#e8e6e1]/80 shadow-soft inline-flex">
          <button
            onClick={() => setBillingCycle('monthly')}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${
              billingCycle === 'monthly'
                ? 'bg-[#0a0f2c] text-white'
                : 'text-slate-500 hover:text-[#1e293b]'
            }`}
          >
            Monthly
          </button>
          <button
            onClick={() => setBillingCycle('yearly')}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2 ${
              billingCycle === 'yearly'
                ? 'bg-[#0a0f2c] text-white'
                : 'text-slate-500 hover:text-[#1e293b]'
            }`}
          >
            Yearly
            <span className="px-1.5 py-0.5 bg-emerald-50 text-emerald-700 text-[10px] font-bold rounded-md border border-emerald-200">
              Save 17%
            </span>
          </button>
        </div>
      </div>

      {/* Pricing Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3 sm:gap-4">
        {plans.map((plan) => {
          const Icon = planIcons[plan.name] || CreditCard;
          const isCurrent = isCurrentPlan(plan.name);
          const price = getPrice(plan);
          const savings = getYearlySavings(plan);

          return (
            <div
              key={plan.id}
              className={`bg-white rounded-2xl p-5 border shadow-soft transition-all hover:shadow-md relative flex flex-col ${
                isCurrent ? 'border-brand-500 ring-1 ring-brand-500' : 'border-[#e8e6e1]/80'
              } ${plan.code === 'standard' ? 'xl:scale-105 xl:z-10' : ''}`}
            >
              {plan.code === 'standard' && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-1 bg-brand-500 text-white text-[10px] font-bold uppercase tracking-wider rounded-full">
                  Most Popular
                </div>
              )}
              {isCurrent && (
                <div className="absolute -top-3 end-4 px-3 py-1 bg-emerald-500 text-white text-[10px] font-bold uppercase tracking-wider rounded-full flex items-center gap-1">
                  <CheckCircle2 size={10} /> Current
                </div>
              )}

              <div className="mb-4">
                <div className="w-10 h-10 rounded-xl bg-[#0a0f2c]/5 flex items-center justify-center mb-3">
                  <Icon size={20} className="text-[#0a0f2c]" />
                </div>
                <h3 className="text-base font-bold text-[#1e293b]">{plan.name}</h3>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">{plan.description}</p>
              </div>

              <div className="mb-4">
                <div className="flex items-baseline gap-1">
                  <span className="text-3xl font-bold text-[#1e293b]">
                    {price === 0 ? 'Free' : `${price}`}
                  </span>
                  {price > 0 && (
                    <span className="text-sm text-slate-400">MAD/{billingCycle === 'yearly' ? 'year' : 'mo'}</span>
                  )}
                </div>
                {savings > 0 && billingCycle === 'yearly' && (
                  <p className="text-xs text-emerald-600 font-medium mt-1">Save {savings}% yearly</p>
                )}
              </div>

              <div className="space-y-2 mb-5 flex-1">
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">Vehicles</span>
                  <span className="font-medium text-[#1e293b]">{plan.maxVehicles}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">Employees</span>
                  <span className="font-medium text-[#1e293b]">{plan.maxEmployees}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">GPS</span>
                  <span className="font-medium text-[#1e293b]">{plan.maxGpsDevices}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">Storage</span>
                  <span className="font-medium text-[#1e293b]">{formatStorage(plan.storageLimitMb)}</span>
                </div>
              </div>

              <button
                onClick={() => isCurrent ? null : openUpgrade(plan)}
                disabled={isCurrent}
                className={`w-full py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                  isCurrent
                    ? 'bg-slate-100 text-slate-400 cursor-default'
                    : plan.code === 'standard'
                    ? 'bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90'
                    : 'bg-slate-100 text-[#1e293b] hover:bg-slate-200'
                }`}
              >
                {isCurrent ? 'Current Plan' : 'Upgrade'}
              </button>
            </div>
          );
        })}
      </div>

      {/* Feature Comparison Table */}
      <div className="bg-white rounded-2xl border border-[#e8e6e1]/80 shadow-soft overflow-hidden p-3 sm:p-5">
        <div className="px-6 py-4 border-b border-[#e8e6e1]/60">
          <h3 className="text-base font-bold text-[#1e293b]">Feature Comparison</h3>
        </div>
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60 bg-slate-50/50">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">Feature</th>
                {plans.map(plan => (
                  <th key={plan.id} className="text-center text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3 min-w-[100px]">
                    {plan.name}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {features.map((feature, idx) => (
                <tr key={feature.key} className={`border-b border-[#e8e6e1]/40 ${idx % 2 === 0 ? 'bg-slate-50/30' : ''}`}>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2 text-sm text-[#1e293b]">
                      <feature.icon size={14} className="text-slate-400" />
                      {feature.label}
                    </div>
                  </td>
                  {plans.map(plan => {
                    const value = getFeatureValue(plan, feature.key);
                    const isBool = typeof value === 'boolean';
                    return (
                      <td key={plan.id} className="text-center px-4 py-3">
                        {isBool ? (
                          value ? (
                            <Check size={16} className="text-emerald-500 mx-auto" />
                          ) : (
                            <X size={16} className="text-slate-300 mx-auto" />
                          )
                        ) : (
                          <span className="text-sm font-medium text-[#1e293b]">{value}</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Billing History */}
      <div className="bg-white rounded-2xl border border-[#e8e6e1]/80 shadow-soft overflow-hidden">
        <button
          onClick={toggleBillingHistory}
          className="w-full flex items-center justify-between px-6 py-4 hover:bg-slate-50/50 transition-colors"
        >
          <div className="flex items-center gap-2">
            <Receipt size={16} className="text-slate-400" />
            <h3 className="text-base font-bold text-[#1e293b]">Billing History</h3>
          </div>
          <ChevronDown
            size={16}
            className={`text-slate-400 transition-transform ${showBillingHistory ? 'rotate-180' : ''}`}
          />
        </button>

        {showBillingHistory && (
          <div className="border-t border-[#e8e6e1]/60">
            {invoicesLoading ? (
              <div className="flex items-center justify-center py-10">
                <Loader2 size={20} className="animate-spin text-slate-400" />
              </div>
            ) : invoices.length === 0 ? (
              <div className="text-center py-10 text-sm text-slate-400">
                No invoices found. Billing history will appear here once you have a paid subscription.
              </div>
            ) : (
              <div className="overflow-x-auto no-scrollbar">
                <table className="w-full">
                  <thead>
                    <tr className="bg-slate-50/50 border-b border-[#e8e6e1]/60">
                      <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-3">Invoice</th>
                      <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Plan</th>
                      <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Billing</th>
                      <th className="text-right text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Total</th>
                      <th className="text-center text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Status</th>
                      <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-4 py-3">Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoices.map((inv, idx) => (
                      <tr key={inv.id} className={`border-b border-[#e8e6e1]/40 ${idx % 2 === 0 ? 'bg-slate-50/20' : ''}`}>
                        <td className="px-5 py-3 text-sm font-mono text-[#1e293b]">{inv.invoiceNumber}</td>
                        <td className="px-4 py-3 text-sm text-[#1e293b]">{inv.planName}</td>
                        <td className="px-4 py-3 text-sm text-slate-500 capitalize">{inv.billingCycle?.toLowerCase()}</td>
                        <td className="px-4 py-3 text-sm font-semibold text-[#1e293b] text-right">
                          {inv.total} {inv.currency}
                        </td>
                        <td className="px-4 py-3 text-center">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                            inv.status === 'PAID'
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : inv.status === 'PENDING'
                              ? 'bg-amber-50 text-amber-700 border border-amber-200'
                              : 'bg-rose-50 text-rose-600 border border-rose-200'
                          }`}>
                            {inv.status}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-slate-500">
                          {inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Cancel Subscription Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowCancelModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-rose-50 flex items-center justify-center">
                <AlertTriangle size={18} className="text-rose-500" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-[#1e293b]">Cancel Subscription</h3>
                <p className="text-xs text-slate-500">Your data is safe — access continues until end of period</p>
              </div>
            </div>

            <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-5 text-xs text-amber-800 space-y-1">
              <p className="font-semibold flex items-center gap-1.5"><Clock size={12} /> What happens when you cancel:</p>
              <ul className="space-y-0.5 ms-4 list-disc">
                <li>Your subscription remains <strong>fully active</strong> until the end of the current billing period</li>
                <li>All your data is preserved and accessible</li>
                <li>You can undo this cancellation anytime before the period ends</li>
                <li>After the period ends, your account switches to read-only mode</li>
              </ul>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-[#1e293b] mb-1.5">
                Why are you cancelling? <span className="text-rose-500">*</span>
              </label>
              <select
                value={cancelReason}
                onChange={e => setCancelReason(e.target.value)}
                className="w-full border border-[#e8e6e1] rounded-xl px-3 py-2.5 text-sm text-[#1e293b] focus:outline-none focus:ring-2 focus:ring-brand-500/30 focus:border-brand-500 bg-white"
              >
                <option value="">Select a reason...</option>
                {CANCEL_REASONS.map(r => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div className="mb-6">
              <label className="block text-sm font-medium text-[#1e293b] mb-1.5">
                Anything else you'd like to share? <span className="text-slate-400 font-normal">(optional)</span>
              </label>
              <textarea
                value={cancelFeedback}
                onChange={e => setCancelFeedback(e.target.value)}
                rows={3}
                maxLength={1000}
                placeholder="Your feedback helps us improve..."
                className="w-full border border-[#e8e6e1] rounded-xl px-3 py-2.5 text-sm text-[#1e293b] focus:outline-none focus:ring-2 focus:ring-brand-500/30 focus:border-brand-500 resize-none"
              />
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setShowCancelModal(false)}
                className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                Keep Subscription
              </button>
              <button
                onClick={handleCancelSubmit}
                disabled={cancelling || !cancelReason}
                className="flex-1 bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center justify-center gap-2"
              >
                {cancelling ? <Loader2 size={14} className="animate-spin" /> : null}
                {cancelling ? 'Scheduling...' : 'Schedule Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Upgrade Modal */}
      {showConfirmModal && selectedPlan && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowConfirmModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">Confirm Subscription</h3>
            <p className="text-sm text-slate-500 mb-4">
              You are subscribing to <span className="font-semibold text-[#1e293b]">{selectedPlan.name}</span> with{' '}
              <span className="font-semibold text-[#1e293b]">{billingCycle}</span> billing. You'll be redirected to our secure checkout to complete payment.
            </p>
            <div className="bg-slate-50 rounded-xl p-4 mb-5">
              <div className="flex justify-between items-center mb-2">
                <span className="text-sm text-slate-500">Plan</span>
                <span className="text-sm font-semibold text-[#1e293b]">{selectedPlan.name}</span>
              </div>
              <div className="flex justify-between items-center mb-2">
                <span className="text-sm text-slate-500">Billing</span>
                <span className="text-sm font-semibold text-[#1e293b]">{billingCycle}</span>
              </div>
              <div className="border-t border-[#e8e6e1]/60 pt-2 flex justify-between items-center">
                <span className="text-sm font-medium text-[#1e293b]">Total</span>
                <span className="text-lg font-bold text-[#1e293b]">
                  {getPrice(selectedPlan)} MAD
                </span>
              </div>
            </div>
            {checkoutUrlMissing && (
              <p className="text-sm text-rose-600 mb-4">Checkout is not configured for this plan yet. Please contact support.</p>
            )}
            <div className="flex gap-3">
              <button
                onClick={handleUpgrade}
                disabled={upgrading || checkoutUrlMissing}
                className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {upgrading ? 'Redirecting...' : 'Continue to Checkout'}
              </button>
              <button
                onClick={() => setShowConfirmModal(false)}
                className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feature Lock Upgrade Modal */}
      <UpgradeModal
        isOpen={showUpgradeModal}
        onClose={() => setShowUpgradeModal(false)}
        featureName={upgradeFeature.name}
        featureDescription={upgradeFeature.desc}
        requiredPlan={upgradeFeature.requiredPlan}
        currentPlan={status?.planName}
        onUpgrade={() => {
          setShowUpgradeModal(false);
          const plan = plans.find(p => p.name === upgradeFeature.requiredPlan);
          if (plan) openUpgrade(plan);
        }}
      />
    </div>
  );
}
