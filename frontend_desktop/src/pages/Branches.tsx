import { useCallback, useEffect, useState } from 'react';
import { Building2, MapPin, Pencil, Plus, Trash2, Users, X } from 'lucide-react';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import { GlassPageHeader } from '../components/GlassPageHeader';
import ApiErrorState from '../components/ApiErrorState';
import PremiumLoader from '../components/PremiumLoader';

type Branch = {
  id: number;
  name: string;
  code: string;
  address: string;
  city: string;
  phone: string;
  email: string;
  managerName: string;
  openingHours: string;
  status: 'ACTIVE' | 'INACTIVE';
  active: boolean;
  vehicleCount: number;
};

const emptyForm = {
  name: '',
  address: '',
  city: '',
  phone: '',
  email: '',
  managerName: '',
  openingHours: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
};

function unwrapBranches(payload: unknown): Branch[] {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === 'object') {
    const response = payload as Record<string, unknown>;
    if (Array.isArray(response.data)) return response.data as Branch[];
    if (response.data && typeof response.data === 'object') {
      const nested = response.data as Record<string, unknown>;
      if (Array.isArray(nested.branches)) return nested.branches as Branch[];
      if (Array.isArray(nested.items)) return nested.items as Branch[];
    }
    if (Array.isArray(response.branches)) return response.branches as Branch[];
  }
  return [];
}

export default function Branches() {
  const { showToast } = useToast();
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Branch | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await api.get<unknown>('/branches');
      setBranches(unwrapBranches(response.data));
    } catch (requestError) {
      console.error(requestError);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const openForm = (branch?: Branch) => {
    setEditing(branch ?? null);
    setForm(branch ? {
      name: branch.name,
      address: branch.address,
      city: branch.city,
      phone: branch.phone,
      email: branch.email,
      managerName: branch.managerName || '',
      openingHours: branch.openingHours || '',
      status: branch.active ? 'ACTIVE' : 'INACTIVE',
    } : emptyForm);
    setModalOpen(true);
  };

  const save = async () => {
    if (!form.name.trim()) {
      showToast('Branch name is required.', 'warning');
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await api.put(`/branches/${editing.id}`, form);
        showToast('Branch updated successfully', 'success');
      } else {
        await api.post('/branches', form);
        showToast('Branch created successfully', 'success');
      }
      closeForm();
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast((requestError as any)?.response?.data?.message || 'Unable to save this branch. Please check the details and try again.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const closeForm = () => {
    setModalOpen(false);
    setEditing(null);
    setForm(emptyForm);
  };

  const remove = async (branch: Branch) => {
    if (!window.confirm(`Delete ${branch.name}?`)) return;
    try {
      await api.delete(`/branches/${branch.id}`);
      showToast('Branch deleted successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast(branch.vehicleCount > 0 ? 'Move assigned vehicles before deleting this branch.' : 'Unable to delete this branch.', 'error');
    }
  };

  if (loading && branches.length === 0) return <PremiumLoader />;
  if (error) return <ApiErrorState message="Unable to load branch information." onRetry={load} />;

  return (
    <div className="space-y-5">
      <GlassPageHeader
        title="Branches"
        subtitle="Coordinate fleet locations while keeping every record inside your agency workspace."
        icon={Building2}
        actions={(
          <button onClick={() => openForm()} className="inline-flex items-center gap-2 rounded-lg bg-[var(--brand-primary)] px-4 py-2.5 text-sm font-semibold text-[#171817]">
            <Plus size={16} /> Add branch
          </button>
        )}
      />

      {branches.length === 0 ? (
        <button onClick={() => openForm()} className="data-surface flex min-h-72 w-full flex-col items-center justify-center p-8 text-center">
          <Building2 size={30} className="text-[var(--brand-primary)]" />
          <h2 className="mt-4 font-semibold text-[var(--text-primary)]">Create your first branch</h2>
          <p className="mt-2 max-w-md text-sm text-[var(--text-muted)]">Assign vehicles to physical locations and understand fleet distribution without duplicating agency data.</p>
        </button>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {branches.map((branch) => (
            <article key={branch.id} className="data-surface p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--bg-hover)] text-[var(--brand-primary)]"><Building2 size={19} /></div>
                <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${branch.active ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300' : 'bg-slate-100 text-slate-500 dark:bg-white/5'}`}>{branch.active ? 'ACTIVE' : 'INACTIVE'}</span>
              </div>
              <h2 className="mt-5 text-lg font-semibold text-[var(--text-primary)]">{branch.name}</h2>
              <p className="mt-1 font-mono text-xs text-[var(--brand-primary)]">{branch.code}</p>
              <div className="mt-5 space-y-2 text-sm text-[var(--text-muted)]">
                <p className="flex items-center gap-2"><MapPin size={14} /> {[branch.address, branch.city].filter(Boolean).join(', ') || 'Address not added'}</p>
                <p className="flex items-center gap-2"><Users size={14} /> {branch.vehicleCount} assigned vehicles</p>
              </div>
              <div className="mt-5 flex gap-2 border-t border-[var(--border-subtle)] pt-4">
                <button onClick={() => openForm(branch)} className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)]"><Pencil size={14} /> Edit</button>
                <button onClick={() => remove(branch)} className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-red-200 text-red-600 dark:border-red-500/20" title="Delete branch"><Trash2 size={14} /></button>
              </div>
            </article>
          ))}
        </div>
      )}

      {modalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm">
          <div className="data-surface w-full max-w-xl p-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">{editing ? 'Edit branch' : 'Create Branch'}</h2>
              <button onClick={closeForm} className="rounded-lg p-2 text-[var(--text-muted)]"><X size={18} /></button>
            </div>
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Branch name" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as 'ACTIVE' | 'INACTIVE' })} className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none">
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
              <input value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} placeholder="Address" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none sm:col-span-2" />
              <input value={form.city} onChange={(event) => setForm({ ...form, city: event.target.value })} placeholder="City" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} placeholder="Phone" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="Email" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <input value={form.managerName} onChange={(event) => setForm({ ...form, managerName: event.target.value })} placeholder="Manager name" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
              <input value={form.openingHours} onChange={(event) => setForm({ ...form, openingHours: event.target.value })} placeholder="Opening hours" className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-3 text-sm text-[var(--text-primary)] outline-none" />
            </div>
            <div className="mt-5 flex gap-2">
              <button disabled={saving} onClick={closeForm} className="flex-1 rounded-lg border border-[var(--border-subtle)] px-4 py-3 text-sm font-semibold text-[var(--text-secondary)] disabled:opacity-50">Cancel</button>
              <button disabled={saving} onClick={save} className="flex-1 rounded-lg bg-[var(--brand-primary)] px-4 py-3 text-sm font-semibold text-[#171817] disabled:opacity-50">{saving ? 'Saving...' : 'Save Branch'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
