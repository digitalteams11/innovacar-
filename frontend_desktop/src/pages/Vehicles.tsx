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
import { Plus, Download, ChevronRight, Fuel, Shield, Users as UsersIcon, Camera, Loader2, Car } from 'lucide-react';
import api from '../api/axios';
import { useSubscription } from '../hooks/useSubscription';
import ApiErrorState from '../components/ApiErrorState';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import { normalizeStatusCode, translateFuelType, translateTransmission, translateVehicleCategory, translateVehicleStatus } from '../utils/statusLabels';

interface Vehicle {
  id: number;
  marque: string;
  category: string;
  plate: string;
  statut: string;
  prixJour: number;
  fuel: string;
  transmission: string;
  imageUrl: string;
  branchId?: number;
  branchName?: string;
}

interface BranchOption {
  id: number;
  name: string;
  active: boolean;
}

const vehicleCategories = ['Economy', 'Compact', 'Sedan', 'SUV', 'Luxury', 'Sport', 'Electric', 'Hybrid', 'Van', 'Pickup', 'Minibus', 'Truck', 'Motorcycle'];

const statusVariantMap: Record<string, 'available' | 'rented' | 'maintenance'> = {
  AVAILABLE: 'available',
  RESERVED: 'rented',
  RENTED: 'rented',
  MAINTENANCE: 'maintenance',
  IN_MAINTENANCE: 'maintenance',
  OUT_OF_SERVICE: 'maintenance',
  SOLD: 'maintenance',
  ARCHIVED: 'maintenance',
};

const normalizeVehicle = (vehicle: Vehicle): Vehicle => ({
  ...vehicle,
  statut: normalizeStatusCode(vehicle.statut) || 'AVAILABLE',
});

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
  const [data, setData] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({
    marque: '',
    category: '',
    plate: '',
    statut: 'AVAILABLE',
    prixJour: '',
    fuel: 'Essence',
    transmission: 'Manual',
    imageUrl: '',
    branchId: '',
  });
  const [branches, setBranches] = useState<BranchOption[]>([]);
  const [imagePreview, setImagePreview] = useState<string | null>(null);

  const { showToast } = useToast();
  const { t } = useTranslation();
  const { canCreateVehicle, status } = useSubscription();
  const { hasFeature } = useFeatureAccess();

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
      setLoadError(err?.userMessage || 'Unable to load vehicle information. Please try again later.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchVehicles();
  }, [fetchVehicles]);

  useEffect(() => {
    if (!hasFeature('MULTI_BRANCH')) return;
    api.get<any>('/branches')
      .then(({ data }) => {
        const items = Array.isArray(data) ? data : Array.isArray(data?.data) ? data.data : [];
        setBranches(items.filter((branch: BranchOption) => branch.active));
      })
      .catch((requestError) => console.error('Unable to load branches', requestError));
  }, [hasFeature]);

  const filters = [
    { id: 'All', label: t('vehicles.all') },
    { id: 'Available', label: t('vehicles.available') },
    { id: 'Rented', label: t('vehicles.rented') },
    { id: 'In_Maintenance', label: t('vehicles.maintenance') },
    { id: 'Out_Of_Service', label: t('vehicles.outOfService') },
    { id: 'Archived', label: t('vehicles.archived') },
  ];

  const filteredData = data.filter((v) => {
    const matchesFilter = filter === 'All' || v.statut === filter.toUpperCase();
    const q = searchQuery.toLowerCase();
    const matchesSearch = v.marque?.toLowerCase().includes(q) || v.category?.toLowerCase().includes(q) || v.plate?.toLowerCase().includes(q);
    return matchesFilter && matchesSearch;
  });

  const exportFleet = () => {
    const headers = ['ID', t('vehicles.brandModel'), t('vehicles.category'), t('vehicles.plate'), t('vehicles.status'), t('vehicles.pricePerDay'), t('vehicles.fuel'), t('vehicles.transmission')];
    const rows = filteredData.map((v) => [
      v.id,
      v.marque,
      translateVehicleCategory(v.category),
      v.plate,
      translateVehicleStatus(v.statut),
      v.prixJour,
      translateFuelType(v.fuel),
      translateTransmission(v.transmission),
    ]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'fleet.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.fleetExported'));
  };

  const openCreate = () => {
    if (!canCreateVehicle) {
      showToast(`Plan limit reached (${status?.maxVehicles} vehicles max). Please upgrade your plan.`, 'warning');
      return;
    }
    setEditingId(null);
    setForm({ marque: '', category: '', plate: '', statut: 'AVAILABLE', prixJour: '', fuel: 'Essence', transmission: 'Manual', imageUrl: '', branchId: '' });
    setImagePreview(null);
    setIsModalOpen(true);
  };

  const openEdit = (vehicle: Vehicle) => {
    setEditingId(vehicle.id);
    setForm({
      marque: vehicle.marque || '',
      category: vehicle.category || '',
      plate: vehicle.plate || '',
      statut: vehicle.statut || 'AVAILABLE',
      prixJour: vehicle.prixJour ? String(vehicle.prixJour) : '',
      fuel: vehicle.fuel || 'Essence',
      transmission: vehicle.transmission || 'Manual',
      imageUrl: vehicle.imageUrl || '',
      branchId: vehicle.branchId ? String(vehicle.branchId) : '',
    });
    setImagePreview(vehicle.imageUrl || null);
    setIsModalOpen(true);
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
    if (!form.marque || !form.prixJour || !form.plate) {
      showToast(t('vehicles.validation.requiredSummary'), 'warning');
      return;
    }
    try {
      const payload = {
        marque: form.marque,
        prixJour: Number(form.prixJour),
        statut: form.statut,
        category: form.category || 'Economy',
        plate: form.plate.trim(),
        fuel: form.fuel,
        transmission: form.transmission,
        imageUrl: form.imageUrl || 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400',
        branchId: form.branchId ? Number(form.branchId) : null,
      };
      if (editingId) {
        const { data: updated } = await api.put(`/vehicles/${editingId}`, payload);
        setData((prev) => prev.map((v) => (v.id === editingId ? updated : v)));
        showToast(t('toast.success', { action: t('common.update') }));
      } else {
        const { data: newVehicle } = await api.post('/vehicles', payload);
        setData((prev) => [...prev, newVehicle]);
        showToast(t('toast.newVehicleAdded'));
      }
      setIsModalOpen(false);
      setEditingId(null);
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

  const deleteVehicle = async (id: number) => {
    if (confirm(t('vehicles.deleteConfirm'))) {
      try {
        await api.delete(`/vehicles/${id}`);
        setData((prev) => prev.filter((v) => v.id !== id));
        showToast(t('toast.success', { action: t('common.delete') }));
      } catch (err) {
        showToast(t('vehicles.deleteFailed'), 'error');
      }
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <GlassPageHeader
        title={t('vehicles.title')}
        subtitle={t('vehicles.subtitle')}
        icon={Car}
        actions={
          <>
            <motion.button
              onClick={exportFleet}
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
        <FilterChips
          options={filters}
          activeId={filter}
          onChange={(id) => {
            setFilter(id);
            const label = filters.find((f) => f.id === id)?.label;
            if (label) showToast(t('toast.filterApplied', { action: label }), 'info');
          }}
        />
      </div>

      {loadError ? (
        <ApiErrorState message={loadError} onRetry={fetchVehicles} />
      ) : loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
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
                        <span className="text-[11px] font-bold uppercase tracking-widest" style={{ color: 'var(--text-muted)' }}>{translateVehicleCategory(vehicle.category || 'Economy')}</span>
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
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>5 {t('vehicles.seats')}</span>
                    </div>
                    <div className="flex flex-col items-center gap-1">
                      <Fuel size={15} className="group-hover:text-brand-400 transition-colors" style={{ color: 'var(--text-muted)' }} />
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>{translateFuelType(vehicle.fuel || 'Diesel')}</span>
                    </div>
                    <div className="flex flex-col items-center gap-1">
                      <Shield size={15} className="group-hover:text-brand-400 transition-colors" style={{ color: 'var(--text-muted)' }} />
                      <span className="text-[10px] font-bold uppercase" style={{ color: 'var(--text-muted)' }}>{translateTransmission(vehicle.transmission || 'Manual')}</span>
                    </div>
                  </div>

                  <motion.button
                    onClick={() => openEdit(vehicle)}
                    whileHover={{ scale: 1.01 }}
                    whileTap={{ scale: 0.98 }}
                    className="w-full py-2.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-2"
                    style={{
                      backgroundColor: 'var(--bg-hover)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    {t('vehicles.manageDetails')}
                    <ChevronRight size={16} />
                  </motion.button>
                </div>
              </GlassCard>
            </motion.div>
          ))}
          {filteredData.length === 0 && (
            <div className="col-span-full py-12 text-center" style={{ color: 'var(--text-muted)' }}>
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                {data.length === 0 ? t('vehicles.emptyTitle') : t('vehicles.noFilterResults')}
              </p>
              <p className="mt-1 text-xs">
                {data.length === 0 ? t('vehicles.emptyHint') : t('vehicles.noFilterHint')}
              </p>
            </div>
          )}
        </motion.div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setEditingId(null); }} title={editingId ? t('vehicles.manageDetails') : t('vehicles.addVehicle')} maxWidth="max-w-xl">
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

          <div>
            <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.brandModel')}</label>
            <input type="text" value={form.marque} onChange={(e) => setForm({ ...form, marque: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }} />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.category')}</label>
              <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="">{t('vehicles.selectCategory')}</option>
                {vehicleCategories.map((category) => <option key={category} value={category}>{translateVehicleCategory(category)}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.plate')}</label>
              <input type="text" value={form.plate} onChange={(e) => setForm({ ...form, plate: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }} />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.status')}</label>
              <select value={form.statut} onChange={(e) => setForm({ ...form, statut: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="AVAILABLE">{t('common.available')}</option>
                <option value="RESERVED">{t('common.reserved')}</option>
                <option value="RENTED">{t('common.rented')}</option>
                <option value="MAINTENANCE">{t('common.maintenance')}</option>
                <option value="OUT_OF_SERVICE">{t('vehicles.outOfService')}</option>
                <option value="SOLD">{t('vehicles.sold')}</option>
                <option value="ARCHIVED">{t('vehicles.archived')}</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.pricePerDay')}</label>
              <input type="number" value={form.prixJour} onChange={(e) => setForm({ ...form, prixJour: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }} />
            </div>
          </div>
          {hasFeature('MULTI_BRANCH') && (
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('branches.branch')}</label>
              <select value={form.branchId} onChange={(e) => setForm({ ...form, branchId: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="">{t('branches.unassigned')}</option>
                {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
              </select>
            </div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.fuel')}</label>
              <select value={form.fuel} onChange={(e) => setForm({ ...form, fuel: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="Essence">{t('vehicles.essence')}</option>
                <option value="Diesel">{t('vehicles.diesel')}</option>
                <option value="Hybrid">{t('vehicles.hybrid')}</option>
                <option value="Electric">{t('vehicles.electric')}</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-2" style={{ color: 'var(--text-primary)' }}>{t('vehicles.transmission')}</label>
              <select value={form.transmission} onChange={(e) => setForm({ ...form, transmission: e.target.value })} className="w-full px-4 py-2.5 rounded-xl text-sm outline-none transition-all" style={{ backgroundColor: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}>
                <option value="Manual">{t('vehicles.manual')}</option>
                <option value="Automatic">{t('vehicles.automatic')}</option>
              </select>
            </div>
          </div>
          <div className="pt-2">
            <motion.button
              onClick={saveVehicle}
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.98 }}
              className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 transition-all"
            >
              {editingId ? t('vehicles.manageDetails') : t('vehicles.addVehicle')}
            </motion.button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
