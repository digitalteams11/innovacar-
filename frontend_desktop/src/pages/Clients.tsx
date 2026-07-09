import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import ClientProfileModal from '../components/ClientProfileModal';
import { GlassCard } from '../components/GlassCard';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import { StatusBadge } from '../components/StatusBadge';
import api from '../api/axios';
import {
  Plus, Mail, Phone, MapPin, Loader2,
  Shield, Building2, Users, Pencil, Trash2, Eye, User
} from 'lucide-react';
import ApiErrorState from '../components/ApiErrorState';

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

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] as const },
  },
};

export default function Clients() {
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [selectedClient, setSelectedClient] = useState<Client | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [duplicateField, setDuplicateField] = useState<string | null>(null);

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
      setLoadError('');
      const response = await api.get('/clients');
      const clients = Array.isArray(response.data) ? response.data : response.data?.data;
      setData(Array.isArray(clients) ? clients : []);
    } catch (err: any) {
      console.error('Failed to fetch clients', err);
      setLoadError('Unable to load client information. Please try again later.');
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
    setDuplicateField(null);
    setForm({
      name: '', email: '', phone: '', secondaryPhone: '', address: '', city: '',
      country: '', postalCode: '', nationality: '', gender: '', birthDate: '',
      cin: '', passportNumber: '', drivingLicense: '', companyName: '', notes: ''
    });
    setIsModalOpen(true);
  };

  const openEdit = (client: Client) => {
    setEditingId(client.id);
    setDuplicateField(null);
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
    if (saving) return;
    if (!form.name.trim() || !form.phone.trim() || (!form.cin.trim() && !form.passportNumber.trim())
        || !form.address.trim() || !form.city.trim() || !form.country.trim() || !form.nationality.trim()) {
      showToast('Please fill required fields: Full Name, Phone, CIN or Passport, Address, City, Country, and Nationality.', 'error');
      return;
    }
    const optional = (value: string) => value.trim() || null;
    const normalizeDate = (value: string) => {
      const trimmed = value.trim();
      if (!trimmed) return null;
      if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
      const match = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
      if (!match) return trimmed;
      const [, day, month, year] = match;
      return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
    };
    const normalizeGender = (value: string) => {
      const gender = value.trim();
      return gender ? gender.toUpperCase() : null;
    };
    const payload = {
      fullName: form.name.trim(),
      email: optional(form.email),
      phone: form.phone.trim(),
      secondaryPhone: optional(form.secondaryPhone),
      address: form.address.trim(),
      city: form.city.trim(),
      country: form.country.trim(),
      postalCode: optional(form.postalCode),
      nationality: form.nationality.trim(),
      gender: normalizeGender(form.gender),
      birthDate: normalizeDate(form.birthDate),
      cin: optional(form.cin),
      passportNumber: optional(form.passportNumber),
      drivingLicense: optional(form.drivingLicense),
      companyName: optional(form.companyName),
      notes: optional(form.notes),
    };
    try {
      setSaving(true);
      setDuplicateField(null);
      if (editingId !== null) {
        await api.put(`/clients/${editingId}`, { ...payload, name: payload.fullName });
        showToast(t('toast.success', { action: 'Update' }));
      } else {
        const response = await api.post('/clients', payload);
        const message = response.data?.message || t('toast.newClientAdded');
        showToast(message, message.toLowerCase().includes('already exists') ? 'info' : 'success');
      }
      setIsModalOpen(false);
      setEditingId(null);
      setForm({
        name: '', email: '', phone: '', secondaryPhone: '', address: '', city: '',
        country: '', postalCode: '', nationality: '', gender: '', birthDate: '',
        cin: '', passportNumber: '', drivingLicense: '', companyName: '', notes: ''
      });
      await fetchClients();
    } catch (err: any) {
      const responseData = err?.response?.data;
      const field = responseData?.data?.field;
      if (field) setDuplicateField(field);
      showToast((err as any).userMessage || 'Unable to save client. Please try again later.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const deleteClient = async (id: number) => {
    if (confirm('Delete this client?')) {
      try {
        await api.delete(`/clients/${id}`);
        fetchClients();
        showToast(t('toast.success', { action: 'Delete' }));
      } catch (err) {
        showToast('Unable to delete client. Please try again later.', 'error');
      }
    }
  };

  const getInitials = (name: string) => {
    return name?.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase() || '?';
  };

  const inputClass = (field: string) =>
    `w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] ${
      duplicateField === field ? 'border-danger-500 ring-2 ring-danger-500/20' : ''
    }`;

  return (
    <div className="space-y-6 p-3 sm:p-4 lg:p-6">
      {/* Page Header */}
      <GlassPageHeader
        title={t('clients.title')}
        subtitle={t('clients.subtitle')}
        icon={User}
        actions={
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={openCreate}
            disabled={saving}
            className="flex items-center gap-2 px-4 sm:px-5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/20 active:scale-95 transition-all disabled:cursor-wait disabled:opacity-60"
          >
            <Plus size={18} />
            <span className="hidden sm:inline">{t('clients.newClient')}</span>
            <span className="sm:hidden">{t('clients.newClient')}</span>
          </motion.button>
        }
      />

      {/* Search */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
      >
        <SearchInput
          placeholder={t('clients.searchPlaceholder')}
          value={searchQuery}
          onChange={setSearchQuery}
          size="md"
        />
      </motion.div>

      {/* Content */}
      {loadError ? (
        <ApiErrorState message={loadError} onRetry={fetchClients} />
      ) : loading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-5"
        >
          <AnimatePresence>
            {filteredClients.map((client, index) => (
              <motion.div key={client.id} variants={itemVariants}>
                <GlassCard
                  hover
                  glow="gold"
                  padding="md"
                  delay={index * 50}
                  className="group h-full flex flex-col"
                >
                  {/* Card Header */}
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex items-center gap-3.5 min-w-0">
                      <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-brand-500/20 to-brand-500/5 flex items-center justify-center text-brand-500 text-sm font-bold shrink-0 group-hover:from-brand-500 group-hover:to-brand-600 group-hover:text-white transition-all duration-300">
                        {getInitials(client.name)}
                      </div>
                      <div className="min-w-0">
                        <h3 className="text-sm font-semibold text-[var(--text-primary)] truncate group-hover:text-brand-500 transition-colors">
                          {client.name}
                        </h3>
                        {client.companyName && (
                          <div className="flex items-center gap-1 text-[11px] text-[var(--text-muted)] mt-0.5">
                            <Building2 size={10} />
                            <span className="truncate">{client.companyName}</span>
                          </div>
                        )}
                        <div className="mt-1">
                          <StatusBadge variant="neutral" size="sm">
                            {client.cin || client.passportNumber || 'Identity not provided'}
                          </StatusBadge>
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-1 shrink-0">
                      <motion.button
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                        onClick={() => openEdit(client)}
                        className="p-1.5 text-[var(--text-muted)] hover:text-brand-500 hover:bg-brand-500/10 rounded-lg transition-colors"
                      >
                        <Pencil size={14} />
                      </motion.button>
                      <motion.button
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                        onClick={() => deleteClient(client.id)}
                        className="p-1.5 text-[var(--text-muted)] hover:text-danger-500 hover:bg-danger-500/10 rounded-lg transition-colors"
                      >
                        <Trash2 size={14} />
                      </motion.button>
                    </div>
                  </div>

                  {/* Contact Info */}
                  <div className="space-y-2 mb-4 flex-1">
                    <div className="flex items-center gap-2.5 text-[var(--text-secondary)] text-sm">
                      <div className="w-7 h-7 rounded-lg bg-brand-500/5 flex items-center justify-center shrink-0">
                        <Phone size={13} className="text-brand-500" />
                      </div>
                      <span className="truncate">{client.phone || 'N/A'}</span>
                    </div>
                    {client.email && (
                      <div className="flex items-center gap-2.5 text-[var(--text-secondary)] text-sm">
                        <div className="w-7 h-7 rounded-lg bg-brand-500/5 flex items-center justify-center shrink-0">
                          <Mail size={13} className="text-brand-500" />
                        </div>
                        <span className="truncate">{client.email}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-2.5 text-[var(--text-secondary)] text-sm">
                      <div className="w-7 h-7 rounded-lg bg-brand-500/5 flex items-center justify-center shrink-0">
                        <Shield size={13} className="text-brand-500" />
                      </div>
                      <span className="truncate">{client.cin || client.passportNumber || 'N/A'}</span>
                    </div>
                    <div className="flex items-center gap-2.5 text-[var(--text-secondary)] text-sm">
                      <div className="w-7 h-7 rounded-lg bg-brand-500/5 flex items-center justify-center shrink-0">
                        <MapPin size={13} className="text-brand-500" />
                      </div>
                      <span className="truncate">{client.city || client.address || 'N/A'}</span>
                    </div>
                  </div>

                  {/* Action Button */}
                  <div className="pt-4 border-t border-[var(--border-subtle)]">
                    <motion.button
                      whileHover={{ scale: 1.01 }}
                      whileTap={{ scale: 0.99 }}
                      onClick={() => { setSelectedClient(client); setIsProfileModalOpen(true); }}
                      className="w-full flex items-center justify-center gap-2 py-2.5 bg-[var(--bg-hover)] text-[var(--text-primary)] rounded-xl font-medium text-xs hover:bg-brand-500/10 hover:text-brand-500 active:scale-95 transition-all"
                    >
                      <Eye size={14} />
                      {t('clients.viewProfile')}
                    </motion.button>
                  </div>
                </GlassCard>
              </motion.div>
            ))}
          </AnimatePresence>

          {filteredClients.length === 0 && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="col-span-full"
            >
              <GlassCard padding="lg" className="flex flex-col items-center justify-center py-16 text-center">
                <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-brand-500/10 to-brand-500/5 flex items-center justify-center mb-4">
                  <Users size={32} className="text-brand-500/40" />
                </div>
                <h3 className="text-base font-bold text-[var(--text-primary)]">No clients available</h3>
                <p className="text-sm text-[var(--text-muted)] mt-1">Get started by adding your first client.</p>
              </GlassCard>
            </motion.div>
          )}
        </motion.div>
      )}

      {/* Create/Edit Modal */}
      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('clients.editClient') : t('clients.newClient')} maxWidth="2xl">
        <div className="space-y-5 pe-1">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Full Name <span className="text-danger-500">*</span></label>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputClass('fullName')} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Email</label>
              <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
                className={inputClass('email')} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Phone <span className="text-danger-500">*</span></label>
              <input type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className={inputClass('phone')} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Secondary Phone</label>
              <input type="tel" value={form.secondaryPhone} onChange={(e) => setForm({ ...form, secondaryPhone: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">CIN / Passport <span className="text-danger-500">*</span></label>
              <input type="text" value={form.cin} onChange={(e) => setForm({ ...form, cin: e.target.value })}
                className={inputClass('cin')} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Passport Number</label>
              <input type="text" value={form.passportNumber} onChange={(e) => setForm({ ...form, passportNumber: e.target.value })}
                className={inputClass('passportNumber')} />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Driving License</label>
              <input type="text" value={form.drivingLicense} onChange={(e) => setForm({ ...form, drivingLicense: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Nationality <span className="text-danger-500">*</span></label>
              <input type="text" value={form.nationality} onChange={(e) => setForm({ ...form, nationality: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Gender</label>
              <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)]">
                <option value="">Select</option>
                <option value="Male">Male</option>
                <option value="Female">Female</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Birth Date</label>
              <input type="date" value={form.birthDate} onChange={(e) => setForm({ ...form, birthDate: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)]" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Address <span className="text-danger-500">*</span></label>
              <input type="text" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">City <span className="text-danger-500">*</span></label>
              <input type="text" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Country <span className="text-danger-500">*</span></label>
              <input type="text" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Postal Code</label>
              <input type="text" value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Company Name</label>
              <input type="text" value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">Notes</label>
              <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} rows={2}
                className="w-full px-3.5 py-2.5 glass-input text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] resize-none" />
            </div>
          </div>
          <div className="pt-2">
            <motion.button
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              onClick={saveClient}
              disabled={saving}
              className="w-full py-3 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/20 active:scale-95 transition-all disabled:cursor-wait disabled:opacity-60"
            >
              {saving ? 'Saving...' : editingId ? 'Save Changes' : t('clients.newClient')}
            </motion.button>
          </div>
        </div>
      </Modal>

      <ClientProfileModal isOpen={isProfileModalOpen} onClose={() => setIsProfileModalOpen(false)} client={selectedClient as any} />
    </div>
  );
}
