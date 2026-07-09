import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import {
  CreditCard, Plus, Edit2, Trash2, Crown,
  Zap, Shield, Rocket, Tag, Users
} from 'lucide-react';
import { PageHeader, Modal, FormField, TextInput, ToggleSwitch, Badge } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

const planIcons: Record<string, any> = {
  Trial: Zap,
  Basic: CreditCard,
  Standard: Shield,
  Premium: Crown,
  Enterprise: Rocket,
};

export default function SuperAdminSubscriptions() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [plans, setPlans] = useState<any[]>([]);
  const [agencies, setAgencies] = useState<any[]>([]);
  const [promoCodes, setPromoCodes] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [showPromoModal, setShowPromoModal] = useState(false);
  const [editingPlan, setEditingPlan] = useState<any>(null);
  const [planForm, setPlanForm] = useState<any>({});
  const [promoForm, setPromoForm] = useState<any>({
    code: '', promotionName: '', promotionType: 'DISCOUNT', discountType: 'PERCENTAGE',
    discountValue: 0, maxUses: 100, validFrom: '', validTo: '', applicablePlans: 'basic,standard,premium',
    freeMonths: 0, freeFeatureCode: '', trialPlanCode: '', isActive: true,
  });

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [plansRes, agenciesRes, promoRes] = await Promise.all([
        superAdminApi.getPlans(),
        superAdminApi.getAgencies(),
        superAdminApi.getPromoCodes().catch(() => ({ data: [] })),
      ]);
      setPlans(plansRes.data);
      setAgencies(agenciesRes.data);
      setPromoCodes(promoRes.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSavePlan = async () => {
    try {
      if (editingPlan) {
        await superAdminApi.updatePlan(editingPlan.id, planForm);
      } else {
        await superAdminApi.createPlan(planForm);
      }
      setShowPlanModal(false);
      setEditingPlan(null);
      setPlanForm({});
      fetchData();
      showToast('Plan saved successfully', 'success');
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeletePlan = async (id: number) => {
    if (!window.confirm(t('superAdmin.subscriptions.confirmDelete'))) return;
    try {
      await superAdminApi.deletePlan(id);
      fetchData();
      showToast('Plan deleted successfully', 'success');
    } catch (err) {
      console.error(err);
    }
  };

  const handleSavePromo = async () => {
    try {
      await superAdminApi.createPromoCode(promoForm);
      setShowPromoModal(false);
      setPromoForm({
        code: '', promotionName: '', promotionType: 'DISCOUNT', discountType: 'PERCENTAGE',
        discountValue: 0, maxUses: 100, validFrom: '', validTo: '', applicablePlans: 'basic,standard,premium',
        freeMonths: 0, freeFeatureCode: '', trialPlanCode: '', isActive: true,
      });
      fetchData();
      showToast('Promo code created successfully', 'success');
    } catch (err) {
      console.error(err);
    }
  };

  const openEdit = (plan: any) => {
    setEditingPlan(plan);
    setPlanForm({ ...plan });
    setShowPlanModal(true);
  };

  const openCreate = () => {
    setEditingPlan(null);
    setPlanForm({
      name: '', code: '', monthlyPrice: 0, yearlyPrice: 0,
      description: '', maxVehicles: 0, maxEmployees: 0, maxGpsDevices: 0,
      maxReservations: 0, storageLimitMb: 0, apiAccess: false,
      whiteLabel: false, prioritySupport: false, isActive: true,
    });
    setShowPlanModal(true);
  };

  const subscribeAgency = async (agencyId: number, planCode: string) => {
    try {
      await superAdminApi.subscribeAgency(agencyId, planCode);
      fetchData();
      showToast('Agency subscribed successfully', 'success');
    } catch (err) {
      console.error(err);
    }
  };

  const planCounts = (planName: string) => agencies.filter(a => a.planName === planName).length;

  return (
    <div className="space-y-8 animate-fade">
      <PageHeader title={t('superAdmin.subscriptions.title')} subtitle={t('superAdmin.subscriptions.subtitle')}>
        <button onClick={openCreate} className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft">
          <Plus size={16} />
          <span className="hidden sm:inline">{t('superAdmin.subscriptions.newPlan')}</span>
        </button>
      </PageHeader>

      {/* Plans Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3 sm:gap-4">
        {loading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft animate-pulse h-64" />
          ))
        ) : plans.map((plan) => {
          const Icon = planIcons[plan.name] || CreditCard;
          const count = planCounts(plan.name);
          return (
            <div key={plan.id} className={`bg-white dark:bg-[#1a2332]/70 rounded-2xl p-5 border shadow-soft transition-all hover:shadow-md ${plan.isActive ? 'border-[#e8e6e1]/80 dark:border-white/5' : 'border-slate-200 dark:border-white/5 opacity-60'}`}>
              <div className="flex items-start justify-between mb-4">
                <div className="w-10 h-10 rounded-xl bg-[#0a0f2c]/5 dark:bg-white/5 flex items-center justify-center">
                  <Icon size={18} className="text-[#0a0f2c] dark:text-white/70" />
                </div>
                <div className="flex gap-1">
                  <button onClick={() => openEdit(plan)} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors">
                    <Edit2 size={14} />
                  </button>
                  <button onClick={() => handleDeletePlan(plan.id)} className="p-1.5 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg text-slate-400 hover:text-rose-600 transition-colors">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-1">{plan.name}</h3>
              <p className="text-2xl font-bold text-[#1e293b] dark:text-white mb-1">{plan.monthlyPrice} <span className="text-sm font-normal text-slate-500">MAD/{t('superAdmin.subscriptions.month')}</span></p>
              <p className="text-xs text-slate-500 mb-4">{plan.yearlyPrice} MAD/year</p>
              <div className="space-y-2 text-xs text-slate-600 dark:text-slate-400 mb-4">
                <div className="flex justify-between"><span>{t('superAdmin.subscriptions.vehicles')}</span><span className="font-medium">{plan.maxVehicles}</span></div>
                <div className="flex justify-between"><span>{t('superAdmin.subscriptions.employees')}</span><span className="font-medium">{plan.maxEmployees}</span></div>
                <div className="flex justify-between"><span>{t('superAdmin.subscriptions.gpsDevices')}</span><span className="font-medium">{plan.maxGpsDevices}</span></div>
              </div>
              <div className="pt-3 border-t border-[#e8e6e1]/40 dark:border-white/5">
                <div className="flex items-center gap-1.5 text-xs text-slate-500">
                  <Users size={12} />
                  <span>{count} {t('superAdmin.subscriptions.agenciesOnPlan')}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Promo Codes Section */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-[#1e293b] dark:text-white">Promo Codes</h2>
          <button onClick={() => setShowPromoModal(true)} className="flex items-center gap-2 bg-white dark:bg-[#1a2332]/70 border border-[#e8e6e1]/80 dark:border-white/5 hover:bg-slate-50 dark:hover:bg-white/5 text-[#1e293b] dark:text-white px-4 py-2 rounded-xl text-sm font-medium transition-colors shadow-soft">
            <Tag size={16} />
            Add Promo Code
          </button>
        </div>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Code</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Discount</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Uses</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Valid Until</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Status</th>
                </tr>
              </thead>
              <tbody>
                {promoCodes.length === 0 ? (
                  <tr><td colSpan={5} className="text-center py-12 text-slate-400">No promo codes found</td></tr>
                ) : promoCodes.map((promo: any) => (
                  <tr key={promo.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                    <td className="px-5 py-4 text-sm font-medium text-[#1e293b] dark:text-white">{promo.code}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.discountValue}{promo.discountType === 'PERCENTAGE' ? '%' : ' MAD'}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.usedCount} / {promo.maxUses}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.validTo ? new Date(promo.validTo).toLocaleDateString() : '-'}</td>
                    <td className="px-5 py-4"><Badge variant={promo.isActive ? 'success' : 'default'}>{promo.isActive ? 'Active' : 'Inactive'}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Agency Assignments */}
      <div>
        <h2 className="text-lg font-bold text-[#1e293b] dark:text-white mb-4">{t('superAdmin.subscriptions.agencyAssignments')}</h2>
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full">
              <thead>
                <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.subscriptions.agency')}</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.subscriptions.currentPlan')}</th>
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">{t('superAdmin.subscriptions.changePlan')}</th>
                </tr>
              </thead>
              <tbody>
                {agencies.slice(0, 10).map((agency) => (
                  <tr key={agency.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                    <td className="px-5 py-4 text-sm font-medium text-[#1e293b] dark:text-white">{agency.name}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{agency.planName || '-'}</td>
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap items-center gap-2">
                        {plans.filter(p => p.isActive).map((plan) => (
                          <button
                            key={plan.id}
                            onClick={() => subscribeAgency(agency.id, plan.code)}
                            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
                              agency.planName === plan.name
                                ? 'bg-[#0a0f2c] text-white'
                                : 'bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10'
                            }`}
                            disabled={agency.planName === plan.name}
                          >
                            {plan.name}
                          </button>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Plan Modal */}
      <Modal isOpen={showPlanModal} onClose={() => setShowPlanModal(false)} title={editingPlan ? t('superAdmin.subscriptions.editPlan') : t('superAdmin.subscriptions.newPlan')} size="lg"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSavePlan} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">{t('superAdmin.common.save')}</button>
            <button onClick={() => setShowPlanModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">{t('superAdmin.common.cancel')}</button>
          </div>
        }
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <FormField label={t('superAdmin.subscriptions.planName')} required><TextInput value={planForm.name || ''} onChange={(v) => setPlanForm({ ...planForm, name: v })} /></FormField>
          <FormField label={t('superAdmin.subscriptions.planCode')} required><TextInput value={planForm.code || ''} onChange={(v) => setPlanForm({ ...planForm, code: v })} /></FormField>
          <FormField label={t('superAdmin.subscriptions.monthlyPrice')} required><TextInput value={planForm.monthlyPrice || ''} onChange={(v) => setPlanForm({ ...planForm, monthlyPrice: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.yearlyPrice')} required><TextInput value={planForm.yearlyPrice || ''} onChange={(v) => setPlanForm({ ...planForm, yearlyPrice: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.maxVehicles')}><TextInput value={planForm.maxVehicles || ''} onChange={(v) => setPlanForm({ ...planForm, maxVehicles: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.maxEmployees')}><TextInput value={planForm.maxEmployees || ''} onChange={(v) => setPlanForm({ ...planForm, maxEmployees: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.maxGps')}><TextInput value={planForm.maxGpsDevices || ''} onChange={(v) => setPlanForm({ ...planForm, maxGpsDevices: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.maxReservations')}><TextInput value={planForm.maxReservations || ''} onChange={(v) => setPlanForm({ ...planForm, maxReservations: Number(v) })} type="number" /></FormField>
          <FormField label={t('superAdmin.subscriptions.storageLimit')}><TextInput value={planForm.storageLimitMb || ''} onChange={(v) => setPlanForm({ ...planForm, storageLimitMb: Number(v) })} type="number" /></FormField>
          <FormField label="Whop Checkout URL (Monthly)"><TextInput value={planForm.whopCheckoutUrlMonthly || ''} onChange={(v) => setPlanForm({ ...planForm, whopCheckoutUrlMonthly: v })} placeholder="https://whop.com/checkout/..." /></FormField>
          <FormField label="Whop Checkout URL (Yearly)"><TextInput value={planForm.whopCheckoutUrlYearly || ''} onChange={(v) => setPlanForm({ ...planForm, whopCheckoutUrlYearly: v })} placeholder="https://whop.com/checkout/..." /></FormField>
          <div className="space-y-3 pt-2">
            <ToggleSwitch checked={planForm.apiAccess || false} onChange={(v) => setPlanForm({ ...planForm, apiAccess: v })} label={t('superAdmin.subscriptions.apiAccess')} />
            <ToggleSwitch checked={planForm.whiteLabel || false} onChange={(v) => setPlanForm({ ...planForm, whiteLabel: v })} label={t('superAdmin.subscriptions.whiteLabel')} />
            <ToggleSwitch checked={planForm.prioritySupport || false} onChange={(v) => setPlanForm({ ...planForm, prioritySupport: v })} label={t('superAdmin.subscriptions.prioritySupport')} />
            <ToggleSwitch checked={planForm.isActive !== false} onChange={(v) => setPlanForm({ ...planForm, isActive: v })} label={t('superAdmin.subscriptions.isActive')} />
          </div>
        </div>
      </Modal>

      {/* Promo Modal */}
      <Modal isOpen={showPromoModal} onClose={() => setShowPromoModal(false)} title="Create Promo Code" size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSavePromo} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Create</button>
            <button onClick={() => setShowPromoModal(false)} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label="Promotion Name"><TextInput value={promoForm.promotionName || ''} onChange={(v) => setPromoForm({ ...promoForm, promotionName: v })} placeholder="2 Months Free" /></FormField>
          <FormField label="Code" required><TextInput value={promoForm.code} onChange={(v) => setPromoForm({ ...promoForm, code: v.toUpperCase() })} placeholder="SUMMER2024" /></FormField>
          <FormField label="Promotion Type">
            <select value={promoForm.promotionType} onChange={(e) => setPromoForm({ ...promoForm, promotionType: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none">
              <option value="DISCOUNT">Discount</option>
              <option value="FREE_MONTHS">Free Months</option>
              <option value="FREE_FEATURE">Free Feature Module</option>
              <option value="PLAN_TRIAL">Temporary Plan Access</option>
            </select>
          </FormField>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Discount Type">
              <select value={promoForm.discountType} onChange={(e) => setPromoForm({ ...promoForm, discountType: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none">
                <option value="PERCENTAGE">Percentage</option>
                <option value="FIXED">Fixed Amount</option>
              </select>
            </FormField>
            <FormField label="Discount Value" required><TextInput value={promoForm.discountValue} onChange={(v) => setPromoForm({ ...promoForm, discountValue: Number(v) })} type="number" /></FormField>
          </div>
          <FormField label="Max Uses"><TextInput value={promoForm.maxUses} onChange={(v) => setPromoForm({ ...promoForm, maxUses: Number(v) })} type="number" /></FormField>
          <FormField label="Applicable Plans"><TextInput value={promoForm.applicablePlans || ''} onChange={(v) => setPromoForm({ ...promoForm, applicablePlans: v })} placeholder="basic,standard,premium" /></FormField>
          {promoForm.promotionType === 'FREE_MONTHS' && (
            <FormField label="Free Months"><TextInput value={promoForm.freeMonths || 0} onChange={(v) => setPromoForm({ ...promoForm, freeMonths: Number(v) })} type="number" /></FormField>
          )}
          {promoForm.promotionType === 'FREE_FEATURE' && (
            <FormField label="Free Feature Code"><TextInput value={promoForm.freeFeatureCode || ''} onChange={(v) => setPromoForm({ ...promoForm, freeFeatureCode: v.toUpperCase() })} placeholder="GPS_TRACKING" /></FormField>
          )}
          {promoForm.promotionType === 'PLAN_TRIAL' && (
            <FormField label="Trial Plan Code"><TextInput value={promoForm.trialPlanCode || ''} onChange={(v) => setPromoForm({ ...promoForm, trialPlanCode: v.toLowerCase() })} placeholder="premium" /></FormField>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Valid From"><input type="date" value={promoForm.validFrom} onChange={(e) => setPromoForm({ ...promoForm, validFrom: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
            <FormField label="Valid To"><input type="date" value={promoForm.validTo} onChange={(e) => setPromoForm({ ...promoForm, validTo: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
          </div>
        </div>
      </Modal>
    </div>
  );
}
