import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { useNavigate } from 'react-router-dom';
import Modal from '../components/Modal';
import SmartClientSearch from '../components/shared/SmartClientSearch';
import SmartVehicleSelector from '../components/shared/SmartVehicleSelector';
import api from '../api/axios';
import {
  Search, Plus, Download, ChevronRight, Clock, MapPin, CreditCard,
  Trash2, Edit3, FileText, Car, Shield, X, Landmark
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
  paymentStatus: string;
  totalPrice: number;
  depositAmount: number;
  pickupLocation: string;
  returnLocation: string;
}

export default function Reservations() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const [clientData, setClientData] = useState<any>({});
  const [selectedVehicle, setSelectedVehicle] = useState<any>(null);
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endDate, setEndDate] = useState('');
  const [endTime, setEndTime] = useState('18:00');
  const [pickupLocation, setPickupLocation] = useState('');
  const [returnLocation, setReturnLocation] = useState('');
  const [notes, setNotes] = useState('');

  // Deposit fields
  const [depositRequired, setDepositRequired] = useState(true);
  const [depositType, setDepositType] = useState('CASH');
  const [depositAmount, setDepositAmount] = useState<number>(0);
  const [depositReference, setDepositReference] = useState('');
  const [depositNotes, setDepositNotes] = useState('');
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => { fetchReservations(); }, []);

  const fetchReservations = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/reservations');
      setData(data.map((r: any) => ({
        id: r.id,
        clientId: r.clientId,
        clientName: r.clientName || `Client #${r.id}`,
        vehicleId: r.vehicleId,
        vehicleMarque: r.vehicleMarque,
        dateStart: r.dateStart,
        startTime: r.startTime || '09:00',
        dateEnd: r.dateEnd,
        endTime: r.endTime || '18:00',
        contractId: r.contractId,
        status: r.status || 'CONFIRMED',
        paymentStatus: r.paymentStatus || 'pending',
        totalPrice: Number(r.totalPrice) || 0,
        depositAmount: Number(r.depositAmount) || 0,
        pickupLocation: r.pickupLocation,
        returnLocation: r.returnLocation,
      })));
    } catch (err) {
      console.error('Failed to fetch reservations', err);
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { key: 'All', label: t('reservations.all') },
    { key: 'CONFIRMED', label: t('reservations.confirmed') },
    { key: 'PENDING', label: t('reservations.pending') },
    { key: 'ACTIVE', label: t('reservations.rented') },
    { key: 'CANCELLED', label: t('reservations.cancelled') },
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
    const rows = filteredData.map((r) => [r.id, r.clientName, r.vehicleMarque, r.dateStart, r.dateEnd, r.status, r.totalPrice]);
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
        depositRequired,
        depositType: depositRequired ? depositType : undefined,
        depositAmount: depositRequired ? (depositAmount || selectedVehicle.depositAmount || 0) : undefined,
        depositReference: depositRequired ? (depositReference || undefined) : undefined,
        depositNotes: depositRequired ? (depositNotes || undefined) : undefined,
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
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to save reservation');
    } finally {
      setSaving(false);
    }
  };

  const deleteReservation = async (id: number) => {
    if (confirm(t('reservations.deleteConfirm') || 'Delete this reservation?')) {
      try {
        await api.delete(`/reservations/${id}`);
        fetchReservations();
        showToast(t('reservations.delete'));
      } catch (err) {
        showToast('Failed to delete reservation');
      }
    }
  };

  const generateContract = async (res: Reservation) => {
    try {
      setActionId(res.id);
      if (res.contractId) {
        navigate(`/contracts/${res.contractId}`);
      } else {
        const { data } = await api.post(`/contracts/from-reservation/${res.id}`);
        showToast('Contract generated successfully');
        navigate(`/contracts/${data.id}`);
      }
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to generate contract');
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      <div className="flex flex-col gap-3">
        <div>
          <h1 className="text-lg sm:text-xl font-bold text-[#1e293b]">{t('reservations.title')}</h1>
          <p className="text-slate-500 font-normal text-xs sm:text-sm mt-0.5">{t('reservations.subtitle')}</p>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <button onClick={exportCSV}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-[#1e293b] font-medium text-xs sm:text-sm hover:bg-[#f5f5f0] active:scale-95 transition-all">
            <Download size={16} className="sm:hidden" />
            <Download size={18} className="hidden sm:block" />
            <span className="hidden sm:inline">{t('reservations.exportCSV')}</span>
          </button>
          <button onClick={openCreate}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-xs sm:text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
            <Plus size={16} className="sm:hidden" />
            <Plus size={18} className="hidden sm:block" />
            {t('reservations.createReservation')}
          </button>
        </div>
      </div>

      <div className="card-premium flex flex-col gap-3 p-3 sm:p-5">
        <div className="flex p-1 bg-[#f5f5f0] rounded-xl overflow-x-auto no-scrollbar">
          {tabs.map((tab) => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`px-2.5 sm:px-4 py-1.5 sm:py-2 rounded-lg text-xs sm:text-sm font-medium transition-all active:scale-95 whitespace-nowrap flex-shrink-0 ${activeTab === tab.key ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>
              {tab.label}
            </button>
          ))}
        </div>
        <div className="flex-1 relative group">
          <Search size={16} className="absolute left-3.5 sm:left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors" />
          <input type="text" placeholder={t('reservations.searchPlaceholder')} value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 sm:pl-11 pr-4 py-2 sm:py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm font-normal text-[#1e293b] focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
        </div>
      </div>

      <div className="card-premium overflow-hidden p-0">
        <div className="overflow-x-auto no-scrollbar">
          <table className="w-full text-left min-w-[640px]">
            <thead>
              <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                <th className="px-3 sm:px-5 py-3 sm:py-4">ID</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Client</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Vehicle</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Dates</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Status</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4">Total</th>
                <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#e8e6e1]/50">
              {loading ? (
                [1, 2, 3].map(i => (
                  <tr key={i} className="animate-pulse">
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-32 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-24 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-40 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-6 w-20 bg-gray-100 rounded-full"></div></td>
                    <td className="px-5 py-6"><div className="h-5 w-16 bg-gray-100 rounded"></div></td>
                    <td className="px-5 py-6"><div className="h-8 w-8 bg-gray-100 rounded ml-auto"></div></td>
                  </tr>
                ))
              ) : filteredData.length > 0 ? (
                filteredData.map((res) => (
                  <tr key={res.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-5 py-4">
                      <span className="font-mono text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">#RES-{res.id}</span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 bg-[#f5f5f0] rounded-lg flex items-center justify-center text-slate-600 font-bold group-hover:bg-white group-hover:shadow-sm transition-colors text-sm">
                          {res.clientName?.charAt(0) || '?'}
                        </div>
                        <div className="min-w-0">
                          <div className="text-sm font-medium text-[#1e293b]">{res.clientName}</div>
                          <div className="text-[11px] text-slate-400 font-normal">{t('reservations.individualClient')}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-sm font-medium text-[#1e293b]">{res.vehicleMarque}</div>
                      <div className="flex items-center gap-1 mt-0.5 text-slate-400">
                        <MapPin size={11} />
                        <span className="text-xs font-normal">{res.pickupLocation || t('reservations.casablancaAirport')}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2 text-sm font-medium text-[#1e293b]">
                        <span>{new Date(res.dateStart).toLocaleDateString()} {res.startTime?.slice(0, 5)}</span>
                        <ChevronRight size={13} className="text-slate-300" />
                        <span>{new Date(res.dateEnd).toLocaleDateString()} {res.endTime?.slice(0, 5)}</span>
                      </div>
                      <div className="flex items-center gap-1 mt-0.5 text-slate-400">
                        <Clock size={11} />
                        <span className="text-xs font-normal">{
                          Math.max(1, Math.ceil((new Date(res.dateEnd).getTime() - new Date(res.dateStart).getTime()) / (1000 * 60 * 60 * 24)))
                        } {t('reservations.rentalDays')}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider flex items-center gap-1.5 w-fit ${
                        res.status === 'CONFIRMED' ? 'bg-success-50 text-success-500' :
                        res.status === 'PENDING' ? 'bg-warning-50 text-warning-500' :
                        res.status === 'ACTIVE' ? 'bg-brand-50 text-brand-500' :
                        'bg-danger-50 text-danger-500'
                      }`}>
                        <div className={`w-1.5 h-1.5 rounded-full ${
                          res.status === 'CONFIRMED' ? 'bg-success-500' :
                          res.status === 'PENDING' ? 'bg-warning-500' :
                          res.status === 'ACTIVE' ? 'bg-brand-500' :
                          'bg-danger-500'
                        }`}></div>
                        {res.status}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="text-sm font-bold text-[#1e293b]">{res.totalPrice} MAD</div>
                      <div className="flex items-center gap-1 mt-0.5 text-success-500">
                        <CreditCard size={11} />
                        <span className="text-[10px] font-bold uppercase tracking-wider">{res.paymentStatus}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => generateContract(res)} disabled={actionId === res.id}
                          className="p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all" title="Generate Contract">
                          {actionId === res.id ? <Clock size={17} className="animate-spin" /> : <FileText size={17} />}
                        </button>
                        <button onClick={() => openEdit(res)}
                          className="p-2 text-slate-400 hover:text-warning-500 hover:bg-warning-50 rounded-lg transition-all">
                          <Edit3 size={17} />
                        </button>
                        <button onClick={() => deleteReservation(res.id)}
                          className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all">
                          <Trash2 size={17} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="px-5 py-8 text-center text-slate-400 text-sm">{t('reservations.noResults') || 'No reservations found'}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? t('reservations.edit') : t('reservations.createReservation')} maxWidth="3xl">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="space-y-4">
            <SmartClientSearch value={clientData} onSelect={setClientData} required />

            {clientData.clientFullName && (
              <div className="p-4 bg-gradient-to-br from-brand-50 to-white rounded-2xl border border-brand-100 space-y-3 animate-fade">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-brand-500 text-white rounded-xl flex items-center justify-center text-sm font-bold shadow-sm">
                      {clientData.clientFullName?.split(' ').map((n: string) => n[0]).join('').toUpperCase().slice(0, 2) || '?'}
                    </div>
                    <div>
                      <p className="text-sm font-bold text-[#1e293b]">{clientData.clientFullName}</p>
                      <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-0.5">
                        {clientData.clientPhone && <span className="text-[11px] text-slate-400">{clientData.clientPhone}</span>}
                        {clientData.clientEmail && <span className="text-[11px] text-slate-400">{clientData.clientEmail}</span>}
                      </div>
                    </div>
                  </div>
                  <button onClick={() => setClientData({})} className="p-1.5 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all" title="Clear">
                    <X size={14} />
                  </button>
                </div>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                  {clientData.clientCin && <div className="flex items-center gap-1"><Shield size={10} className="text-brand-400" /><span className="text-slate-400">CIN:</span><span className="font-medium text-[#1e293b]">{clientData.clientCin}</span></div>}
                  {clientData.clientDriverLicense && <div className="flex items-center gap-1"><Car size={10} className="text-brand-400" /><span className="text-slate-400">License:</span><span className="font-medium text-[#1e293b]">{clientData.clientDriverLicense}</span></div>}
                  {clientData.clientCity && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span className="text-slate-400">City:</span><span className="font-medium text-[#1e293b]">{clientData.clientCity}</span></div>}
                  {clientData.clientNationality && <div className="flex items-center gap-1"><MapPin size={10} className="text-brand-400" /><span className="text-slate-400">Nationality:</span><span className="font-medium text-[#1e293b]">{clientData.clientNationality}</span></div>}
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Start Date <span className="text-danger-500">*</span></label>
                <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">End Date <span className="text-danger-500">*</span></label>
                <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Start Time <span className="text-danger-500">*</span></label>
                <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">End Time <span className="text-danger-500">*</span></label>
                <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
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
                <label className="block text-xs font-medium text-slate-500 mb-1">Pickup Location</label>
                <input type="text" value={pickupLocation} onChange={(e) => setPickupLocation(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Return Location</label>
                <input type="text" value={returnLocation} onChange={(e) => setReturnLocation(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Notes</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm resize-none focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
            </div>

            {/* Security Deposit */}
            <div className="bg-amber-50 rounded-2xl p-4 border border-amber-100 space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-amber-700">
                  <Landmark size={14} />
                  <span className="text-xs font-bold uppercase tracking-wider">Security Deposit</span>
                </div>
                <button
                  onClick={() => setDepositRequired(!depositRequired)}
                  className={`relative w-11 h-6 rounded-full transition-all ${depositRequired ? 'bg-amber-500' : 'bg-slate-300'}`}
                >
                  <span className={`absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-all ${depositRequired ? 'translate-x-5' : ''}`} />
                </button>
              </div>
              {depositRequired && (
                <div className="space-y-3">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Deposit Type</label>
                      <select value={depositType} onChange={(e) => setDepositType(e.target.value)}
                        className="w-full px-3 py-2 bg-white border border-amber-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-amber-100">
                        <option value="CASH">Cash</option>
                        <option value="CHECK">Check</option>
                        <option value="BANK_TRANSFER">Bank Transfer</option>
                        <option value="CARD_HOLD">Card Hold</option>
                        <option value="OTHER">Other</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Amount (MAD)</label>
                      <input type="number" value={depositAmount || selectedVehicle?.depositAmount || 0}
                        onChange={(e) => setDepositAmount(Number(e.target.value))}
                        className="w-full px-3 py-2 bg-white border border-amber-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-amber-100" />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 mb-1">Reference (optional)</label>
                    <input type="text" value={depositReference} onChange={(e) => setDepositReference(e.target.value)}
                      placeholder="e.g. DEP-2026-001"
                      className="w-full px-3 py-2 bg-white border border-amber-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-amber-100" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 mb-1">Deposit Notes (optional)</label>
                    <textarea value={depositNotes} onChange={(e) => setDepositNotes(e.target.value)} rows={2}
                      className="w-full px-3 py-2 bg-white border border-amber-200 rounded-xl text-sm resize-none focus:outline-none focus:ring-2 ring-amber-100" />
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="space-y-4">
            <div className="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm space-y-3">
              <div className="flex items-center gap-2 text-brand-500">
                <CreditCard size={14} />
                <span className="text-xs font-bold uppercase tracking-wider">Price Preview</span>
              </div>
              {!selectedVehicle || !startDate || !endDate ? (
                <p className="text-sm text-slate-400">Select vehicle and dates to see pricing</p>
              ) : (
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-400">Daily Rate</span>
                    <span className="font-medium text-[#1e293b]">{selectedVehicle.prixJour} MAD</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-400">Duration</span>
                    <span className="font-medium text-[#1e293b]">{daysCount()} days</span>
                  </div>
                  <div className="h-px bg-slate-100" />
                  <div className="flex justify-between items-baseline">
                    <span className="text-sm font-bold text-[#1e293b]">Total</span>
                    <span className="text-2xl font-black text-brand-500">{calculatedPrice()} <span className="text-sm font-medium">MAD</span></span>
                  </div>
                  {selectedVehicle.depositAmount > 0 && (
                    <div className="flex justify-between text-xs text-slate-400">
                      <span>Deposit</span>
                      <span>{selectedVehicle.depositAmount} MAD</span>
                    </div>
                  )}
                </div>
              )}
            </div>

            {selectedVehicle && (
              <div className="p-4 bg-gradient-to-br from-slate-50 to-white rounded-2xl border border-slate-200 space-y-3 animate-fade">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-slate-800 text-white rounded-xl flex items-center justify-center shadow-sm">
                    <Car size={18} />
                  </div>
                  <div>
                    <p className="text-sm font-bold text-[#1e293b]">{selectedVehicle.marque}</p>
                    <p className="text-[11px] text-slate-400">{selectedVehicle.category} • {selectedVehicle.year} • {selectedVehicle.color}</p>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                  <div className="flex items-center gap-1"><span className="text-slate-400">Plate:</span><span className="font-medium text-[#1e293b]">{selectedVehicle.plate}</span></div>
                  <div className="flex items-center gap-1"><span className="text-slate-400">Fuel:</span><span className="font-medium text-[#1e293b]">{selectedVehicle.fuel}</span></div>
                  <div className="flex items-center gap-1"><span className="text-slate-400">Day:</span><span className="font-medium text-[#1e293b]">{selectedVehicle.prixJour} MAD</span></div>
                  <div className="flex items-center gap-1"><span className="text-slate-400">Deposit:</span><span className="font-medium text-[#1e293b]">{selectedVehicle.depositAmount || 0} MAD</span></div>
                </div>
              </div>
            )}

            <button onClick={saveReservation} disabled={saving}
              className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all">
              {saving ? 'Saving...' : editingId ? 'Save Changes' : t('reservations.createReservation')}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
