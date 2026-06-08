import { useEffect, useState } from 'react';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import UsageBar from '../components/UsageBar';
import UpgradeModal from '../components/UpgradeModal';
import {
  CreditCard, Check, Zap, Shield, Crown, Rocket, ArrowRight,
  Car, Users, MapPin, Calendar, HardDrive, BarChart3, FileSignature,
  QrCode, Globe, Headphones, CheckCircle2, Loader2, X
} from 'lucide-react';

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

export default function Subscription() {
  const { showToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [upgrading, setUpgrading] = useState(false);
  const [plans, setPlans] = useState<any[]>([]);
  const [status, setStatus] = useState<any>(null);
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [selectedPlan, setSelectedPlan] = useState<any>(null);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [upgradeFeature] = useState({ name: '', desc: '', requiredPlan: '' });

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

  const handleUpgrade = async () => {
    if (!selectedPlan) return;
    setUpgrading(true);
    try {
      await api.post('/subscriptions/upgrade', {
        planCode: selectedPlan.code,
        billingCycle,
      });
      showToast(`Upgraded to ${selectedPlan.name} successfully!`);
      setShowConfirmModal(false);
      setSelectedPlan(null);
      fetchData();
    } catch (err: any) {
      showToast((err as any).userMessage || 'Upgrade failed');
    } finally {
      setUpgrading(false);
    }
  };

  const openUpgrade = (plan: any) => {
    setSelectedPlan(plan);
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

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-fade max-w-7xl mx-auto w-full p-3 sm:p-4 lg:p-6">
      {/* Header */}
      <div>
        <h1 className="text-xl sm:text-2xl font-bold text-[#1e293b] tracking-tight">Subscription & Billing</h1>
        <p className="text-slate-500 text-xs sm:text-sm mt-1">Manage your plan, usage, and billing preferences</p>
      </div>

      {/* Current Plan Overview */}
      {status && (
        <div className="bg-white rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 shadow-soft">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-6">
            <div className="flex flex-col sm:flex-row sm:items-center gap-4">
              <div className={`w-14 h-14 rounded-2xl bg-[#0a0f2c] flex items-center justify-center text-white`}>
                <Crown size={26} />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold text-[#1e293b]">{status.planName || 'Trial'}</h2>
                  {status.inTrial && (
                    <span className="px-2.5 py-0.5 rounded-lg bg-blue-50 text-blue-700 text-xs font-semibold border border-blue-200">
                      Trial
                    </span>
                  )}
                  {isCurrentPlan('Standard') && (
                    <span className="px-2.5 py-0.5 rounded-lg bg-brand-50 text-brand-700 text-xs font-semibold border border-brand-200">
                      Most Popular
                    </span>
                  )}
                </div>
                <p className="text-sm text-slate-500 mt-0.5">
                  {status.inTrial
                    ? `Trial ends on ${status.trialEndDate ? new Date(status.trialEndDate).toLocaleDateString() : 'N/A'}`
                    : status.subscriptionActive
                    ? `Active until ${status.subscriptionEndDate ? new Date(status.subscriptionEndDate).toLocaleDateString() : 'N/A'}`
                    : 'Subscription expired'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-4">
              {status.inTrial && (
                <div className="text-center px-4 py-2 bg-blue-50 rounded-xl border border-blue-200">
                  <p className="text-2xl font-bold text-blue-700">{status.daysRemaining}</p>
                  <p className="text-xs text-blue-500 font-medium">days left</p>
                </div>
              )}
              <button
                onClick={() => {
                  const premium = plans.find(p => p.code === 'premium');
                  if (premium) openUpgrade(premium);
                }}
                className="bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl text-sm font-semibold transition-colors flex items-center gap-2"
              >
                Upgrade Plan <ArrowRight size={16} />
              </button>
            </div>
          </div>

          {/* Usage Bars */}
          <div className="mt-6 pt-6 border-t border-[#e8e6e1]/60 grid grid-cols-1 md:grid-cols-2 gap-5">
            <UsageBar
              label="Vehicles"
              current={status.vehicleCount}
              max={status.maxVehicles}
              icon={<Car size={16} />}
            />
            <UsageBar
              label="Employees"
              current={status.employeeCount}
              max={status.maxEmployees}
              icon={<Users size={16} />}
            />
            <UsageBar
              label="GPS Devices"
              current={status.gpsCount}
              max={status.maxGpsDevices}
              icon={<MapPin size={16} />}
            />
            <UsageBar
              label="Reservations"
              current={status.reservationCount}
              max={status.maxReservations}
              icon={<Calendar size={16} />}
            />
          </div>
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
                <div className="absolute -top-3 right-4 px-3 py-1 bg-emerald-500 text-white text-[10px] font-bold uppercase tracking-wider rounded-full flex items-center gap-1">
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

      {/* Confirm Upgrade Modal */}
      {showConfirmModal && selectedPlan && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowConfirmModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md p-6">
            <h3 className="text-lg font-bold text-[#1e293b] mb-2">Confirm Upgrade</h3>
            <p className="text-sm text-slate-500 mb-4">
              You are upgrading to <span className="font-semibold text-[#1e293b]">{selectedPlan.name}</span> with{' '}
              <span className="font-semibold text-[#1e293b]">{billingCycle}</span> billing.
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
            <div className="flex gap-3">
              <button
                onClick={handleUpgrade}
                disabled={upgrading}
                className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
              >
                {upgrading ? 'Processing...' : 'Confirm Upgrade'}
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
