import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import { GlassCard } from '../components/GlassCard';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import { FilterChips } from '../components/FilterChips';
import { StatusBadge } from '../components/StatusBadge';
import { motion } from 'framer-motion';
import { Plus, Download, ChevronRight, Fuel, Shield, Users as UsersIcon, Camera, Loader2, Car, RotateCcw, Trash2, AlertTriangle, FileText, FileSpreadsheet, FileType, SlidersHorizontal, MoreHorizontal, X } from 'lucide-react';
import api from '../api/axios';
import { useSubscription } from '../hooks/useSubscription';
import ApiErrorState from '../components/ApiErrorState';
import { normalizeStatusCode, translateFuelType, translateTransmission, translateVehicleCategory, translateVehicleStatus } from '../utils/statusLabels';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';

interface Vehicle {
  id: number;
  marque: string;
  brand?: string;
  model?: string;
  category: string;
  plate: string;
  statut: string;
  prixJour: number;
  fuel: string;
  transmission: string;
  seatCount?: number | null;
  imageUrl: string;
}

interface TrashedVehicle {
  id: number;
  marque: string;
  plate?: string;
  statut: string;
  deletedAt: string;
  deletedBy?: string;
  restorableUntil: string;
  daysRemaining: number;
}

const vehicleCategories = ['Economy', 'Compact', 'Sedan', 'SUV', 'Luxury', 'Sport', 'Electric', 'Hybrid', 'Van', 'Pickup', 'Minibus', 'Truck', 'Motorcycle'];

const statusVariantMap: Record<string, 'available' | 'rented' | 'maintenance' | 'warning' | 'neutral'> = {
  AVAILABLE: 'available',
  RESERVED: 'warning',
  RENTED: 'rented',
  MAINTENANCE: 'maintenance',
  IN_MAINTENANCE: 'maintenance',
  OUT_OF_SERVICE: 'neutral',
  SOLD: 'neutral',
  ARCHIVED: 'neutral',
};

const normalizeVehicle = (vehicle: Vehicle): Vehicle => {
  const parts = String(vehicle.marque || '').trim().split(/\s+/, 2);
  return {
    ...vehicle,
    brand: vehicle.brand || parts[0] || '',
    model: vehicle.model || (parts.length > 1 ? String(vehicle.marque).trim().slice(parts[0].length).trim() : ''),
    statut: normalizeStatusCode(vehicle.statut) || 'AVAILABLE',
  };
};

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.06,
    },
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

export default function Vehicles() {
  const [filter, setFilter] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [availabilityIds, setAvailabilityIds] = useState<Set<number> | null>(null);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState({
    category: '', brand: '', fuel: '', transmission: '', seats: '',
    minPrice: '', maxPrice: '', startDate: '', endDate: '',
  });
  const [data, setData] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [showLoadingIndicator, setShowLoadingIndicator] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'view' | 'edit'>('create');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedVehicle, setSelectedVehicle] = useState<Vehicle | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState({
    brand: '',
    model: '',
    category: '',
    plate: '',
    statut: 'AVAILABLE',
    prixJour: '',
    fuel: '',
    transmission: '',
    seatCount: '',
    imageUrl: '',
  });
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [initialForm, setInitialForm] = useState<typeof form | null>(null);
  const [trashData, setTrashData] = useState<TrashedVehicle[]>([]);
  const [trashLoading, setTrashLoading] = useState(false);
  const [trashLoadError, setTrashLoadError] = useState('');
  const [restoringId, setRestoringId] = useState<number | null>(null);
  const [purgingId, setPurgingId] = useState<number | null>(null);

  const { showToast } = useToast();
  const { t, i18n } = useTranslation();
  const { canCreateVehicle, status } = useSubscription();

  const fetchVehicles = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError('');
      const { data } = await api.get('/vehicles');
      const vehicles = Array.isArray(data)
        ? data
        : Array.isArray(data?.data)
          ? data.data
          : [];
      setData(vehicles.map(normalizeVehicle));
    } catch (err: any) {
      console.error('Failed to fetch vehicles', err);
      setData([]);
      setLoadError(t('vehicles.loadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchVehicles();
  }, [fetchVehicles]);
  useEffect(() => {
    if (!loading) {
      setShowLoadingIndicator(false);
      return;
    }
    const timer = window.setTimeout(() => setShowLoadingIndicator(true), 250);
    return () => window.clearTimeout(timer);
  }, [loading]);
  useEffect(() => {
    const { startDate, endDate } = advancedFilters;
    if (!startDate || !endDate || startDate > endDate) {
      setAvailabilityIds(null);
      return;
    }
    let cancelled = false;
    setAvailabilityLoading(true);
    api.get('/availability/vehicles', {
      params: { startDate, endDate, startTime: '09:00', endTime: '18:00' },
    }).then(({ data: response }) => {
      if (cancelled) return;
      const rows = Array.isArray(response?.data) ? response.data : [];
      setAvailabilityIds(new Set(rows.map((row: { id: number }) => Number(row.id))));
    }).catch(() => {
      if (!cancelled) setAvailabilityIds(new Set());
    }).finally(() => {
      if (!cancelled) setAvailabilityLoading(false);
    });
    return () => { cancelled = true; };
  }, [advancedFilters.startDate, advancedFilters.endDate]);

  const fetchTrash = useCallback(async () => {
    try {
      setTrashLoading(true);
      setTrashLoadError('');
      const { data } = await api.get('/vehicles/trash');
      const items = Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
      setTrashData(items.map((vehicle: TrashedVehicle) => ({
        ...vehicle,
        statut: normalizeStatusCode(vehicle.statut) || 'AVAILABLE',
      })));
    } catch (err: any) {
      console.error('Failed to fetch vehicle trash', err);
      setTrashLoadError(err?.userMessage || 'Unable to load trashed vehicles. Please try again later.');
    } finally {
      setTrashLoading(false);
    }
  }, []);

  useEffect(() => {
    if (filter === 'Trash') fetchTrash();
  }, [filter, fetchTrash]);

  const restoreVehicle = async (id: number) => {
    if (restoringId) return;
    setRestoringId(id);
    try {
      const { data: response } = await api.post(`/vehicles/${id}/restore`);
      showToast(response?.message || 'Vehicle restored successfully', 'success');
      setTrashData((current) => current.filter((v) => v.id !== id));
      fetchVehicles();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to restore this vehicle. Please try again later.', 'error');
    } finally {
      setRestoringId(null);
    }
  };

  const purgeVehiclePermanently = async (id: number, marque: string) => {
    if (purgingId) return;
    if (!confirm(`Permanently delete vehicle "${marque}"? This cannot be undone.`)) return;
    setPurgingId(id);
    try {
      const { data: response } = await api.delete(`/vehicles/${id}/purge`);
      showToast(response?.message || 'Vehicle permanently deleted', 'success');
      setTrashData((current) => current.filter((v) => v.id !== id));
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to permanently delete this vehicle. Please try again later.', 'error');
    } finally {
      setPurgingId(null);
    }
  };


  const filters = [
    { id: 'All', label: t('vehicles.all') },
    { id: 'AVAILABLE', label: t('vehicles.available') },
    { id: 'RESERVED', label: t('common.reserved') },
    { id: 'RENTED', label: t('vehicles.rented') },
    { id: 'MAINTENANCE', label: t('vehicles.maintenance') },
    { id: 'OUT_OF_SERVICE', label: t('vehicles.outOfService') },
  ];

  const categories = Array.from(new Set(data.map((v) => v.category).filter(Boolean))).sort();
  const brands = Array.from(new Set(data.map((v) => v.brand).filter((value): value is string => Boolean(value)))).sort();
  const fuels = Array.from(new Set(data.map((v) => v.fuel).filter(Boolean))).sort();
  const transmissions = Array.from(new Set(data.map((v) => v.transmission).filter(Boolean))).sort();

  const filteredData = data.filter((v) => {
    const technicalMaintenance = v.statut === 'MAINTENANCE' || v.statut === 'IN_MAINTENANCE';
    const matchesStatus = filter === 'All'
      || (filter === 'MAINTENANCE' ? technicalMaintenance : v.statut === filter);
    const q = searchQuery.trim().toLowerCase();
    const matchesSearch = !q || [v.marque, v.brand, v.model, v.category, v.plate]
      .some((value) => value?.toLowerCase().includes(q));
    const matchesAdvanced = (!advancedFilters.category || v.category === advancedFilters.category)
      && (!advancedFilters.brand || v.brand === advancedFilters.brand)
      && (!advancedFilters.fuel || v.fuel === advancedFilters.fuel)
      && (!advancedFilters.transmission || v.transmission === advancedFilters.transmission)
      && (!advancedFilters.seats || v.seatCount === Number(advancedFilters.seats))
      && (!advancedFilters.minPrice || v.prixJour >= Number(advancedFilters.minPrice))
      && (!advancedFilters.maxPrice || v.prixJour <= Number(advancedFilters.maxPrice))
      && (availabilityIds == null || availabilityIds.has(v.id));
    return matchesStatus && matchesSearch && matchesAdvanced;
  });

  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [exportFormat, setExportFormat] = useState<'pdf' | 'xlsx' | 'csv'>('pdf');
  const [exportScope, setExportScope] = useState<'all' | 'filtered'>('filtered');
  const [exporting, setExporting] = useState(false);

  /**
   * Real server-side export — replaces the old client-side comma-join that
   * produced a mislabeled, mojibake-prone "fleet.csv" (no BOM, no quoting,
   * everything jammed into one cell in French-locale Excel). The backend now
   * returns a real PDF, a real .xlsx workbook, or a correctly-encoded CSV.
   */
  const runExport = async () => {
    if (exporting) return;
    setExporting(true);
    try {
      const params: Record<string, string> = {};
      if (exportScope === 'filtered') {
        if (filter !== 'All') params.status = filter.toUpperCase();
        if (searchQuery.trim()) params.search = searchQuery.trim();
      }
      const response = await api.get(`/vehicles/export/${exportFormat}`, {
        params,
        responseType: 'blob',
      });

      const contentType = String(response.headers['content-type'] || '');
      if (contentType.includes('application/json')) {
        // A 2xx with a JSON body would be unexpected, but never trust
        // Content-Type blindly — treat any JSON body as an error payload
        // rather than downloading it as a fake PDF/Excel/CSV file.
        const text = await (response.data as Blob).text();
        const parsed = JSON.parse(text);
        throw new Error(parsed.message || t('vehicles.export.errors.generic'));
      }

      const disposition: string = String(response.headers['content-disposition'] || '');
      const match = disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)"?/i);
      const filename = match ? decodeURIComponent(match[1]) : `innovacar-flotte.${exportFormat}`;

      const blob = new Blob([response.data], { type: contentType || undefined });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);

      showToast(t('vehicles.export.downloadReady'));
      setIsExportModalOpen(false);
    } catch (err: any) {
      let message = t('vehicles.export.errors.generic');
      const data = err?.response?.data;
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text());
          const errorCode = parsed.errorCode;
          if (errorCode === 'EXPORT_NO_DATA') message = t('vehicles.export.errors.noData');
          else if (errorCode === 'EXPORT_TOO_LARGE') message = t('vehicles.export.errors.tooLarge');
          else if (parsed.message) message = parsed.message;
        } catch {
          // Body wasn't JSON either — fall back to the generic message below.
        }
      } else if (err?.message) {
        message = err.message;
      }
      showToast(message, 'error');
    } finally {
      setExporting(false);
    }
  };

  const openCreate = () => {
    if (!canCreateVehicle) {
      showToast(`Plan limit reached (${status?.maxVehicles} vehicles max). Please upgrade your plan.`, 'warning');
      return;
    }
    setEditingId(null);
    setSelectedVehicle(null);
    setModalMode('create');
    setInitialForm(null);
    setFieldErrors({});
    setForm({ brand: '', model: '', category: '', plate: '', statut: 'AVAILABLE', prixJour: '', fuel: '', transmission: '', seatCount: '', imageUrl: '' });
    setImagePreview(null);
    setIsModalOpen(true);
  };

  const vehicleToForm = (vehicle: Vehicle): typeof form => ({
    brand: vehicle.brand || '',
    model: vehicle.model || '',
    category: vehicle.category || '',
    plate: vehicle.plate || '',
    statut: vehicle.statut || 'AVAILABLE',
    prixJour: vehicle.prixJour != null ? String(vehicle.prixJour) : '',
    fuel: vehicle.fuel || '',
    transmission: vehicle.transmission || '',
    seatCount: vehicle.seatCount != null ? String(vehicle.seatCount) : '',
    imageUrl: vehicle.imageUrl || '',
  });

  const openDetails = (vehicle: Vehicle) => {
    setEditingId(vehicle.id);
    setSelectedVehicle(vehicle);
    setModalMode('view');
    setInitialForm(null);
    setFieldErrors({});
    setIsModalOpen(true);
  };

  const startEditing = () => {
    if (!selectedVehicle) return;
    const nextForm = vehicleToForm(selectedVehicle);
    setForm(nextForm);
    setInitialForm(nextForm);
    setImagePreview(selectedVehicle.imageUrl || null);
    setFieldErrors({});
    setModalMode('edit');
  };

  const cancelEditing = () => {
    if (initialForm) {
      setForm(initialForm);
      setImagePreview(initialForm.imageUrl || null);
    }
    setFieldErrors({});
    setModalMode('view');
  };

  const hasUnsavedChanges = modalMode === 'edit' && initialForm != null
    && JSON.stringify(form) !== JSON.stringify(initialForm);

  const closeVehicleModal = () => {
    if (hasUnsavedChanges && !window.confirm(t('vehicles.unsavedChangesConfirm'))) return;
    setIsModalOpen(false);
    setEditingId(null);
    setSelectedVehicle(null);
    setInitialForm(null);
    setFieldErrors({});
  };

  const updateFormField = (field: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const validateVehicleForm = () => {
    const errors: Record<string, string> = {};
    if (!form.brand.trim()) errors.brand = t('vehicles.validation.brandRequired');
    if (!form.model.trim()) errors.model = t('vehicles.validation.modelRequired', { defaultValue: 'Le modèle est obligatoire.' });
    if (!form.category) errors.category = t('vehicles.validation.categoryRequired', { defaultValue: 'La catégorie est obligatoire.' });
    if (!form.fuel) errors.fuel = t('vehicles.validation.fuelRequired', { defaultValue: 'Le carburant est obligatoire.' });
    if (!form.transmission) errors.transmission = t('vehicles.validation.transmissionRequired', { defaultValue: 'La transmission est obligatoire.' });
    if (!form.plate.trim()) errors.plate = t('vehicles.validation.plateRequired');
    if (!form.prixJour.trim()) errors.prixJour = t('vehicles.validation.dailyPriceRequired');
    else if (!Number.isFinite(Number(form.prixJour)) || Number(form.prixJour) < 0) errors.prixJour = t('vehicles.validation.dailyPriceInvalid');
    if (!form.seatCount.trim()) {
      errors.seatCount = t('vehicles.validation.seatCountRequired', { defaultValue: 'Le nombre de places est obligatoire.' });
    } else {
      const seats = Number(form.seatCount);
      if (!Number.isInteger(seats) || seats < 1 || seats > 100) {
        errors.seatCount = t('vehicles.validation.seatCountInvalid');
      }
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        const result = reader.result as string;
        setImagePreview(result);
        setForm((prev) => ({ ...prev, imageUrl: result }));
      };
      reader.readAsDataURL(file);
    }
  };

  const saveVehicle = async () => {
    if (!validateVehicleForm()) {
      showToast(t('vehicles.validation.requiredSummary'), 'warning');
      return;
    }
    try {
      const payload = {
        brand: form.brand.trim(),
        model: form.model.trim(),
        marque: `${form.brand.trim()} ${form.model.trim()}`,
        prixJour: Number(form.prixJour),
        statut: editingId && ['RESERVED', 'RENTED'].includes(form.statut) ? undefined : form.statut,
        category: form.category || null,
        plate: form.plate.trim(),
        fuel: form.fuel,
        transmission: form.transmission,
        seatCount: form.seatCount.trim() ? Number(form.seatCount) : null,
        imageUrl: form.imageUrl || null,
      };
      if (editingId) {
        const { data: updated } = await api.put(`/vehicles/${editingId}`, payload);
        const normalized = normalizeVehicle(updated);
        setData((prev) => prev.map((v) => (v.id === editingId ? normalized : v)));
        setSelectedVehicle(normalized);
        setForm(vehicleToForm(normalized));
        setInitialForm(null);
        setModalMode('view');
        showToast(t('vehicles.updateSuccess'));
      } else {
        const { data: newVehicle } = await api.post('/vehicles', payload);
        setData((prev) => [...prev, normalizeVehicle(newVehicle)]);
        showToast(t('toast.newVehicleAdded'));
      }
      if (!editingId) {
        setIsModalOpen(false);
        setEditingId(null);
      }
      setFieldErrors({});
    } catch (err: any) {
      const details = (err as any).response?.data?.details;
      const msg = (err as any).userMessage || t('vehicles.saveFailed');
      let fullMsg = `${t('common.error')}: ${msg}`;
      if (details && typeof details === 'object') {
        const fieldErrors = Object.entries(details).map(([field, error]) => `${field}: ${error}`).join(', ');
        fullMsg += ' - ' + fieldErrors;
      }
      showToast(fullMsg, 'error');
      console.error('Vehicle save error:', err);
    }
  };

  const fieldError = (field: string) => (
    fieldErrors[field] ? <p className="mt-1 text-xs font-medium text-danger-500">{fieldErrors[field]}</p> : null
  );

  const fieldBorder = (field: string) => (
    fieldErrors[field] ? '1px solid #ef4444' : '1px solid var(--border-subtle)'
  );

  const deleteVehicle = async (id: number) => {
    if (!confirm('Move this vehicle to trash?')) return;
    try {
      const { data: response } = await api.delete(`/vehicles/${id}`);
      showToast(response?.message || 'Vehicle moved to trash', 'success');
      fetchVehicles();
    } catch (err: any) {
      showToast(err?.userMessage || t('vehicles.deleteFailed'), 'error');
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <GlassPageHeader
        title={t('vehicles.title')}
        subtitle={t('vehicles.subtitle', { count: data.length })}
        icon={Car}
        actions={
          <>
            <motion.button
              onClick={() => { setExportFormat('pdf'); setIsExportModalOpen(true); }}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl font-medium text-xs sm:text-sm transition-all"
              style={{
                backgroundColor: 'var(--bg-card)',
                color: 'var(--text-primary)',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <Download size={16} className="sm:hidden" />
              <Download size={18} className="hidden sm:block" />
              <span className="hidden sm:inline">{t('vehicles.exportFleet')}</span>
            </motion.button>
            <motion.button
              onClick={openCreate}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 transition-all"
            >
              <Plus size={16} className="sm:hidden" />
              <Plus size={18} className="hidden sm:block" />
              {t('vehicles.addVehicle')}
            </motion.button>
          </>
        }
      />

      <div className="flex flex-col gap-3">
        <SearchInput
          placeholder={t('vehicles.searchPlaceholder')}
          value={searchQuery}
          onChange={setSearchQuery}
          className="w-full"
        />
        <div className="flex flex-wrap items-center gap-2">
          <FilterChips
            options={filters}
            activeId={filter}
            onChange={setFilter}
          />
          <label className="relative inline-flex items-center gap-2 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)]">
            <MoreHorizontal size={15} />
            <span>{t('vehicles.more')}</span>
            <select
              aria-label={t('vehicles.more')}
              value={filter === 'ARCHIVED' || filter === 'Trash' ? filter : ''}
              onChange={(event) => event.target.value && setFilter(event.target.value)}
              className="absolute inset-0 cursor-pointer opacity-0"
            >
              <option value="">{t('vehicles.more')}</option>
              <option value="ARCHIVED">{t('vehicles.archived')}</option>
              <option value="Trash">{t('vehicles.trash')}</option>
            </select>
          </label>
          <button
            type="button"
            onClick={() => setShowAdvancedFilters((current) => !current)}
            className="inline-flex items-center gap-2 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)]"
          >
            <SlidersHorizontal size={15} />
            {t('vehicles.filtersButton')}
          </button>
        </div>
        {showAdvancedFilters && (
          <div dir={i18n.dir()} className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] p-3 text-start sm:p-4">
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-semibold text-[var(--text-primary)]">{t('vehicles.advancedFilters')}</p>
              <button type="button" onClick={() => setShowAdvancedFilters(false)} className="rounded-lg p-1.5 text-[var(--text-muted)] hover:bg-[var(--bg-hover)]"><X size={16} /></button>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {[
                ['category', t('vehicles.category'), categories],
                ['brand', t('vehicles.brand'), brands],
                ['fuel', t('vehicles.fuel'), fuels],
                ['transmission', t('vehicles.transmission'), transmissions],
              ].map(([key, label, options]) => (
                <label key={String(key)} className="space-y-1 text-xs font-medium text-[var(--text-muted)]">
                  <span>{String(label)}</span>
                  <select value={advancedFilters[key as 'category' | 'brand' | 'fuel' | 'transmission']} onChange={(event) => setAdvancedFilters((current) => ({ ...current, [key as string]: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]">
                    <option value="">{t('common.all')}</option>
                    {(options as string[]).map((option) => <option key={option} value={option}>{option}</option>)}
                  </select>
                </label>
              ))}
              <label className="space-y-1 text-xs font-medium text-[var(--text-muted)]"><span>{t('vehicles.seatCount')}</span><input type="number" min={1} step={1} value={advancedFilters.seats} onChange={(event) => setAdvancedFilters((current) => ({ ...current, seats: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]" /></label>
              <label className="space-y-1 text-xs font-medium text-[var(--text-muted)]"><span>{t('vehicles.minPrice')}</span><input type="number" min={0} value={advancedFilters.minPrice} onChange={(event) => setAdvancedFilters((current) => ({ ...current, minPrice: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]" /></label>
              <label className="space-y-1 text-xs font-medium text-[var(--text-muted)]"><span>{t('vehicles.maxPrice')}</span><input type="number" min={0} value={advancedFilters.maxPrice} onChange={(event) => setAdvancedFilters((current) => ({ ...current, maxPrice: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]" /></label>
              <div className="space-y-1 text-xs font-medium text-[var(--text-muted)] sm:col-span-2 lg:col-span-2"><span>{t('vehicles.availableBetween')}</span><div className="grid grid-cols-2 gap-2"><input type="date" value={advancedFilters.startDate} onChange={(event) => setAdvancedFilters((current) => ({ ...current, startDate: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]" /><input type="date" min={advancedFilters.startDate} value={advancedFilters.endDate} onChange={(event) => setAdvancedFilters((current) => ({ ...current, endDate: event.target.value }))} className="w-full rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-hover)] px-3 py-2 text-sm text-[var(--text-primary)]" /></div>{availabilityLoading && <span className="inline-flex items-center gap-1"><Loader2 size={12} className="animate-spin" />{t('common.loading')}</span>}</div>
            </div>
            <button type="button" onClick={() => { setAdvancedFilters({ category: '', brand: '', fuel: '', transmission: '', seats: '', minPrice: '', maxPrice: '', startDate: '', endDate: '' }); setAvailabilityIds(null); }} className="mt-4 inline-flex min-h-10 items-center justify-center rounded-xl border border-brand-500/30 bg-brand-500/10 px-4 py-2 text-xs font-semibold text-brand-600 transition-colors hover:bg-brand-500/15 dark:text-brand-400">{t('common.reset')}</button>
          </div>
        )}
      </div>

      {filter === 'Trash' ? (
        trashLoadError ? (
          <ApiErrorState message={trashLoadError} onRetry={fetchTrash} />
        ) : trashLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 size={32} className="animate-spin text-brand-500" />
          </div>
        ) : (
          <div className="data-surface">
            <ResponsiveDataView
              mobile={
                trashData.length === 0 ? (
                  <div className="flex flex-col items-center gap-2 py-8 text-center text-xs text-slate-400">
                    <Trash2 size={20} className="text-slate-300" />
                    Trash is empty
                  </div>
                ) : (
                  <div className="space-y-3 p-3">
                    {trashData.map((vehicle) => (
                      <div key={vehicle.id} className="rounded-xl border border-[#e8e6e1]/60 bg-white p-3 space-y-2">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-[#1e293b]">{vehicle.marque}</p>
                            <p className="font-mono text-xs text-slate-400">{vehicle.plate || 'N/A'}</p>
                          </div>
                          <span className={`shrink-0 rounded-lg px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${
                            vehicle.daysRemaining <= 3 ? 'bg-danger-50 text-danger-500' : 'bg-warning-50 text-warning-500'
                          }`}>
                            {t('vehicles.daysLeft', { count: vehicle.daysRemaining })}
                          </span>
                        </div>
                        <p className="text-xs text-slate-400">
                          {vehicle.deletedAt ? new Date(vehicle.deletedAt).toLocaleString() : 'N/A'}
                        </p>
                        <div className="flex items-center gap-2 border-t border-[#e8e6e1]/60 pt-2">
                          <button
                            onClick={() => restoreVehicle(vehicle.id)}
                            disabled={restoringId === vehicle.id}
                            className="flex min-h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-success-50 text-sm font-semibold text-success-600 disabled:opacity-50"
                          >
                            {restoringId === vehicle.id ? <Loader2 size={15} className="animate-spin" /> : <RotateCcw size={15} />}
                            {t('common.restore')}
                          </button>
                          <button
                            onClick={() => purgeVehiclePermanently(vehicle.id, vehicle.marque)}
                            disabled={purgingId === vehicle.id}
                            className="flex min-h-11 min-w-11 items-center justify-center rounded-lg bg-danger-50 text-danger-500 disabled:opacity-50"
                            title={t('common.deletePermanently')}
                          >
                            {purgingId === vehicle.id ? <Loader2 size={15} className="animate-spin" /> : <Trash2 size={15} />}
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )
              }
              desktop={
            <div className="overflow-x-auto no-scrollbar">
              <table className="w-full text-left min-w-[600px]">
                <thead>
                  <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('vehicles.vehicle')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('vehicles.plate')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('vehicles.deleted')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('vehicles.daysRemaining')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#e8e6e1]/50">
                  {trashData.map((vehicle) => (
                    <tr key={vehicle.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-medium text-[#1e293b]">{vehicle.marque}</td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400 font-mono">{vehicle.plate || 'N/A'}</td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400">
                        {vehicle.deletedAt ? new Date(vehicle.deletedAt).toLocaleString() : 'N/A'}
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4">
                        <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider w-fit ${
                          vehicle.daysRemaining <= 3 ? 'bg-danger-50 text-danger-500' : 'bg-warning-50 text-warning-500'
                        }`}>
                          {t('vehicles.daysLeft', { count: vehicle.daysRemaining })}
                        </span>
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-right">
                        <div className="flex items-center justify-end gap-0.5 sm:gap-1">
                          <button
                            onClick={() => restoreVehicle(vehicle.id)}
                            disabled={restoringId === vehicle.id}
                            className="p-1.5 sm:p-2 text-slate-400 hover:text-success-500 hover:bg-success-50 rounded-lg transition-all disabled:opacity-50"
                            title={t('common.restore')}
                          >
                            {restoringId === vehicle.id
                              ? <Loader2 size={17} className="animate-spin" />
                              : <RotateCcw size={17} />}
                          </button>
                          <button
                            onClick={() => purgeVehiclePermanently(vehicle.id, vehicle.marque)}
                            disabled={purgingId === vehicle.id}
                            className="p-1.5 sm:p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all disabled:opacity-50"
                            title={t('common.deletePermanently')}
                          >
                            {purgingId === vehicle.id
                              ? <Loader2 size={17} className="animate-spin" />
                              : <Trash2 size={17} />}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {trashData.length === 0 && (
                    <tr><td colSpan={5} className="px-3 sm:px-5 py-6 sm:py-8 text-center text-slate-400 text-xs sm:text-sm">
                      <div className="flex flex-col items-center gap-2">
                        <Trash2 size={20} className="text-slate-300" />
                        Trash is empty
                      </div>
                    </td></tr>
                  )}
                </tbody>
              </table>
            </div>
              }
            />
            {trashData.length > 0 && (
              <div className="px-3 sm:px-5 py-3 flex items-center gap-2 text-[11px] text-slate-400 border-t border-[#e8e6e1]/50">
                <AlertTriangle size={12} />
                Vehicles are permanently deleted automatically 30 days after being trashed (unless still linked to a contract/reservation).
              </div>
            )}
          </div>
        )
      ) : loadError ? (
        <ApiErrorState message={loadError} onRetry={fetchVehicles} />
      ) : loading ? (
        <div className="flex min-h-40 items-center justify-center py-12" aria-busy="true">
          {showLoadingIndicator && (
            <Loader2 size={20} className="animate-spin text-brand-500" aria-label={t('common.loading')} />
          )}
        </div>
      ) : (
        <motion.div
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          {filteredData.map((vehicle, index) => (
            <motion.div key={vehicle.id} variants={itemVariants}>
              <GlassCard
                padding="none"
                hover
                delay={index * 60}
                className="overflow-hidden group"
              >
                <div className="relative h-40 overflow-hidden">
                  <img
                    src={vehicle.imageUrl || 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400'}
                    alt={vehicle.marque}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
                    onError={(e) => { (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400'; }}
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent"></div>
                  <div className="absolute top-3 end-3">
                    <StatusBadge
                      variant={statusVariantMap[vehicle.statut] || 'neutral'}
                      size="sm"
                    >
                      {translateVehicleStatus(vehicle.statut)}
                    </StatusBadge>
                  </div>
                  <motion.button
                    onClick={() => deleteVehicle(vehicle.id)}
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    className="absolute top-3 start-3 p-2 rounded-lg transition-all opacity-0 group-hover:opacity-100"
                    style={{
                      backgroundColor: 'var(--bg-card)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                  </motion.button>
                </div>

                <div className="p-3 sm:p-5">
                  <div className="flex justify-between items-start mb-3">
                    <div className="min-w-0">
                      <h3 className="text-sm font-semibold group-hover:text-brand-500 transition-colors" style={{ color: 'var(--text-primary)' }}>{vehicle.marque}</h3>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-[11px] font-bold uppercase tracking-widest" style={{ color: 'var(--text-muted)' }}>{vehicle.category ? translateVehicleCategory(vehicle.category) : '—'}</span>
                        <div className="w-1 h-1 rounded-full" style={{ backgroundColor: 'var(--border-subtle)' }}></div>
                        <span className="text-[11px] font-mono font-medium" style={{ color: 'var(--text-muted)' }}>{vehicle.plate || `PLT-${vehicle.id}`}</span>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{vehicle.prixJour}<span className="text-[10px] font-medium ms-1" style={{ color: 'var(--text-muted)' }}>{t('vehicles.perDay')}</span></p>
                    </div>
                  </div>

                  <div
                    className="grid grid-cols-3 gap-3 py-3 mb-4 transition-colors"
                    style={{
                      borderTop: '1px solid var(--border-subtle)',
                      borderBottom: '1px solid var(--border-subtle)',
                    }}
                  >
                    <div className="flex flex-col items-center gap-1">
                      <UsersIcon size={15} className="group-hover:text-brand-400 transition-colors" style={{ color: 'var(--text-muted)' }} />
                      <span className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>{vehicle.seatCount ?? '—'}</span>
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>{t('vehicles.seats')}</span>
                    </div>
                    <div className="flex flex-col items-center gap-1">
                      <Fuel size={15} className="group-hover:text-brand-400 transition-colors" style={{ color: 'var(--text-muted)' }} />
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>{vehicle.fuel ? translateFuelType(vehicle.fuel) : '—'}</span>
                    </div>
                    <div className="flex flex-col items-center gap-1">
                      <Shield size={15} className="group-hover:text-brand-400 transition-colors" style={{ color: 'var(--text-muted)' }} />
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>{vehicle.transmission ? translateTransmission(vehicle.transmission) : '—'}</span>
                    </div>
                  </div>

                  <motion.button
                    onClick={() => openDetails(vehicle)}
                    whileHover={{ scale: 1.01 }}
                    whileTap={{ scale: 0.98 }}
                    className="w-full py-2.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-2"
                    style={{
                      backgroundColor: 'var(--bg-hover)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    {t('vehicles.viewDetails')}
                    <ChevronRight size={16} />
                  </motion.button>
                </div>
              </GlassCard>
            </motion.div>
          ))}
          {filteredData.length === 0 && (
            <div className="col-span-full py-12 text-center" style={{ color: 'var(--text-muted)' }}>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {filter === 'AVAILABLE' && !searchQuery
                  ? t('vehicles.noAvailableVehicles')
                  : data.length === 0
                    ? t('vehicles.emptyTitle')
                    : t('vehicles.noFilterResults')}
              </p>
              <p className="mt-1 text-xs">
                {data.length === 0 ? t('vehicles.emptyHint') : t('vehicles.noFilterHint')}
              </p>
            </div>
          )}
        </motion.div>
      )}

      <Modal
        isOpen={isModalOpen}
        onClose={closeVehicleModal}
        title={modalMode === 'view' ? t('vehicles.detailsTitle') : modalMode === 'edit' ? t('vehicles.editTitle') : t('vehicles.addVehicle')}
        maxWidth="max-w-xl"
      >
        {modalMode === 'view' && selectedVehicle ? (
          <div className="space-y-5">
            <div className="h-48 overflow-hidden rounded-2xl bg-[var(--bg-hover)]">
              {selectedVehicle.imageUrl ? (
                <img src={selectedVehicle.imageUrl} alt={selectedVehicle.marque} className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full items-center justify-center text-[var(--text-muted)]"><Car size={40} /></div>
              )}
            </div>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h4 className="text-lg font-bold text-[var(--text-primary)]">{selectedVehicle.brand || '—'} {selectedVehicle.model || ''}</h4>
                <p className="text-sm text-[var(--text-muted)]">{selectedVehicle.plate || '—'}</p>
              </div>
              <StatusBadge variant={statusVariantMap[selectedVehicle.statut] || 'neutral'}>
                {translateVehicleStatus(selectedVehicle.statut)}
              </StatusBadge>
            </div>
            <dl className="grid grid-cols-1 gap-x-6 gap-y-4 rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] p-4 sm:grid-cols-2">
              {[
                [t('vehicles.brand'), selectedVehicle.brand || '—'],
                [t('vehicles.model'), selectedVehicle.model || '—'],
                [t('vehicles.category'), selectedVehicle.category ? translateVehicleCategory(selectedVehicle.category) : '—'],
                [t('vehicles.registration'), selectedVehicle.plate || '—'],
                [t('vehicles.pricePerDay'), `${selectedVehicle.prixJour ?? '—'} DH`],
                [t('vehicles.fuel'), selectedVehicle.fuel ? translateFuelType(selectedVehicle.fuel) : '—'],
                [t('vehicles.transmission'), selectedVehicle.transmission ? translateTransmission(selectedVehicle.transmission) : '—'],
                [t('vehicles.seatCount'), selectedVehicle.seatCount ?? '—'],
              ].map(([label, value]) => (
                <div key={String(label)}>
                  <dt className="text-xs font-semibold uppercase tracking-wide text-[var(--text-muted)]">{label}</dt>
                  <dd className="mt-1 text-sm font-semibold text-[var(--text-primary)]">{value}</dd>
                </div>
              ))}
            </dl>
            <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
              <button type="button" onClick={closeVehicleModal} className="rounded-xl border border-[var(--border-subtle)] px-5 py-2.5 text-sm font-semibold text-[var(--text-secondary)]">
                {t('vehicles.close')}
              </button>
              <button type="button" onClick={startEditing} className="rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-600">
                {t('vehicles.edit')}
              </button>
            </div>
          </div>
        ) : (
        <div className="space-y-4">
          {/* Image Upload */}
          <div
            className="p-3 rounded-xl"
            style={{
              backgroundColor: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
            }}
          >
            <label className="block text-sm font-bold mb-2" style={{ color: 'var(--text-primary)' }}>📷 {t('vehicles.vehicleImage')}</label>
            <div className="relative">
              <input
                type="file"
                accept="image/*"
                onChange={handleImageUpload}
                className="hidden"
                id="vehicle-image-upload"
              />
              <label
                htmlFor="vehicle-image-upload"
                className="flex flex-col items-center justify-center w-full h-28 border-2 border-dashed rounded-xl cursor-pointer transition-all overflow-hidden"
                style={{
                  backgroundColor: 'var(--bg-hover)',
                  borderColor: 'var(--border-subtle)',
                }}
              >
                {imagePreview ? (
                  <div className="relative w-full h-full">
                    <img src={imagePreview} alt={t('vehicles.preview')} className="w-full h-full object-cover" />
                  </div>
                ) : (
                  <div className="flex flex-col items-center">
                    <div className="w-10 h-10 rounded-full flex items-center justify-center mb-1.5" style={{ backgroundColor: 'var(--bg-hover)' }}>
                      <Camera size={20} style={{ color: 'var(--text-muted)' }} />
                    </div>
                    <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{t('vehicles.clickToUpload')}</span>
                    <span className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>{t('vehicles.imageSizeHint')}</span>
                  </div>
                )}
              </label>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.brand')} *</label>
              <input type="text" value={form.brand} onChange={(e) => updateFormField('brand', e.target.value)} aria-invalid={Boolean(fieldErrors.brand)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: fieldBorder('brand'), color: 'var(--text-primary)' }} />
              {fieldError('brand')}
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.model', { defaultValue: 'Modèle' })} *</label>
              <input type="text" value={form.model} onChange={(e) => updateFormField('model', e.target.value)} aria-invalid={Boolean(fieldErrors.model)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: fieldBorder('model'), color: 'var(--text-primary)' }} />
              {fieldError('model')}
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.category')} *</label>
              <select value={form.category} onChange={(e) => updateFormField('category', e.target.value)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="">{t('vehicles.selectCategory')}</option>
                {vehicleCategories.map((category) => <option key={category} value={category}>{translateVehicleCategory(category)}</option>)}
              </select>
              {fieldError('category')}
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.registration', { defaultValue: 'Immatriculation' })} *</label>
              <input type="text" placeholder={t('vehicles.registrationPlaceholder', { defaultValue: 'Ex. : 12345-A-6' })} value={form.plate} onChange={(e) => updateFormField('plate', e.target.value)} aria-invalid={Boolean(fieldErrors.plate)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: fieldBorder('plate'), color: 'var(--text-primary)' }} />
              {fieldError('plate')}
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.status')}</label>
              <select value={form.statut} disabled={Boolean(editingId && ['RESERVED', 'RENTED'].includes(form.statut))} onChange={(e) => updateFormField('statut', e.target.value)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                {editingId && ['RESERVED', 'RENTED'].includes(form.statut) && (
                  <option value={form.statut}>{translateVehicleStatus(form.statut)}</option>
                )}
                <option value="AVAILABLE">{t('common.available')}</option>
                <option value="MAINTENANCE">{t('common.maintenance')}</option>
                <option value="OUT_OF_SERVICE">{t('vehicles.outOfService')}</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.pricePerDay')} *</label>
              <input type="number" value={form.prixJour} onChange={(e) => updateFormField('prixJour', e.target.value)} aria-invalid={Boolean(fieldErrors.prixJour)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: fieldBorder('prixJour'), color: 'var(--text-primary)' }} />
              {fieldError('prixJour')}
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.fuel')} *</label>
              <select value={form.fuel} onChange={(e) => updateFormField('fuel', e.target.value)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="">—</option>
                <option value="Essence">{t('vehicles.essence')}</option>
                <option value="Diesel">{t('vehicles.diesel')}</option>
                <option value="Hybrid">{t('vehicles.hybrid')}</option>
                <option value="Electric">{t('vehicles.electric')}</option>
              </select>
              {fieldError('fuel')}
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.transmission')} *</label>
              <select value={form.transmission} onChange={(e) => updateFormField('transmission', e.target.value)} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="">—</option>
                <option value="Manual">{t('vehicles.manual')}</option>
                <option value="Automatic">{t('vehicles.automatic')}</option>
              </select>
              {fieldError('transmission')}
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.seatCount')} *</label>
              <input
                type="number"
                inputMode="numeric"
                min={1}
                max={100}
                step={1}
                placeholder={t('vehicles.seatCountPlaceholder')}
                value={form.seatCount}
                onChange={(e) => updateFormField('seatCount', e.target.value)}
                aria-invalid={Boolean(fieldErrors.seatCount)}
                className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all"
                style={{ backgroundColor: 'var(--bg-hover)', border: fieldBorder('seatCount'), color: 'var(--text-primary)' }}
              />
              {fieldError('seatCount')}
            </div>
          </div>
          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            {modalMode === 'edit' && (
              <button type="button" onClick={cancelEditing} className="rounded-xl border border-[var(--border-subtle)] px-5 py-2.5 text-sm font-semibold text-[var(--text-secondary)]">
                {t('vehicles.cancel')}
              </button>
            )}
            <motion.button
              onClick={saveVehicle}
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.98 }}
              className="rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10"
            >
              {modalMode === 'edit' ? t('vehicles.saveChanges') : t('vehicles.addVehicle')}
            </motion.button>
          </div>
        </div>
        )}
      </Modal>

      {/* Export modal — PDF is the primary/default format for agency users */}
      <Modal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
        title={t('vehicles.export.title')}
        footer={
          <button
            onClick={runExport}
            disabled={exporting}
            className="w-full min-h-[44px] flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 active:scale-95 transition-all disabled:opacity-60 disabled:cursor-wait"
          >
            {exporting ? <><Loader2 size={16} className="animate-spin" />{t('vehicles.export.generating')}</> : t('vehicles.export.exportButton')}
          </button>
        }
      >
        <div className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wide mb-2">{t('vehicles.export.format')}</label>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              {([
                { key: 'pdf', label: t('vehicles.export.formatPdf'), icon: FileText },
                { key: 'xlsx', label: t('vehicles.export.formatExcel'), icon: FileSpreadsheet },
                { key: 'csv', label: t('vehicles.export.formatCsv'), icon: FileType },
              ] as const).map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setExportFormat(key)}
                  className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 text-sm font-medium transition-all min-h-[44px] ${
                    exportFormat === key ? 'border-brand-500 bg-brand-500/10 text-brand-500' : 'border-[var(--border-subtle)] text-[var(--text-secondary)]'
                  }`}
                >
                  <Icon size={22} />
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wide mb-2">{t('vehicles.export.scope')}</label>
            <div className="flex flex-col sm:flex-row gap-2.5">
              <button
                onClick={() => setExportScope('all')}
                className={`flex-1 min-h-[44px] px-4 py-2.5 rounded-xl border-2 text-sm font-medium transition-all ${
                  exportScope === 'all' ? 'border-brand-500 bg-brand-500/10 text-brand-500' : 'border-[var(--border-subtle)] text-[var(--text-secondary)]'
                }`}
              >
                {t('vehicles.export.scopeAll')}
              </button>
              <button
                onClick={() => setExportScope('filtered')}
                className={`flex-1 min-h-[44px] px-4 py-2.5 rounded-xl border-2 text-sm font-medium transition-all ${
                  exportScope === 'filtered' ? 'border-brand-500 bg-brand-500/10 text-brand-500' : 'border-[var(--border-subtle)] text-[var(--text-secondary)]'
                }`}
              >
                {t('vehicles.export.scopeFiltered')}
              </button>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
}
