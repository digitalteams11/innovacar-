import { useEffect, useState } from 'react';
import { superAdminApi } from '../../api/superAdminApi';
import { useToast } from '../../context/ToastContext';
import { Plus, ShieldOff, ShieldCheck, X, Crown } from 'lucide-react';

interface StaffRole {
  id: number;
  code: string;
  label: string;
}

interface StaffMember {
  id: number;
  email: string;
  firstName?: string;
  lastName?: string;
  superAdminRoleId?: number | null;
  superAdminRoleCode?: string | null;
  superAdminRoleLabel: string;
  accountEnabled: boolean;
  lastLoginAt?: string | null;
  createdAt?: string;
}

export default function SuperAdminStaff() {
  const { showToast } = useToast();
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [roles, setRoles] = useState<StaffRole[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ email: '', password: '', firstName: '', lastName: '', superAdminRoleId: '' });

  const load = async () => {
    setLoading(true);
    try {
      const [staffRes, rolesRes] = await Promise.all([
        superAdminApi.getStaff(),
        superAdminApi.getStaffRoles(),
      ]);
      setStaff(staffRes.data);
      setRoles((rolesRes.data?.roles || []).map((r: any) => ({ id: r.id, code: r.code, label: r.label })));
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to load staff. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const createStaff = async () => {
    if (!form.email.trim() || !form.password.trim() || !form.firstName.trim()) {
      showToast('Email, password, and first name are required.', 'error');
      return;
    }
    setSaving(true);
    try {
      await superAdminApi.createStaff({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim() || undefined,
        superAdminRoleId: form.superAdminRoleId ? Number(form.superAdminRoleId) : null,
      });
      showToast('Staff account created successfully.', 'success');
      setShowCreate(false);
      setForm({ email: '', password: '', firstName: '', lastName: '', superAdminRoleId: '' });
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to create staff account.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const changeRole = async (id: number, superAdminRoleId: string) => {
    try {
      await superAdminApi.updateStaff(id, { superAdminRoleId: superAdminRoleId ? Number(superAdminRoleId) : null });
      showToast('Staff role updated successfully.', 'success');
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update staff role.', 'error');
    }
  };

  const toggleStatus = async (member: StaffMember) => {
    const enabling = !member.accountEnabled;
    if (!enabling && !window.confirm(`Suspend ${member.email}? They will immediately lose access to the Super Admin control center.`)) return;
    try {
      await superAdminApi.setStaffStatus(member.id, enabling);
      showToast(enabling ? 'Staff account activated.' : 'Staff account suspended.', 'success');
      load();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update staff status.', 'error');
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#1e293b] dark:text-white tracking-tight">Super Admin Staff</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">Manage Innovax team accounts and their platform roles.</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#0a0f2c] text-white text-sm font-medium hover:bg-[#0a0f2c]/85 transition-colors"
        >
          <Plus size={16} /> Add Staff
        </button>
      </div>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#e8e6e1]/60 dark:border-white/5">
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Staff</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Role</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Status</th>
                <th className="text-left text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Last login</th>
                <th className="text-right text-xs font-semibold text-slate-500 uppercase tracking-wider px-5 py-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} className="text-center py-12 text-slate-400">Loading...</td></tr>
              ) : staff.length === 0 ? (
                <tr><td colSpan={5} className="text-center py-12 text-slate-400">No staff accounts found.</td></tr>
              ) : (
                staff.map((member) => (
                  <tr key={member.id} className="border-b border-[#e8e6e1]/40 dark:border-white/5 hover:bg-slate-50/50 dark:hover:bg-white/5 transition-colors">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-[#0a0f2c] dark:bg-white/10 flex items-center justify-center text-white text-sm font-bold">
                          {member.email.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <p className="text-sm font-medium text-[#1e293b] dark:text-white">{[member.firstName, member.lastName].filter(Boolean).join(' ') || member.email}</p>
                          <p className="text-xs text-slate-400">{member.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <select
                        value={member.superAdminRoleId || ''}
                        onChange={(e) => changeRole(member.id, e.target.value)}
                        className="text-xs font-medium px-2.5 py-1.5 rounded-lg border border-[#e8e6e1]/80 dark:border-white/5 bg-white dark:bg-[#1a2332] text-[#1e293b] dark:text-white outline-none"
                      >
                        <option value="">Unrestricted (legacy)</option>
                        {roles.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
                      </select>
                      {member.superAdminRoleCode === 'SUPER_OWNER' && (
                        <span className="inline-flex items-center gap-1 ms-2 text-[10px] font-semibold text-amber-600">
                          <Crown size={10} /> Owner
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-4">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border ${
                        member.accountEnabled
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-rose-50 text-rose-700 border-rose-200'
                      }`}>
                        {member.accountEnabled ? 'Active' : 'Suspended'}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-sm text-slate-500">
                      {member.lastLoginAt ? new Date(member.lastLoginAt).toLocaleString() : 'Never'}
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => toggleStatus(member)}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white dark:bg-[#1a2332]/50 border border-[#e8e6e1]/80 dark:border-white/5 text-[#1e293b] dark:text-white hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
                        >
                          {member.accountEnabled ? <ShieldOff size={13} /> : <ShieldCheck size={13} />}
                          {member.accountEnabled ? 'Suspend' : 'Activate'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
          <div className="bg-white dark:bg-[#1a2332] rounded-2xl w-full max-w-md p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Add Staff Account</h3>
              <button onClick={() => setShowCreate(false)} className="text-slate-400 hover:text-[#1e293b] dark:hover:text-white">
                <X size={18} />
              </button>
            </div>
            <div className="space-y-3">
              <input
                type="email" placeholder="Email" value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
              />
              <input
                type="password" placeholder="Password (min 8 characters)" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
              />
              <div className="grid grid-cols-2 gap-3">
                <input
                  type="text" placeholder="First name" value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
                />
                <input
                  type="text" placeholder="Last name" value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
                />
              </div>
              <select
                value={form.superAdminRoleId}
                onChange={(e) => setForm({ ...form, superAdminRoleId: e.target.value })}
                className="w-full px-3 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 bg-[#f5f5f0] dark:bg-white/5 text-sm outline-none"
              >
                <option value="">Unrestricted (legacy super admin)</option>
                {roles.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
              </select>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 rounded-xl text-sm font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5">
                Cancel
              </button>
              <button
                onClick={createStaff}
                disabled={saving}
                className="px-4 py-2 rounded-xl text-sm font-medium bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/85 disabled:opacity-60"
              >
                {saving ? 'Creating...' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
