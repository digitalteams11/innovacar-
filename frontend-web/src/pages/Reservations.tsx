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
import { translateReservationStatus } from '../utils/statusLabels';
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

const statusVariantMap: Record<string, import('../components/StatusBadge').StatusVariant> = {
  CONFIRMED: 'confirmed',
  PENDING: 'pending',
  ACTIVE: 'rented',
  CANCELLED: 'cancelled',
  CONVERTED_TO_CONTRACT: 'confirmed',
  EXPIRED: 'neutral',
  COMPLETED: 'info',
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
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pickupLocation, setPickupLocation] = useState('');
  const [returnLocation, setReturnLocation] = useState('');
  const [notes, setNotes] = useState('');

  const [saving, setSaving] = useState(false);

  const { showToast } = useToast();
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage || i18n.language || undefined;
  const formatDate = (value: string) => new Date(value).toLocaleDateString(locale);

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
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 404) {
        // No reservations endpoint data for this tenant yet — treat as an
        // empty list rather than a hard failure so the page still renders.
        setData([]);
        showToast('No reservations found.', 'warning');
      } else {
        setLoadError(apiErrorMessage(err, 'Unable to load reservation information. Please try again later.'));
      }
    } finally {
      setLoading(false);
    }
  }, [showToast]);

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

  const TAB_STATUSES: Record<string, string[]> = {
    CONFIRMED: ['CONFIRMED', 'APPROVED', 'CONVERTED_TO_CONTRACT'],
    PENDING: ['PENDING', 'PENDING_SIGNATURE', 'WAITING_SIGNATURE'],
    ACTIVE: ['ACTIVE', 'RENTED', 'ONGOING'],
    CANCELLED: ['CANCELLED', 'DELETED', 'REJECTED', 'EXPIRED'],
  };

  const filteredData = data.filter((res) => {
    const matchesTab =
      activeTab === 'All' ||
      (TAB_STATUSES[activeTab] ?? [activeTab]).includes(res.status ?? '');
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
    setFieldErrors({});
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
    setFieldErrors({});
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
    const errors: Record<string, string> = {};
    if (!clientData.clientId) errors.client = 'Client is required.';
    if (!selectedVehicle?.id) errors.vehicle = 'Vehicle is required.';
    if (!startDate) errors.startDate = 'Start date is required.';
    if (!startTime) errors.startTime = 'Start time is required.';
    if (!endDate) errors.endDate = 'End date is required.';
    if (!endTime) errors.endTime = 'End time is required.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      showToast('Please select client, vehicle, and dates', 'warning');
      return;
    }
    try {
      setSaving(true);
      const vehicle = selectedVehicle as SelectedVehicle;
      const payload = {
        vehicleId: vehicle.id,
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
      setFieldErrors({});
      await fetchReservations();
    } catch (error: unknown) {
      showToast(apiErrorMessage(error, 'Unable to save reservation. Please try again later.'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const clearFieldError = (field: string) => {
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

  const inputClass = (field: string) =>
    `w-full px-3 py-2 glass-input text-sm ${
      fieldErrors[field] ? 'border-danger-500 ring-2 ring-danger-500/20' : ''
    }`;

  const deleteReservation = async (id: number) => {
    const reservation = data.find((r) => r.id === id);
    if (!confirm(t('reservations.deleteConfirm') || 'Move this reservation to Trash?')) return;

    console.log('[RESERVATION_DELETE_CLICK]', {
      reservationId: id,
      reservationNumber: `RES-${id}`,
      endpoint: `DELETE /reservations/${id}`,
      status: reservation?.status,
    });

    // Remove optimistically so the row disappears immediately on success.
    setData((prev) => prev.filter((r) => r.id !== id));
    try {
      const { data: response } = await api.delete<{ success: boolean; message: string; data: Record<string, unknown> }>(`/reservations/${id}`);
      console.log('[RESERVATION_DELETE_RESPONSE]', {
        status: 200,
        data: response,
      });
      showToast(response?.message || 'Reservation moved to Trash.', 'success');
    } catch (err: unknown) {
      // Restore the row if backend rejected the delete.
      await fetchReservations();
      const errObj = err as { response?: { status?: number; data?: { message?: string; errorCode?: string } } };
      const status = errObj?.response?.status;
      const body = errObj?.response?.data;
      console.log('[RESERVATION_DELETE_RESPONSE]', { status, data: body });
      if (status === 409 && body?.errorCode === 'RESERVATION_LINKED_TO_ACTIVE_CONTRACT') {
        showToast(body?.message || 'Cannot delete: reservation is linked to an active contract. Delete or cancel the contract first.', 'error');
      } else {
        showToast(body?.message || 'Unable to delete reservation. Please try again later.', 'error');
      }
    }
  };

  // Opens the Create Contract modal on the Contracts page, prefilled from this
  // reservation's client/vehicle/dates — never silently auto-creates the
  // contract, so the user always reviews the prefilled form before saving.
  const openCreateContractModalFromReservation = (res: Reservation) => {
    if (res.contractId) {
      navigate(`/contracts/${res.contractId}`);
      return;
    }
    if (import.meta.env.DEV) {
      console.log('[CONTRACT_PREFILL_DEBUG]', {
        source: 'reservation-icon',
        reservationId: res.id,
        clientId: res.clientId,
        clientName: res.clientName,
        vehicleId: res.vehicleId,
        vehicleName: res.vehicleMarque,
        startDate: res.dateStart,
        startTime: res.startTime,
        endDate: res.dateEnd,
        endTime: res.endTime,
        totalAmount: res.estimatedPrice,
        modalPrefilled: true,
      });
    }
    navigate(`/contracts?fromReservationId=${res.id}`);
  };

  const calendarToday = new Date();
  const calendarYear = calendarToday.getFullYear();
  const calendarMonth = calendarToday.getMonth();
  const monthLabel = calendarToday.toLocaleDateString(locale, { month: 'long', year: 'numeric' });
  const firstWeekday = new Date(calendarYear, calendarMonth, 1).getDay();
  const daysInMonth = new Date(calendarYear, calendarMonth + 1, 0).getDate();
  const calendarCells: Array<number | null> = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, index) => index + 1),
  ];
  const isArabic = locale?.startsWith('ar');
  const weekDays = Array.from({ length: 7 }, (_, index) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2026, 1, index + 1))
  );
  const reservationsForDay = (day: number | null) => {
    if (!day) return [];
    return filteredData.filter((reservation) => {
      const start = new Date(`${reservation.dateStart}T00:00:00`);
      const end = new Date(`${reservation.dateEnd}T23:59:59`);
      const current = new Date(calendarYear, calendarMonth, day, 12, 0, 0);
      return current >= start && current <= end;
    });
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

      <GlassCard padding="md" className="overflow-hidden">
        <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-[var(--text-faint)]">{t('reservations.calendar')}</p>
            <h2 className="text-lg font-extrabold text-[var(--text-primary)]">{monthLabel}</h2>
          </div>
          <div className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] px-4 py-2 text-sm font-bold text-[var(--text-secondary)]">
            {t('reservations.reservationsVisible', { count: filteredData.length })}
          </div>
        </div>
        <div className="grid grid-cols-7 gap-2 text-center">
          {weekDays.map((day) => (
            <div key={day} className={`text-[10px] font-black tracking-[0.16em] text-[var(--text-faint)] ${isArabic ? '' : 'uppercase'}`}>
              {day}
            </div>
          ))}
          {calendarCells.map((day, index) => {
            const dayReservations = reservationsForDay(day);
            const isToday = day === calendarToday.getDate();
            return (
              <div
                key={`${day || 'empty'}-${index}`}
                className={`min-h-[64px] rounded-2xl border p-2 text-left transition-all ${day ? 'bg-[var(--bg-card)] hover:-translate-y-0.5 hover:shadow-lg' : 'opacity-35'}`}
                style={{ borderColor: isToday ? 'rgba(16,185,129,0.45)' : 'var(--border-subtle)' }}
              >
                {day && (
                  <>
                    <div className="flex items-center justify-between">
                      <span className={`text-xs font-black ${isToday ? 'text-emerald-500' : 'text-[var(--text-secondary)]'}`}>{day}</span>
                      {dayReservations.length > 0 && (
                        <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-bold text-emerald-600 dark:text-emerald-300">
                          {dayReservations.length}
                        </span>
                      )}
                    </div>
                    <div className="mt-3 flex flex-wrap gap-1">
                      {dayReservations.slice(0, 4).map((reservation) => (
                        <span
                          key={reservation.id}
                          className={`h-2 w-2 rounded-full ${
                            reservation.status === 'CONFIRMED'
                              ? 'bg-emerald-400'
                              : reservation.status === 'PENDING'
                                ? 'bg-amber-400'
                                : reservation.status === 'CANCELLED'
                                  ? 'bg-rose-400'
                                  : 'bg-cyan-400'
                          }`}
                          title={`${reservation.clientName} - ${reservation.vehicleMarque}`}
                        />
                      ))}
                    </div>
                  </>
                )}
              </div>
            );
          })}
        </div>
      </GlassCard>

      <div className="grid gap-3 md:hidden">
        {loading ? (
          [1, 2, 3].map((item) => <div key={item} className="glass-card h-36 shimmer" />)
        ) : filteredData.length > 0 ? (
          filteredData.map((res) => (
            <GlassCard key={res.id} padding="md" hover>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-mono text-xs font-black text-[var(--text-faint)]">#RES-{res.id}</p>
                  <h3 className="mt-1 truncate text-base font-extrabold text-[var(--text-primary)]">{res.clientName}</h3>
                  <p className="mt-1 flex items-center gap-1 text-sm text-[var(--text-muted)]">
                    <Car size={14} /> {res.vehicleMarque || t('reservations.vehicle')}
                  </p>
                </div>
                <StatusBadge variant={statusVariantMap[res.status] || 'neutral'} dot size="sm">
                  {translateReservationStatus(res.status)}
                </StatusBadge>
              </div>
              <div className="mt-4 rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] p-3 text-sm text-[var(--text-secondary)]">
                <div className="flex items-center justify-between gap-2">
                  <span>{formatDate(res.dateStart)} {res.startTime?.slice(0, 5)}</span>
                  <ChevronRight size={14} />
                  <span>{formatDate(res.dateEnd)} {res.endTime?.slice(0, 5)}</span>
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-[var(--text-muted)]">
                  <span>{res.pickupLocation || t('reservations.casablancaAirport')}</span>
                  <span className="font-bold text-[var(--text-primary)]">{res.estimatedPrice} MAD</span>
                </div>
              </div>
              <div className="mt-4 flex items-center justify-end gap-2">
                <button type="button" onClick={() => openCreateContractModalFromReservation(res)} className="glass-button rounded-2xl p-2 text-[var(--text-muted)]" aria-label={res.contractId ? t('reservations.openContract') : t('reservations.convertToContract')}>
                  <FileText size={17} />
                </button>
                <button type="button" onClick={() => openEdit(res)} disabled={res.readOnly} className="glass-button rounded-2xl p-2 text-[var(--text-muted)] disabled:opacity-40" aria-label={t('reservations.editAria')}>
                  <Edit3 size={17} />
                </button>
                <button type="button" onClick={() => deleteReservation(res.id)} disabled={res.readOnly} className="glass-button rounded-2xl p-2 text-rose-500 disabled:opacity-40" aria-label={t('reservations.deleteAria')}>
                  <Trash2 size={17} />
                </button>
              </div>
            </GlassCard>
          ))
        ) : (
          <GlassCard padding="md">
            <p className="text-center text-sm text-[var(--text-muted)]">{t('reservations.noResults') || 'No reservations found'}</p>
          </GlassCard>
        )}
      </div>
      <GlassCard padding="none" className="hidden overflow-hidden md:block">
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
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.id')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.client')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.vehicle')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.dates')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.status')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">{t('reservations.estimatedPrice')}</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody
              className="divide-y"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              {loading ? (
                [1, 2, 3].map(i => (
                  <tr key={i} className="animate-pulse">
                    <td className="px-5 py-6"><div className="h-5 w-32 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-32 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-24 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-40 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-6 w-20 rounded-full shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-5 w-16 rounded shimmer" /></td>
                    <td className="px-5 py-6"><div className="h-8 w-8 rounded ms-auto shimmer" /></td>
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
                          <div className="mt-2 inline-flex rounded-full border border-emerald-200 bg-emerald-50/60 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/5 dark:text-emerald-300">
                            Auto-created from contract
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          <span>{formatDate(res.dateStart)} {res.startTime?.slice(0, 5)}</span>
                          <ChevronRight size={13} style={{ color: 'var(--text-muted)' }} />
                          <span>{formatDate(res.dateEnd)} {res.endTime?.slice(0, 5)}</span>
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
                          {translateReservationStatus(res.status)}
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
                            onClick={() => openCreateContractModalFromReservation(res)}
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
                            <FileText size={17} />
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
            <SmartClientSearch value={clientData} onSelect={(value) => { setClientData(value); clearFieldError('client'); }} required />
            {fieldError('client')}

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
                      onClick={() => { setClientData({}); clearFieldError('client'); }}
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
                      title={t('reservations.clear')}
                    >
                      <X size={14} />
                    </button>
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    {clientData.clientCin && <div className="flex items-center gap-1"><Shield size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>{t('reservations.cin')}:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientCin}</span></div>}
                    {clientData.clientDriverLicense && <div className="flex items-center gap-1"><Car size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>{t('reservations.license')}:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientDriverLicense}</span></div>}
                    {clientData.clientCity && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>{t('reservations.city')}:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientCity}</span></div>}
                    {clientData.clientNationality && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span style={{ color: 'var(--text-muted)' }}>{t('reservations.nationality')}:</span><span className="font-medium" style={{ color: 'var(--text-primary)' }}>{clientData.clientNationality}</span></div>}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.startDate')} <span className="text-danger-500">*</span></label>
                <input type="date" value={startDate} onChange={(e) => { setStartDate(e.target.value); clearFieldError('startDate'); }}
                  aria-invalid={Boolean(fieldErrors.startDate)}
                  className={inputClass('startDate')} style={{ color: 'var(--text-primary)' }} />
                {fieldError('startDate')}
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.endDate')} <span className="text-danger-500">*</span></label>
                <input type="date" value={endDate} onChange={(e) => { setEndDate(e.target.value); clearFieldError('endDate'); }}
                  aria-invalid={Boolean(fieldErrors.endDate)}
                  className={inputClass('endDate')} style={{ color: 'var(--text-primary)' }} />
                {fieldError('endDate')}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.startTime')} <span className="text-danger-500">*</span></label>
                <input type="time" value={startTime} onChange={(e) => { setStartTime(e.target.value); clearFieldError('startTime'); }}
                  aria-invalid={Boolean(fieldErrors.startTime)}
                  className={inputClass('startTime')} style={{ color: 'var(--text-primary)' }} />
                {fieldError('startTime')}
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.endTime')} <span className="text-danger-500">*</span></label>
                <input type="time" value={endTime} onChange={(e) => { setEndTime(e.target.value); clearFieldError('endTime'); }}
                  aria-invalid={Boolean(fieldErrors.endTime)}
                  className={inputClass('endTime')} style={{ color: 'var(--text-primary)' }} />
                {fieldError('endTime')}
              </div>
            </div>

            <SmartVehicleSelector
              startDate={startDate}
              startTime={startTime}
              endDate={endDate}
              endTime={endTime}
              value={selectedVehicle?.id}
              onSelect={(v) => { setSelectedVehicle(v); clearFieldError('vehicle'); }}
              onUnavailable={() => {
                setSelectedVehicle(null);
                clearFieldError('vehicle');
                showToast(t('reservations.vehicleUnavailableToast'), 'warning');
              }}
            />
            {fieldError('vehicle')}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.pickupLocation')}</label>
                <input type="text" value={pickupLocation} onChange={(e) => setPickupLocation(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
              <div>
                <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.returnLocation')}</label>
                <input type="text" value={returnLocation} onChange={(e) => setReturnLocation(e.target.value)}
                  className="w-full px-3 py-2 glass-input text-sm" style={{ color: 'var(--text-primary)' }} />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium mb-1" style={{ color: 'var(--text-muted)' }}>{t('reservations.notes')}</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                className="w-full px-3 py-2 glass-input text-sm resize-none" style={{ color: 'var(--text-primary)' }} />
            </div>

          </div>

          <div className="space-y-4">
            <div className="rounded-2xl p-5 border space-y-3" style={{ backgroundColor: 'var(--bg-card-solid)', borderColor: 'var(--border-subtle)', boxShadow: 'var(--shadow-card)' }}>
              <div className="flex items-center gap-2 text-brand-500">
                <CreditCard size={14} />
                <span className="text-xs font-bold uppercase tracking-wider">{t('reservations.pricePreview')}</span>
              </div>
              {!selectedVehicle || !startDate || !endDate ? (
                <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{t('reservations.selectVehicleDatesHint')}</p>
              ) : (
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span style={{ color: 'var(--text-muted)' }}>{t('reservations.dailyRate')}</span>
                    <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{selectedVehicle.prixJour} MAD</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span style={{ color: 'var(--text-muted)' }}>{t('reservations.duration')}</span>
                    <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{t('reservations.daysCount', { count: daysCount() })}</span>
                  </div>
                  <div className="h-px" style={{ backgroundColor: 'var(--border-subtle)' }} />
                  <div className="flex justify-between items-baseline">
                    <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{t('reservations.total')}</span>
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
                    background: 'var(--bg-hover)',
                    borderColor: 'var(--border-subtle)',
                  }}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl flex items-center justify-center shadow-sm" style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)' }}>
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


