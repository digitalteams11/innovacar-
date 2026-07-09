import { useEffect, useState } from 'react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import UsageBar from '../UsageBar';
import {
  CreditCard, Crown, Car, Users, MapPin, Calendar, Loader2, ArrowRight,
  Receipt, ShieldAlert, Download, ChevronDown,
} from 'lucide-react';

interface PlanData {
  id: number;
  code: string;
  name: string;
  description: string;
  monthlyPrice: number;
  yearlyPrice: number;
  maxVehicles: number;
  maxEmployees: number;
  maxGpsDevices: number;
  storageLimitMb: number;
  whopCheckoutUrlMonthly?: string;
  whopCheckoutUrlYearly?: string;
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
  const { showToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<any>(null);
  const [plans, setPlans] = useState<PlanData[]>([]);
  const [invoices, setInvoices] = useState<InvoiceData[]>([]);
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [selectedPlan, setSelectedPlan] = useState<PlanData | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [showManage, setShowManage] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelConfirmText, setCancelConfirmText] = useState('');
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [statusRes, plansRes, invoicesRes] = await Promise.all([
        api.get('/subscriptions/status'),
        api.get('/subscriptions/plans'),
        api.get('/subscriptions/invoices'),
      ]);
      setStatus(statusRes.data?.data ? { ...statusRes.data, ...statusRes.data.data } : statusRes.data);
      setPlans(plansRes.data || []);
      setInvoices(invoicesRes.data?.data || []);
    } catch (err) {
      console.error('Failed to load billing data', err);
      showToast('Unable to load billing information. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const getCheckoutUrl = (plan: PlanData) =>
    billingCycle === 'yearly' ? plan.whopCheckoutUrlYearly : plan.whopCheckoutUrlMonthly;

  const openUpgrade = (plan: PlanData) => {
    setSelectedPlan(plan);
    setShowUpgradeModal(true);
  };

  const handleCheckout = () => {
    if (!selectedPlan) return;
    const checkoutUrl = getCheckoutUrl(selectedPlan);
    if (!checkoutUrl) {
      showToast('Payment provider not configured for this plan. Please contact support.', 'error');
      return;
    }
    window.location.href = checkoutUrl;
  };

  const handleCancelSubscription = async () => {
    if (cancelConfirmText.trim().toUpperCase() !== 'CANCEL') {
      showToast('Type CANCEL to confirm.', 'warning');
      return;
    }
    setCancelling(true);
    try {
      const { data } = await api.post('/subscriptions/cancel');
      showToast(data?.message || 'Subscription cancelled.', 'success');
      setShowCancelModal(false);
      setCancelConfirmText('');
      fetchData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to cancel subscription. Please try again later.', 'error');
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

  return (
    <div className="space-y-6">
      {/* Current Plan */}
      <div className="card-premium p-4 sm:p-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-5">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-[#0a0f2c] flex items-center justify-center text-white">
              <Crown size={24} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-[#1e293b]">{status?.planName || 'Trial'}</h3>
                <span className="px-2.5 py-0.5 rounded-lg bg-slate-50 text-slate-600 text-xs font-semibold border border-slate-200">
                  {status?.status || 'TRIAL'}
                </span>
              </div>
              <p className="text-sm text-slate-500 mt-0.5">
                {status?.isTrial
                  ? `Trial ends on ${status?.trialEndsAt ? new Date(status.trialEndsAt).toLocaleDateString() : 'N/A'} (${status?.remainingTrialDays ?? 0} days left)`
                  : status?.subscriptionActive
                    ? `Renews on ${status?.currentPeriodEnd ? new Date(status.currentPeriodEnd).toLocaleDateString() : 'N/A'}`
                    : 'Subscription is not active'}
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
            {status?.isTrial ? 'Upgrade Plan' : 'Change Plan'} <ArrowRight size={16} />
          </button>
        </div>

        {/* Usage */}
        <div className="mt-6 pt-6 border-t border-[#e8e6e1]/60 grid grid-cols-1 md:grid-cols-2 gap-5">
          <UsageBar label="Vehicles" current={status?.vehicleCount ?? 0} max={status?.maxVehicles ?? 0} icon={<Car size={16} />} />
          <UsageBar label="Employees" current={status?.employeeCount ?? 0} max={status?.maxEmployees ?? 0} icon={<Users size={16} />} />
          <UsageBar label="GPS Devices" current={status?.gpsCount ?? 0} max={status?.maxGpsDevices ?? 0} icon={<MapPin size={16} />} />
          <UsageBar label="Reservations" current={status?.reservationCount ?? 0} max={status?.maxReservations ?? 0} icon={<Calendar size={16} />} />
        </div>
      </div>

      {/* Billing History */}
      <div className="card-premium p-4 sm:p-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Receipt size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Billing History</h3>
            <p className="text-sm text-slate-400 font-normal">Invoices issued for your subscription</p>
          </div>
        </div>

        {invoices.length === 0 ? (
          <p className="text-sm text-slate-400 text-center py-6">No invoices yet.</p>
        ) : (
          <div className="divide-y divide-[#e8e6e1]/40">
            {invoices.map((invoice) => (
              <div key={invoice.id} className="flex items-center justify-between py-3">
                <div>
                  <p className="text-sm font-semibold text-[#1e293b]">{invoice.invoiceNumber}</p>
                  <p className="text-xs text-slate-400">{invoice.planName} · {invoice.billingCycle} · {invoice.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString() : '—'}</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold text-[#1e293b]">{invoice.total} {invoice.currency}</span>
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-lg ${invoice.status === 'PAID' ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
                    {invoice.status}
                  </span>
                  <button className="text-slate-400 hover:text-brand-500" title="Download not yet available">
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
            <h3 className="text-base font-bold text-[#1e293b]">Payment Method</h3>
            <p className="text-sm text-slate-400 font-normal">Subscription payments are processed externally</p>
          </div>
        </div>
        <p className="text-sm text-slate-500">
          Payment and checkout are managed securely by our payment provider. Update or remove your payment method from the checkout portal you used to subscribe.
        </p>
      </div>

      {/* Manage Subscription */}
      <div className="card-premium p-4 sm:p-6">
        <button onClick={() => setShowManage((v) => !v)} className="w-full flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-slate-50 flex items-center justify-center">
              <ShieldAlert size={20} className="text-slate-500" />
            </div>
            <div className="text-left">
              <h3 className="text-base font-bold text-[#1e293b]">Manage Subscription</h3>
              <p className="text-sm text-slate-400 font-normal">Plan changes, billing cycle, and cancellation</p>
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
                Monthly
              </button>
              <button
                onClick={() => setBillingCycle('yearly')}
                className={`px-4 py-2 rounded-lg text-sm font-medium ${billingCycle === 'yearly' ? 'bg-[#0a0f2c] text-white' : 'bg-slate-50 text-slate-500'}`}
              >
                Yearly
              </button>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {plans.map((plan) => {
                const isCurrent = status?.planName === plan.name;
                const price = billingCycle === 'yearly' ? plan.yearlyPrice : plan.monthlyPrice;
                return (
                  <div key={plan.id} className={`p-4 rounded-xl border ${isCurrent ? 'border-brand-500 bg-brand-50/40' : 'border-[#e8e6e1]'}`}>
                    <p className="text-sm font-bold text-[#1e293b]">{plan.name}</p>
                    <p className="text-lg font-bold text-[#1e293b] mt-1">{price ? `${price} MAD` : 'Free'}</p>
                    <button
                      onClick={() => !isCurrent && openUpgrade(plan)}
                      disabled={isCurrent}
                      className={`mt-3 w-full py-2 rounded-lg text-xs font-semibold ${isCurrent ? 'bg-slate-100 text-slate-400' : 'bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90'}`}
                    >
                      {isCurrent ? 'Current Plan' : 'Select'}
                    </button>
                  </div>
                );
              })}
            </div>

            <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
              <button onClick={() => setShowCancelModal(true)} className="text-xs font-medium text-rose-500 hover:text-rose-600">
                Cancel subscription
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Upgrade/checkout confirm modal */}
      {showUpgradeModal && selectedPlan && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowUpgradeModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">Confirm Plan Change</h3>
            <p className="text-sm text-slate-500 mb-4">
              You are subscribing to <span className="font-semibold text-[#1e293b]">{selectedPlan.name}</span> with{' '}
              <span className="font-semibold text-[#1e293b]">{billingCycle}</span> billing. You will be redirected to our secure checkout to complete payment.
            </p>
            {!getCheckoutUrl(selectedPlan) && (
              <p className="text-sm text-rose-600 mb-4">Payment provider not configured for this plan yet. Please contact support.</p>
            )}
            <div className="flex gap-3">
              <button
                onClick={handleCheckout}
                disabled={!getCheckoutUrl(selectedPlan)}
                className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                Continue to Checkout
              </button>
              <button onClick={() => setShowUpgradeModal(false)} className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cancel confirmation modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowCancelModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">Cancel Subscription</h3>
            <p className="text-sm text-slate-500 mb-4">
              Cancelling will deactivate your subscription immediately. You will lose access to paid features until you resubscribe. This does not delete your data.
            </p>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">Type CANCEL to confirm</label>
            <input
              type="text"
              value={cancelConfirmText}
              onChange={(e) => setCancelConfirmText(e.target.value)}
              placeholder="CANCEL"
              className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm mb-4 focus:outline-none focus:ring-2 ring-rose-100"
            />
            <div className="flex gap-3">
              <button
                onClick={handleCancelSubscription}
                disabled={cancelling}
                className="flex-1 bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {cancelling ? 'Cancelling...' : 'Confirm Cancellation'}
              </button>
              <button onClick={() => { setShowCancelModal(false); setCancelConfirmText(''); }} className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors">
                Keep Subscription
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
