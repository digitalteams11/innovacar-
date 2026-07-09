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
      showToast('Complete required fields and use a strong temporary password.', 'warning');
      return;
    }
    try {
      if (editingId !== null) {
        await api.put(`/employees/${editingId}`, form);
        setData((prev) => prev.map((e) => (e.id === editingId ? { ...e, ...form } : e)));
        showToast(t('toast.success', { action: 'Update' }));
      } else {
        const { data: newEmp } = await api.post('/employees', form);
        setData((prev) => [...prev, newEmp]);
        showToast(t('toast.success', { action: 'Employee created' }));
      }
      setIsModalOpen(false);
    } catch (err) {
      showToast((err as any).userMessage || 'Unable to save employee. Please try again later.', 'error');
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
    } catch {
      showToast('Unable to reset password. Please try again later.', 'error');
    }
  };

  const deleteEmployee = async (id: number) => {
    if (confirm('Delete this employee?')) {
      try {
        await api.delete(`/employees/${id}`);
        setData((prev) => prev.filter((e) => e.id !== id));
        showToast(t('toast.success', { action: 'Delete' }));
      } catch (err) {
        showToast('Unable to delete employee. Please try again later.', 'error');
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
            <Download size={18} /> Export
          </button>
          <button onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-sm active:scale-95">
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
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">Employee</th>
                  <th className="px-5 py-4">Contact</th>
                  <th className="px-5 py-4">Role</th>
                  <th className="px-5 py-4">Department</th>
                  <th className="px-5 py-4">Status</th>
                  <th className="px-5 py-4 text-right">Actions</th>
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
                      <div className="text-xs text-slate-500 mt-0.5"><Phone size={12} className="inline mr-1" />{emp.phone || 'N/A'}</div>
                    </td>
                    <td className="px-5 py-4 text-sm text-[#1e293b]">{emp.role || 'N/A'}</td>
                    <td className="px-5 py-4 text-sm text-slate-500">{emp.department || 'N/A'}</td>
                    <td className="px-5 py-4">
                      <span className={`px-2 py-1 rounded-lg text-[10px] font-bold uppercase ${emp.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 'bg-slate-50 text-slate-500'}`}>
                        {emp.status}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => openEdit(emp)} className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"><Edit3 size={17} /></button>
                        <button title="Reset login password" onClick={() => resetPassword(emp)} className="p-2 text-slate-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-all"><KeyRound size={17} /></button>
                        <button onClick={() => deleteEmployee(emp.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredEmployees.length === 0 && (
                  <tr><td colSpan={6} className="px-5 py-8 text-center text-slate-400 text-sm">No employees found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? 'Edit Employee' : 'Add Employee'}>
        <div className="space-y-4">
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Full Name *</label><input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Email *</label><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Phone</label><input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Role</label><select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"><option value="ADMIN">Administrator</option><option value="MANAGER">Manager</option><option value="EMPLOYEE">Employee</option><option value="ACCOUNTANT">Accountant</option><option value="RECEPTIONIST">Receptionist</option><option value="VIEWER">Viewer</option><option value="AGENT">Agent</option></select></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Department</label><input type="text" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          </div>
          {editingId === null && <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Temporary Password *</label><input type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Hire Date</label><input type="date" value={form.hireDate} onChange={(e) => setForm({ ...form, hireDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Status</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
              </select>
            </div>
          </div>
          <div className="pt-2"><button onClick={saveEmployee} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">{editingId ? 'Save Changes' : 'Add Employee'}</button></div>
        </div>
      </Modal>
    </div>
  );
}
