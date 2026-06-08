import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import SmartClientSearch from '../components/shared/SmartClientSearch';
import api from '../api/axios';
import { Search, Plus, Download, FileText, Trash2, CheckCircle2, Clock, AlertCircle, Loader2 } from 'lucide-react';

interface Invoice {
  id: number;
  invoiceNumber: string;
  clientName: string;
  clientId: number;
  issueDate: string;
  dueDate: string;
  amount: number;
  status: string;
}

export default function Invoices() {
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [clientData, setClientData] = useState<any>({});
  const [form, setForm] = useState({ invoiceNumber: '', issueDate: new Date().toISOString().split('T')[0], dueDate: '', amount: '', status: 'PENDING' });

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => { fetchInvoices(); }, []);

  const fetchInvoices = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/invoices');
      setData(data);
    } catch (err) {
      console.error('Failed to fetch invoices', err);
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { key: 'All', label: t('invoices.all') },
    { key: 'PAID', label: t('invoices.paid') },
    { key: 'PENDING', label: t('invoices.pending') },
    { key: 'OVERDUE', label: t('invoices.overdue') },
  ];

  const filteredData = data.filter((i) => {
    const matchesTab = activeTab === 'All' || i.status === activeTab;
    const q = searchQuery.toLowerCase();
    return matchesTab && ((i.clientName || '').toLowerCase().includes(q) || i.invoiceNumber?.toLowerCase().includes(q));
  });

  const totalPaid = data.filter((i) => i.status === 'PAID').reduce((sum, i) => sum + i.amount, 0);
  const totalPending = data.filter((i) => i.status === 'PENDING').reduce((sum, i) => sum + i.amount, 0);
  const totalOverdue = data.filter((i) => i.status === 'OVERDUE').reduce((sum, i) => sum + i.amount, 0);

  const exportCSV = () => {
    const headers = ['Invoice ID', 'Client', 'Date', 'Due Date', 'Amount', 'Status'];
    const rows = filteredData.map((i) => [i.invoiceNumber, i.clientName, i.issueDate, i.dueDate, i.amount, i.status]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'invoices.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const openCreate = () => {
    setEditingId(null);
    setClientData({});
    setForm({ invoiceNumber: '', issueDate: new Date().toISOString().split('T')[0], dueDate: '', amount: '', status: 'PENDING' });
    setIsModalOpen(true);
  };

  const openEdit = (invoice: Invoice) => {
    setEditingId(invoice.id);
    setClientData({ clientId: invoice.clientId, clientFullName: invoice.clientName });
    setForm({ invoiceNumber: invoice.invoiceNumber, issueDate: invoice.issueDate, dueDate: invoice.dueDate, amount: String(invoice.amount), status: invoice.status });
    setIsModalOpen(true);
  };

  const saveInvoice = async () => {
    if (!form.invoiceNumber || !form.issueDate || !form.dueDate || !form.amount) {
      showToast('Please fill all required fields');
      return;
    }
    try {
      const payload: any = {
        invoiceNumber: form.invoiceNumber,
        issueDate: form.issueDate,
        dueDate: form.dueDate,
        amount: Number(form.amount),
        status: form.status,
      };
      if (clientData.clientId) {
        payload.clientId = clientData.clientId;
      }
      if (editingId !== null) {
        await api.put(`/invoices/${editingId}`, payload);
        showToast(t('toast.success', { action: 'Invoice updated' }));
      } else {
        await api.post('/invoices', payload);
        showToast(t('toast.success', { action: 'Invoice created' }));
      }
      setIsModalOpen(false);
      fetchInvoices();
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to save invoice');
    }
  };

  const deleteInvoice = async (id: number) => {
    if (confirm(t('invoices.deleteConfirm') || 'Delete this invoice?')) {
      try {
        await api.delete(`/invoices/${id}`);
        fetchInvoices();
        showToast(t('invoices.delete'));
      } catch (err) {
        showToast('Failed to delete invoice');
      }
    }
  };

  const markAsPaid = async (id: number) => {
    try {
      await api.patch(`/invoices/${id}/pay`);
      fetchInvoices();
      showToast(t('toast.success', { action: 'Invoice marked as paid' }));
    } catch (err) {
      showToast('Failed to mark invoice as paid');
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('invoices.title')}</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('invoices.subtitle')}</p>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-3">
          <button onClick={exportCSV} className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all">
            <Download size={18} /> {t('invoices.export')}
          </button>
          <button onClick={openCreate} className="flex items-center gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
            <Plus size={18} /> {t('invoices.newInvoice')}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer p-3 sm:p-5">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalPaid')}</p>
          <h3 className="text-xl font-bold text-success-500">{totalPaid.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'PAID').length} {t('invoices.invoices')}</p>
        </div>
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer p-3 sm:p-5">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalPending')}</p>
          <h3 className="text-xl font-bold text-warning-500">{totalPending.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'PENDING').length} {t('invoices.invoices')}</p>
        </div>
        <div className="card-premium hover:-translate-y-0.5 transition-all duration-300 cursor-pointer p-3 sm:p-5">
          <p className="text-slate-500 text-xs font-medium mb-1 tracking-wide">{t('invoices.totalOverdue')}</p>
          <h3 className="text-xl font-bold text-danger-500">{totalOverdue.toLocaleString()} MAD</h3>
          <p className="text-[10px] text-slate-400 mt-1">{data.filter((i) => i.status === 'OVERDUE').length} {t('invoices.invoices')}</p>
        </div>
      </div>

      <div className="card-premium flex flex-col sm:flex-row sm:items-center gap-3 p-3">
        <div className="flex gap-2 overflow-x-auto pb-1 no-scrollbar p-1 bg-[#f5f5f0] rounded-xl">
          {tabs.map((tab) => (
            <button key={tab.key} onClick={() => { setActiveTab(tab.key); }}
              className={`px-3 sm:px-4 py-1.5 sm:py-2 rounded-lg text-xs sm:text-sm font-medium transition-all active:scale-95 whitespace-nowrap flex-shrink-0 ${activeTab === tab.key ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>
              {tab.label}
            </button>
          ))}
        </div>
        <div className="flex-1 relative group">
          <Search size={17} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
          <input type="text" placeholder={t('invoices.searchPlaceholder')} value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
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
                  <th className="px-5 py-4">{t('invoices.invoiceId')}</th>
                  <th className="px-5 py-4">{t('invoices.client')}</th>
                  <th className="px-5 py-4">{t('invoices.date')}</th>
                  <th className="px-5 py-4">{t('invoices.dueDate')}</th>
                  <th className="px-5 py-4">{t('invoices.amount')}</th>
                  <th className="px-5 py-4">{t('invoices.status')}</th>
                  <th className="px-5 py-4 text-right">{t('invoices.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/50">
                {filteredData.map((invoice) => (
                  <tr key={invoice.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <FileText size={14} className="text-slate-400 group-hover:text-brand-500 transition-colors" />
                        <span className="font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">{invoice.invoiceNumber}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 font-medium text-[#1e293b]">{invoice.clientName}</td>
                    <td className="px-5 py-4 text-sm text-slate-400 font-normal">{new Date(invoice.issueDate).toLocaleDateString()}</td>
                    <td className="px-5 py-4 text-sm text-slate-400 font-normal">{new Date(invoice.dueDate).toLocaleDateString()}</td>
                    <td className="px-5 py-4 font-bold text-[#1e293b]">{invoice.amount.toLocaleString()} MAD</td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5 w-fit ${
                        invoice.status === 'PAID' ? 'bg-success-50 text-success-500' : invoice.status === 'PENDING' ? 'bg-warning-50 text-warning-500' : 'bg-danger-50 text-danger-500'
                      }`}>
                        {invoice.status === 'PAID' ? <CheckCircle2 size={12} /> : invoice.status === 'PENDING' ? <Clock size={12} /> : <AlertCircle size={12} />}
                        {invoice.status === 'PAID' ? t('invoices.paid') : invoice.status === 'PENDING' ? t('invoices.pending') : t('invoices.overdue')}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {invoice.status !== 'PAID' && (
                          <button onClick={() => markAsPaid(invoice.id)} className="px-3 py-1.5 bg-success-50 text-success-500 rounded-lg text-[10px] font-bold uppercase tracking-wider hover:bg-success-500 hover:text-white transition-all">Pay</button>
                        )}
                        <button onClick={() => openEdit(invoice)} className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all"><FileText size={17} /></button>
                        <button onClick={() => deleteInvoice(invoice.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all"><Trash2 size={17} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredData.length === 0 && (
                  <tr><td colSpan={7} className="px-5 py-8 text-center text-slate-400 text-sm">No invoices found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? 'Edit Invoice' : t('invoices.newInvoice')}>
        <div className="space-y-4">
          <SmartClientSearch value={clientData} onSelect={setClientData} required />
          {clientData.clientFullName && (
            <div className="p-3 bg-brand-50/50 rounded-xl border border-brand-100 text-sm">
              <span className="text-brand-500 font-medium">{clientData.clientFullName}</span>
            </div>
          )}
          <div><label className="block text-sm font-medium text-[#1e293b] mb-2">Invoice Number *</label><input type="text" value={form.invoiceNumber} onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.date')} *</label><input type="date" value={form.issueDate} onChange={(e) => setForm({ ...form, issueDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.dueDate')} *</label><input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.amount')} (MAD) *</label><input type="number" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" /></div>
            <div><label className="block text-sm font-medium text-[#1e293b] mb-2">{t('invoices.status')}</label>
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="PENDING">{t('invoices.pending')}</option><option value="PAID">{t('invoices.paid')}</option><option value="OVERDUE">{t('invoices.overdue')}</option>
              </select>
            </div>
          </div>
          <div className="pt-2"><button onClick={saveInvoice} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">{editingId ? 'Save Changes' : t('invoices.newInvoice')}</button></div>
        </div>
      </Modal>
    </div>
  );
}
