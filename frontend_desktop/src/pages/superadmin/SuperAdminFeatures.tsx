import { useEffect, useState } from 'react';
import { Edit2, Plus, Trash2, X } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';

const emptyForm = {
  code: '',
  name: '',
  description: '',
  benefits: '',
  category: 'Core',
  active: true,
};

export default function SuperAdminFeatures() {
  const [features, setFeatures] = useState<any[]>([]);
  const [plans, setPlans] = useState<any[]>([]);
  const [access, setAccess] = useState<Record<string, boolean>>({});
  const [form, setForm] = useState<any>(emptyForm);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const { showToast } = useToast();

  const load = async () => {
    setLoading(true);
    try {
      const [featureRes, planRes] = await Promise.all([
        api.get('/super-admin/features'),
        api.get('/subscriptions/plans'),
      ]);
      const nextFeatures = featureRes.data || [];
      const nextPlans = planRes.data || [];
      setFeatures(nextFeatures);
      setPlans(nextPlans);

      const checks = await Promise.all(nextPlans.map(async (plan: any) => {
        const { data } = await api.get(`/super-admin/features/plans/${plan.id}`);
        return [plan.id, data] as const;
      }));
      const matrix: Record<string, boolean> = {};
      checks.forEach(([planId, assignments]) => {
        (assignments || []).forEach((assignment: any) => {
          matrix[`${planId}:${assignment.featureCode}`] = Boolean(assignment.enabled);
        });
      });
      setAccess(matrix);
    } catch (error) {
      console.error(error);
      showToast('Unable to load feature matrix. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const saveFeature = async () => {
    try {
      if (form.id) await api.put(`/super-admin/features/${form.id}`, form);
      else await api.post('/super-admin/features', form);
      setModalOpen(false);
      setForm(emptyForm);
      await load();
      showToast('Feature saved successfully', 'success');
    } catch (error) {
      console.error(error);
      showToast('Unable to save feature. Please try again later.', 'error');
    }
  };

  const removeFeature = async (feature: any) => {
    if (!window.confirm(`Delete ${feature.name}?`)) return;
    await api.delete(`/super-admin/features/${feature.id}`);
    await load();
  };

  const toggle = async (planId: number, featureCode: string) => {
    const key = `${planId}:${featureCode}`;
    const enabled = !access[key];
    setAccess((current) => ({ ...current, [key]: enabled }));
    try {
      await api.post(`/super-admin/features/plans/${planId}/${featureCode}`, { enabled });
    } catch (error) {
      setAccess((current) => ({ ...current, [key]: !enabled }));
      showToast('Unable to update plan feature. Please try again later.', 'error');
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">Feature Access Control</h1>
          <p className="text-sm text-slate-500 mt-1">Manage the feature catalog and plan access matrix.</p>
        </div>
        <button onClick={() => { setForm(emptyForm); setModalOpen(true); }} className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#0a0f2c] text-white rounded-lg text-sm font-semibold">
          <Plus size={16} /> Create Feature
        </button>
      </div>

      <div className="bg-white border border-[#e8e6e1] shadow-soft overflow-x-auto">
        <table className="w-full min-w-[900px] text-left">
          <thead>
            <tr className="bg-[#f5f5f0] text-[10px] uppercase font-bold text-slate-400">
              <th className="px-5 py-4">Feature</th>
              <th className="px-5 py-4">Category</th>
              {plans.map((plan) => <th key={plan.id} className="px-4 py-4 text-center">{plan.name}</th>)}
              <th className="px-5 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e8e6e1]">
            {loading ? (
              <tr><td colSpan={plans.length + 3} className="p-10 text-center text-slate-400">Loading feature matrix...</td></tr>
            ) : features.map((feature) => (
              <tr key={feature.id} className={!feature.active ? 'opacity-50' : ''}>
                <td className="px-5 py-4">
                  <p className="text-sm font-semibold text-[#1e293b]">{feature.name}</p>
                  <p className="text-[10px] font-mono text-slate-400 mt-1">{feature.code}</p>
                </td>
                <td className="px-5 py-4 text-xs text-slate-500">{feature.category}</td>
                {plans.map((plan) => {
                  const checked = Boolean(access[`${plan.id}:${feature.code}`]);
                  return (
                    <td key={plan.id} className="px-4 py-4 text-center">
                      <button
                        onClick={() => toggle(plan.id, feature.code)}
                        className={`w-10 h-6 rounded-full p-1 transition-colors ${checked ? 'bg-emerald-500' : 'bg-slate-200'}`}
                        aria-label={`${checked ? 'Disable' : 'Enable'} ${feature.name} for ${plan.name}`}
                      >
                        <span className={`block w-4 h-4 rounded-full bg-white transition-transform ${checked ? 'translate-x-4' : ''}`} />
                      </button>
                    </td>
                  );
                })}
                <td className="px-5 py-4">
                  <div className="flex justify-end gap-1">
                    <button onClick={() => { setForm(feature); setModalOpen(true); }} className="p-2 text-slate-400 hover:text-brand-500"><Edit2 size={15} /></button>
                    <button onClick={() => removeFeature(feature)} className="p-2 text-slate-400 hover:text-rose-500"><Trash2 size={15} /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-[100] bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white w-full max-w-xl border border-[#e8e6e1] shadow-elevated">
            <div className="px-5 py-4 border-b border-[#e8e6e1] flex items-center justify-between">
              <h2 className="font-bold text-[#1e293b]">{form.id ? 'Edit Feature' : 'Create Feature'}</h2>
              <button onClick={() => setModalOpen(false)} className="p-1.5 text-slate-400"><X size={17} /></button>
            </div>
            <div className="p-5 grid grid-cols-1 sm:grid-cols-2 gap-4">
              {[
                ['code', 'Feature Code'],
                ['name', 'Feature Name'],
                ['category', 'Category'],
              ].map(([key, label]) => (
                <label key={key} className="text-xs font-semibold text-slate-500">
                  {label}
                  <input value={form[key] || ''} onChange={(e) => setForm({ ...form, [key]: key === 'code' ? e.target.value.toUpperCase() : e.target.value })}
                    className="mt-1 w-full px-3 py-2.5 border border-[#e8e6e1] rounded-lg text-sm text-[#1e293b] outline-none focus:border-brand-400" />
                </label>
              ))}
              <label className="flex items-center gap-2 text-sm text-slate-600 pt-6">
                <input type="checkbox" checked={form.active !== false} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                Enabled in catalog
              </label>
              <label className="sm:col-span-2 text-xs font-semibold text-slate-500">
                Description
                <textarea value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} className="mt-1 w-full px-3 py-2.5 border border-[#e8e6e1] rounded-lg text-sm min-h-20 outline-none" />
              </label>
              <label className="sm:col-span-2 text-xs font-semibold text-slate-500">
                Benefits shown on upgrade card
                <textarea value={form.benefits || ''} onChange={(e) => setForm({ ...form, benefits: e.target.value })} className="mt-1 w-full px-3 py-2.5 border border-[#e8e6e1] rounded-lg text-sm min-h-20 outline-none" />
              </label>
            </div>
            <div className="px-5 py-4 border-t border-[#e8e6e1] flex justify-end gap-2">
              <button onClick={() => setModalOpen(false)} className="px-4 py-2 text-sm font-semibold text-slate-500">Cancel</button>
              <button onClick={saveFeature} className="px-4 py-2 bg-[#0a0f2c] text-white rounded-lg text-sm font-semibold">Save Feature</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
