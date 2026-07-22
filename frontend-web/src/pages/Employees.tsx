import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { usePermissions } from '../context/PermissionContext';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import api from '../api/axios';
import { isPasswordStrong } from '../lib/passwordPolicy';
import { Plus, Download, Mail, Phone, Trash2, Edit3, Loader2, KeyRound, Users } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';
import ActionMenu from '../components/shared/ActionMenu';

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
  { value: 'MANAGER' },
  { value: 'AGENT' },
  { value: 'ACCOUNTANT' },
  { value: 'FLEET_MANAGER' },
  { value: 'RECEPTIONIST' },
  { value: 'DRIVER' },
  { value: 'VIEWER' },
  { value: 'EMPLOYEE' },
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
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { showToast } = useToast();
  const { t } = useTranslation();
  const { user } = useAuth();
  const { hasAnyPermission, loading: permissionsLoading } = usePermissions();
  const roleLabel = (role?: string) => role ? t(`employees.roles.${role}`, { defaultValue: role }) : t('common.notAvailable', 'N/A');
  const statusLabel = (status?: string) => status ? t(`employees.statuses.${status}`, { defaultValue: status }) : t('common.notAvailable', 'N/A');

  const fetchEmployees = async () => {
    try {
      setLoading(true);
      const { data: response } = await api.get('/employees');
      const employees = Array.isArray(response) ? response : response?.data || [];
      setData(employees);
    } catch (err: any) {
      showToast(err?.userMessage || t('employees.errors.loadFailed'), 'error');
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
    const headers = [t('employees.name'), t('employees.email'), t('employees.phone'), t('employees.role'), t('employees.department'), t('employees.status')];
    const rows = filteredEmployees.map((e) => [e.name, e.email, e.phone, roleLabel(e.role), e.department, statusLabel(e.status)]);
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
    setFieldErrors({});
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
    setFieldErrors({});
    setIsModalOpen(true);
  };

  const canCreateOrManageEmployees = () => hasAnyPermission(['EMPLOYEE_CREATE', 'MANAGE_EMPLOYEES']);

  const updateFormField = (field: keyof ReturnType<typeof emptyForm>, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const validateEmployeeForm = () => {
    const errors: Record<string, string> = {};
    if (!form.fullName.trim()) errors.fullName = t('employees.errors.fullNameRequired');
    if (!form.email.trim()) errors.email = t('employees.errors.emailRequired');
    if (editingId === null && !form.temporaryPassword) {
      errors.temporaryPassword = t('employees.errors.temporaryPasswordRequired');
    } else if (editingId === null && !isPasswordStrong(form.temporaryPassword)) {
      errors.temporaryPassword = t('employees.errors.strongPassword');
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const saveEmployee = async () => {
    if (saving) return;
    setFormError('');

    if (!validateEmployeeForm()) {
      showToast(t('employees.errors.completeRequired'), 'warning');
      return;
    }
    if (permissionsLoading) {
      showToast(t('employees.errors.permissionsLoading'), 'warning');
      return;
    }
    if (!canCreateOrManageEmployees()) {
      showToast(t('employees.errors.noPermission'), 'error');
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
        showToast(t('toast.success', { action: t('employees.actionLabels.update') }));
      } else {
        const { data: createdResponse } = await api.post('/employees', {
          ...safePayload,
          temporaryPassword: form.temporaryPassword,
        });
        showToast(createdResponse?.message || t('employees.messages.created'), 'success');
      }
      setIsModalOpen(false);
      setEditingId(null);
      setForm(emptyForm());
      setFieldErrors({});
      await fetchEmployees();
    } catch (err: any) {
      const status = err?.response?.status;
      const errorCode = err?.errorCode || err?.response?.data?.errorCode;
      const message = err?.userMessage || err?.response?.data?.message || t('employees.errors.saveFailed');
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

  const inputClass = (field: string) =>
    `w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)] ${
      fieldErrors[field] ? 'border-danger-500 ring-2 ring-danger-500/20' : ''
    }`;

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

  const resetPassword = async (emp: Employee) => {
    if (!emp.userId) {
      showToast(t('employees.errors.noLoginAccount'), 'warning');
      return;
    }
    const newPassword = window.prompt(t('employees.resetPasswordPrompt', { name: emp.name }));
    if (!newPassword || !isPasswordStrong(newPassword)) return;
    try {
      await api.put(`/users/${emp.userId}/admin-reset-password`, { newPassword });
      showToast(t('employees.messages.passwordReset'), 'success');
    } catch (err: any) {
      showToast(err?.userMessage || t('employees.errors.resetPasswordFailed'), 'error');
    }
  };

  const deleteEmployee = async (id: number) => {
    if (confirm(t('employees.deleteConfirm'))) {
      try {
        await api.delete(`/employees/${id}`);
        setData((prev) => prev.filter((e) => e.id !== id));
        showToast(t('toast.success', { action: t('employees.actionLabels.delete') }));
      } catch (err: any) {
        showToast(err?.userMessage || t('employees.errors.deleteFailed'), 'error');
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
            <Download size={18} /> {t('employees.export')}
          </button>
          <button type="button" onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
            <Plus size={18} /> {t('employees.newEmployee')}
          </button>
        </>}
      />

      <div className="flex justify-end">
        <SearchInput className="w-full sm:w-[380px]" placeholder={t('employees.searchPlaceholder')} value={searchQuery} onChange={setSearchQuery} />
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <ResponsiveDataView
          mobile={
            filteredEmployees.length === 0 ? (
              <div className="data-surface py-10 text-center text-sm text-[var(--text-muted)]">{t('employees.noEmployees')}</div>
            ) : (
              <div className="space-y-3">
                {filteredEmployees.map((emp) => (
                  <div key={emp.id} className="data-surface space-y-3 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-500/10 text-sm font-bold text-brand-500">
                          {emp.name?.split(' ').map((n) => n[0]).join('').slice(0, 2) || '?'}
                        </div>
                        <div className="min-w-0">
                          <h3 className="truncate text-sm font-semibold text-[var(--text-primary)]">{emp.name}</h3>
                          <p className="truncate text-xs text-[var(--text-muted)]">{roleLabel(emp.role)}{emp.department ? ` · ${emp.department}` : ''}</p>
                        </div>
                      </div>
                      <span className={`shrink-0 rounded-lg px-2 py-1 text-[10px] font-bold uppercase ${emp.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 'bg-slate-500/10 text-[var(--text-muted)]'}`}>
                        {statusLabel(emp.status)}
                      </span>
                    </div>
                    <div className="space-y-1 border-t border-[var(--border-subtle)] pt-3 text-xs text-[var(--text-muted)]">
                      <div className="flex items-center gap-1.5 truncate"><Mail size={12} className="shrink-0" />{emp.email}</div>
                      <div className="flex items-center gap-1.5"><Phone size={12} className="shrink-0" />{emp.phone || t('common.notAvailable', 'N/A')}</div>
                    </div>
                    <div className="flex items-center gap-2 border-t border-[var(--border-subtle)] pt-3">
                      <button
                        type="button"
                        onClick={() => openEdit(emp)}
                        className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-brand-50 text-sm font-semibold text-brand-600"
                      >
                        <Edit3 size={15} /> {t('common.edit')}
                      </button>
                      <ActionMenu
                        ariaLabel={t('employees.actions')}
                        items={[
                          { label: t('employees.resetPassword'), icon: <KeyRound size={15} />, onClick: () => resetPassword(emp) },
                          { label: t('employees.deleteEmployee'), icon: <Trash2 size={15} />, onClick: () => deleteEmployee(emp.id), danger: true },
                        ]}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )
          }
          desktop={
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-[var(--bg-hover)] text-[var(--text-muted)] text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">{t('employees.employee')}</th>
                  <th className="px-5 py-4">{t('employees.contact')}</th>
                  <th className="px-5 py-4">{t('employees.role')}</th>
                  <th className="px-5 py-4">{t('employees.department')}</th>
                  <th className="px-5 py-4">{t('employees.status')}</th>
                  <th className="px-5 py-4 text-right">{t('employees.actions')}</th>
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
                      <div className="text-xs text-[var(--text-muted)]"><Mail size={12} className="inline me-1" />{emp.email}</div>
                      <div className="text-xs text-[var(--text-muted)] mt-0.5"><Phone size={12} className="inline me-1" />{emp.phone || t('common.notAvailable', 'N/A')}</div>
                    </td>
                    <td className="px-5 py-4 text-sm text-[var(--text-primary)]">{roleLabel(emp.role)}</td>
                    <td className="px-5 py-4 text-sm text-[var(--text-muted)]">{emp.department || t('common.notAvailable', 'N/A')}</td>
                    <td className="px-5 py-4">
                      <span className={`px-2 py-1 rounded-lg text-[10px] font-bold uppercase ${emp.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 'bg-slate-500/10 text-[var(--text-muted)]'}`}>
                        {statusLabel(emp.status)}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button type="button" onClick={() => openEdit(emp)} className="p-2 text-[var(--text-muted)] hover:text-brand-500 hover:bg-brand-500/10 rounded-lg transition-all" aria-label={t('employees.editEmployee')}><Edit3 size={17} /></button>
                        <button type="button" title={t('employees.resetPassword')} onClick={() => resetPassword(emp)} className="p-2 text-[var(--text-muted)] hover:text-amber-600 hover:bg-amber-500/10 rounded-lg transition-all"><KeyRound size={17} /></button>
                        <button type="button" onClick={() => deleteEmployee(emp.id)} className="p-2 text-[var(--text-muted)] hover:text-danger-500 hover:bg-danger-500/10 rounded-lg transition-all" aria-label={t('employees.deleteEmployee')}><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredEmployees.length === 0 && (
                  <tr><td colSpan={6} className="px-5 py-8 text-center text-[var(--text-muted)] text-sm">{t('employees.noEmployees')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
          }
        />
      )}

      <Modal isOpen={isModalOpen} onClose={() => { if (!saving) setIsModalOpen(false); }} title={editingId ? t('employees.editEmployee') : t('employees.newEmployee')}>
        <form onSubmit={(e) => { e.preventDefault(); void saveEmployee(); }} className="space-y-4">
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.fullName')} *</label><input type="text" value={form.fullName} onChange={(e) => updateFormField('fullName', e.target.value)} aria-invalid={Boolean(fieldErrors.fullName)} className={inputClass('fullName')} />{fieldError('fullName')}</div>
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.email')} *</label><input type="email" value={form.email} onChange={(e) => updateFormField('email', e.target.value)} aria-invalid={Boolean(fieldErrors.email)} className={inputClass('email')} />{fieldError('email')}</div>
          <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.phone')}</label><input type="tel" value={form.phone} onChange={(e) => updateFormField('phone', e.target.value)} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.role')}</label><select value={form.roleCode} onChange={(e) => updateFormField('roleCode', e.target.value)} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]">{ROLE_OPTIONS.map(o => <option key={o.value} value={o.value}>{roleLabel(o.value)}</option>)}</select></div>
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.department')}</label><input type="text" value={form.department} onChange={(e) => updateFormField('department', e.target.value)} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
          </div>
          {editingId === null && <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.temporaryPassword')} *</label><input type="password" value={form.temporaryPassword} onChange={(e) => updateFormField('temporaryPassword', e.target.value)} aria-invalid={Boolean(fieldErrors.temporaryPassword)} placeholder={t('employees.temporaryPasswordPlaceholder')} className={inputClass('temporaryPassword')} />{fieldError('temporaryPassword')}</div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.hireDate')}</label><input type="date" value={form.hireDate} onChange={(e) => setForm({ ...form, hireDate: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]" /></div>
            <div><label className="block text-sm font-medium text-[var(--text-primary)] mb-2">{t('employees.status')}</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 glass-input text-sm text-[var(--text-primary)]">
                <option value="ACTIVE">{t('employees.statuses.ACTIVE')}</option><option value="INACTIVE">{t('employees.statuses.INACTIVE')}</option>
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
              {editingId ? t('employees.saveChanges') : saving ? t('employees.addingEmployee') : t('employees.newEmployee')}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
