import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import QRCodeModal from '../components/shared/QRCodeModal';
import VehicleInspection from '../components/shared/VehicleInspection';
import SmartClientSearch from '../components/shared/SmartClientSearch';
import SmartVehicleSelector from '../components/shared/SmartVehicleSelector';
import LivePriceSidebar from '../components/shared/LivePriceSidebar';
import api from '../api/axios';
import ApiErrorState from '../components/ApiErrorState';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { SearchInput } from '../components/SearchInput';
import { FilterChips } from '../components/FilterChips';
import {
  Plus, Download, FileText, Trash2, CheckCircle2,
  Loader2, QrCode, Eye, User, Car, Shield, Fuel, Gauge,
  Calendar, ChevronDown, ChevronUp, Hash, Wallet, X,
  MapPin, Phone
} from 'lucide-react';

interface Contract {
  id: number;
  contractNumber: string;
  clientFullName: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  startDate: string;
  endDate: string;
  status: string;
  totalPrice: number;
  clientSigned: boolean;
  ownerSigned: boolean;
  termsAccepted: boolean;
  qrToken?: string;
  publicSigningUrl?: string;
}

/* ── Sub-components ───────────────────────────────────────────────────── */

function ClientCard({ data, onClear }: { data: any; onClear?: () => void }) {
  const initials = data.clientFullName
    ?.split(' ')
    .map((n: string) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2) || '?';

  return (
    <div className="p-4 bg-gradient-to-br from-brand-50 to-white rounded-2xl border border-brand-100 space-y-3 animate-fade">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 bg-brand-500 text-white rounded-xl flex items-center justify-center text-sm font-bold shadow-sm">
            {initials}
          </div>
          <div>
            <p className="text-sm font-bold text-[#1e293b]">{data.clientFullName}</p>
            <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-0.5">
              {data.clientPhone && <span className="text-[11px] text-slate-400">{data.clientPhone}</span>}
              {data.clientEmail && <span className="text-[11px] text-slate-400">{data.clientEmail}</span>}
            </div>
          </div>
        </div>
        {onClear && (
          <button onClick={onClear} className="p-1.5 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all" title="Clear">
            <X size={14} />
          </button>
        )}
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        {data.clientCin && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-brand-400" />
            <span className="text-slate-400">CIN:</span>
            <span className="font-medium text-[#1e293b]">{data.clientCin}</span>
          </div>
        )}
        {data.clientPassportNumber && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-brand-400" />
            <span className="text-slate-400">Passport:</span>
            <span className="font-medium text-[#1e293b]">{data.clientPassportNumber}</span>
          </div>
        )}
        {data.clientDriverLicense && (
          <div className="flex items-center gap-1.5">
            <Car size={10} className="text-brand-400" />
            <span className="text-slate-400">License:</span>
            <span className="font-medium text-[#1e293b]">{data.clientDriverLicense}</span>
          </div>
        )}
        {data.clientNationality && (
          <div className="flex items-center gap-1.5">
            <MapPin size={10} className="text-brand-400" />
            <span className="text-slate-400">Nationality:</span>
            <span className="font-medium text-[#1e293b]">{data.clientNationality}</span>
          </div>
        )}
        {data.clientCity && (
          <div className="flex items-center gap-1.5">
            <MapPin size={10} className="text-brand-400" />
            <span className="text-slate-400">City:</span>
            <span className="font-medium text-[#1e293b]">{data.clientCity}</span>
          </div>
        )}
        {data.clientBirthDate && (
          <div className="flex items-center gap-1.5">
            <Calendar size={10} className="text-brand-400" />
            <span className="text-slate-400">Birth:</span>
            <span className="font-medium text-[#1e293b]">{new Date(data.clientBirthDate).toLocaleDateString()}</span>
          </div>
        )}
        {data.emergencyContactName && (
          <div className="flex items-center gap-1.5 col-span-2">
            <Phone size={10} className="text-brand-400" />
            <span className="text-slate-400">Emergency:</span>
            <span className="font-medium text-[#1e293b]">{data.emergencyContactName} {data.emergencyContactPhone && `(${data.emergencyContactPhone})`}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function VehicleCard({ vehicle }: { vehicle: any }) {
  return (
    <div className="p-4 bg-gradient-to-br from-slate-50 to-white rounded-2xl border border-slate-200 space-y-3 animate-fade">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 bg-slate-800 text-white rounded-xl flex items-center justify-center shadow-sm">
            <Car size={20} />
          </div>
          <div>
            <p className="text-sm font-bold text-[#1e293b]">{vehicle.marque}</p>
            <p className="text-[11px] text-slate-400">{vehicle.category} • {vehicle.year} • {vehicle.color}</p>
          </div>
        </div>
        <span className="px-2 py-0.5 bg-brand-50 text-brand-500 rounded-lg text-[10px] font-bold uppercase tracking-wider">
          {vehicle.status || 'Available'}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        <div className="flex items-center gap-1.5">
          <Hash size={10} className="text-slate-400" />
          <span className="text-slate-400">Plate:</span>
          <span className="font-medium text-[#1e293b]">{vehicle.plate}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Fuel size={10} className="text-slate-400" />
          <span className="text-slate-400">Fuel:</span>
          <span className="font-medium text-[#1e293b]">{vehicle.fuel}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Gauge size={10} className="text-slate-400" />
          <span className="text-slate-400">Trans:</span>
          <span className="font-medium text-[#1e293b]">{vehicle.transmission}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Wallet size={10} className="text-slate-400" />
          <span className="text-slate-400">Day:</span>
          <span className="font-medium text-[#1e293b]">{vehicle.prixJour} MAD</span>
        </div>
        {vehicle.depositAmount > 0 && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-slate-400" />
            <span className="text-slate-400">Deposit:</span>
            <span className="font-medium text-[#1e293b]">{vehicle.depositAmount} MAD</span>
          </div>
        )}
        {vehicle.gpsEnabled && (
          <div className="flex items-center gap-1">
            <CheckCircle2 size={10} className="text-success-500" />
            <span className="text-[10px] font-bold text-success-500 uppercase tracking-wider">GPS Enabled</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Contracts() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [qrModal, setQrModal] = useState<{ open: boolean; contract?: Contract }>({ open: false });

  // Form state
  const [contractNumber, setContractNumber] = useState('');
  const [clientData, setClientData] = useState<any>({});
  const [selectedVehicle, setSelectedVehicle] = useState<any>(null);
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endDate, setEndDate] = useState('');
  const [endTime, setEndTime] = useState('18:00');
  const [pickupLocation, setPickupLocation] = useState('');
  const [returnLocation, setReturnLocation] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [paidAmount, setPaidAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [discountAmount, setDiscountAmount] = useState('');
  const [discountPercent, setDiscountPercent] = useState('');
  const [inspectionZones, setInspectionZones] = useState<any[]>([]);
  const [documents, setDocuments] = useState<any[]>([
    { documentType: 'registration', documentName: 'Vehicle Registration', isPresent: false },
    { documentType: 'insurance', documentName: 'Insurance Document', isPresent: false },
    { documentType: 'technical', documentName: 'Technical Inspection', isPresent: false },
    { documentType: 'circulation', documentName: 'Circulation Authorization', isPresent: false },
    { documentType: 'passport', documentName: 'Passport Copy', isPresent: false },
    { documentType: 'cin', documentName: 'CIN Copy', isPresent: false },
    { documentType: 'license', documentName: 'Driver License Copy', isPresent: false },
  ]);
  const [additionalDrivers, setAdditionalDrivers] = useState<any[]>([]);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [reservations, setReservations] = useState<any[]>([]);
  const [selectedReservation, setSelectedReservation] = useState<any>(null);
  const [saving, setSaving] = useState(false);

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => { fetchContracts(); }, []);

  const fetchContracts = async () => {
    try {
      setLoading(true);
      setLoadError('');
      const { data } = await api.get('/contracts');
      setData(data);
    } catch (err) {
      console.error(err);
      setLoadError('Unable to load contract information. Please try again later.');
    }
    finally { setLoading(false); }
  };

  const tabs = [
    { key: 'All', label: 'All' },
    { key: 'ACTIVE', label: 'Active' },
    { key: 'WAITING_SIGNATURE', label: 'Waiting signature' },
    { key: 'SIGNED', label: 'Signed' },
    { key: 'COMPLETED', label: 'Completed' },
    { key: 'CANCELLED', label: 'Cancelled' },
  ];

  const filteredData = data.filter((c) => {
    const matchesTab = activeTab === 'All' || c.status === activeTab;
    const q = searchQuery.toLowerCase();
    return matchesTab && (
      c.clientFullName?.toLowerCase().includes(q) ||
      c.contractNumber?.toLowerCase().includes(q)
    );
  });

  const exportCSV = () => {
    const headers = ['Contract ID', 'Client', 'Status', 'Start', 'End', 'Total', 'Signed'];
    const rows = filteredData.map((c) => [
      c.contractNumber, c.clientFullName, c.status, c.startDate, c.endDate,
      c.totalPrice, c.clientSigned ? 'Yes' : 'No'
    ]);
    const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'contracts.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast(t('toast.dataExported'));
  };

  const generateNumber = async () => {
    try {
      const { data } = await api.get('/contracts/generate-number');
      setContractNumber(data.contractNumber);
    } catch (err) { showToast('Unable to generate contract number. Please try again later.', 'error'); }
  };

  const openCreate = async () => {
    setContractNumber('');
    setClientData({});
    setSelectedVehicle(null);
    setStartDate('');
    setStartTime('09:00');
    setEndDate('');
    setEndTime('18:00');
    setPickupLocation('');
    setReturnLocation('');
    setPaymentMethod('CASH');
    setPaidAmount('');
    setNotes('');
    setDiscountAmount('');
    setDiscountPercent('');
    setInspectionZones([]);
    setAdditionalDrivers([]);
    setShowAdvanced(false);
    setSelectedReservation(null);
    setDocuments([
      { documentType: 'registration', documentName: 'Vehicle Registration', isPresent: false },
      { documentType: 'insurance', documentName: 'Insurance Document', isPresent: false },
      { documentType: 'technical', documentName: 'Technical Inspection', isPresent: false },
      { documentType: 'circulation', documentName: 'Circulation Authorization', isPresent: false },
      { documentType: 'passport', documentName: 'Passport Copy', isPresent: false },
      { documentType: 'cin', documentName: 'CIN Copy', isPresent: false },
      { documentType: 'license', documentName: 'Driver License Copy', isPresent: false },
    ]);
    try {
      const { data } = await api.get('/reservations');
      const items = Array.isArray(data) ? data : (data?.content || []);
      setReservations(items.filter((r: any) =>
        (r.status === 'PENDING' || r.status === 'CONFIRMED') && !r.contractId && !r.readOnly
      ));
    } catch (err) {
      setReservations([]);
    }
    setIsModalOpen(true);
  };

  const handleSelectReservation = (res: any) => {
    setSelectedReservation(res);
    if (res) {
      setStartDate(res.dateStart);
      setStartTime(res.startTime?.slice(0, 5) || '09:00');
      setEndDate(res.dateEnd);
      setEndTime(res.endTime?.slice(0, 5) || '18:00');
      setPickupLocation(res.pickupLocation || '');
      setReturnLocation(res.returnLocation || '');
      setPaymentMethod('CASH');
      setPaidAmount('');
      setNotes(res.notes || '');
      // Auto-populate client
      if (res.clientId) {
        setClientData({
          clientId: res.clientId,
          clientFullName: res.clientName,
        });
        // Fetch full client details
        api.get(`/clients/${res.clientId}`).then(({ data }) => {
          setClientData({
            clientId: data.id,
            clientFirstName: data.firstName || data.name?.split(' ')[0],
            clientLastName: data.lastName || data.name?.split(' ').slice(1).join(' '),
            clientFullName: data.name,
            clientEmail: data.email,
            clientPhone: data.phone,
            clientSecondaryPhone: data.secondaryPhone,
            clientAddress: data.address,
            clientCity: data.city,
            clientCountry: data.country,
            clientPostalCode: data.postalCode,
            clientNationality: data.nationality,
            clientGender: data.gender,
            clientBirthDate: data.birthDate,
            clientCin: data.cin,
            clientPassportNumber: data.passportNumber,
            clientDriverLicense: data.drivingLicense,
            clientDriverLicenseIssue: data.drivingLicenseIssue,
            clientDriverLicenseExpiry: data.drivingLicenseExpiry,
            emergencyContactName: data.emergencyContactName,
            emergencyContactPhone: data.emergencyContactPhone,
            companyName: data.companyName,
            notes: data.notes,
          });
        }).catch(() => {});
      }
      // Auto-populate vehicle
      if (res.vehicleId) {
        api.get(`/vehicles/${res.vehicleId}`).then(({ data }) => {
          setSelectedVehicle(data);
        }).catch(() => {});
      }
    } else {
      setStartDate('');
      setStartTime('09:00');
      setEndDate('');
      setEndTime('18:00');
      setPickupLocation('');
      setReturnLocation('');
      setPaymentMethod('CASH');
      setPaidAmount('');
      setNotes('');
      setClientData({});
      setSelectedVehicle(null);
    }
  };

  const saveContract = async () => {
    if (saving) return;
    try {
      setSaving(true);
      if (selectedReservation) {
        const { data } = await api.post(`/contracts/from-reservation/${selectedReservation.id}`);
        showToast('Reservation converted to contract successfully', 'success');
        setIsModalOpen(false);
        fetchContracts();
        if (data?.id) navigate(`/contracts/${data.id}`);
        return;
      }

      if (!clientData.clientId || !startDate || !startTime || !endDate || !endTime || !selectedVehicle) {
        showToast('Please select a saved client, rental dates, and an available vehicle', 'warning');
        return;
      }

      const payload: any = {
        contractNumber: contractNumber || undefined,
        clientId: clientData.clientId,
        vehicleId: selectedVehicle.id,
        startDate,
        startTime,
        endDate,
        endTime,
        pickupTime: startTime,
        returnTime: endTime,
        pickupLocation,
        returnLocation,
        dailyPrice: selectedVehicle.prixJour,
        depositAmount: selectedVehicle.depositAmount || 0,
        paidAmount: Number(paidAmount) || 0,
        paymentMethod,
        deliveryFees: selectedVehicle.deliveryFees || 0,
        discountAmount: calculateDiscountAmount(),
        fuelLevelStart: 'Full',
        mileageStart: 0,
        notes,
        additionalDrivers: additionalDrivers.length > 0 ? additionalDrivers.filter(d => d.fullName) : undefined,
        documents: documents.filter((d: any) => d.isPresent).map((d: any) => ({
          documentType: d.documentType, documentName: d.documentName, isPresent: d.isPresent,
        })),
      };
      const { data } = await api.post('/contracts/direct-create', payload);
      const contractId = data?.data?.contractId || data?.contractId || data?.id;
      const isExisting = data?.data?.isNew === false;
      showToast(data?.message || 'Contract and reservation created successfully', isExisting ? 'info' : 'success');
      setIsModalOpen(false);
      fetchContracts();
      if (contractId) navigate(`/contracts/${contractId}`);
    } catch (err: any) {
      showToast((err as any).userMessage || 'Unable to save contract. Please try again later.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const calculateDiscountAmount = () => {
    if (!startDate || !endDate || !selectedVehicle) return 0;
    const start = new Date(startDate);
    const end = new Date(endDate);
    const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)));
    const base = (selectedVehicle.prixJour || 0) * days;
    const insurance = ((selectedVehicle.insuranceFees || 0) * days);
    const delivery = (selectedVehicle.deliveryFees || 0);
    const subtotal = base + insurance + delivery;
    if (discountAmount) return Number(discountAmount);
    if (discountPercent) return Math.round(subtotal * (Number(discountPercent) / 100) * 100) / 100;
    return 0;
  };

  const deleteContract = async (id: number) => {
    if (confirm('Delete this contract?')) {
      try { await api.delete(`/contracts/${id}`); fetchContracts(); showToast('Contract deleted successfully', 'success'); }
      catch (err) { showToast('Unable to delete contract. Please try again later.', 'error'); }
    }
  };

  const handleGenerateQR = async (contract: Contract) => {
    try {
      const res = await api.post(`/contracts/${contract.id}/qr`, { frontendUrl: window.location.origin + '/#' });
      // Open modal immediately with updated token/URL from response
      setQrModal({
        open: true,
        contract: {
          ...contract,
          qrToken: res.data?.qrToken || contract.qrToken,
          publicSigningUrl: res.data?.publicSigningUrl || contract.publicSigningUrl,
        },
      });
      // Refresh list in background
      fetchContracts();
      showToast('QR code generated successfully', 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || 'Unable to generate QR code. Please try again later.', 'error');
    }
  };

  const addDriver = () => setAdditionalDrivers([...additionalDrivers, { fullName: '', driverLicenseNumber: '', phone: '' }]);
  const updateDriver = (idx: number, field: string, value: string) => {
    const updated = [...additionalDrivers];
    updated[idx] = { ...updated[idx], [field]: value };
    setAdditionalDrivers(updated);
  };
  const removeDriver = (idx: number) => setAdditionalDrivers(additionalDrivers.filter((_, i) => i !== idx));

  const getStatusBadge = (status: string) => {
    const configs: Record<string, string> = {
      ACTIVE: 'bg-success-50 text-success-500', DRAFT: 'bg-slate-50 text-slate-500',
      WAITING_SIGNATURE: 'bg-warning-50 text-warning-500',
      PENDING_SIGNATURE: 'bg-warning-50 text-warning-500', PARTIALLY_SIGNED: 'bg-brand-50 text-brand-500',
      SIGNED: 'bg-emerald-50 text-emerald-500', COMPLETED: 'bg-brand-50 text-brand-500',
      CANCELLED: 'bg-danger-50 text-danger-500', EXPIRED: 'bg-slate-50 text-slate-400',
    };
    return <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider w-fit ${configs[status] || configs.DRAFT}`}>{status.replace('_', ' ')}</span>;
  };

  const daysCount = () => {
    if (!startDate || !endDate) return 0;
    const start = new Date(startDate);
    const end = new Date(endDate);
    return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)));
  };

  if (loadError) {
    return (
      <div className="p-3 sm:p-4 lg:p-6">
        <ApiErrorState message={loadError} onRetry={fetchContracts} />
      </div>
    );
  }

  return (
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title="Contracts & Agreements"
        subtitle="Signature, PDF, QR verification, and payment status in one legal workflow"
        icon={FileText}
        actions={<>
          <button onClick={exportCSV} className="surface-control flex items-center gap-2 h-10 px-4 font-medium text-xs sm:text-sm active:scale-95">
            <Download size={16} className="sm:hidden" />
            <Download size={18} className="hidden sm:block" />
            <span className="hidden sm:inline">Export</span>
          </button>
          <button onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-xs sm:text-sm active:scale-95">
            <Plus size={16} className="sm:hidden" />
            <Plus size={18} className="hidden sm:block" />
            New Contract
          </button>
        </>}
      />

      {/* Filters */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <FilterChips options={tabs.map((tab) => ({ id: tab.key, label: tab.label }))} activeId={activeTab} onChange={setActiveTab} />
        <SearchInput className="w-full lg:w-[380px]" placeholder="Search by contract #, client..." value={searchQuery} onChange={setSearchQuery} />
      </div>

      {/* Table */}
      {loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 size={32} className="animate-spin text-brand-500" /></div>
      ) : (
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left min-w-[600px]">
              <thead>
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-3 sm:px-5 py-3 sm:py-4">Contract #</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">Client</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">Period</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">Status</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">Total</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/50">
                {filteredData.map((contract) => (
                  <tr key={contract.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                    <td className="px-3 sm:px-5 py-3 sm:py-4">
                      <div className="flex items-center gap-2">
                        <FileText size={13} className="text-slate-400 group-hover:text-brand-500 transition-colors sm:hidden" />
                        <FileText size={14} className="text-slate-400 group-hover:text-brand-500 transition-colors hidden sm:block" />
                        <span className="font-mono text-[10px] sm:text-xs font-bold text-slate-400 group-hover:text-brand-500 transition-colors">{contract.contractNumber}</span>
                      </div>
                    </td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-medium text-[#1e293b]">{contract.clientFullName || 'N/A'}</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400 font-normal">{new Date(contract.startDate).toLocaleDateString()} - {new Date(contract.endDate).toLocaleDateString()}</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4">{getStatusBadge(contract.status)}</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-bold text-[#1e293b]">{contract.totalPrice || 0} MAD</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-right">
                      <div className="flex items-center justify-end gap-0.5 sm:gap-1">
                        <button onClick={() => navigate(`/contracts/${contract.id}`)} className="p-1.5 sm:p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all" title="View">
                          <Eye size={15} className="sm:hidden" />
                          <Eye size={17} className="hidden sm:block" />
                        </button>
                        <button onClick={() => handleGenerateQR(contract)} className="p-1.5 sm:p-2 text-slate-400 hover:text-emerald-500 hover:bg-emerald-50 rounded-lg transition-all" title="QR">
                          <QrCode size={15} className="sm:hidden" />
                          <QrCode size={17} className="hidden sm:block" />
                        </button>
                        <button onClick={() => deleteContract(contract.id)} className="p-1.5 sm:p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all">
                          <Trash2 size={15} className="sm:hidden" />
                          <Trash2 size={17} className="hidden sm:block" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredData.length === 0 && (
                  <tr><td colSpan={6} className="px-3 sm:px-5 py-6 sm:py-8 text-center text-slate-400 text-xs sm:text-sm">No contracts found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Modal */}
      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create Contract" maxWidth="5xl">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main Form */}
          <div className="lg:col-span-2 space-y-5 pr-1">

            {/* Section 0: Select Reservation */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">Select Reservation (Optional)</span>
              </div>
              <select
                value={selectedReservation?.id || ''}
                onChange={(e) => {
                  const resId = Number(e.target.value);
                  handleSelectReservation(reservations.find((r) => r.id === resId) || null);
                }}
                className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              >
                <option value="">-- Walk-in (no reservation) --</option>
                {reservations.map((res) => (
                  <option key={res.id} value={res.id}>
                    #{res.id} — {res.clientName} — {res.vehicleMarque} ({res.dateStart} to {res.dateEnd})
                  </option>
                ))}
              </select>
              {selectedReservation && (
                <div className="p-3 bg-brand-50/50 rounded-xl border border-brand-100 text-sm flex items-center gap-2 text-brand-600">
                  <CheckCircle2 size={14} />
                  Reservation selected. Client, vehicle, and dates are auto-filled.
                </div>
              )}
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 1: Contract Number */}
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.contractNumber')}</label>
                <div className="relative">
                  <Hash size={14} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    autoComplete="off"
                    value={contractNumber}
                    onChange={(e) => setContractNumber(e.target.value)}
                    placeholder={t('contracts.contractNumberPlaceholder')}
                    className="ltr-field w-full ps-9 pe-4 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  />
                </div>
              </div>
              <button
                onClick={generateNumber}
                className="px-4 py-2.5 bg-brand-50 text-brand-500 rounded-xl text-sm font-medium hover:bg-brand-100 transition-all whitespace-nowrap"
              >
                Auto Generate
              </button>
            </div>

            {/* Section 2: Client */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <User size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">Client <span className="text-danger-500">*</span></span>
              </div>

              {selectedReservation ? (
                clientData.clientFullName ? (
                  <ClientCard data={clientData} onClear={() => handleSelectReservation(null)} />
                ) : (
                  <div className="p-3 bg-warning-50 rounded-xl text-sm text-warning-600">Loading client data from reservation...</div>
                )
              ) : (
                <>
                  <SmartClientSearch value={clientData} onSelect={setClientData} required />
                  {clientData.clientFullName && (
                    <ClientCard data={clientData} onClear={() => setClientData({})} />
                  )}
                </>
              )}
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 3: Rental Period & Vehicle */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Calendar size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">Rental Period & Vehicle</span>
                {daysCount() > 0 && (
                  <span className="ms-auto text-xs font-bold text-brand-500 bg-brand-50 px-2 py-0.5 rounded-lg">{daysCount()} {t('contracts.days')}</span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Start Date <span className="text-danger-500">*</span></label>
                  <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Start Time <span className="text-danger-500">*</span></label>
                  <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">End Date <span className="text-danger-500">*</span></label>
                  <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">End Time <span className="text-danger-500">*</span></label>
                  <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
              </div>
              {selectedReservation && selectedVehicle ? (
                <VehicleCard vehicle={selectedVehicle} />
              ) : (
                <SmartVehicleSelector
                  startDate={startDate}
                  startTime={startTime}
                  endDate={endDate}
                  endTime={endTime}
                  value={selectedVehicle?.id}
                  onSelect={(v) => {
                    setSelectedVehicle(v);
                    setPickupLocation('');
                    setReturnLocation('');
                  }}
                />
              )}
              {selectedVehicle && !selectedReservation && <VehicleCard vehicle={selectedVehicle} />}
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 4: Pricing & Rental Details */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Wallet size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">Pricing & Rental Details</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Discount (MAD)</label>
                  <input type="number" autoComplete="off" value={discountAmount} onChange={(e) => { setDiscountAmount(e.target.value); setDiscountPercent(''); }}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Discount (%)</label>
                  <input type="number" autoComplete="off" value={discountPercent} onChange={(e) => { setDiscountPercent(e.target.value); setDiscountAmount(''); }}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Payment Method</label>
                  <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                    <option value="CASH">Cash</option>
                    <option value="CHECK">Check</option>
                    <option value="BANK_TRANSFER">Bank Transfer</option>
                    <option value="CARD">Card</option>
                    <option value="ONLINE">Online Payment</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Amount Paid (MAD)</label>
                  <input type="number" min="0" autoComplete="off" value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Pickup Location</label>
                  <input type="text" autoComplete="off" value={pickupLocation} onChange={(e) => setPickupLocation(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Return Location</label>
                  <input type="text" autoComplete="off" value={returnLocation} onChange={(e) => setReturnLocation(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Notes</label>
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm resize-none focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 5: Documents */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">Required Documents</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                {documents.map((doc, idx) => (
                  <label key={doc.documentType} className="flex items-center gap-2 p-2.5 bg-white border border-slate-100 rounded-xl cursor-pointer hover:bg-slate-50 transition-all">
                    <input type="checkbox" checked={doc.isPresent}
                      onChange={(e) => { const updated = [...documents]; updated[idx] = { ...doc, isPresent: e.target.checked }; setDocuments(updated); }}
                      className="w-4 h-4 rounded border-slate-300 text-brand-500 focus:ring-brand-500 shrink-0" />
                    <span className="text-xs text-slate-600 leading-tight">{doc.documentName}</span>
                  </label>
                ))}
              </div>
            </div>

            {/* Advanced Toggle */}
            <button
              onClick={() => setShowAdvanced(!showAdvanced)}
              className="flex items-center gap-2 text-xs font-medium text-slate-400 hover:text-brand-500 transition-colors"
            >
              {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
              Advanced Options
            </button>

            {showAdvanced && (
              <div className="space-y-4 animate-fade">
                {/* Additional Drivers */}
                <div className="border border-slate-100 rounded-2xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <User size={14} className="text-slate-400" />
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Additional Drivers</span>
                    </div>
                    <button onClick={addDriver} className="text-xs text-brand-500 hover:text-brand-600 font-medium">+ Add Driver</button>
                  </div>
                  {additionalDrivers.length === 0 && (
                    <p className="text-xs text-slate-400">No additional drivers</p>
                  )}
                  {additionalDrivers.map((driver, idx) => (
                    <div key={idx} className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                      <input placeholder="Full Name" value={driver.fullName} onChange={(e) => updateDriver(idx, 'fullName', e.target.value)}
                        className="px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm" />
                      <input placeholder="License #" value={driver.driverLicenseNumber} onChange={(e) => updateDriver(idx, 'driverLicenseNumber', e.target.value)}
                        className="px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm" />
                      <div className="flex gap-2">
                        <input placeholder="Phone" value={driver.phone} onChange={(e) => updateDriver(idx, 'phone', e.target.value)}
                          className="flex-1 px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm" />
                        <button onClick={() => removeDriver(idx)} className="p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all">
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Inspection */}
                <div className="border border-slate-100 rounded-2xl p-4 space-y-3">
                  <div className="flex items-center gap-2">
                    <Shield size={14} className="text-slate-400" />
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Vehicle Inspection</span>
                  </div>
                  <VehicleInspection value={inspectionZones} onChange={setInspectionZones} />
                </div>
              </div>
            )}
          </div>

          {/* Right Sidebar */}
          <div className="space-y-4 lg:sticky lg:top-0 self-start">
            {/* Relation Summary Card */}
            {(clientData.clientFullName || selectedVehicle) && (
              <div className="bg-gradient-to-br from-[#1e293b] to-[#334155] rounded-2xl p-5 text-white space-y-4">
                <div className="flex items-center gap-2 text-white/70">
                  <FileText size={14} />
                  <span className="text-xs font-bold uppercase tracking-wider">Reservation Summary</span>
                </div>

                {clientData.clientFullName && (
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-white/10 rounded-xl flex items-center justify-center text-lg font-bold">
                      {clientData.clientFullName.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold truncate">{clientData.clientFullName}</p>
                      <p className="text-xs text-white/50">{clientData.clientPhone || 'No phone'}</p>
                    </div>
                  </div>
                )}

                {clientData.clientFullName && selectedVehicle && (
                  <div className="flex items-center justify-center">
                    <div className="h-px bg-white/20 flex-1" />
                    <div className="px-3 py-1 bg-white/10 rounded-full text-[10px] font-bold uppercase tracking-wider">rents</div>
                    <div className="h-px bg-white/20 flex-1" />
                  </div>
                )}

                {selectedVehicle && (
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-white/10 rounded-xl flex items-center justify-center">
                      <Car size={18} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold truncate">{selectedVehicle.marque}</p>
                      <p className="text-xs text-white/50">{selectedVehicle.plate} • {selectedVehicle.category}</p>
                    </div>
                  </div>
                )}

                {startDate && endDate && (
                  <div className="pt-2 border-t border-white/10">
                    <div className="flex justify-between text-xs text-white/50">
                      <span>{new Date(startDate).toLocaleDateString()} → {new Date(endDate).toLocaleDateString()}</span>
                      <span className="font-bold text-white">{daysCount()} days</span>
                    </div>
                  </div>
                )}
              </div>
            )}

            <LivePriceSidebar
              vehiclePrice={selectedVehicle?.prixJour || 0}
              dailyPrice={selectedVehicle?.prixJour || 0}
              startDate={startDate}
              endDate={endDate}
              insuranceFees={selectedVehicle?.insuranceFees || 0}
              deliveryFees={selectedVehicle?.deliveryFees || 0}
              discountAmount={discountAmount ? Number(discountAmount) : 0}
              discountPercent={discountPercent ? Number(discountPercent) : 0}
            />

            <button onClick={saveContract} disabled={saving} className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/10 active:scale-95 transition-all disabled:cursor-not-allowed disabled:opacity-70">
              {saving ? (
                <span className="inline-flex items-center justify-center gap-2">
                  <Loader2 size={16} className="animate-spin" />
                  Creating contract...
                </span>
              ) : 'Create Contract'}
            </button>
          </div>
        </div>
      </Modal>

      {/* QR Modal */}
      {qrModal.contract && (
        <QRCodeModal isOpen={qrModal.open} onClose={() => setQrModal({ open: false })}
          qrToken={qrModal.contract.qrToken || ''}
          signingUrl={qrModal.contract.publicSigningUrl || ''}
          contractNumber={qrModal.contract.contractNumber} clientName={qrModal.contract.clientFullName || ''} />
      )}
    </div>
  );
}
