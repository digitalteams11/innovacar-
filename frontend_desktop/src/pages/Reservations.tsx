import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import Modal from '../components/Modal';
import SmartClientSearch, { type SmartClientSearchValue } from '../components/shared/SmartClientSearch';
import SmartVehicleSelector, { type Vehicle } from '../components/shared/SmartVehicleSelector';
import api from '../api/axios';
import ApiErrorState from '../components/ApiErrorState';
import { GlassCard } from '../components/GlassCard';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import { FilterChips } from '../components/FilterChips';
import { StatusBadge } from '../components/StatusBadge';
import {
  Plus, Download, ChevronRight, Clock, MapPin, CreditCard,
  Trash2, Edit3, FileText, Car, Shield, X, Calendar
} from 'lucide-react';

interface Reservation {
  id: number;
  clientId: number;
  clientName: string;
  vehicleId: number;
  vehicleMarque: string;
  dateStart: string;
  startTime: string;
  dateEnd: string;
  endTime: string;
  contractId?: number;
  status: string;
  source?: string;
  estimatedPrice: number;
  pickupLocation: string;
  returnLocation: string;
  readOnly: boolean;
}

interface ReservationApiResponse {
  id: number;
  clientId: number;
  clientName?: string;
  vehicleId: number;
  vehicleMarque: string;
  dateStart: string;
  startTime?: string;
  dateEnd: string;
  endTime?: string;
  contractId?: number;
  status?: string;
  source?: string;
  paymentStatus?: string;
  totalPrice?: number | string;
  estimatedPrice?: number | string;
  pickupLocation?: string;
  returnLocation?: string;
  readOnly?: boolean;
}

type SelectedVehicle = Partial<Vehicle> & Pick<Vehicle, 'id' | 'marque'>;

const apiErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error !== 'object' || error === null || !('userMessage' in error)) return fallback;
  const userMessage = (error as { userMessage?: unknown }).userMessage;
  return typeof userMessage === 'string' && userMessage.trim() ? userMessage : fallback;
};

const unwrapArray = <T,>(payload: unknown): T[] => {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === 'object') {
    const response = payload as Record<string, unknown>;
    if (Array.isArray(response.data)) return response.data as T[];
    if (response.data && typeof response.data === 'object') {
      const nested = response.data as Record<string, unknown>;
      if (Array.isArray(nested.content)) return nested.content as T[];
      if (Array.isArray(nested.items)) return nested.items as T[];
    }
    if (Array.isArray(response.content)) return response.content as T[];
  }
  return [];
};

const unwrapObject = <T extends Record<string, unknown>>(payload: unknown): Partial<T> => {
  if (!payload || typeof payload !== 'object') return {};
  const response = payload as Record<string, unknown>;
  if (response.data && typeof response.data === 'object' && !Array.isArray(response.data)) {
    return response.data as Partial<T>;
  }
  return response as Partial<T>;
};

const statusVariantMap: Record<string, import('../components/StatusBadge').StatusVariant> = {
  CONFIRMED: 'confirmed',
  PENDING: 'pending',
  ACTIVE: 'rented',
  CANCELLED: 'cancelled',
  CONVERTED_TO_CONTRACT: 'confirmed',
};

export default function Reservations() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const [clientData, setClientData] = useState<Partial<SmartClientSearchValue>>({});
  const [selectedVehicle, setSelectedVehicle] = useState<SelectedVehicle | null>(null);
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endDate, setEndDate] = useState('');
  const [endTime, setEndTime] = useState('18:00');
  const [pickupLocation, setPickupLocation] = useState('');
  const [returnLocation, setReturnLocation] = useState('');
  const [notes, setNotes] = useState('');

  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);

  const { showToast } = useToast();
  const { t } = useTranslation();

  const fetchReservations = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError('');
      const { data: response } = await api.get<unknown>('/reservations');
      const reservations = unwrapArray<ReservationApiResponse>(response);
      setData(reservations.map((reservation) => ({
        id: reservation.id,
        clientId: reservation.clientId,
        clientName: reservation.clientName || `Client #${reservation.clientId}`,
        vehicleId: reservation.vehicleId,
        vehicleMarque: reservation.vehicleMarque,
        dateStart: reservation.dateStart,
        startTime: reservation.startTime || '09:00',
        dateEnd: reservation.dateEnd,
        endTime: reservation.endTime || '18:00',
        contractId: reservation.contractId,
        status: reservation.status || 'CONFIRMED',
        source: reservation.source || 'MANUAL',
        estimatedPrice: Number(reservation.estimatedPrice ?? reservation.totalPrice) || 0,
        pickupLocation: reservation.pickupLocation || '',
        returnLocation: reservation.returnLocation || '',
        readOnly: reservation.readOnly === true || reservation.status === 'CONVERTED_TO_CONTRACT',
      })));
    } catch (err) {
      console.error('Failed to fetch reservations', err);
      setLoadError('Unable to load reservation information. Please try again later.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void fetchReservations(), 0);
    return () => window.clearTimeout(timer);
  }, [fetchReservations]);

  const tabs = [
    { id: 'All', label: t('reservations.all') },
    { id: 'CONFIRMED', label: t('reservations.confirmed') },
    { id: 'PENDING', label: t('reservations.pending') },
    { id: 'ACTIVE', label: t('reservations.rented') },
    { id: 'CANCELLED', label: t('reservations.cancelled') },
  ];

  const filteredData = data.filter((res) => {
    const matchesTab = activeTab === 'All' || res.status === activeTab;
    const q = searchQuery.toLowerCase();
    return matchesTab && (
      (res.clientName || '').toLowerCase().includes(q) ||
      (res.vehicleMarque || '').toLowerCase().includes(q) ||
      String(res.id).includes(q)
    );
  });

  const exportCSV = () => {
    const headers = ['ID', 'Client', 'Vehicle', 'Start Date', 'End Date', 'Status', 'Total'];
    const rows = filteredData.map((r) => [r.id, r.clientName, r.vehicleMarque, r.dateStart, r.dateEnd, r.status, r.estimatedPrice]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'reservations.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const daysCount = () => {
    if (!startDate || !endDate) return 0;
    const start = new Date(startDate);
    const end = new Date(endDate);
    return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)));
  };

  const calculatedPrice = () => {
    if (!selectedVehicle || !startDate || !endDate) return 0;
    const days = daysCount();
    return (selectedVehicle.prixJour || 0) * days;
  };

  const openCreate = () => {
    setEditingId(null);
    setClientData({});
    setSelectedVehicle(null);
    setStartDate('');
    setStartTime('09:00');
    setEndDate('');
    setEndTime('18:00');
    setPickupLocation('');
    setReturnLocation('');
    setNotes('');
    setIsModalOpen(true);
  };

  const openEdit = (res: Reservation) => {
    if (res.readOnly) {
      showToast('Converted reservations are read-only.', 'warning');
      return;
    }
    setEditingId(res.id);
    setClientData({ clientId: res.clientId, clientFullName: res.clientName });
    setSelectedVehicle(res.vehicleId ? { id: res.vehicleId, marque: res.vehicleMarque } : null);
    setStartDate(res.dateStart);
    setStartTime(res.startTime || '09:00');
    setEndDate(res.dateEnd);
    setEndTime(res.endTime || '18:00');
    setPickupLocation(res.pickupLocation || '');
    setReturnLocation(res.returnLocation || '');
    setNotes('');
    setIsModalOpen(true);
  };

  const saveReservation = async () => {
    if (!clientData.clientId || !selectedVehicle?.id || !startDate || !endDate) {
      showToast('Please select client, vehicle, and dates');
      return;
    }
    try {
      setSaving(true);
      const payload = {
        vehicleId: selectedVehicle.id,
        clientId: clientData.clientId,
        dateStart: startDate,
        startTime,
        dateEnd: endDate,
        endTime,
        pickupLocation: pickupLocation || undefined,
        returnLocation: returnLocation || undefined,
        notes: notes || undefined,
      };
      if (editingId !== null) {
        await api.put(`/reservations/${editingId}`, payload);
        showToast(t('toast.success', { action: t('reservations.edit') }));
      } else {
        await api.post('/reservations', payload);
        showToast(t('toast.newReservationCreated'));
      }
      setIsModalOpen(false);
      await fetchReservations();
    } catch (error: unknown) {
      showToast(apiErrorMessage(error, 'Unable to save reservation. Please try again later.'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const deleteReservation = async (id: number) => {
    if (confirm(t('reservations.deleteConfirm') || 'Delete this reservation?')) {
      try {
        await api.delete(`/reservations/${id}`);
        await fetchReservations();
        showToast('Reservation deleted successfully', 'success');
      } catch {
        showToast('Unable to delete reservation. Please try again later.', 'error');
      }
    }
  };

  const generateContract = async (res: Reservation) => {
    try {
      setActionId(res.id);
      if (res.contractId) {
        navigate(`/contracts/${res.contractId}`);
      } else {
        const { data: response } = await api.post<unknown>(`/contracts/from-reservation/${res.id}`);
        const contract = unwrapObject<{ id: number }>(response);
        showToast('Contract generated successfully', 'success');
        if (contract.id) {
          navigate(`/contracts/${contract.id}`);
        } else {
          await fetchReservations();
        }
      }
    } catch (error: unknown) {
      showToast(apiErrorMessage(error, 'Unable to generate contract. Please try again later.'), 'error');
    } finally {
      setActionId(null);
    }
  };

  if (loadError) {
    return (
      <div className="p-3 sm:p-4 lg:p-6">
        <ApiErrorState message={loadError} onRetry={fetchReservations} />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <GlassPageHeader
        title={t('reservations.title')}
        subtitle={t('reservations.subtitle')}
        icon={Calendar}
        actions={
          <div className="flex items-center gap-2">
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={exportCSV}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 glass-button text-[var(--text-primary)] font-medium text-xs sm:text-sm"
            >
              <Download size={16} className="sm:hidden" />
              <Download size={18} className="hidden sm:block" />
              <span className="hidden sm:inline">{t('reservations.exportCSV')}</span>
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={openCreate}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all"
            >
              <Plus size={16} className="sm:hidden" />
              <Plus size={18} className="hidden sm:block" />
              {t('reservations.createReservation')}
            </motion.button>
          </div>
        }
      />

      <GlassCard padding="md">
        <div className="flex flex-col gap-4">
          <FilterChips
            options={tabs}
            activeId={activeTab}
            onChange={setActiveTab}
          />
          <SearchInput
            placeholder={t('reservations.searchPlaceholder')}
            value={searchQuery}
            onChange={setSearchQuery}
            className="w-full"
          />
        </div>
      </GlassCard>

      <GlassCard padding="none" className="overflow-hidden">
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left min-w-[640px]">
            <thead>
              <tr
                className="text-[10px] font-bold uppercase tracking-[0.08em]"
                style={{
                  backgroundColor: 'var(--bg-hover)',
                  color: 'var(--text-muted)',
                }}
              >
                <th className="px-3 sm:px-5 py-3 sm:py-4">ID</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Client</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Vehicle</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Dates</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Status</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Estimated price</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody
              className="divide-y"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              {loading ? (
                [1, 2, 3].map(i => (
                  <tr key={i} className="animate-pulse">
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-24 bg-gray-100 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-40 bg-gray-100 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-6 w-20 bg-gray-100 rounded-full shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-16 bg-gray-100 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-8 w-8 bg-gray-100 rounded ms-auto shimmer" /></td>
                  </tr>
                ))
              ) : filteredData.length > 0 ? (
                <AnimatePresence>
                  {filteredData.map((res, index) => (
                    <motion.tr
                      key={res.id}
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -8 }}
                      transition={{ duration: 0.3, delay: index * 0.04, ease: [0.16, 1, 0.3, 1] }}
                      className="group transition-colors"
                      style={{ '--hover-bg': 'var(--bg-hover)' } as React.CSSProperties}
                      onMouseEnter={(e) => {
                        (e.currentTarget as HTMLElement).style.backgroundColor = 'var(--bg-hover)';
                      }}
                      onMouseLeave={(e) => {
                        (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                      }}
                    >
                      <td className="px-5 py-4">
                        <span
                          className="font-mono text-xs font-bold group-hover:text-brand-500 transition-colors"
                          style={{ color: 'var(--text-muted)' }}
                        >
                          #RES-{res.id}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div
                            className="w-9 h-9 rounded-lg flex items-center justify-center font-bold text-sm transition-all group-hover:shadow-sm"
                            style={{
                              backgroundColor: 'var(--bg-hover)',
                              color: 'var(--text-secondary)',
                            }}
                          >
                            {res.clientName?.charAt(0) || '?'}
                          </div>
                          <div className="min-w-0">
                            <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                              {res.clientName}
                            </div>
                            <div className="text-[11px] font-normal" style={{ color: 'var(--text-muted)' }}>
                              {t('reservations.individualClient')}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4">
                        <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          {res.vehicleMarque}
                        </div>
                        <div className="flex items-center gap-1 mt-0.5" style={{ color: 'var(--text-muted)' }}>
                          <MapPin size={11} />
                          <span className="text-xs font-normal">{res.pickupLocation || t('reservations.casablancaAirport')}</span>
                        </div>
                        {res.source === 'AUTO_FROM_CONTRACT' && (
                          <div className="mt-2 inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-700">
                            Auto-created from contract
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          <span>{new Date(res.dateStart).toLocaleDateString()} {res.startTime?.slice(0, 5)}</span>
                          <ChevronRight size={13} style={{ color: 'var(--text-muted)' }} />
                          <span>{new Date(res.dateEnd).toLocaleDateString()} {res.endTime?.slice(0, 5)}</span>
                        </div>
                        <div className="flex items-center gap-1 mt-0.5" style={{ color: 'var(--text-muted)' }}>
                          <Clock size={11} />
                          <span className="text-xs font-normal">{
                            Math.max(1, Math.ceil((new Date(res.dateEnd).getTime() - new Date(res.dateStart).getTime()) / (1000 * 60 * 60 * 24)))
                          } {t('reservations.rentalDays')}</span>
                        </div>
                      </td>
                      <td className="px-5 py-4">
                        <StatusBadge
                          variant={statusVariantMap[res.status] || 'neutral'}
                          dot
                          size="md"
                        >
                          {res.status}
                        </StatusBadge>
                      </td>
                      <td className="px-5 py-4">
                        <div className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                          {res.estimatedPrice} MAD
                        </div>
                      </td>
                      <td className="px-5 py-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <motion.button
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            onClick={() => generateContract(res)}
                            disabled={actionId === res.id}
                            className="p-2 rounded-lg transition-all"
                            style={{ color: 'var(--text-muted)' }}
                            onMouseEnter={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--brand-gold)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(212, 168, 83, 0.08)';
                            }}
                            onMouseLeave={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                            }}
                            title={res.contractId ? 'Open contract' : 'Convert to contract'}
                          >
                            {actionId === res.id ? <Clock size={17} className="animate-spin" /> : <FileText size={17} />}
                          </motion.button>
                          <motion.button
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            onClick={() => openEdit(res)}
                            disabled={res.readOnly}
                            className="p-2 rounded-lg transition-all disabled:cursor-not-allowed disabled:opacity-30"
                            style={{ color: 'var(--text-muted)' }}
                            onMouseEnter={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--warning)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(245, 158, 11, 0.08)';
                            }}
                            onMouseLeave={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                            }}
                          >
                            <Edit3 size={17} />
                          </motion.button>
                          <motion.button
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            onClick={() => deleteReservation(res.id)}
                            disabled={res.readOnly}
                            className="p-2 rounded-lg transition-all disabled:cursor-not-allowed disabled:opacity-30"
                            style={{ color: 'var(--text-muted)' }}
                            onMouseEnter={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--danger)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(239, 68, 68, 0.08)';
                            }}
                            onMouseLeave={(e) => {
                              (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
                              (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                            }}
                          >
                            <Trash2 size={17} />
                          </motion.button>
                        </div>
                      </td>
                    </motion.tr>
                  ))}
                </AnimatePresence>
              ) : (
                <tr>
                  <td colSpan={7} className="px-5 py-8 text-center text-sm" style={{ color: 'var(--text-muted)' }}>
                    {t('reservations.noResults') || 'No reservations found'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </GlassCard>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('reservations.edit') : t('reservations.createReservation')} maxWidth="5xl">
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(300px,0.65fr)] lg:gap-6">
          <div className="space-y-4 lg:sticky lg:top-0 lg:self-start">
            <SmartClientSearch value={clientData} onSelect={setClientData} required />

            <AnimatePresence>
              {clientData.clientFullName && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                  className="p-4 rounded-2xl border space-y-3"
                  style={{
                    background: 'linear-gradient(135deg, rgba(30,58,95,0.04), rgba(255,255,255,0.8))',
                    borderColor: 'var(--border-subtle)',
                  }}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-brand-500 text-white rounded-xl flex items-center justify-center text-sm font-bold shadow-sm">
                        {clientData.clientFullName?.split(' ').map((n: string) => n[0]).join('').toUpperCase().slice(0, 2) || '?'}
                      </div>
                      <div>
                        <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{clientData.clientFullName}</p>
                        <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-0.5">
                          {clientData.clientPhone && <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{clientData.clientPhone}</span>}
                          {clientData.clientEmail && <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{clientData.clientEmail}</span>}
                        </div>
                      </div>
                    </div>
                    <button
                      onClick={() => setClientData({})}
                      className="p-1.5 rounded-lg transition-all"
                      style={{ color: 'var(--text-muted)' }}
                      onMouseEnter={(e) => {
                        (e.currentTarget as HTMLElement).style.color = 'var(--danger)';
                        (e.currentTarget as HTMLElement).style.backgroundColor = 'rgba(239, 68, 68, 0.08)';
                      }}
                      onMouseLeave={(e) => {
                        (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
                        (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                      }}
                      title="Clear"
                    >
                      <X size={14} />
                    </button>
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    {clientData.clientCin && <div className="flex items-center gap-1"><Shield size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>CIN:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientCin}</span></div>}
                    {clientData.clientDriverLicense && <div className="flex items-center gap-1"><Car size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>License:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientDriverLicense}</span></div>}
                    {clientData.clientCity && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>City:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientCity}</span></div>}
                    {clientData.clientNationality && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>Nationality:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientNationality}</span></div>}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>Start Date <span className="text-danger-500">*</span></label>
                <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>End Date <span className="text-danger-500">*</span></label>
                <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>Start Time <span className="text-danger-500">*</span></label>
                <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>End Time <span className="text-danger-500">*</span></label>
                <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
            </div>

            <SmartVehicleSelector
              startDate={startDate}
              startTime={startTime}
              endDate={endDate}
              endTime={endTime}
              value={selectedVehicle?.id}
              onSelect={(v) => setSelectedVehicle(v)}
            />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>Pickup Location</label>
                <input type="text" value={pickupLocation} onChange={(e) => setPickupLocation(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>Return Location</label>
                <input type="text" value={returnLocation} onChange={(e) => setReturnLocation(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>Notes</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                className="w-full px-3 py-2 glass-input text-sm resize-none" style={{ color: 'var(--text-primary)' }} />
            </div>

          </div>

          <div className="space-y-4">
            <div className="rounded-2xl p-5 border space-y-3" style={{ backgroundColor: 'var(--bg-card-solid)', borderColor: 'var(--border-subtle)', boxShadow: 'var(--shadow-card)' }}>
              <div className="flex items-center gap-2 text-brand-500">
                <CreditCard size={14} />
                <span className="text-xs font-bold uppercase tracking-wider">Price Preview</span>
              </div>
              {!selectedVehicle || !startDate || !endDate ? (
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Select vehicle and dates to see pricing</p>
              ) : (
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span style={{ color: 'var(--text-muted)' }}>Daily Rate</span>
                    <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.prixJour} MAD</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span style={{ color: 'var(--text-muted)' }}>Duration</span>
                    <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{daysCount()} days</span>
                  </div>
                  <div className="h-px" style={{ backgroundColor: 'var(--border-subtle)' }} />
                  <div className="flex justify-between items-baseline">
                    <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>Total</span>
                    <span className="text-2xl font-black text-brand-500">{calculatedPrice()} <span className="text-sm font-medium">MAD</span></span>
                  </div>
                </div>
              )}
            </div>

            <AnimatePresence>
              {selectedVehicle && (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                  className="p-4 rounded-2xl border space-y-3"
                  style={{
                    background: 'linear-gradient(135deg, rgba(248,249,252,0.8), rgba(255,255,255,0.9))',
                    borderColor: 'var(--border-subtle)',
                  }}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-slate-800 text-white rounded-xl flex items-center justify-center shadow-sm">
                      <Car size={18} />
                    </div>
                    <div>
                      <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.marque}</p>
                      <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{selectedVehicle.category} • {selectedVehicle.year} • {selectedVehicle.color}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    <div className="flex items-center gap-1"><span style={{ color: 'var(--text-muted)' }}>Plate:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.plate}</span></div>
                    <div className="flex items-center gap-1"><span style={{ color: 'var(--text-muted)' }}>Fuel:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.fuel}</span></div>
                    <div className="flex items-center gap-1"><span style={{ color: 'var(--text-muted)' }}>Day:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.prixJour} MAD</span></div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <motion.button
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.98 }}
              onClick={saveReservation}
              disabled={saving}
              className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saving ? 'Saving...' : editingId ? 'Save Changes' : t('reservations.createReservation')}
            </motion.button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
