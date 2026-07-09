import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../context/PermissionContext';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import api from '../api/axios';
import { Plus, Download, Mail, Phone, Trash2, Edit3, Loader2, KeyRound, Users } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';

interface Employee {
  id: number;
  name: string;
  email: string;
  phone: string;
  role: string;
  department: string;
  hireDate: string;
  status: string;
  userId?: number;
  loginEnabled?: boolean;
}

const getEmployeeAccessToken = () =>
  localStorage.getItem('accessToken')
  || sessionStorage.getItem('accessToken')
  || localStorage.getItem('token')
  || sessionStorage.getItem('token');

const ROLE_OPTIONS = [
  { value: 'MANAGER', label: 'Manager' },
  { value: 'AGENT', label: 'Agent / Counter Staff' },
  { value: 'ACCOUNTANT', label: 'Accountant' },
  { value: 'FLEET_MANAGER', label: 'Fleet Manager' },
  { value: 'RECEPTIONIST', label: 'Receptionist' },
  { value: 'DRIVER', label: 'Driver' },
  { value: 'VIEWER', label: 'Viewer' },
  { value: 'EMPLOYEE', label: 'Employee' },
];

const emptyForm = () => ({
  fullName: '',
  email: '',
  phone: '',
  roleCode: 'EMPLOYEE',
  department: '',
  hireDate: new Date().toISOString().split('T')[0],
  status: 'ACTIVE',
  temporaryPassword: '',
});

export default function Employees() {
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyForm());
  const [formError, setFormError] = useState('');

  const { showToast } = useToast();
  const { t } = useTranslation();
  const { user } = useAuth();
  const { hasAnyPermission, loading: permissionsLoading } = usePermissions();

  const fetchEmployees = async () => {
    try {
      setLoading(true);
      const { data: response } = await api.get('/employees');
      const employees = Array.isArray(response) ? response : response?.data || [];
      setData(employees);
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to load employees.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchEmployees();
  }, []);

  const filteredEmployees = data.filter((e) => {
    const q = searchQuery.toLowerCase();
    return e.name?.toLowerCase().includes(q)
      || e.email?.toLowerCase().includes(q)
      || e.phone?.toLowerCase().includes(q)
      || e.role?.toLowerCase().includes(q)
      || e.department?.toLowerCase().includes(q);
  });

  const exportCSV = () => {
    const headers = ['Name', 'Email', 'Phone', 'Role', 'Department', 'Status'];
    const rows = filteredEmployees.map((e) => [e.name, e.email, e.phone, e.role, e.department, e.status]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'employees.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setFormError('');
    setIsModalOpen(true);
  };

  const openEdit = (emp: Employee) => {
    setEditingId(emp.id);
    setForm({
      fullName: emp.name || '',
      email: emp.email || '',
      phone: emp.phone || '',
      roleCode: emp.role || 'EMPLOYEE',
      department: emp.department || '',
      hireDate: emp.hireDate || new Date().toISOString().split('T')[0],
      status: emp.status || 'ACTIVE',
      temporaryPassword: '',
    });
    setFormError('');
    setIsModalOpen(true);
  };

  const canCreateOrManageEmployees = () => hasAnyPermission(['EMPLOYEE_CREATE', 'MANAGE_EMPLOYEES']);

  const saveEmployee = async () => {
    if (saving) return;
    setFormError('');

    if (!form.fullName.trim() || !form.email.trim()) {
      showToast('Full name and email are required.', 'warning');
      return;
    }
    if (editingId === null && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(form.temporaryPassword)) {
      showToast('Temporary password must be 10+ characters with uppercase, lowercase, number, and symbol.', 'warning');
      return;
    }
    if (permissionsLoading) {
      showToast('Permissions are still loading. Please try again in a moment.', 'warning');
      return;
    }
    if (!canCreateOrManageEmployees()) {
      showToast('You do not have permission to manage employees.', 'error');
      return;
    }

    const endpoint = editingId !== null ? `PUT /api/employees/${editingId}` : 'POST /api/employees';
    const safePayload = {
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim() || null,
      role: form.roleCode,
      department: form.department.trim() || null,
      hireDate: form.hireDate || null,
      status: form.status,
    };

    if (import.meta.env.DEV) {
      console.log('[ADD_EMPLOYEE_DEBUG]', {
        tokenExists: Boolean(getEmployeeAccessToken()),
        endpoint,
        currentUserRole: user?.role,
        agencyId: user?.tenantId,
        payload: safePayload,
        passwordProvided: Boolean(form.temporaryPassword),
        passwordLength: form.temporaryPassword?.length || 0,
      });
    }

    setSaving(true);
    try {
      if (editingId !== null) {
        await api.put(`/employees/${editingId}`, safePayload);
        showToast(t('toast.success', { action: 'Update' }));
      } else {
        const { data: createdResponse } = await api.post('/employees', {
          ...safePayload,
          temporaryPassword: form.temporaryPassword,
        });
        showToast(createdResponse?.message || 'Employee created successfully.', 'success');
      }
      setIsModalOpen(false);
      setEditingId(null);
      setForm(emptyForm());
      await fetchEmployees();
    } catch (err: any) {
      const status = err?.response?.status;
      const errorCode = err?.errorCode || err?.response?.data?.errorCode;
      const message = err?.userMessage || err?.response?.data?.message || 'Unable to save employee. Please try again later.';
      if (import.meta.env.DEV) {
        console.log('[ADD_EMPLOYEE_DEBUG]', {
          tokenExists: Boolean(getEmployeeAccessToken()),
          endpoint,
          status,
          errorCode,
          message,
        });
      }
      setFormError(message);
      showToast(message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const resetPassword = async (emp: Employee) => {
    if (!emp.userId) {
      showToast('This legacy employee does not have a login account', 'warning');
      return;
    }
    const newPassword = window.prompt(`New password for ${emp.name} (10+ characters with uppercase, lowercase, number, and symbol)`);
    if (!newPassword || !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(newPassword)) return;
    try {
      await api.put(`/users/${emp.userId}/admin-reset-password`, { newPassword });
      showToast('Password reset successfully', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to reset password. Please try again later.', 'error');
    }
  };

  const deleteEmployee = async (id: number) => {
    if (confirm('Delete this employee?')) {
      try {
        await api.delete(`/employees/${id}`);
        setData((prev) => prev.filter((e) => e.id !== id));
        showToast(t('toast.success', { action: 'Delete' }));
      } catch (err: any) {
        showToast(err?.userMessage || 'Unable to delete employee. Please try again later.', 'error');
      }
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title={t('employees.title')}
        subtitle={t('employees.subtitle')}
        icon={Users}
        actions={<>
          <button type="button" onClick={exportCSV} className="surface-control flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
            <Download size={18} /> Export
          </button>
          <button type="button" onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
            <Plus size={18} /> Add Employee
          </button>
        </>}
      />

      <div className="flex justify-end">
        <SearchInput className="w-full sm:w-[380px]" placeholder="Search employees..." value={searchQuery} onChange={setSearchQuery} />
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-[var(--bg-hover)] text-[var(--text-muted)] text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">Employee</th>
                  <th className="px-5 py-4">Contact</th>
                  <th className="px-5 py-4">Role</th>
                  <th className="px-5 py-4">Department</th>
                  <th className="px-5 py-4">Status</th>
                  <th className="px-5 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)]">
                {filteredEmployees.map((emp) => (
                  <tr key={emp.id} className="hover:bg-[var(--bg-hover)] transition-colors group">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 bg-brand-500/10 rounded-lg flex items-center justify-center text-brand-500 font-bold text-sm">
                          {emp.name?.split(' ').map((n) => n[0]).join('').slice(0, 2) || '?'}
                        </div>
                        <span className="text-sm font-medium text-[var(--text-primary)]">{emp.name}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-xs text-[var(--text-muted)]"><Mail size={12} className="inline mr-1" />{emp.email}</div>
                      <div className="text-xs text-[var(--text-muted)] mt-0.5"><Phone size={12} className="inline mr-1" />{emp.phone || 'N/A'}</div>
                    </td>
                    <td className="px-5 py-4 text-sm text-[var(--text-primary)]">{emp.role || 'N/A'}</td>
                    <td className="px-5 py-4 text-sm text-[var(--text-muted)]">{emp.department || 'N/A'}</td>
                    <td className="px-5 py-4">
                      <span className={`px-2 py-1 rounded-lg text-[10px] font-bold uppercase ${emp.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 'bg-slate-500/10 text-[var(--text-muted)]'}`}>
                        {emp.status}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button type="button" onClick={() => openEdit(emp)} className="p-2 text-[var(--text-muted)] hover:text-brand-500 hover:bg-brand-500/10 rounded-lg transition-all" aria-label="Edit employee"><Edit3 size={17} /></button>
                        <button type="button" title="Reset login password" onClick={() => resetPassword(emp)} className="p-2 text-[var(--text-muted)] hover:text-amber-600 hover:bg-amber-500/10 rounded-lg transition-all"><KeyRound size={17} /></button>
                        <button type="button" onClick={() => deleteEmployee(emp.id)} className="p-2 text-[var(--text-muted)] hover:text-danger-500 hover:bg-danger-500/10 rounded-lg transition-all" aria-label="Delete employee"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredEmployees.length === 0 && (
                  <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-muted)] text-sm">No employees found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => { if (!saving) setIsModalOpen(false); }} title={editingId ? 'Edit Employee' : 'Add Employee'}>
        <form onSubmit={(e) => { e.preventDefault(); void saveEmployee(); }} className="space-y-4">
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Full Name *</label><input type="text" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Email *</label><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Phone</label><input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Role</label><select value={form.roleCode} onChange={(e) => setForm({ ...form, roleCode: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]">{ROLE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></div>
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Department</label><input type="text" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          </div>
          {editingId === null && <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Temporary Password *</label><input type="password" value={form.temporaryPassword} onChange={(e) => setForm({ ...form, temporaryPassword: e.target.value })} placeholder="10+ chars, upper, lower, number, symbol" className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Hire Date</label><input type="date" value={form.hireDate} onChange={(e) => setForm({ ...form, hireDate: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">Status</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]">
                <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
              </select>
            </div>
          </div>
          {formError && (
            <div className="rounded-2xl border border-rose-400/30 bg-rose-500/10 px-4 py-3 text-sm font-medium text-rose-600 dark:text-rose-200">
              {formError}
            </div>
          )}
          <div className="pt-2">
            <button type="submit" disabled={saving || permissionsLoading} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2">
              {saving && <Loader2 size={16} className="animate-spin" />}
              {editingId ? 'Save Changes' : saving ? 'Adding Employee...' : 'Add Employee'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
