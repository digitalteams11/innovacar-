import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import ClientProfileModal from '../components/ClientProfileModal';
import api from '../api/axios';
import { Search, Plus, Mail, Phone, MapPin, MoreVertical, Loader2, Shield, Building2 } from 'lucide-react';

interface Client {
  id: number;
  name: string;
  email: string;
  phone: string;
  secondaryPhone: string;
  address: string;
  city: string;
  country: string;
  postalCode: string;
  nationality: string;
  gender: string;
  birthDate: string;
  cin: string;
  passportNumber: string;
  drivingLicense: string;
  companyName: string;
  notes: string;
}

export default function Clients() {
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [selectedClient, setSelectedClient] = useState<Client | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);

  const [form, setForm] = useState({
    name: '', email: '', phone: '', secondaryPhone: '', address: '', city: '',
    country: '', postalCode: '', nationality: '', gender: '', birthDate: '',
    cin: '', passportNumber: '', drivingLicense: '', companyName: '', notes: ''
  });

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => {
    fetchClients();
  }, []);

  const fetchClients = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/clients');
      setData(data);
    } catch (err) {
      console.error('Failed to fetch clients', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredClients = data.filter((c) => {
    const q = searchQuery.toLowerCase();
    return c.name?.toLowerCase().includes(q) ||
      c.email?.toLowerCase().includes(q) ||
      c.phone?.includes(q) ||
      c.cin?.includes(q) ||
      c.companyName?.toLowerCase().includes(q);
  });

  const openCreate = () => {
    setEditingId(null);
    setForm({
      name: '', email: '', phone: '', secondaryPhone: '', address: '', city: '',
      country: '', postalCode: '', nationality: '', gender: '', birthDate: '',
      cin: '', passportNumber: '', drivingLicense: '', companyName: '', notes: ''
    });
    setIsModalOpen(true);
  };

  const openEdit = (client: Client) => {
    setEditingId(client.id);
    setForm({
      name: client.name || '', email: client.email || '', phone: client.phone || '',
      secondaryPhone: client.secondaryPhone || '', address: client.address || '',
      city: client.city || '', country: client.country || '', postalCode: client.postalCode || '',
      nationality: client.nationality || '', gender: client.gender || '',
      birthDate: client.birthDate || '', cin: client.cin || '',
      passportNumber: client.passportNumber || '', drivingLicense: client.drivingLicense || '',
      companyName: client.companyName || '', notes: client.notes || ''
    });
    setIsModalOpen(true);
  };

  const saveClient = async () => {
    if (!form.name || !form.phone || !form.cin) {
      showToast('Please fill required fields: Name, Phone, CIN/Passport');
      return;
    }
    try {
      const payload = { ...form, email: form.email || null };
      if (editingId !== null) {
        await api.put(`/clients/${editingId}`, payload);
        showToast(t('toast.success', { action: 'Update' }));
      } else {
        await api.post('/clients', payload);
        showToast(t('toast.newClientAdded'));
      }
      setIsModalOpen(false);
      fetchClients();
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to save client');
    }
  };

  const deleteClient = async (id: number) => {
    if (confirm('Delete this client?')) {
      try {
        await api.delete(`/clients/${id}`);
        fetchClients();
        showToast(t('toast.success', { action: 'Delete' }));
      } catch (err) {
        showToast('Failed to delete client');
      }
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="flex-1 min-w-0">
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('clients.title')}</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('clients.subtitle')}</p>
        </div>
        <button onClick={openCreate}
          className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all w-fit">
          <Plus size={16} className="sm:hidden" />
          <Plus size={18} className="hidden sm:block" />
          {t('clients.newClient')}
        </button>
      </div>

      <div className="card-premium flex flex-col gap-3">
        <div className="flex-1 relative group">
          <Search size={16} className="absolute left-3.5 sm:left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
          <input type="text" placeholder={t('clients.searchPlaceholder')} value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 sm:pl-11 pr-4 py-2 sm:py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
          {filteredClients.map((client) => (
            <div key={client.id} className="card-premium hover:shadow-elevated transition-all duration-300 group p-3 sm:p-5">
              <div className="flex justify-between items-start mb-4">
                <div className="flex items-center gap-3.5">
                  <div className="w-12 h-12 bg-brand-50 rounded-xl flex items-center justify-center text-brand-500 text-base font-bold group-hover:bg-brand-500 group-hover:text-white transition-all duration-300">
                    {client.name?.split(' ').map((n) => n[0]).join('')}
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-semibold text-[#1e293b] group-hover:text-brand-500 transition-colors">{client.name}</h3>
                    {client.companyName && (
                      <p className="text-[11px] text-slate-400 flex items-center gap-1"><Building2 size={10} /> {client.companyName}</p>
                    )}
                    <p className="text-[11px] text-slate-400 mt-0.5">{client.cin || client.passportNumber || 'Identity not provided'}</p>
                  </div>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => openEdit(client)} className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all">
                    <MoreVertical size={18} />
                  </button>
                  <button onClick={() => deleteClient(client.id)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                  </button>
                </div>
              </div>

              <div className="space-y-2 mb-4">
                <div className="flex items-center gap-3 text-slate-500 text-sm font-normal hover:text-brand-500 transition-colors">
                  <Phone size={14} />
                  {client.phone || 'N/A'}
                </div>
                {client.email && (
                  <div className="flex items-center gap-3 text-slate-500 text-sm font-normal hover:text-brand-500 transition-colors">
                    <Mail size={14} />
                    {client.email}
                  </div>
                )}
                <div className="flex items-center gap-3 text-slate-500 text-sm font-normal hover:text-brand-500 transition-colors">
                  <Shield size={14} />
                  {client.cin || client.passportNumber || 'N/A'}
                </div>
                <div className="flex items-center gap-3 text-slate-500 text-sm font-normal hover:text-brand-500 transition-colors">
                  <MapPin size={14} />
                  {client.city || client.address || 'N/A'}
                </div>
              </div>

              <div className="pt-4 border-t border-[#e8e6e1]/60 flex gap-3">
                <button onClick={() => { setSelectedClient(client); setIsProfileModalOpen(true); }}
                  className="flex-1 py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl font-semibold text-xs hover:bg-[#ebe9e4] active:scale-95 transition-all">
                  {t('clients.viewProfile')}
                </button>
              </div>
            </div>
          ))}
          {filteredClients.length === 0 && (
            <div className="col-span-full card-premium py-12 text-center text-slate-400 text-sm">No clients found</div>
          )}
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? 'Edit Client' : t('clients.newClient')} maxWidth="2xl">
        <div className="space-y-4 pr-1">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Full Name <span className="text-danger-500">*</span></label>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Email</label>
              <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Phone <span className="text-danger-500">*</span></label>
              <input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Secondary Phone</label>
              <input type="tel" value={form.secondaryPhone} onChange={(e) => setForm({ ...form, secondaryPhone: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">CIN / Passport <span className="text-danger-500">*</span></label>
              <input type="text" value={form.cin} onChange={(e) => setForm({ ...form, cin: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Passport Number</label>
              <input type="text" value={form.passportNumber} onChange={(e) => setForm({ ...form, passportNumber: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Driving License <span className="text-danger-500">*</span></label>
              <input type="text" value={form.drivingLicense} onChange={(e) => setForm({ ...form, drivingLicense: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Nationality <span className="text-danger-500">*</span></label>
              <input type="text" value={form.nationality} onChange={(e) => setForm({ ...form, nationality: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Gender</label>
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="">Select</option>
                <option value="Male">Male</option>
                <option value="Female">Female</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Birth Date</label>
              <input type="date" value={form.birthDate} onChange={(e) => setForm({ ...form, birthDate: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-slate-500 mb-1">Address <span className="text-danger-500">*</span></label>
              <input type="text" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">City</label>
              <input type="text" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Country</label>
              <input type="text" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Postal Code</label>
              <input type="text" value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Company Name</label>
              <input type="text" value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-slate-500 mb-1">Notes</label>
              <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} rows={2}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm resize-none focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="pt-2">
            <button onClick={saveClient}
              className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
              {editingId ? 'Save Changes' : t('clients.newClient')}
            </button>
          </div>
        </div>
      </Modal>

      <ClientProfileModal isOpen={isProfileModalOpen} onClose={() => setIsProfileModalOpen(false)} client={selectedClient as any} />
    </div>
  );
}
