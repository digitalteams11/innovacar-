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
  Shield, Building2, Users, Pencil, Trash2, Eye, User, ChevronDown, AlertTriangle
} from 'lucide-react';
import ApiErrorState from '../components/ApiErrorState';

type DocumentType = 'CIN' | 'PASSPORT' | 'RESIDENCE_PERMIT' | 'OTHER';

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
  documentType: DocumentType | null;
  documentNumber: string | null;
  documentMasked: string | null;
  documentIssuingCountry: string | null;
  documentIssuingAuthority: string | null;
  documentIssueDate: string | null;
  documentExpiryDate: string | null;
  drivingLicense: string;
  drivingLicenseIssue: string | null;
  drivingLicenseExpiry: string | null;
  drivingLicenseCategory: string | null;
  drivingLicenseCountry: string | null;
  companyName: string;
  notes: string;
  warnings?: string[];
}

const DOCUMENT_TYPES: DocumentType[] = ['CIN', 'PASSPORT', 'RESIDENCE_PERMIT', 'OTHER'];

/** CIN -> Cin, RESIDENCE_PERMIT -> ResidencePermit — matches i18n key suffixes below. */
function toPascalCase(value: string) {
  return value.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('');
}

const EMPTY_FORM = {
  name: '', email: '', phone: '', secondaryPhone: '', gender: '', birthDate: '', nationality: '',
  documentType: '' as DocumentType | '', documentNumber: '', documentIssuingCountry: '',
  documentIssuingAuthority: '', documentIssueDate: '', documentExpiryDate: '', documentNotes: '',
  address: '', city: '', postalCode: '', country: '',
  drivingLicense: '', drivingLicenseIssue: '', drivingLicenseExpiry: '', drivingLicenseCategory: '', drivingLicenseCountry: '',
  companyName: '', notes: '',
};

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
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showDocumentDetails, setShowDocumentDetails] = useState(false);

  const [form, setForm] = useState({ ...EMPTY_FORM });

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
      c.documentMasked?.toLowerCase().includes(q) ||
      c.companyName?.toLowerCase().includes(q);
  });

  const openCreate = () => {
    setEditingId(null);
    setDuplicateField(null);
    setFieldErrors({});
    setShowDocumentDetails(false);
    setForm({ ...EMPTY_FORM });
    setIsModalOpen(true);
  };

  const openEdit = (client: Client) => {
    setEditingId(client.id);
    setDuplicateField(null);
    setFieldErrors({});
    setShowDocumentDetails(Boolean(client.documentIssueDate || client.documentExpiryDate || client.documentIssuingCountry || client.documentIssuingAuthority));
    setForm({
      name: client.name || '', email: client.email || '', phone: client.phone || '',
      secondaryPhone: client.secondaryPhone || '', gender: client.gender || '', birthDate: client.birthDate || '',
      nationality: client.nationality || '',
      documentType: (client.documentType || (client.cin ? 'CIN' : client.passportNumber ? 'PASSPORT' : '')) as DocumentType | '',
      documentNumber: client.documentMasked ? (client.documentNumber || '') : (client.cin || client.passportNumber || ''),
      documentIssuingCountry: client.documentIssuingCountry || '', documentIssuingAuthority: client.documentIssuingAuthority || '',
      documentIssueDate: client.documentIssueDate || '', documentExpiryDate: client.documentExpiryDate || '', documentNotes: '',
      address: client.address || '', city: client.city || '', postalCode: client.postalCode || '', country: client.country || '',
      drivingLicense: client.drivingLicense || '', drivingLicenseIssue: client.drivingLicenseIssue || '',
      drivingLicenseExpiry: client.drivingLicenseExpiry || '', drivingLicenseCategory: client.drivingLicenseCategory || '',
      drivingLicenseCountry: client.drivingLicenseCountry || '',
      companyName: client.companyName || '', notes: client.notes || ''
    });
    setIsModalOpen(true);
  };

  /** Document-type label/placeholder pairs — the single number input relabels itself per type. */
  const documentFieldMeta = (type: string) => {
    switch (type) {
      case 'CIN': return { label: t('clients.form.documentNumberCin'), placeholder: t('clients.form.documentPlaceholderCin') };
      case 'PASSPORT': return { label: t('clients.form.documentNumberPassport'), placeholder: t('clients.form.documentPlaceholderPassport') };
      case 'RESIDENCE_PERMIT': return { label: t('clients.form.documentNumberResidencePermit'), placeholder: '' };
      case 'OTHER': return { label: t('clients.form.documentNumberOther'), placeholder: '' };
      default: return { label: t('clients.form.documentNumber'), placeholder: '' };
    }
  };

  const updateFormField = (field: keyof typeof form, value: string, errorKeys?: string[]) => {
    setForm((prev) => {
      const next = { ...prev, [field]: value };
      // Default to CIN only for Moroccan clients, and only while the admin
      // hasn't already picked a document type — never force it otherwise.
      if (field === 'nationality' && !prev.documentType && /^morocc/i.test(value.trim())) {
        next.documentType = 'CIN';
      }
      return next;
    });
    const keys = errorKeys || [field === 'name' ? 'fullName' : field];
    setFieldErrors((prev) => {
      if (!keys.some((key) => prev[key])) return prev;
      const next = { ...prev };
      keys.forEach((key) => delete next[key]);
      return next;
    });
    if (keys.includes(duplicateField || '')) setDuplicateField(null);
  };

  const validateClientForm = () => {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.fullName = t('clients.validation.fullNameRequired');
    if (!form.phone.trim()) errors.phone = t('clients.validation.phoneRequired');
    if (!form.documentType) errors.documentType = t('clients.validation.documentTypeRequired');
    if (!form.documentNumber.trim()) errors.documentNumber = t('clients.validation.documentNumberRequired');
    if (form.documentIssueDate && form.documentExpiryDate && form.documentExpiryDate < form.documentIssueDate) {
      errors.documentExpiryDate = t('clients.validation.documentExpiryBeforeIssue');
    }
    if (form.birthDate && form.birthDate > new Date().toISOString().slice(0, 10)) {
      errors.birthDate = t('clients.validation.birthDateFuture');
    }
    if (!form.nationality.trim()) errors.nationality = t('clients.validation.nationalityRequired');
    if (!form.address.trim()) errors.address = t('clients.validation.addressRequired');
    if (!form.city.trim()) errors.city = t('clients.validation.cityRequired');
    if (!form.country.trim()) errors.country = t('clients.validation.countryRequired');
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const saveClient = async () => {
    if (saving) return;
    if (!validateClientForm()) {
      showToast(t('clients.validation.requiredSummary'), 'error');
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
      nationality: optional(form.nationality),
      gender: normalizeGender(form.gender),
      birthDate: normalizeDate(form.birthDate),
      documentType: form.documentType || null,
      documentNumber: optional(form.documentNumber),
      documentIssuingCountry: optional(form.documentIssuingCountry),
      documentIssuingAuthority: optional(form.documentIssuingAuthority),
      documentIssueDate: normalizeDate(form.documentIssueDate),
      documentExpiryDate: normalizeDate(form.documentExpiryDate),
      documentNotes: optional(form.documentNotes),
      drivingLicense: optional(form.drivingLicense),
      drivingLicenseIssue: normalizeDate(form.drivingLicenseIssue),
      drivingLicenseExpiry: normalizeDate(form.drivingLicenseExpiry),
      drivingLicenseCategory: optional(form.drivingLicenseCategory),
      drivingLicenseCountry: optional(form.drivingLicenseCountry),
      companyName: optional(form.companyName),
      notes: optional(form.notes),
    };
    try {
      setSaving(true);
      setDuplicateField(null);
      if (editingId !== null) {
        await api.put(`/clients/${editingId}`, { ...payload, name: payload.fullName });
        showToast(t('toast.success', { action: t('clients.updateAction') }));
      } else {
        const params = new URLSearchParams();
        if (payload.email) params.set('email', payload.email);
        if (payload.phone) params.set('phone', payload.phone);
        if (payload.documentType === 'CIN' && payload.documentNumber) params.set('cin', payload.documentNumber);
        if (payload.documentType === 'PASSPORT' && payload.documentNumber) params.set('passportNumber', payload.documentNumber);
        const checkResponse = await api.get(`/clients/check?${params.toString()}`);
        const duplicate = checkResponse.data?.data;
        if (duplicate?.exists) {
          const field = duplicate.field || 'phone';
          setDuplicateField(field);
          showToast(duplicate.message || `A client with this ${field} already exists`, 'error');
          return;
        }
        const response = await api.post('/clients', payload);
        const message = response.data?.message || t('toast.newClientAdded');
        showToast(message, message.toLowerCase().includes('already exists') ? 'info' : 'success');
      }
      setIsModalOpen(false);
      setEditingId(null);
      setFieldErrors({});
      setForm({ ...EMPTY_FORM });
      await fetchClients();
    } catch (err: any) {
      const responseData = err?.response?.data;
      const field = responseData?.field || responseData?.data?.field;
      if (field) setDuplicateField(field);
      showToast((err as any).userMessage || t('clients.saveFailed'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const deleteClient = async (id: number) => {
    if (confirm(t('clients.deleteConfirm'))) {
      try {
        await api.delete(`/clients/${id}`);
        fetchClients();
        showToast(t('toast.success', { action: t('clients.deleteAction') }));
      } catch (err) {
        showToast(t('clients.deleteFailed'), 'error');
      }
    }
  };

  const getInitials = (name: string) => {
    return name?.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase() || '?';
  };

  const getAvatarStyle = (gender: string) => {
    const g = gender?.toUpperCase();
    if (g === 'MALE' || g === 'M') {
      return {
        wrapper: 'bg-gradient-to-br from-blue-500/20 to-blue-600/10 text-blue-600 group-hover:from-blue-500 group-hover:to-blue-600 group-hover:text-white',
      };
    }
    if (g === 'FEMALE' || g === 'F') {
      return {
        wrapper: 'bg-gradient-to-br from-pink-500/20 to-pink-600/10 text-pink-600 group-hover:from-pink-500 group-hover:to-pink-600 group-hover:text-white',
      };
    }
    return {
      wrapper: 'bg-gradient-to-br from-brand-500/20 to-brand-500/5 text-brand-500 group-hover:from-brand-500 group-hover:to-brand-600 group-hover:text-white',
    };
  };

  const inputClass = (field: string) =>
    `w-full px-3.5 py-2.5 min-h-[44px] glass-input text-base sm:text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] ${
      duplicateField === field || fieldErrors[field] ? 'border-danger-500 ring-2 ring-danger-500/20' : ''
    }`;

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

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
                      <div className={`w-11 h-11 rounded-xl flex items-center justify-center text-sm font-bold shrink-0 transition-all duration-300 ${getAvatarStyle(client.gender).wrapper}`}>
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
                        <div className="mt-1 flex items-center gap-1.5 flex-wrap">
                          <StatusBadge variant="neutral" size="sm">
                            {client.documentType
                              ? `${t(`clients.form.documentType${toPascalCase(client.documentType)}`)} · ${client.documentMasked || t('clients.identityNotProvided')}`
                              : (client.documentMasked || t('clients.identityNotProvided'))}
                          </StatusBadge>
                          {client.warnings?.includes('DOCUMENT_EXPIRED') && (
                            <span title={t('clients.warnings.documentExpired')}><AlertTriangle size={12} className="text-danger-500" /></span>
                          )}
                          {client.warnings?.includes('LICENSE_EXPIRED') && (
                            <span title={t('clients.warnings.licenseExpired')}><AlertTriangle size={12} className="text-amber-500" /></span>
                          )}
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
                      <span className="truncate">{client.documentMasked || 'N/A'}</span>
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
                <h3 className="text-base font-bold text-[var(--text-primary)]">{t('clients.emptyTitle')}</h3>
                <p className="text-sm text-[var(--text-muted)] mt-1">{t('clients.emptyHint')}</p>
              </GlassCard>
            </motion.div>
          )}
        </motion.div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingId ? t('clients.editClient') : t('clients.newClient')}
        maxWidth="form"
        footer={
          <div className="flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
            <button
              onClick={() => setIsModalOpen(false)}
              className="min-h-[44px] px-5 py-2.5 rounded-xl text-sm font-medium bg-[var(--bg-hover)] text-[var(--text-primary)] transition-colors"
            >
              {t('common.cancel')}
            </button>
            <motion.button
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              onClick={saveClient}
              disabled={saving}
              className="min-h-[44px] px-5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/20 active:scale-95 transition-all disabled:cursor-wait disabled:opacity-60"
            >
              {saving ? t('common.saving') : editingId ? t('common.saveChanges') : t('clients.newClient')}
            </motion.button>
          </div>
        }
      >
        <div className="space-y-6 pe-1">
          {/* A. Personal information */}
          <section>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">{t('clients.form.sectionPersonal')}</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.fullName')} <span className="text-danger-500">*</span></label>
                <input type="text" value={form.name} onChange={(e) => updateFormField('name', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.fullName)}
                  className={inputClass('fullName')} />
                {fieldError('fullName')}
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.email')}</label>
                <input type="email" value={form.email} onChange={(e) => updateFormField('email', e.target.value)}
                  className={inputClass('email')} />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.phone')} <span className="text-danger-500">*</span></label>
                <input type="tel" value={form.phone} onChange={(e) => updateFormField('phone', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.phone)}
                  className={inputClass('phone')} />
                {fieldError('phone')}
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.secondaryPhone')}</label>
                <input type="tel" value={form.secondaryPhone} onChange={(e) => updateFormField('secondaryPhone', e.target.value)}
                  className={inputClass('secondaryPhone')} />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.gender')}</label>
                <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}
                  className="w-full px-3.5 py-2.5 glass-input text-base sm:text-sm text-[var(--text-primary)]">
                  <option value="">{t('common.select')}</option>
                  <option value="Male">{t('clients.gender.male')}</option>
                  <option value="Female">{t('clients.gender.female')}</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.birthDate')}</label>
                <input type="date" value={form.birthDate} onChange={(e) => updateFormField('birthDate', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.birthDate)}
                  className={inputClass('birthDate')} />
                {fieldError('birthDate')}
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.nationality')} <span className="text-danger-500">*</span></label>
                <input type="text" value={form.nationality} onChange={(e) => updateFormField('nationality', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.nationality)}
                  className={inputClass('nationality')} />
                {fieldError('nationality')}
              </div>
            </div>
          </section>

          {/* B. Identity document — one type selector, one number input */}
          <section className="pt-5 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">{t('clients.form.sectionIdentityDocument')}</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.documentType')} <span className="text-danger-500">*</span></label>
                <select value={form.documentType} onChange={(e) => updateFormField('documentType', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.documentType)}
                  className={`${inputClass('documentType')} text-base sm:text-sm`}>
                  <option value="">{t('common.select')}</option>
                  {DOCUMENT_TYPES.map((type) => (
                    <option key={type} value={type}>{t(`clients.form.documentType${toPascalCase(type)}`)}</option>
                  ))}
                </select>
                {fieldError('documentType')}
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">
                  {documentFieldMeta(form.documentType).label} <span className="text-danger-500">*</span>
                </label>
                <input type="text" value={form.documentNumber}
                  onChange={(e) => updateFormField('documentNumber', e.target.value.toUpperCase())}
                  placeholder={documentFieldMeta(form.documentType).placeholder}
                  aria-invalid={Boolean(fieldErrors.documentNumber)}
                  className={`${inputClass('documentNumber')} uppercase tracking-wide`} />
                {fieldError('documentNumber')}
              </div>
            </div>

            <button
              type="button"
              onClick={() => setShowDocumentDetails((v) => !v)}
              className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-brand-500"
            >
              <ChevronDown size={14} className={`transition-transform ${showDocumentDetails ? 'rotate-180' : ''}`} />
              {t('clients.form.documentDetails')}
            </button>

            {showDocumentDetails && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.documentIssueDate')}</label>
                  <input type="date" value={form.documentIssueDate} onChange={(e) => updateFormField('documentIssueDate', e.target.value)}
                    className={inputClass('documentIssueDate')} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.documentExpiryDate')}</label>
                  <input type="date" value={form.documentExpiryDate} onChange={(e) => updateFormField('documentExpiryDate', e.target.value)}
                    aria-invalid={Boolean(fieldErrors.documentExpiryDate)}
                    className={inputClass('documentExpiryDate')} />
                  {fieldError('documentExpiryDate')}
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.issuingCountry')}</label>
                  <input type="text" value={form.documentIssuingCountry} onChange={(e) => setForm({ ...form, documentIssuingCountry: e.target.value })}
                    className={inputClass('documentIssuingCountry')} />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.issuingAuthority')}</label>
                  <input type="text" value={form.documentIssuingAuthority} onChange={(e) => setForm({ ...form, documentIssuingAuthority: e.target.value })}
                    className={inputClass('documentIssuingAuthority')} />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.notes')}</label>
                  <textarea value={form.documentNotes} onChange={(e) => setForm({ ...form, documentNotes: e.target.value })} rows={2}
                    className={`${inputClass('documentNotes')} resize-none`} />
                </div>
              </div>
            )}
          </section>

          {/* C. Address */}
          <section className="pt-5 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">{t('clients.form.sectionAddress')}</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.address')} <span className="text-danger-500">*</span></label>
                <input type="text" value={form.address} onChange={(e) => updateFormField('address', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.address)}
                  className={inputClass('address')} />
                {fieldError('address')}
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.city')} <span className="text-danger-500">*</span></label>
                <input type="text" value={form.city} onChange={(e) => updateFormField('city', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.city)}
                  className={inputClass('city')} />
                {fieldError('city')}
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.postalCode')}</label>
                <input type="text" value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
                  className={inputClass('postalCode')} />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.country')} <span className="text-danger-500">*</span></label>
                <input type="text" value={form.country} onChange={(e) => updateFormField('country', e.target.value)}
                  aria-invalid={Boolean(fieldErrors.country)}
                  className={inputClass('country')} />
                {fieldError('country')}
              </div>
            </div>
          </section>

          {/* D. Driver license — separate from identity documents */}
          <section className="pt-5 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">
              {t('clients.form.sectionDriverLicense')} <span className="font-normal normal-case text-[var(--text-faint)]">({t('common.optional')})</span>
            </h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.drivingLicense')}</label>
                <input type="text" value={form.drivingLicense} onChange={(e) => updateFormField('drivingLicense', e.target.value)}
                  className={inputClass('drivingLicense')} />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.drivingLicenseCategory')}</label>
                <input type="text" value={form.drivingLicenseCategory} onChange={(e) => setForm({ ...form, drivingLicenseCategory: e.target.value.toUpperCase() })}
                  placeholder="B"
                  className={inputClass('drivingLicenseCategory')} />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.documentIssueDate')}</label>
                <input type="date" value={form.drivingLicenseIssue} onChange={(e) => setForm({ ...form, drivingLicenseIssue: e.target.value })}
                  className={inputClass('drivingLicenseIssue')} />
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.documentExpiryDate')}</label>
                <input type="date" value={form.drivingLicenseExpiry} onChange={(e) => setForm({ ...form, drivingLicenseExpiry: e.target.value })}
                  className={inputClass('drivingLicenseExpiry')} />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.issuingCountry')}</label>
                <input type="text" value={form.drivingLicenseCountry} onChange={(e) => setForm({ ...form, drivingLicenseCountry: e.target.value })}
                  className={inputClass('drivingLicenseCountry')} />
              </div>
            </div>
          </section>

          {/* E. Professional information */}
          <section className="pt-5 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">{t('clients.form.sectionProfessional')}</h4>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="sm:col-span-2">
                <label className="block text-xs font-medium text-[var(--text-muted)] mb-1.5">{t('clients.form.companyName')}</label>
                <input type="text" value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                  className={inputClass('companyName')} />
              </div>
            </div>
          </section>

          {/* F. Notes */}
          <section className="pt-5 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
            <h4 className="text-xs font-bold uppercase tracking-wide text-brand-500 mb-3">{t('clients.form.sectionNotes')}</h4>
            <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} rows={3}
              className={`${inputClass('notes')} resize-none`} />
          </section>
        </div>
      </Modal>

      <ClientProfileModal isOpen={isProfileModalOpen} onClose={() => setIsProfileModalOpen(false)} client={selectedClient as any} />
    </div>
  );
}


