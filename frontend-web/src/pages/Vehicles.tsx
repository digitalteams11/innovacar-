import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import { Search, Plus, Download, ChevronRight, Fuel, Shield, Users as UsersIcon, Camera, Loader2 } from 'lucide-react';
import api from '../api/axios';
import { useSubscription } from '../hooks/useSubscription';

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
}

export default function Vehicles() {
  const [filter, setFilter] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
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
  });
  const [imagePreview, setImagePreview] = useState<string | null>(null);

  const { showToast } = useToast();
  const { t } = useTranslation();
  const { canCreateVehicle, status } = useSubscription();

  useEffect(() => {
    const fetchVehicles = async () => {
      try {
        const { data } = await api.get('/vehicles');
        setData(data);
      } catch (err) {
        console.error('Failed to fetch vehicles', err);
      } finally {
        setLoading(false);
      }
    };
    fetchVehicles();
  }, []);

  const filters = [
    { key: 'All', label: t('vehicles.all') },
    { key: 'Available', label: t('vehicles.available') },
    { key: 'Rented', label: t('vehicles.rented') },
    { key: 'Maintenance', label: t('vehicles.maintenance') },
  ];

  const filteredData = data.filter((v) => {
    const matchesFilter = filter === 'All' || v.statut === filter.toUpperCase();
    const q = searchQuery.toLowerCase();
    const matchesSearch = v.marque?.toLowerCase().includes(q) || v.category?.toLowerCase().includes(q) || v.plate?.toLowerCase().includes(q);
    return matchesFilter && matchesSearch;
  });

  const exportFleet = () => {
    const headers = ['ID', 'Brand', 'Category', 'Plate', 'Status', 'Price/Day', 'Fuel', 'Transmission'];
    const rows = filteredData.map((v) => [v.id, v.marque, v.category, v.plate, v.statut, v.prixJour, v.fuel, v.transmission]);
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
      showToast(`Plan limit reached (${status?.maxVehicles} vehicles max). Please upgrade your plan.`);
      return;
    }
    setEditingId(null);
    setForm({ marque: '', category: '', plate: '', statut: 'AVAILABLE', prixJour: '', fuel: 'Essence', transmission: 'Manual', imageUrl: '' });
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
    if (!form.marque || !form.prixJour) {
      showToast('Please fill all required fields (Brand and Price)');
      return;
    }
    try {
      const payload = {
        marque: form.marque,
        prixJour: Number(form.prixJour),
        statut: form.statut,
        category: form.category || 'Economy',
        plate: form.plate || `PLT-${Date.now()}`,
        fuel: form.fuel,
        transmission: form.transmission,
        imageUrl: form.imageUrl || 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400',
      };
      if (editingId) {
        const { data: updated } = await api.put(`/vehicles/${editingId}`, payload);
        setData((prev) => prev.map((v) => (v.id === editingId ? updated : v)));
        showToast(t('toast.success', { action: 'Update' }));
      } else {
        const { data: newVehicle } = await api.post('/vehicles', payload);
        setData((prev) => [...prev, newVehicle]);
        showToast(t('toast.newVehicleAdded'));
      }
      setIsModalOpen(false);
      setEditingId(null);
    } catch (err: any) {
      const details = (err as any).response?.data?.details;
      const msg = (err as any).userMessage || 'Failed to save vehicle';
      let fullMsg = 'Error: ' + msg;
      if (details && typeof details === 'object') {
        const fieldErrors = Object.entries(details).map(([field, error]) => `${field}: ${error}`).join(', ');
        fullMsg += ' - ' + fieldErrors;
      }
      showToast(fullMsg);
      console.error('Vehicle save error:', err);
    }
  };

  const deleteVehicle = async (id: number) => {
    if (confirm('Delete this vehicle?')) {
      try {
        await api.delete(`/vehicles/${id}`);
        setData((prev) => prev.filter((v) => v.id !== id));
        showToast(t('toast.success', { action: 'Delete' }));
      } catch (err) {
        showToast('Failed to delete vehicle');
      }
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="flex flex-col gap-3">
        <div>
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('vehicles.title')}</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('vehicles.subtitle')}</p>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <button
            onClick={exportFleet}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-xs sm:text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all"
          >
            <Download size={16} className="sm:hidden" />
            <Download size={18} className="hidden sm:block" />
            <span className="hidden sm:inline">{t('vehicles.exportFleet')}</span>
          </button>
          <button
            onClick={openCreate}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all"
          >
            <Plus size={16} className="sm:hidden" />
            <Plus size={18} className="hidden sm:block" />
            {t('vehicles.addVehicle')}
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex-1 flex items-center gap-3 bg-white px-3 sm:px-4 py-2 sm:py-2.5 rounded-xl border border-[#e8e6e1] shadow-soft focus-within:ring-2 ring-brand-100/50 transition-all">
          <Search size={16} className="text-slate-400 sm:hidden" />
          <Search size={18} className="text-slate-400 hidden sm:block" />
          <input
            type="text"
            placeholder={t('vehicles.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="bg-transparent border-none outline-none text-sm font-normal w-full text-[#1e293b] placeholder:text-slate-400"
          />
        </div>
        <div className="flex gap-2 overflow-x-auto pb-1 no-scrollbar">
          {filters.map((cat) => (
            <button
              key={cat.key}
              onClick={() => {
                setFilter(cat.key);
                showToast(t('toast.filterApplied', { action: cat.label }));
              }}
              className={`px-3 sm:px-5 py-2 sm:py-2.5 rounded-xl font-medium text-xs sm:text-sm whitespace-nowrap flex-shrink-0 transition-all active:scale-95 ${
                filter === cat.key
                  ? 'bg-[#1e293b] text-white shadow-md'
                  : 'bg-white text-slate-500 border border-[#e8e6e1] hover:bg-[#f5f5f0] shadow-soft'
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 size={32} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
          {filteredData.map((vehicle) => (
            <div
              key={vehicle.id}
              className="card-premium overflow-hidden hover:shadow-elevated transition-all duration-300 group p-0"
            >
              <div className="relative h-40 bg-slate-100 overflow-hidden">
                <img
                  src={vehicle.imageUrl || 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400'}
                  alt={vehicle.marque}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
                  onError={(e) => { (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1549317661-bd32c8ce0db2?auto=format&fit=crop&q=80&w=400'; }}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent"></div>
                <div className="absolute top-3 right-3">
                  <span className={`px-3 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider shadow-md ${
                    vehicle.statut === 'AVAILABLE' ? 'bg-success-500 text-white' :
                    vehicle.statut === 'RENTED' ? 'bg-brand-500 text-white' :
                    'bg-danger-500 text-white'
                  }`}>
                    {vehicle.statut}
                  </span>
                </div>
                <button onClick={() => deleteVehicle(vehicle.id)} className="absolute top-3 left-3 p-2 bg-white/80 hover:bg-danger-500 hover:text-white rounded-lg transition-all opacity-0 group-hover:opacity-100">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
                </button>
              </div>

              <div className="p-3 sm:p-5">
                <div className="flex justify-between items-start mb-3">
                  <div className="min-w-0">
                    <h3 className="text-sm font-semibold text-[#1e293b] group-hover:text-brand-500 transition-colors">{vehicle.marque}</h3>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest">{vehicle.category || 'Economy'}</span>
                      <div className="w-1 h-1 bg-[#e8e6e1] rounded-full"></div>
                      <span className="text-[11px] font-mono font-medium text-slate-500">{vehicle.plate || `PLT-${vehicle.id}`}</span>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold text-[#1e293b]">{vehicle.prixJour}<span className="text-[10px] font-medium text-slate-400 ml-1">{t('vehicles.perDay')}</span></p>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-3 py-3 border-y border-[#e8e6e1]/60 mb-4 group-hover:border-[#e8e6e1] transition-colors">
                  <div className="flex flex-col items-center gap-1">
                    <UsersIcon size={15} className="text-slate-300 group-hover:text-brand-400 transition-colors" />
                    <span className="text-[10px] font-bold text-slate-500 uppercase">5 {t('vehicles.seats')}</span>
                  </div>
                  <div className="flex flex-col items-center gap-1">
                    <Fuel size={15} className="text-slate-300 group-hover:text-brand-400 transition-colors" />
                    <span className="text-[10px] font-bold text-slate-500 uppercase">{vehicle.fuel || 'Diesel'}</span>
                  </div>
                  <div className="flex flex-col items-center gap-1">
                    <Shield size={15} className="text-slate-300 group-hover:text-brand-400 transition-colors" />
                    <span className="text-[10px] font-bold text-slate-500 uppercase">{vehicle.transmission || 'Manual'}</span>
                  </div>
                </div>

                <button onClick={() => openEdit(vehicle)} className="w-full py-2.5 bg-[#f5f5f0] text-[#1e293b] rounded-xl font-semibold text-sm hover:bg-brand-500 hover:text-white hover:shadow-md hover:shadow-brand-500/10 transition-all active:scale-95 flex items-center justify-center gap-2">
                  {t('vehicles.manageDetails')}
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          ))}
          {filteredData.length === 0 && (
            <div className="col-span-full card-premium py-12 text-center text-slate-400 text-sm">No vehicles found</div>
          )}
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setEditingId(null); }} title={editingId ? t('vehicles.manageDetails') : t('vehicles.addVehicle')} maxWidth="max-w-xl">
        <div className="space-y-4">
          {/* Image Upload */}
          <div className="bg-brand-50/50 p-3 rounded-xl border border-brand-100">
            <label className="block text-sm font-bold text-brand-600 mb-2">📷 Vehicle Image</label>
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
                className="flex flex-col items-center justify-center w-full h-28 bg-white border-2 border-dashed border-brand-200 rounded-xl cursor-pointer hover:border-brand-400 hover:bg-brand-50 transition-all overflow-hidden"
              >
                {imagePreview ? (
                  <div className="relative w-full h-full">
                    <img src={imagePreview} alt="Preview" className="w-full h-full object-cover" />
                  </div>
                ) : (
                  <div className="flex flex-col items-center">
                    <div className="w-10 h-10 bg-brand-100 rounded-full flex items-center justify-center mb-1.5">
                      <Camera size={20} className="text-brand-500" />
                    </div>
                    <span className="text-sm text-brand-600 font-semibold">Click to upload</span>
                    <span className="text-xs text-slate-400 mt-0.5">JPG, PNG up to 5MB</span>
                  </div>
                )}
              </label>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">Brand / Model *</label>
            <input type="text" value={form.marque} onChange={(e) => setForm({ ...form, marque: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Category</label>
              <input type="text" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Plate</label>
              <input type="text" value={form.plate} onChange={(e) => setForm({ ...form, plate: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Status</label>
              <select value={form.statut} onChange={(e) => setForm({ ...form, statut: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="AVAILABLE">Available</option>
                <option value="RENTED">Rented</option>
                <option value="MAINTENANCE">Maintenance</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Price/Day (DH) *</label>
              <input type="number" value={form.prixJour} onChange={(e) => setForm({ ...form, prixJour: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Fuel</label>
              <select value={form.fuel} onChange={(e) => setForm({ ...form, fuel: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="Essence">Essence</option>
                <option value="Diesel">Diesel</option>
                <option value="Hybrid">Hybrid</option>
                <option value="Electric">Electric</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2">Transmission</label>
              <select value={form.transmission} onChange={(e) => setForm({ ...form, transmission: e.target.value })} className="w-full px-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                <option value="Manual">Manual</option>
                <option value="Automatic">Automatic</option>
              </select>
            </div>
          </div>
          <div className="pt-2">
            <button onClick={saveVehicle} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
              {editingId ? t('vehicles.manageDetails') : t('vehicles.addVehicle')}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
