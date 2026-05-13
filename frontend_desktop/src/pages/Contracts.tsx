import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import api from '../api/axios';
import { Search, Plus, Download, FileText, Edit3, Trash2, CheckCircle2, Clock, XCircle, Loader2 } from 'lucide-react';

interface Contract {
  id: number;
  contractNumber: string;
  clientName: string;
  vehicleMarque: string;
  startDate: string;
  endDate: string;
  status: string;
  totalAmount: number;
}

export default function Contracts() {
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ contractNumber: '', clientName: '', vehicleMarque: '', startDate: '', endDate: '', status: 'ACTIVE', totalAmount: '' });

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    const fetchContracts = async () => {
      try {
        const { data } = await api.get('/contracts');
        setData(data);
      } catch (err) {
        console.error('Failed to fetch contracts', err);
      } finally {
        setLoading(false);
      }
    };
    fetchContracts();
  }, []);

  const tabs = [
    { key: 'All', label: t('contracts.all') },
    { key: 'ACTIVE', label: t('contracts.active') },
    { key: 'PENDING', label: t('contracts.pending') },
    { key: 'COMPLETED', label: t('contracts.completed') },
    { key: 'CANCELLED', label: t('contracts.cancelled') },
  ];

  const filteredData = data.filter((c) => {
    const matchesTab = activeTab === 'All' || c.status === activeTab;
    const q = searchQuery.toLowerCase();
    return matchesTab && (c.clientName?.toLowerCase().includes(q) || c.vehicleMarque?.toLowerCase().includes(q) || c.contractNumber?.toLowerCase().includes(q));
  });

  const exportCSV = () => {
    const headers = ['Contract ID', 'Client', 'Vehicle', 'Start', 'End', 'Status', 'Total'];
    const rows = filteredData.map((c) => [c.contractNumber, c.clientName, c.vehicleMarque, c.startDate, c.endDate, c.status, c.totalAmount]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'contracts.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const openCreate = () => {
    setEditingId(null);
    setForm({ contractNumber: '', clientName: '', vehicleMarque: '', startDate: '', endDate: '', status: 'ACTIVE', totalAmount: '' });
    setIsModalOpen(true);
  };

  const openEdit = (contract: Contract) => {
    setEditingId(contract.id);
    setForm({ contractNumber: contract.contractNumber, clientName: contract.clientName, vehicleMarque: contract.vehicleMarque, startDate: contract.startDate, endDate: contract.endDate, status: contract.status, totalAmount: String(contract.totalAmount) });
    setIsModalOpen(true);
  };

  const saveContract = async () => {
    if (!form.contractNumber || !form.clientName || !form.vehicleMarque || !form.startDate || !form.endDate || !form.totalAmount) {
      showToast('Please fill all fields');
      return;
    }
    try {
      const payload = { ...form, totalAmount: Number(form.totalAmount) };
      if (editingId !== null) {
        await api.put(`/contracts/${editingId}`, payload);
        setData((prev) => prev.map((c) => (c.id === editingId ? { ...c, ...payload } : c)));
        showToast(t('toast.success', { action: 'Contract updated' }));
      } else {
        const { data: newContract } = await api.post('/contracts', payload);
        setData((prev) => [...prev, newContract]);
        showToast(t('toast.success', { action: 'Contract created' }));
      }
      setIsModalOpen(false);
    } catch (err) {
      showToast('Failed to save contract');
    }
  };

  const deleteContract = async (id: number) => {
    if (confirm(t('contracts.deleteConfirm') || 'Delete this contract?')) {
      try {
        await api.delete(`/contracts/${id}`);
        setData((prev) => prev.filter((c) => c.id !== id));
        showToast(t('contracts.delete'));
      } catch (err) {
        showToast('Failed to delete contract');
      }
    }
  };

  return (
    <div className="space-y-5 animate-fade">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('contracts.title')}</h1>
          <p className="text-slate-500 font-normal text-sm mt-0.5">{t('contracts.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={exportCSV} className="flex items-center gap-2 px-5 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all">
            <Download size={18} /> {t('contracts.export')}
          </button>
          <button onClick={openCreate} className="flex items-center gap-2 px-5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
            <Plus size={18} /> {t('contracts.newContract')}
          </button>
        </div>
      </div>

      <div className="card-premium flex flex-col md:flex-row md:items-center gap-3 p-3">
        <div className="flex p-1 bg-[#f5f5f0] rounded-xl">
          {tabs.map((tab) => (
            <button key={tab.key} onClick={() => { setActiveTab(tab.key); showToast(t('toast.filterApplied', { action: tab.label })); }}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-all active:scale-95 ${activeTab === tab.key ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>
              {tab.label}
            </button>
          ))}
        </div>
        <div className="flex-1 relative group">
          <Search size={17} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
          <input type="text" placeholder={t('contracts.searchPlaceholder')} value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-11 pr-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <div className="card-premium overflow-hidden p-0">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-5 py-4">{t('contracts.contractId')}</th>
                  <th className="px-5 py-4">{t('contracts.client')}</th>
                  <th className="px-5 py-4">{t('contracts.vehicle')}</th>
                  <th className="px-5 py-4">{t('contracts.period')}</th>
                  <th className="px-5 py-4">{t('contracts.status')}</th>
                  <th className="px-5 py-4">{t('contracts.total')}</th>
                  <th className="px-5 py-4 text-right">{t('contracts.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/50">
                {filteredData.map((contract) => (
                  <tr key={contract.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <FileText size={14} className="text-slate-400 group-hover:text-brand-500 transition-colors" />
                        <span className="font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">{contract.contractNumber}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 font-medium text-[#1e293b]">{contract.clientName}</td>
                    <td className="px-5 py-4 text-sm text-slate-500 font-normal">{contract.vehicleMarque}</td>
                    <td className="px-5 py-4 text-sm text-slate-400 font-normal">{new Date(contract.startDate).toLocaleDateString()} - {new Date(contract.endDate).toLocaleDateString()}</td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5 w-fit ${
                        contract.status === 'ACTIVE' ? 'bg-success-50 text-success-500' : 
                        contract.status === 'PENDING' ? 'bg-warning-50 text-warning-500' : 
                        contract.status === 'COMPLETED' ? 'bg-brand-50 text-brand-500' : 
                        'bg-danger-50 text-danger-500'
                      }`}>
                        {contract.status === 'ACTIVE' ? <CheckCircle2 size={12} /> : 
                         contract.status === 'PENDING' ? <Clock size={12} /> : 
                         contract.status === 'COMPLETED' ? <CheckCircle2 size={12} /> :
                         <XCircle size={12} />}
                        {contract.status}
                      </span>
                    </td>
                    <td className="px-5 py-4 font-bold text-[#1e293b]">{contract.totalAmount} DH</td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => openEdit(contract)} className="p-2 text-slate-400 hover:text-warning-500 hover:bg-warning-50 rounded-lg transition-all"><Edit3 size={17} /></button>
                        <button onClick={() => deleteContract(contract.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredData.length === 0 && (
                  <tr><td colSpan={7} className="px-5 py-8 text-center text-slate-400 text-sm">No contracts found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('contracts.edit') : t('contracts.newContract')}>
        <div className="space-y-4">
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Contract Number *</label><input type="text" value={form.contractNumber} onChange={(e) => setForm({ ...form, contractNumber: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('contracts.client')} *</label><input type="text" value={form.clientName} onChange={(e) => setForm({ ...form, clientName: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('contracts.vehicle')} *</label><input type="text" value={form.vehicleMarque} onChange={(e) => setForm({ ...form, vehicleMarque: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Start Date *</label><input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">End Date *</label><input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('contracts.status')}</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="ACTIVE">{t('contracts.active')}</option><option value="PENDING">{t('contracts.pending')}</option><option value="COMPLETED">{t('contracts.completed')}</option><option value="CANCELLED">{t('contracts.cancelled')}</option>
              </select>
            </div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('contracts.total')} (DH) *</label><input type="number" value={form.totalAmount} onChange={(e) => setForm({ ...form, totalAmount: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          </div>
          <div className="pt-2"><button onClick={saveContract} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">{editingId ? 'Save Changes' : t('contracts.newContract')}</button></div>
        </div>
      </Modal>
    </div>
  );
}
