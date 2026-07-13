import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
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

export default function Employees() {
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ name: '', email: '', phone: '', role: 'EMPLOYEE', department: '', hireDate: '', status: 'ACTIVE', password: '' });

  const { showToast } = useToast();
  const { t } = useTranslation();
  const roleLabel = (role?: string) => role ? t(`employees.roles.${role}`, { defaultValue: role }) : t('common.notAvailable', 'N/A');
  const statusLabel = (status?: string) => status ? t(`employees.statuses.${status}`, { defaultValue: status }) : t('common.notAvailable', 'N/A');

  useEffect(() => {
    const fetchEmployees = async () => {
      try {
        const { data } = await api.get('/employees');
        setData(data);
      } catch (err) {
        console.error('Failed to fetch employees', err);
      } finally {
        setLoading(false);
      }
    };
    fetchEmployees();
  }, []);

  const filteredEmployees = data.filter((e) => {
    const q = searchQuery.toLowerCase();
    return e.name?.toLowerCase().includes(q) || e.email?.toLowerCase().includes(q) || e.role?.toLowerCase().includes(q);
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
    setForm({ name: '', email: '', phone: '', role: 'EMPLOYEE', department: '', hireDate: new Date().toISOString().split('T')[0], status: 'ACTIVE', password: '' });
    setIsModalOpen(true);
  };

  const openEdit = (emp: Employee) => {
    setEditingId(emp.id);
    setForm({ name: emp.name, email: emp.email, phone: emp.phone || '', role: emp.role || 'EMPLOYEE', department: emp.department || '', hireDate: emp.hireDate || '', status: emp.status, password: '' });
    setIsModalOpen(true);
  };

  const saveEmployee = async () => {
    if (!form.name || !form.email || (editingId === null && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(form.password))) {
      showToast(t('employees.errors.completeRequired'), 'warning');
      return;
    }
    try {
      if (editingId !== null) {
        await api.put(`/employees/${editingId}`, form);
        setData((prev) => prev.map((e) => (e.id === editingId ? { ...e, ...form } : e)));
        showToast(t('toast.success', { action: t('employees.actionLabels.update') }));
      } else {
        const { data: newEmp } = await api.post('/employees', form);
        setData((prev) => [...prev, newEmp]);
        showToast(t('toast.success', { action: t('employees.messages.created') }));
      }
      setIsModalOpen(false);
    } catch (err) {
      showToast((err as any).userMessage || t('employees.errors.saveFailed'), 'error');
    }
  };

  const resetPassword = async (emp: Employee) => {
    if (!emp.userId) {
      showToast(t('employees.errors.noLoginAccount'), 'warning');
      return;
    }
    const newPassword = window.prompt(t('employees.resetPasswordPrompt', { name: emp.name }));
    if (!newPassword || !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(newPassword)) return;
    try {
      await api.put(`/users/${emp.userId}/admin-reset-password`, { newPassword });
      showToast(t('employees.messages.passwordReset'), 'success');
    } catch {
      showToast(t('employees.errors.resetPasswordFailed'), 'error');
    }
  };

  const deleteEmployee = async (id: number) => {
    if (confirm(t('employees.deleteConfirm'))) {
      try {
        await api.delete(`/employees/${id}`);
        setData((prev) => prev.filter((e) => e.id !== id));
        showToast(t('toast.success', { action: t('employees.actionLabels.delete') }));
      } catch (err) {
        showToast(t('employees.errors.deleteFailed'), 'error');
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
          <button onClick={exportCSV} className="surface-control flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
            <Download size={18} /> {t('employees.export')}
          </button>
          <button onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
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
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">{t('employees.employee')}</th>
                  <th className="px-5 py-4">{t('employees.contact')}</th>
                  <th className="px-5 py-4">{t('employees.role')}</th>
                  <th className="px-5 py-4">{t('employees.department')}</th>
                  <th className="px-5 py-4">{t('employees.status')}</th>
                  <th className="px-5 py-4 text-right">{t('employees.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/50">
                {filteredEmployees.map((emp) => (
                  <tr key={emp.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 bg-brand-50 rounded-lg flex items-center justify-center text-brand-500 font-bold text-sm">
                          {emp.name?.split(' ').map((n) => n[0]).join('')}
                        </div>
                        <span className="text-sm font-medium text-[#1e293b]">{emp.name}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-xs text-slate-500"><Mail size={12} className="inline mr-1" />{emp.email}</div>
                      <div className="text-xs text-slate-500 mt-0.5"><Phone size={12} className="inline mr-1" />{emp.phone || t('common.notAvailable', 'N/A')}</div>
                    </td>
                    <td className="px-5 py-4 text-sm text-[#1e293b]">{roleLabel(emp.role)}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{emp.department || t('common.notAvailable', 'N/A')}</td>
                    <td className="px-5 py-4">
                      <span className={`px-2 py-1 rounded-lg text-[10px] font-bold uppercase ${emp.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 'bg-slate-50 text-slate-500'}`}>
                        {statusLabel(emp.status)}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => openEdit(emp)} className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"><Edit3 size={17} /></button>
                        <button title={t('employees.resetPassword')} onClick={() => resetPassword(emp)} className="p-2 text-slate-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-all"><KeyRound size={17} /></button>
                        <button onClick={() => deleteEmployee(emp.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredEmployees.length === 0 && (
                  <tr><td colSpan={6} className="px-5 py-8 text-center text-slate-400 text-sm">{t('employees.noEmployees')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('employees.editEmployee') : t('employees.newEmployee')}>
        <div className="space-y-4">
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.fullName')} *</label><input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.email')} *</label><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.phone')}</label><input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.role')}</label><select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"><option value="ADMIN">{roleLabel('ADMIN')}</option><option value="MANAGER">{roleLabel('MANAGER')}</option><option value="EMPLOYEE">{roleLabel('EMPLOYEE')}</option><option value="ACCOUNTANT">{roleLabel('ACCOUNTANT')}</option><option value="RECEPTIONIST">{roleLabel('RECEPTIONIST')}</option><option value="VIEWER">{roleLabel('VIEWER')}</option><option value="AGENT">{roleLabel('AGENT')}</option></select></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.department')}</label><input type="text" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          </div>
          {editingId === null && <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.temporaryPassword')} *</label><input type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder={t('employees.temporaryPasswordPlaceholder')} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.hireDate')}</label><input type="date" value={form.hireDate} onChange={(e) => setForm({ ...form, hireDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('employees.status')}</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="ACTIVE">{t('employees.statuses.ACTIVE')}</option><option value="INACTIVE">{t('employees.statuses.INACTIVE')}</option>
              </select>
            </div>
          </div>
          <div className="pt-2"><button onClick={saveEmployee} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">{editingId ? t('employees.saveChanges') : t('employees.newEmployee')}</button></div>
        </div>
      </Modal>
    </div>
  );
}
