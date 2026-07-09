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
  const [editingPromo, setEditingPromo] = useState<any>(null);
  const [planForm, setPlanForm] = useState<any>({});

  const defaultPromoForm = () => ({
    code: '', promotionName: '', promotionType: 'DISCOUNT', discountType: 'PERCENTAGE',
    discountValue: 0, maxUses: null, maxUsesPerAgency: null, validFrom: '', validTo: '',
    applicablePlans: '', billingCycle: 'BOTH', minimumAmount: null,
    firstTimeOnly: false, appliesToAllPlans: true,
    freeMonths: 0, isActive: true, planLinks: [] as any[],
  });
  const [promoForm, setPromoForm] = useState<any>(defaultPromoForm());
  const [promoSaving, setPromoSaving] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [plansRes, agenciesRes, promoRes] = await Promise.all([
        superAdminApi.getPlans(),
        superAdminApi.getAgencies(),
        superAdminApi.getPromoCodes().catch(() => ({ data: { data: [] } })),
      ]);
      setPlans(plansRes.data);
      setAgencies(agenciesRes.data);
      // Backend returns { success, data: [...] }
      const promoData = promoRes.data?.data ?? promoRes.data ?? [];
      setPromoCodes(Array.isArray(promoData) ? promoData : []);
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

  const openCreatePromo = () => {
    setEditingPromo(null);
    setPromoForm(defaultPromoForm());
    setShowPromoModal(true);
  };

  const openEditPromo = (promo: any) => {
    setEditingPromo(promo);
    setPromoForm({
      code: promo.code ?? '',
      promotionName: promo.promotionName ?? '',
      promotionType: promo.promotionType ?? 'DISCOUNT',
      discountType: promo.discountType ?? 'PERCENTAGE',
      discountValue: promo.discountValue ?? 0,
      maxUses: promo.maxUses ?? null,
      maxUsesPerAgency: promo.maxUsesPerAgency ?? null,
      validFrom: promo.validFrom ?? '',
      validTo: promo.validTo ?? '',
      applicablePlans: promo.applicablePlans ?? '',
      billingCycle: promo.billingCycle ?? 'BOTH',
      minimumAmount: promo.minimumAmount ?? null,
      firstTimeOnly: promo.firstTimeOnly ?? false,
      appliesToAllPlans: promo.appliesToAllPlans ?? true,
      freeMonths: promo.freeMonths ?? 0,
      isActive: promo.isActive ?? true,
      planLinks: promo.planLinks ?? [],
    });
    setShowPromoModal(true);
  };

  const handleSavePromo = async () => {
    setPromoSaving(true);
    try {
      if (editingPromo) {
        await superAdminApi.updatePromoCode(editingPromo.id, promoForm);
        showToast('Promo code updated successfully', 'success');
      } else {
        await superAdminApi.createPromoCode(promoForm);
        showToast('Promo code created successfully', 'success');
      }
      setShowPromoModal(false);
      setEditingPromo(null);
      setPromoForm(defaultPromoForm());
      fetchData();
    } catch (err: any) {
      showToast(err?.response?.data?.message ?? 'Failed to save promo code.', 'error');
    } finally {
      setPromoSaving(false);
    }
  };

  const handleTogglePromo = async (promo: any) => {
    try {
      if (promo.isActive) {
        await superAdminApi.deactivatePromoCode(promo.id);
      } else {
        await superAdminApi.activatePromoCode(promo.id);
      }
      fetchData();
    } catch {
      showToast('Failed to update promo status.', 'error');
    }
  };

  const handleDeletePromo = async (id: number) => {
    if (!window.confirm('Soft-delete this promo code? It will be hidden from agencies.')) return;
    try {
      await superAdminApi.deletePromoCode(id);
      fetchData();
      showToast('Promo code deleted.', 'success');
    } catch {
      showToast('Failed to delete promo code.', 'error');
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
      whiteLabel: false, prioritySupport: false, isActive: true, highlighted: false,
      featuresJson: '', displayOrder: 0,
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
              <div className="pt-3 border-t border-[#e8e6e1]/40 dark:border-white/5 space-y-1.5">
                <div className="flex items-center gap-1.5 text-xs text-slate-500">
                  <Users size={12} />
                  <span>{count} {t('superAdmin.subscriptions.agenciesOnPlan')}</span>
                </div>
                {plan.monthlyPrice > 0 && !plan.whopCheckoutUrlMonthly && !plan.whopCheckoutUrlYearly && !plan.whopPlanId && (
                  <div className="text-[10px] text-amber-600 font-medium">⚠ Checkout not configured</div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Promo Codes Section */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-[#1e293b] dark:text-white">Promo Codes</h2>
          <button onClick={openCreatePromo} className="flex items-center gap-2 bg-white dark:bg-[#1a2332]/70 border border-[#e8e6e1]/80 dark:border-white/5 hover:bg-slate-50 dark:hover:bg-white/5 text-[#1e293b] dark:text-white px-4 py-2 rounded-xl text-sm font-medium transition-colors shadow-soft">
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
                  <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Actions</th>
                </tr>
              </thead>
              <tbody>
                {promoCodes.length === 0 ? (
                  <tr><td colSpan={6} className="text-center py-12 text-slate-400">No promo codes found</td></tr>
                ) : promoCodes.map((promo: any) => (
                  <tr key={promo.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                    <td className="px-5 py-4">
                      <div className="text-sm font-mono font-semibold text-[#1e293b] dark:text-white">{promo.code}</div>
                      {promo.promotionName && <div className="text-xs text-slate-400 mt-0.5">{promo.promotionName}</div>}
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.discountValue}{promo.discountType === 'PERCENTAGE' ? '%' : ' MAD'}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.redemptionCount ?? promo.usedCount ?? 0} / {promo.maxUses ?? '∞'}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{promo.validTo ? new Date(promo.validTo).toLocaleDateString() : '-'}</td>
                    <td className="px-5 py-4"><Badge variant={promo.isActive ? 'success' : 'default'}>{promo.isActive ? 'Active' : 'Inactive'}</Badge></td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-1">
                        <button onClick={() => openEditPromo(promo)} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-brand-600 transition-colors" title="Edit">
                          <Edit2 size={13} />
                        </button>
                        <button onClick={() => handleTogglePromo(promo)} className="p-1.5 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg text-slate-400 hover:text-amber-600 transition-colors" title={promo.isActive ? 'Deactivate' : 'Activate'}>
                          {promo.isActive ? '⏸' : '▶'}
                        </button>
                        <button onClick={() => handleDeletePromo(promo.id)} className="p-1.5 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg text-slate-400 hover:text-rose-600 transition-colors" title="Delete">
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </td>
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
          <FormField label="Max Clients"><TextInput value={planForm.clientLimit ?? ''} onChange={(v) => setPlanForm({ ...planForm, clientLimit: Number(v) })} type="number" /></FormField>
          <FormField label="Max Contracts"><TextInput value={planForm.contractLimit ?? ''} onChange={(v) => setPlanForm({ ...planForm, contractLimit: Number(v) })} type="number" /></FormField>
          <FormField label="Trial Days"><TextInput value={planForm.trialDays ?? ''} onChange={(v) => setPlanForm({ ...planForm, trialDays: Number(v) })} type="number" /></FormField>
          <FormField label="Currency"><TextInput value={planForm.currency || 'MAD'} onChange={(v) => setPlanForm({ ...planForm, currency: v })} placeholder="MAD" /></FormField>
          <FormField label="Whop Product ID"><TextInput value={planForm.whopProductId || ''} onChange={(v) => setPlanForm({ ...planForm, whopProductId: v })} placeholder="prod_xxx" /></FormField>
          <FormField label="Whop Plan ID (Monthly)"><TextInput value={planForm.whopPlanId || ''} onChange={(v) => setPlanForm({ ...planForm, whopPlanId: v })} placeholder="plan_xxx" /></FormField>
          <FormField label="Whop Price ID (Yearly)"><TextInput value={planForm.whopPriceId || ''} onChange={(v) => setPlanForm({ ...planForm, whopPriceId: v })} placeholder="price_xxx" /></FormField>
          <FormField label="Whop Checkout URL (Monthly)"><TextInput value={planForm.whopCheckoutUrlMonthly || ''} onChange={(v) => setPlanForm({ ...planForm, whopCheckoutUrlMonthly: v })} placeholder="https://whop.com/checkout/..." /></FormField>
          <FormField label="Whop Checkout URL (Yearly)"><TextInput value={planForm.whopCheckoutUrlYearly || ''} onChange={(v) => setPlanForm({ ...planForm, whopCheckoutUrlYearly: v })} placeholder="https://whop.com/checkout/..." /></FormField>
          <FormField label="Sort Order"><TextInput value={planForm.displayOrder ?? ''} onChange={(v) => setPlanForm({ ...planForm, displayOrder: Number(v) })} type="number" /></FormField>
          <div className="space-y-3 pt-2">
            <ToggleSwitch checked={planForm.billingCycleAllowedMonthly !== false} onChange={(v) => setPlanForm({ ...planForm, billingCycleAllowedMonthly: v })} label="Allow Monthly Billing" />
            <ToggleSwitch checked={planForm.billingCycleAllowedYearly !== false} onChange={(v) => setPlanForm({ ...planForm, billingCycleAllowedYearly: v })} label="Allow Yearly Billing" />
            <ToggleSwitch checked={planForm.apiAccess || false} onChange={(v) => setPlanForm({ ...planForm, apiAccess: v })} label={t('superAdmin.subscriptions.apiAccess')} />
            <ToggleSwitch checked={planForm.whiteLabel || false} onChange={(v) => setPlanForm({ ...planForm, whiteLabel: v })} label={t('superAdmin.subscriptions.whiteLabel')} />
            <ToggleSwitch checked={planForm.prioritySupport || false} onChange={(v) => setPlanForm({ ...planForm, prioritySupport: v })} label={t('superAdmin.subscriptions.prioritySupport')} />
            <ToggleSwitch checked={!!planForm.highlighted} onChange={(v) => setPlanForm({ ...planForm, highlighted: v })} label="Popular / Highlighted" />
            <ToggleSwitch checked={planForm.isActive !== false} onChange={(v) => setPlanForm({ ...planForm, isActive: v })} label={t('superAdmin.subscriptions.isActive')} />
          </div>
          <div className="sm:col-span-2 lg:col-span-3">
            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
              Features (JSON array, e.g. ["Vehicle management","Basic contracts"])
            </label>
            <textarea
              value={planForm.featuresJson || ''}
              onChange={(e) => setPlanForm({ ...planForm, featuresJson: e.target.value })}
              rows={4}
              placeholder='["Vehicle management","Client management","Basic contracts","Basic reports"]'
              className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none resize-none font-mono"
            />
            {(() => {
              const price = planForm.monthlyPrice;
              const isPaid = price && Number(price) > 0;
              const hasWhop = planForm.whopCheckoutUrlMonthly || planForm.whopCheckoutUrlYearly || planForm.whopPlanId;
              if (isPaid && !hasWhop) {
                return (
                  <p className="mt-1 text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                    ⚠ Checkout not configured. Agencies cannot subscribe to this plan until you add a Whop Checkout URL or Whop Plan ID.
                  </p>
                );
              }
              return null;
            })()}
          </div>
        </div>
      </Modal>

      {/* Promo Modal */}
      <Modal isOpen={showPromoModal} onClose={() => { setShowPromoModal(false); setEditingPromo(null); }} title={editingPromo ? 'Edit Promo Code' : 'Create Promo Code'} size="md"
        footer={
          <div className="flex gap-3">
            <button onClick={handleSavePromo} disabled={promoSaving} className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50">
              {promoSaving ? 'Saving…' : editingPromo ? 'Save Changes' : 'Create'}
            </button>
            <button onClick={() => { setShowPromoModal(false); setEditingPromo(null); }} className="flex-1 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-[#1e293b] dark:text-white py-2.5 rounded-xl text-sm font-semibold transition-colors">Cancel</button>
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
            </select>
          </FormField>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Discount Type">
              <select value={promoForm.discountType} onChange={(e) => setPromoForm({ ...promoForm, discountType: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none">
                <option value="PERCENTAGE">Percentage (%)</option>
                <option value="FIXED">Fixed Amount (MAD)</option>
              </select>
            </FormField>
            <FormField label="Discount Value" required><TextInput value={promoForm.discountValue} onChange={(v) => setPromoForm({ ...promoForm, discountValue: Number(v) })} type="number" /></FormField>
          </div>
          {promoForm.promotionType === 'FREE_MONTHS' && (
            <FormField label="Free Months"><TextInput value={promoForm.freeMonths || 0} onChange={(v) => setPromoForm({ ...promoForm, freeMonths: Number(v) })} type="number" /></FormField>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Max Uses (global)"><TextInput value={promoForm.maxUses ?? ''} onChange={(v) => setPromoForm({ ...promoForm, maxUses: v === '' ? null : Number(v) })} placeholder="unlimited" type="number" /></FormField>
            <FormField label="Max Uses / Agency"><TextInput value={promoForm.maxUsesPerAgency ?? ''} onChange={(v) => setPromoForm({ ...promoForm, maxUsesPerAgency: v === '' ? null : Number(v) })} placeholder="unlimited" type="number" /></FormField>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Valid From"><input type="date" value={promoForm.validFrom || ''} onChange={(e) => setPromoForm({ ...promoForm, validFrom: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
            <FormField label="Valid To"><input type="date" value={promoForm.validTo || ''} onChange={(e) => setPromoForm({ ...promoForm, validTo: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none" /></FormField>
          </div>
          <FormField label="Billing Cycle">
            <select value={promoForm.billingCycle || 'BOTH'} onChange={(e) => setPromoForm({ ...promoForm, billingCycle: e.target.value })} className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none">
              <option value="BOTH">Both</option>
              <option value="MONTHLY">Monthly only</option>
              <option value="YEARLY">Yearly only</option>
            </select>
          </FormField>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300 cursor-pointer">
              <input type="checkbox" checked={!!promoForm.appliesToAllPlans} onChange={(e) => setPromoForm({ ...promoForm, appliesToAllPlans: e.target.checked })} />
              Applies to all plans
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300 cursor-pointer">
              <input type="checkbox" checked={!!promoForm.firstTimeOnly} onChange={(e) => setPromoForm({ ...promoForm, firstTimeOnly: e.target.checked })} />
              First-time subscribers only
            </label>
          </div>
          {!promoForm.appliesToAllPlans && (
            <FormField label="Applicable Plans (comma-separated codes)">
              <TextInput value={promoForm.applicablePlans || ''} onChange={(v) => setPromoForm({ ...promoForm, applicablePlans: v })} placeholder="BASIC,STANDARD,PREMIUM" />
            </FormField>
          )}

          {/* Whop checkout URL overrides per plan+cycle */}
          <div className="pt-2 border-t border-[#e8e6e1]/60 dark:border-white/5">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Discounted Whop Checkout URLs (Mode 1)</p>
            <p className="text-xs text-slate-400 mb-3">For each plan+cycle this promo applies to, enter the Whop checkout URL that charges the discounted price. If not set, promo checkout will return an error.</p>
            {(promoForm.planLinks as any[]).map((link: any, idx: number) => (
              <div key={idx} className="grid grid-cols-[1fr_1fr_2fr_auto] gap-2 mb-2 items-center">
                <input value={link.planCode || ''} onChange={(e) => { const l = [...promoForm.planLinks]; l[idx] = { ...l[idx], planCode: e.target.value.toUpperCase() }; setPromoForm({ ...promoForm, planLinks: l }); }} placeholder="BASIC" className="px-3 py-2 text-xs rounded-lg border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-[#1e293b] dark:text-white outline-none" />
                <select value={link.billingCycle || 'MONTHLY'} onChange={(e) => { const l = [...promoForm.planLinks]; l[idx] = { ...l[idx], billingCycle: e.target.value }; setPromoForm({ ...promoForm, planLinks: l }); }} className="px-3 py-2 text-xs rounded-lg border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-[#1e293b] dark:text-white outline-none">
                  <option value="MONTHLY">Monthly</option>
                  <option value="YEARLY">Yearly</option>
                </select>
                <input value={link.whopCheckoutUrlOverride || ''} onChange={(e) => { const l = [...promoForm.planLinks]; l[idx] = { ...l[idx], whopCheckoutUrlOverride: e.target.value }; setPromoForm({ ...promoForm, planLinks: l }); }} placeholder="https://whop.com/checkout/..." className="px-3 py-2 text-xs rounded-lg border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-[#1e293b] dark:text-white outline-none" />
                <button onClick={() => { const l = promoForm.planLinks.filter((_: any, i: number) => i !== idx); setPromoForm({ ...promoForm, planLinks: l }); }} className="text-rose-500 hover:text-rose-700 text-xs px-2">✕</button>
              </div>
            ))}
            <button onClick={() => setPromoForm({ ...promoForm, planLinks: [...promoForm.planLinks, { planCode: '', billingCycle: 'MONTHLY', whopCheckoutUrlOverride: '' }] })} className="text-xs text-brand-600 hover:underline mt-1">+ Add plan link</button>
          </div>

          <ToggleSwitch label="Active" checked={!!promoForm.isActive} onChange={(v) => setPromoForm({ ...promoForm, isActive: v })} />
        </div>
      </Modal>
    </div>
  );
}
