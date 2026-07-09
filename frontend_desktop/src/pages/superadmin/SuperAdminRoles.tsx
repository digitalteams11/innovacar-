import { useEffect, useMemo, useState } from 'react';
import { Loader2, ShieldCheck, Plus, Crown, X } from 'lucide-react';
import { superAdminApi } from '../../api/superAdminApi';
import { useToast } from '../../context/ToastContext';

interface PermissionDefinition {
  code: string;
  name: string;
  category: string;
}

interface RolePermissionRow {
  permissionCode: string;
  enabled: boolean;
}

interface RoleEntry {
  id: number;
  code: string;
  label: string;
  description?: string;
  systemRole: boolean;
}

interface PermissionMatrix {
  definitions: PermissionDefinition[];
  roles: RoleEntry[];
  matrix: Record<string, RolePermissionRow[]>;
}

export default function SuperAdminRoles() {
  const { showToast } = useToast();
  const [matrix, setMatrix] = useState<PermissionMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ code: '', label: '', description: '' });

  const load = () => {
    setLoading(true);
    superAdminApi.getStaffRoles()
      .then(({ data }) => setMatrix(data))
      .catch((err: any) => showToast(err?.userMessage || 'Unable to load roles. Please try again later.', 'error'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const categories = useMemo(() => {
    if (!matrix) return [];
    return Array.from(new Set(matrix.definitions.map((item) => item.category)));
  }, [matrix]);

  const isEnabled = (roleCode: string, code: string) =>
    matrix?.matrix?.[roleCode]?.find((item) => item.permissionCode === code)?.enabled ?? false;

  const toggle = async (role: RoleEntry, code: string, enabled: boolean) => {
    const key = `${role.code}:${code}`;
    setSavingKey(key);
    try {
      await superAdminApi.updateStaffRolePermissions(role.id, { [code]: enabled });
      setMatrix((current) => {
        if (!current) return current;
        return {
          ...current,
          matrix: {
            ...current.matrix,
            [role.code]: current.matrix[role.code].map((item) =>
              item.permissionCode === code ? { ...item, enabled } : item),
          },
        };
      });
      showToast('Role permission updated successfully.', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update this permission.', 'error');
    } finally {
      setSavingKey('');
    }
  };

  const createRole = async () => {
    if (!form.code.trim() || !form.label.trim()) {
      showToast('Role code and label are required.', 'error');
      return;
    }
    setCreating(true);
    try {
      await superAdminApi.createStaffRole({ code: form.code.trim(), label: form.label.trim(), description: form.description.trim() || undefined });
      showToast('Custom role created successfully.', 'success');
      setShowCreate(false);
      setForm({ code: '', label: '', description: '' });
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to create role.', 'error');
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[320px] items-center justify-center">
        <Loader2 className="animate-spin text-brand-500" size={30} />
      </div>
    );
  }

  if (!matrix) {
    return <div className="p-6 text-sm text-slate-500">Roles & permissions are unavailable.</div>;
  }

  const roles = matrix.roles;

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-500">
            <ShieldCheck size={21} />
          </div>
          <div>
            <h1 className="text-xl font-bold text-[#1e293b] dark:text-white">Roles & Permissions</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">Control what each Innovax staff role can do in the Super Admin control center.</p>
          </div>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#0a0f2c] text-white text-sm font-medium hover:bg-[#0a0f2c]/85 transition-colors self-start sm:self-auto"
        >
          <Plus size={16} /> New Role
        </button>
      </div>

      {categories.map((category) => {
        const definitions = matrix.definitions.filter((item) => item.category === category);
        return (
          <section key={category} className="card-premium overflow-hidden p-0">
            <div className="border-b border-[#e8e6e1] dark:border-white/10 px-5 py-4">
              <h2 className="text-sm font-bold text-[#1e293b] dark:text-white">{category}</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-left">
                <thead>
                  <tr className="bg-[#f5f5f0]/70 dark:bg-white/5 text-[10px] font-bold uppercase text-slate-400">
                    <th className="w-56 px-5 py-3">Permission</th>
                    {roles.map((role) => (
                      <th key={role.code} className="px-3 py-3 text-center">
                        <span className="inline-flex items-center gap-1">
                          {role.code === 'SUPER_OWNER' && <Crown size={11} className="text-amber-500" />}
                          {role.label}
                        </span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#e8e6e1]/60 dark:divide-white/5">
                  {definitions.map((definition) => (
                    <tr key={definition.code}>
                      <td className="px-5 py-3">
                        <p className="text-sm font-medium text-[#1e293b] dark:text-white">{definition.name}</p>
                        <p className="text-[10px] text-slate-400">{definition.code}</p>
                      </td>
                      {roles.map((role) => {
                        const key = `${role.code}:${definition.code}`;
                        const enabled = isEnabled(role.code, definition.code);
                        const locked = role.code === 'SUPER_OWNER';
                        return (
                          <td key={role.code} className="px-3 py-3 text-center">
                            <button
                              type="button"
                              role="switch"
                              aria-checked={enabled}
                              aria-label={`${definition.name} for ${role.label}`}
                              disabled={savingKey === key || locked}
                              onClick={() => toggle(role, definition.code, !enabled)}
                              title={locked ? 'Super Owner always has full access' : undefined}
                              className={`relative h-6 w-11 rounded-full transition-colors ${
                                enabled ? 'bg-brand-500' : 'bg-slate-200 dark:bg-white/10'
                              } disabled:opacity-60`}
                            >
                              <span className={`absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all ${
                                enabled ? 'left-6' : 'left-1'
                              }`} />
                            </button>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}

      {showCreate && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
          <div className="bg-white dark:bg-[#1a2332] rounded-2xl w-full max-w-md p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">New Staff Role</h3>
              <button onClick={() => setShowCreate(false)} className="text-slate-400 hover:text-[#1e293b] dark:hover:text-white">
                <X size={18} />
              </button>
            </div>
            <div className="space-y-3">
              <input
                type="text" placeholder="Code (e.g. REGIONAL_MANAGER)" value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
              />
              <input
                type="text" placeholder="Label (e.g. Regional Manager)" value={form.label}
                onChange={(e) => setForm({ ...form, label: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
              />
              <textarea
                placeholder="Description (optional)" value={form.description} rows={2}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none resize-none"
              />
              <p className="text-xs text-slate-400">New roles start with no permissions granted — enable them below after creating.</p>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 rounded-xl text-sm font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5">
                Cancel
              </button>
              <button
                onClick={createRole}
                disabled={creating}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/85 disabled:opacity-60"
              >
                {creating ? 'Creating...' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
