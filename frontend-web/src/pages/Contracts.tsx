import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
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
  MapPin, Phone, RotateCcw, AlertTriangle, XCircle
} from 'lucide-react';

interface Contract {
  id: number;
  contractNumber: string;
  clientFullName: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  vehicleMissing?: boolean;
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

interface TrashedContract {
  id: number;
  contractNumber: string;
  clientFullName?: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  vehicleRegistration?: string;
  startDate?: string;
  endDate?: string;
  previousContractStatus?: string;
  deletedAt: string;
  deletedBy?: string;
  restorableUntil: string;
  daysRemaining: number;
}

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
  }
  return [];
};
/* ── Sub-components ───────────────────────────────────────────────────── */

function ClientCard({ data, onClear }: { data: any; onClear?: () => void }) {
  const { t } = useTranslation();
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
          <button onClick={onClear} className="p-1.5 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all" title={t('contracts.card.clear')}>
            <X size={14} />
          </button>
        )}
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        {data.clientCin && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.cin')}</span>
            <span className="font-medium text-[#1e293b]">{data.clientCin}</span>
          </div>
        )}
        {data.clientPassportNumber && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.passport')}</span>
            <span className="font-medium text-[#1e293b]">{data.clientPassportNumber}</span>
          </div>
        )}
        {data.clientDriverLicense && (
          <div className="flex items-center gap-1.5">
            <Car size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.license')}</span>
            <span className="font-medium text-[#1e293b]">{data.clientDriverLicense}</span>
          </div>
        )}
        {data.clientNationality && (
          <div className="flex items-center gap-1.5">
            <MapPin size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.nationality')}</span>
            <span className="font-medium text-[#1e293b]">{data.clientNationality}</span>
          </div>
        )}
        {data.clientCity && (
          <div className="flex items-center gap-1.5">
            <MapPin size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.city')}</span>
            <span className="font-medium text-[#1e293b]">{data.clientCity}</span>
          </div>
        )}
        {data.clientBirthDate && (
          <div className="flex items-center gap-1.5">
            <Calendar size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.birth')}</span>
            <span className="font-medium text-[#1e293b]">{new Date(data.clientBirthDate).toLocaleDateString()}</span>
          </div>
        )}
        {data.emergencyContactName && (
          <div className="flex items-center gap-1.5 col-span-2">
            <Phone size={10} className="text-brand-400" />
            <span className="text-slate-400">{t('contracts.card.emergency')}</span>
            <span className="font-medium text-[#1e293b]">{data.emergencyContactName} {data.emergencyContactPhone && `(${data.emergencyContactPhone})`}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function VehicleCard({ vehicle }: { vehicle: any }) {
  const { t } = useTranslation();
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
          {vehicle.status || t('common.available')}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs">
        <div className="flex items-center gap-1.5">
          <Hash size={10} className="text-slate-400" />
          <span className="text-slate-400">{t('contracts.card.plate')}</span>
          <span className="font-medium text-[#1e293b]">{vehicle.plate}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Fuel size={10} className="text-slate-400" />
          <span className="text-slate-400">{t('contracts.card.fuel')}</span>
          <span className="font-medium text-[#1e293b]">{vehicle.fuel}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Gauge size={10} className="text-slate-400" />
          <span className="text-slate-400">{t('contracts.card.trans')}</span>
          <span className="font-medium text-[#1e293b]">{vehicle.transmission}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Wallet size={10} className="text-slate-400" />
          <span className="text-slate-400">{t('contracts.card.day')}</span>
          <span className="font-medium text-[#1e293b]">{vehicle.prixJour} MAD</span>
        </div>
        {vehicle.depositAmount > 0 && (
          <div className="flex items-center gap-1.5">
            <Shield size={10} className="text-slate-400" />
            <span className="text-slate-400">{t('contracts.card.deposit')}</span>
            <span className="font-medium text-[#1e293b]">{vehicle.depositAmount} MAD</span>
          </div>
        )}
        {vehicle.gpsEnabled && (
          <div className="flex items-center gap-1">
            <CheckCircle2 size={10} className="text-success-500" />
            <span className="text-[10px] font-bold text-success-500 uppercase tracking-wider">{t('contracts.card.gpsEnabled')}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Contracts() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeTab, setActiveTab] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [trashData, setTrashData] = useState<TrashedContract[]>([]);
  const [trashLoading, setTrashLoading] = useState(false);
  const [trashLoadError, setTrashLoadError] = useState('');
  const [restoringId, setRestoringId] = useState<number | null>(null);
  const [purgingId, setPurgingId] = useState<number | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [restoreConflict, setRestoreConflict] = useState<{
    open: boolean;
    contractId?: number;
    contractNumber?: string;
    vehicleId?: number;
    requestedStartDate?: string;
    requestedEndDate?: string;
    conflictSource?: string;
    conflictId?: number;
    conflictNumber?: string;
    conflictStatus?: string;
    conflictStartDate?: string;
    conflictEndDate?: string;
  }>({ open: false });
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
  const [depositAmount, setDepositAmount] = useState('');
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
  const [templates, setTemplates] = useState<any[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('');

  // New-client inline creation state
  const [clientMode, setClientMode] = useState<'existing' | 'new'>('existing');
  const [newClientForm, setNewClientForm] = useState({ fullName: '', phone: '', cin: '', passportNumber: '', driverLicenseNumber: '', email: '', address: '', dateOfBirth: '', nationality: '' });
  const [newClientErrors, setNewClientErrors] = useState<Record<string, string>>({});
  const [duplicateClientAlert, setDuplicateClientAlert] = useState<{ existingClientId: number; existingClientName: string; existingClientPhone: string; field: string; matchedFields?: string[] } | null>(null);
  const [vehicleConflictAlert, setVehicleConflictAlert] = useState<{ message: string; conflictStartDate?: string; conflictEndDate?: string; conflictSource?: string } | null>(null);
  const [paidAmountAlert, setPaidAmountAlert] = useState<{ paidAmount: number; totalAmount: number } | null>(null);
  const [contractNumberAlert, setContractNumberAlert] = useState<boolean>(false);
  const [dataConflictAlert, setDataConflictAlert] = useState<{ message: string; requestId?: string } | null>(null);

  const { showToast } = useToast();
  const { t } = useTranslation();

  useEffect(() => { fetchContracts(); }, []);
  useEffect(() => { if (activeTab === 'TRASH') fetchTrash(); }, [activeTab]);

  // Real-time auto-refresh when a contract is signed via QR
  useEffect(() => {
    const handleContractUpdated = () => {
      fetchContracts();
      showToast(t('contracts.contractSignedByClient') || 'A contract was signed by the client', 'success');
    };
    window.addEventListener('contract:updated', handleContractUpdated);
    return () => window.removeEventListener('contract:updated', handleContractUpdated);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchContracts = async () => {
    try {
      setLoading(true);
      setLoadError('');
      const { data } = await api.get('/contracts');
      setData(unwrapArray<Contract>(data));
    } catch (err) {
      console.error(err);
      setLoadError('Unable to load contract information. Please try again later.');
    }
    finally { setLoading(false); }
  };

  const fetchTrash = async () => {
    try {
      setTrashLoading(true);
      setTrashLoadError('');
      const { data } = await api.get('/contracts/trash');
      setTrashData(unwrapArray<TrashedContract>(data));
    } catch (err) {
      console.error(err);
      setTrashLoadError(t('contracts.trashLoadFailed'));
    } finally {
      setTrashLoading(false);
    }
  };

  const restoreContract = async (id: number, mode: 'NORMAL' | 'DRAFT_ONLY' = 'NORMAL') => {
    if (restoringId) return;
    console.log('[CONTRACT_RESTORE_CLICK] contractId=', id, 'mode=', mode);
    setRestoringId(id);
    try {
      const payload = { mode };
      console.log('[CONTRACT_RESTORE_PAYLOAD] contractId=', id, 'mode=', mode, 'payload=', JSON.stringify(payload));
      const { data: response } = await api.post(`/contracts/${id}/restore`, payload);
      const afterData = response?.data || {};
      const trashedEntry = trashData.find((c) => c.id === id);
      console.log('[CONTRACT_ACTION_DEBUG]', {
        action: mode === 'DRAFT_ONLY' ? 'RESTORE_DRAFT' : 'RESTORE_NORMAL',
        contractId: id,
        endpoint: `POST /contracts/${id}/restore`,
        beforeStatus: trashedEntry?.previousContractStatus ?? 'UNKNOWN',
        afterStatus: afterData.contractStatus ?? afterData.status ?? 'UNKNOWN',
        deletedBefore: true,
        deletedAfter: false,
        reservationStatus: afterData.reservationStatus,
      });
      showToast(response?.message || 'Contract restored successfully', 'success');
      setTrashData((current) => current.filter((c) => c.id !== id));
      setRestoreConflict({ open: false });
      fetchContracts();
    } catch (err: any) {
      const errorCode: string | undefined = err?.errorCode;
      const conflictData = err?.data;
      console.error('[CONTRACT_RESTORE_ERROR] contractId=', id, 'status=', err?.response?.status, 'errorCode=', errorCode, 'data=', conflictData);
      if (errorCode === 'RESTORE_VEHICLE_CONFLICT') {
        const trashedContract = trashData.find((c) => c.id === id);
        setRestoreConflict({
          open: true,
          contractId: id,
          contractNumber: trashedContract?.contractNumber,
          vehicleId: conflictData?.vehicleId,
          requestedStartDate: conflictData?.requestedStartDate,
          requestedEndDate: conflictData?.requestedEndDate,
          conflictSource: conflictData?.conflictSource,
          conflictId: conflictData?.conflictId,
          conflictNumber: conflictData?.conflictNumber,
          conflictStatus: conflictData?.conflictStatus,
          conflictStartDate: conflictData?.conflictStartDate,
          conflictEndDate: conflictData?.conflictEndDate,
        });
      } else {
        let errorMessage: string;
        if (errorCode === 'RESTORE_WINDOW_EXPIRED') {
          errorMessage = 'Restore window expired. You can only permanently delete this contract.';
        } else if (errorCode === 'CONTRACT_NOT_FOUND') {
          errorMessage = 'Contract not found in trash.';
        } else {
          errorMessage = err?.userMessage || 'Unable to restore this contract. Please try again later.';
        }
        showToast(errorMessage, 'error');
      }
    } finally {
      setRestoringId(null);
    }
  };

  const purgeContractPermanently = async (id: number, contractNumber: string) => {
    if (purgingId) return;
    if (!confirm(`Permanently delete contract ${contractNumber}? This cannot be undone.`)) return;
    setPurgingId(id);
    try {
      const { data: response } = await api.delete(`/contracts/${id}/purge`);
      showToast(response?.message || 'Contract permanently deleted', 'success');
      setTrashData((current) => current.filter((c) => c.id !== id));
    } catch (err: any) {
      const errorCode = err?.errorCode;
      const errData = err?.data as Record<string, any> | undefined;
      if (errorCode === 'CONTRACT_PURGE_BLOCKED' && errData?.blockingTable) {
        showToast(
          `Cannot delete ${contractNumber}: linked records in "${errData.blockingTable}" still exist.`,
          'error',
        );
      } else if (errorCode === 'CONTRACT_PURGE_BLOCKED') {
        showToast(
          err?.userMessage || `Cannot delete ${contractNumber}: linked records still exist.`,
          'error',
        );
      } else {
        showToast(
          err?.userMessage || 'Unable to permanently delete this contract. Please try again later.',
          'error',
        );
      }
    } finally {
      setPurgingId(null);
    }
  };

  const CONTRACT_STATUS_LABELS: Record<string, string> = {
    PENDING_SIGNATURE: t('contracts.statusLabel.PENDING_SIGNATURE'),
    WAITING_SIGNATURE: t('contracts.statusLabel.WAITING_SIGNATURE'),
    WAITING_CLIENT_SIGNATURE: t('contracts.statusLabel.WAITING_CLIENT_SIGNATURE'),
    PARTIALLY_SIGNED: t('contracts.statusLabel.PARTIALLY_SIGNED'),
    DRAFT: t('contracts.statusLabel.DRAFT'),
    SIGNED: t('contracts.statusLabel.SIGNED'),
    ACTIVE: t('contracts.statusLabel.ACTIVE'),
    COMPLETED: t('contracts.statusLabel.COMPLETED'),
    CANCELLED: t('contracts.statusLabel.CANCELLED'),
    EXPIRED: t('contracts.statusLabel.EXPIRED'),
    PAID: t('contracts.statusLabel.PAID'),
  };

  const normalizeStatus = (status: string) => String(status || '').trim().toUpperCase().replace(/[\s-]+/g, '_');

  const WAITING_SIGNATURE_STATUSES = ['WAITING_SIGNATURE', 'PENDING_SIGNATURE', 'WAITING_CLIENT_SIGNATURE', 'DRAFT'];

  const tabs = [
    { key: 'All', label: t('contracts.all') },
    { key: 'ACTIVE', label: t('contracts.active') },
    { key: 'WAITING_SIGNATURE', label: t('contracts.waitingSignature') },
    { key: 'SIGNED', label: t('contracts.signed') },
    { key: 'COMPLETED', label: t('contracts.completed') },
    { key: 'CANCELLED', label: t('contracts.cancelled') },
    { key: 'TRASH', label: t('contracts.trash') },
  ];

  const filteredData = activeTab === 'TRASH' ? [] : data.filter((c) => {
    let matchesTab: boolean;
    if (activeTab === 'All') {
      // All tab: show every non-deleted contract (including CANCELLED ones).
      // Deleted contracts are never in `data` — backend excludes them via @SQLRestriction.
      matchesTab = true;
    } else if (activeTab === 'CANCELLED') {
      // Cancelled tab: business-cancelled contracts that are NOT in trash.
      matchesTab = normalizeStatus(c.status) === 'CANCELLED';
    } else if (activeTab === 'WAITING_SIGNATURE') {
      matchesTab = WAITING_SIGNATURE_STATUSES.includes(normalizeStatus(c.status));
    } else {
      matchesTab = normalizeStatus(c.status) === activeTab;
    }
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
    setDepositAmount('');
    setNotes('');
    setDiscountAmount('');
    setDiscountPercent('');
    setInspectionZones([]);
    setAdditionalDrivers([]);
    setShowAdvanced(false);
    setSelectedReservation(null);
    setClientMode('existing');
    setNewClientForm({ fullName: '', phone: '', cin: '', passportNumber: '', driverLicenseNumber: '', email: '', address: '', dateOfBirth: '', nationality: '' });
    setNewClientErrors({});
    setDuplicateClientAlert(null);
    setVehicleConflictAlert(null);
    setPaidAmountAlert(null);
    setContractNumberAlert(false);
    setDataConflictAlert(null);
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
      const items = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : (data?.content || []));
      setReservations(items.filter((r: any) =>
        (r.status === 'PENDING' || r.status === 'CONFIRMED') && !r.contractId && !r.readOnly
      ));
    } catch (err) {
      setReservations([]);
    }
    try {
      const { data } = await api.get('/contract-templates');
      const activeTemplates = (data?.data || []).filter((item: any) => item.active);
      setTemplates(activeTemplates);
      const defaultTemplate = activeTemplates.find((item: any) => item.default);
      setSelectedTemplateId(defaultTemplate ? String(defaultTemplate.id) : '');
    } catch (err) {
      setTemplates([]);
      setSelectedTemplateId('');
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
      // Auto-populate vehicle — set a minimal card immediately so the UI and
      // validation don't wait on the network round trip, then replace it with
      // full details (plate, daily price, etc.) once they arrive.
      if (res.vehicleId) {
        setSelectedVehicle({ id: res.vehicleId, marque: res.vehicleMarque || res.vehicleName || '' });
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

  // Opens the New Contract modal already filled from a reservation — used when
  // the contract icon is clicked on the Reservations page (source of truth is
  // the read-only /contract-prefill endpoint, then reuses handleSelectReservation
  // so client/vehicle full details and the reservationId link all populate the
  // same way the in-modal "select reservation" dropdown already does).
  const openCreateFromReservation = async (reservationId: number) => {
    await openCreate();
    try {
      const { data: response } = await api.get(`/reservations/${reservationId}/contract-prefill`);
      const p = response?.data || {};
      const reservationLike = {
        id: p.reservationId,
        clientId: p.clientId,
        clientName: p.clientName,
        vehicleId: p.vehicleId,
        vehicleMarque: p.vehicleName,
        dateStart: p.startDate,
        startTime: p.startTime,
        dateEnd: p.endDate,
        endTime: p.endTime,
        pickupLocation: p.pickupLocation,
        returnLocation: p.returnLocation,
        notes: p.notes,
      };
      // The reservation-select dropdown only lists reservations from the
      // filtered /reservations fetch — inject this one so the dropdown shows
      // it as selected even if it was filtered out or hasn't loaded yet.
      setReservations((prev) => (prev.some((r) => r.id === reservationLike.id) ? prev : [reservationLike, ...prev]));
      handleSelectReservation(reservationLike);
      if (p.depositAmount != null) setDepositAmount(String(p.depositAmount));
      if (p.paidAmount != null) setPaidAmount(String(p.paidAmount));
      if (import.meta.env.DEV) {
        console.log('[CONTRACT_PREFILL_DEBUG]', {
          source: 'reservation-icon',
          reservationId: p.reservationId,
          clientId: p.clientId,
          clientName: p.clientName,
          vehicleId: p.vehicleId,
          vehicleName: p.vehicleName,
          startDate: p.startDate,
          startTime: p.startTime,
          endDate: p.endDate,
          endTime: p.endTime,
          totalAmount: p.totalAmount,
          modalPrefilled: true,
        });
      }
      showToast(t('contracts.fromReservation.prefilledNotice'), 'info');
    } catch (err: any) {
      const errorCode = err?.response?.data?.errorCode || err?.errorCode;
      showToast(
        errorCode ? t(`errors.${errorCode}`, { defaultValue: t('contracts.prefillLoadFailed') }) : t('contracts.prefillLoadFailed'),
        'error',
      );
    }
  };

  useEffect(() => {
    const fromReservationId = searchParams.get('fromReservationId');
    if (fromReservationId) {
      openCreateFromReservation(Number(fromReservationId));
      setSearchParams({}, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Normalize any date string to ISO yyyy-MM-dd (handles HTML date inputs which already return this format)
  const toIsoDate = (value: string): string => {
    if (!value) return value;
    // Already ISO
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
    // MM/DD/YYYY → yyyy-MM-dd
    const parts = value.split('/');
    if (parts.length === 3 && parts[2].length === 4) return `${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}`;
    return value;
  };

  // Normalize time to 24-hour HH:mm (handles "09:00 AM" / "06:00 PM" edge cases)
  const to24hTime = (value: string): string => {
    if (!value) return value;
    if (/^\d{2}:\d{2}$/.test(value)) return value;
    const match = value.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/i);
    if (match) {
      let hours = parseInt(match[1], 10);
      const minutes = match[2];
      const period = match[3].toUpperCase();
      if (period === 'AM' && hours === 12) hours = 0;
      if (period === 'PM' && hours !== 12) hours += 12;
      return `${String(hours).padStart(2, '0')}:${minutes}`;
    }
    return value;
  };

  const saveContract = async () => {
    if (saving) return;
    try {
      setSaving(true);

      // Validate client mode
      if (clientMode === 'existing') {
        if (!clientData.clientId) {
          showToast('Please select a saved client', 'warning');
          return;
        }
      } else {
        const errs: Record<string, string> = {};
        if (!newClientForm.fullName.trim()) errs.fullName = 'Full name is required';
        if (!newClientForm.phone.trim()) errs.phone = 'Phone is required';
        if (!newClientForm.cin.trim() && !newClientForm.driverLicenseNumber.trim()) errs.cin = 'CIN or driver license number is required';
        if (!newClientForm.driverLicenseNumber.trim()) errs.driverLicenseNumber = 'Driver license number is required';
        if (Object.keys(errs).length > 0) {
          setNewClientErrors(errs);
          showToast('Please complete the required client fields', 'warning');
          return;
        }
        setNewClientErrors({});
      }
      if (!startDate || !startTime || !endDate || !endTime || !selectedVehicle) {
        showToast('Please fill in rental dates and select an available vehicle', 'warning');
        return;
      }

      setDuplicateClientAlert(null);
      setVehicleConflictAlert(null);
      setPaidAmountAlert(null);
      setContractNumberAlert(false);
      setDataConflictAlert(null);
      const isoStart = toIsoDate(startDate);
      const isoEnd   = toIsoDate(endDate);
      const h24Start = to24hTime(startTime);
      const h24End   = to24hTime(endTime);
      const payload: any = {
        contractNumber: contractNumber || undefined,
        vehicleId: selectedVehicle.id,
        selectedTemplateId: selectedTemplateId ? Number(selectedTemplateId) : undefined,
        startDate: isoStart,
        startTime: h24Start,
        endDate: isoEnd,
        endTime: h24End,
        pickupTime: h24Start,
        returnTime: h24End,
        pickupLocation,
        returnLocation,
        dailyPrice: selectedVehicle.prixJour,
        depositAmount: Number(depositAmount) || 0,
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
      if (selectedReservation) {
        payload.reservationId = selectedReservation.id;
      }
      if (clientMode === 'existing') {
        payload.clientId = clientData.clientId;
      } else {
        payload.newClient = {
          fullName: newClientForm.fullName.trim(),
          phone: newClientForm.phone.trim(),
          cin: newClientForm.cin.trim() || undefined,
          passportNumber: newClientForm.passportNumber.trim() || undefined,
          driverLicenseNumber: newClientForm.driverLicenseNumber.trim() || undefined,
          email: newClientForm.email.trim() || undefined,
          address: newClientForm.address.trim() || undefined,
          dateOfBirth: newClientForm.dateOfBirth || undefined,
          nationality: newClientForm.nationality.trim() || undefined,
        };
      }
      if (import.meta.env.DEV) {
        console.log('[DIRECT_CREATE_PAYLOAD]', JSON.stringify(payload, null, 2));
        console.log('[CONTRACT_SUBMIT_DEBUG]', {
          reservationId: payload.reservationId,
          clientId: payload.clientId,
          vehicleId: payload.vehicleId,
          startDate: payload.startDate,
          endDate: payload.endDate,
          totalAmount: (selectedVehicle.prixJour || 0) * daysCount(),
        });
      }
      const { data } = await api.post('/contracts/direct-create', payload);
      const contractId = data?.data?.contractId || data?.contractId || data?.id;
      const isExisting = data?.data?.isNew === false;
      const clientWasCreated = data?.data?.clientCreated === true;
      const msg = clientWasCreated
        ? `Contract created and new client "${data?.data?.clientName}" saved.`
        : (isExisting ? t('contracts.fromReservation.alreadyExists') : (data?.message || 'Contract and reservation created successfully'));
      showToast(msg, isExisting ? 'info' : 'success');
      setIsModalOpen(false);
      fetchContracts();
      if (contractId) navigate(`/contracts/${contractId}`);
    } catch (err: any) {
      if (import.meta.env.DEV) {
        console.warn('[DIRECT_CREATE_ERROR]', {
          status: err?.response?.status,
          errorCode: err?.response?.data?.errorCode || err?.errorCode,
          data: err?.response?.data?.data,
          message: err?.response?.data?.message || err?.message,
        });
      }
      const errData = err?.response?.data || {};
      const errCode = errData.errorCode || err?.errorCode;

      // CLIENT_ALREADY_EXISTS — show inline banner, keep modal open
      if (errCode === 'CLIENT_ALREADY_EXISTS') {
        const d = errData.data || {};
        setDuplicateClientAlert({
          existingClientId: d.existingClientId,
          existingClientName: d.existingClientName || 'Unknown client',
          existingClientPhone: d.existingClientPhone || '',
          field: errData.field || d.matchedFields?.[0] || 'phone',
          matchedFields: d.matchedFields,
        });
        return;
      }

      // VEHICLE_ALREADY_RESERVED / VEHICLE_INACTIVE — show inline banner, keep modal open
      if (errCode === 'VEHICLE_ALREADY_RESERVED' || errCode === 'VEHICLE_INACTIVE') {
        const d = err?.data || errData.data || {};
        setVehicleConflictAlert({
          message: errData.message || err?.userMessage || contractErrorMessage(err),
          conflictStartDate: d.conflictStartDate,
          conflictEndDate: d.conflictEndDate,
          conflictSource: d.conflictSource,
        });
        return;
      }

      // PAID_AMOUNT_EXCEEDS_TOTAL — show inline banner, keep modal open, focus amount field
      if (errCode === 'PAID_AMOUNT_EXCEEDS_TOTAL') {
        const d = errData.data || {};
        setPaidAmountAlert({
          paidAmount: Number(d.paidAmount ?? paidAmount),
          totalAmount: Number(d.totalAmount ?? 0),
        });
        return;
      }

      // CONTRACT_NUMBER_EXISTS — show inline banner with regenerate button, keep modal open
      if (errCode === 'CONTRACT_NUMBER_EXISTS') {
        setContractNumberAlert(true);
        return;
      }

      // DATA_CONFLICT — show inline banner with requestId, keep modal open
      if (errCode === 'DATA_CONFLICT') {
        setDataConflictAlert({
          message: errData.message || 'A data conflict prevented saving. Please check your input and try again.',
          requestId: errData.requestId,
        });
        return;
      }

      showToast(contractErrorMessage(err), 'error');
    } finally {
      setSaving(false);
    }
  };

  function contractErrorMessage(err: any): string {
    const errData = err?.response?.data || {};
    const errorCode = err?.errorCode || errData.errorCode;
    const conflictData = err?.data || errData.data || {};
    switch (errorCode) {
      case 'VEHICLE_ALREADY_RESERVED': {
        const period = conflictData.conflictStartDate && conflictData.conflictEndDate
          ? ` (${conflictData.conflictStartDate} → ${conflictData.conflictEndDate}, ${conflictData.conflictSource || 'booking'} #${conflictData.conflictId ?? ''})`
          : '';
        return `Vehicle is not available for the selected dates.${period}`;
      }
      case 'VEHICLE_INACTIVE':
        return 'This vehicle is not available for rental in its current status.';
      case 'VEHICLE_PRICE_MISSING':
        return 'This vehicle has no daily price. Update vehicle pricing first.';
      case 'PAYMENT_REQUIRED_FIELD_MISSING':
        return 'Payment creation failed because a required payment field is missing.';
      case 'CLIENT_NOT_IN_AGENCY':
        return 'Selected client does not belong to this agency.';
      case 'VEHICLE_NOT_IN_AGENCY':
        return 'Selected vehicle does not belong to this agency.';
      case 'TEMPLATE_PLAN_REQUIRED':
      case 'TEMPLATE_NOT_ALLOWED':
        return `This contract template requires a higher plan (${conflictData.requiredPlan || 'STANDARD'}). Choose another template or upgrade your plan.`;
      case 'CONTRACT_NUMBER_EXISTS':
        return 'Contract number conflict. Please try submitting again — a new number will be generated.';
      case 'DATA_CONFLICT':
        return errData.message || err?.userMessage || 'A data conflict prevented saving. Please check your input and try again.';
      default:
        return errData.message || err?.userMessage || 'Unable to save contract. Please try again later.';
    }
  }

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
    const contract = data.find((c) => c.id === id);
    const activeWarning = contract && ['ACTIVE', 'SIGNED', 'WAITING_SIGNATURE'].includes(contract.status)
      ? t('contracts.confirmMoveToTrashActiveWarning')
      : '';
    if (!confirm(`${t('contracts.confirmMoveToTrash')}${activeWarning}`)) return;

    const previous = data;
    const beforeStatus = contract?.status;
    setData((current) => current.filter((c) => c.id !== id));
    console.log('[CONTRACT_ACTION_DEBUG]', { action: 'SOFT_DELETE', contractId: id, endpoint: `DELETE /contracts/${id}`, beforeStatus });
    try {
      const { data: response } = await api.delete(`/contracts/${id}`);
      const afterData = response?.data || {};
      const afterStatus = afterData.contractStatus ?? beforeStatus;
      console.log('[CONTRACT_ACTION_DEBUG]', {
        action: 'SOFT_DELETE',
        contractId: id,
        endpoint: `DELETE /contracts/${id}`,
        beforeStatus,
        afterStatus,
        deletedBefore: false,
        deletedAfter: true,
        statusUnchanged: afterStatus === beforeStatus,
      });
      showToast(response?.message || 'Contract moved to trash', 'success');
      fetchContracts();
      fetchTrash();
    } catch (err: any) {
      const status = err?.response?.status;
      if (status === 404) {
        showToast(err?.userMessage || 'Contract not found or already deleted.', 'warning');
        fetchContracts();
      } else if (err?.errorCode === 'RELATED_ENTITY_MISSING') {
        setData(previous);
        showToast('Linked vehicle is missing. Contract can still be deleted safely — please try again.', 'error');
      } else {
        setData(previous);
        showToast(err?.userMessage || 'Unable to delete contract. Please try again later.', 'error');
      }
    }
  };

  const cancelContract = async (id: number) => {
    const contract = data.find((c) => c.id === id);
    if (!confirm(`Cancel contract ${contract?.contractNumber ?? id}? This will mark it as Cancelled (not deleted).`)) return;
    setCancellingId(id);
    const beforeStatus = contract?.status;
    console.log('[CONTRACT_ACTION_DEBUG]', { action: 'CANCEL', contractId: id, endpoint: `POST /contracts/${id}/cancel`, beforeStatus });
    try {
      const { data: response } = await api.post(`/contracts/${id}/cancel`);
      const afterData = response?.data || {};
      console.log('[CONTRACT_ACTION_DEBUG]', {
        action: 'CANCEL',
        contractId: id,
        endpoint: `POST /contracts/${id}/cancel`,
        beforeStatus,
        afterStatus: afterData.contractStatus ?? 'CANCELLED',
        deletedBefore: false,
        deletedAfter: false,
      });
      showToast(response?.message || 'Contract cancelled', 'success');
      fetchContracts();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to cancel contract. Please try again.', 'error');
    } finally {
      setCancellingId(null);
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
      ACTIVE: 'bg-success-50 text-success-500',
      DRAFT: 'bg-slate-50 text-slate-500',
      WAITING_SIGNATURE: 'bg-warning-50 text-warning-500',
      PENDING_SIGNATURE: 'bg-warning-50 text-warning-500',
      WAITING_CLIENT_SIGNATURE: 'bg-warning-50 text-warning-500',
      PARTIALLY_SIGNED: 'bg-brand-50 text-brand-500',
      SIGNED: 'bg-emerald-50 text-emerald-500',
      COMPLETED: 'bg-brand-50 text-brand-500',
      CANCELLED: 'bg-danger-50 text-danger-500',
      EXPIRED: 'bg-slate-50 text-slate-400',
      PAID: 'bg-success-50 text-success-500',
    };
    const label = CONTRACT_STATUS_LABELS[normalizeStatus(status)] ?? status.replaceAll('_', ' ');
    return <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider w-fit ${configs[normalizeStatus(status)] || configs.DRAFT}`}>{label}</span>;
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
        title={t('contracts.title')}
        subtitle={t('contracts.subtitle')}
        icon={FileText}
        actions={<>
          <button onClick={exportCSV} className="surface-control flex items-center gap-2 h-10 px-4 font-medium text-xs sm:text-sm active:scale-95">
            <Download size={16} className="sm:hidden" />
            <Download size={18} className="hidden sm:block" />
            <span className="hidden sm:inline">{t('contracts.export')}</span>
          </button>
          <button onClick={openCreate} className="premium-action flex items-center gap-2 h-10 px-4 font-medium text-xs sm:text-sm active:scale-95">
            <Plus size={16} className="sm:hidden" />
            <Plus size={18} className="hidden sm:block" />
            {t('contracts.newContract')}
          </button>
        </>}
      />

      {/* Filters */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <FilterChips options={tabs.map((tab) => ({ id: tab.key, label: tab.label }))} activeId={activeTab} onChange={setActiveTab} />
        <SearchInput className="w-full lg:w-[380px]" placeholder={t('contracts.searchPlaceholder')} value={searchQuery} onChange={setSearchQuery} />
      </div>

      {/* Table */}
      {activeTab === 'TRASH' ? (
        trashLoadError ? (
          <ApiErrorState message={trashLoadError} onRetry={fetchTrash} />
        ) : trashLoading ? (
          <div className="flex items-center justify-center py-12"><Loader2 size={32} className="animate-spin text-brand-500" /></div>
        ) : (
          <div className="data-surface">
            <div className="overflow-x-auto no-scrollbar">
              <table className="w-full text-left min-w-[700px]">
                <thead>
                  <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.contractHash')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.client')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.vehicle')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.deletedAt')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.daysRemaining')}</th>
                    <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">{t('contracts.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#e8e6e1]/50">
                  {trashData.map((contract) => (
                    <tr key={contract.id} className="hover:bg-[#f5f5f0]/40 transition-colors group">
                      <td className="px-3 sm:px-5 py-3 sm:py-4">
                        <span className="font-mono text-[10px] sm:text-xs font-bold text-slate-400">{contract.contractNumber}</span>
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-medium text-[#1e293b]">{contract.clientFullName || 'N/A'}</td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400">
                        {[contract.vehicleBrand, contract.vehicleModel].filter(Boolean).join(' ') || 'N/A'}
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400">
                        {contract.deletedAt ? new Date(contract.deletedAt).toLocaleString() : 'N/A'}
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4">
                        <div className="flex flex-col gap-1">
                          {contract.previousContractStatus && (
                            <span className="px-2 py-0.5 rounded-md text-[9px] font-bold uppercase tracking-wider bg-slate-100 text-slate-500 w-fit">
                              {t('contracts.previousStatus', { status: contract.previousContractStatus })}
                            </span>
                          )}
                          <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider w-fit ${
                            contract.daysRemaining <= 3 ? 'bg-danger-50 text-danger-500' : 'bg-warning-50 text-warning-500'
                          }`}>
                            {t('contracts.daysLeftCount', { count: contract.daysRemaining })}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 sm:px-5 py-3 sm:py-4 text-right">
                        <div className="flex items-center justify-end gap-0.5 sm:gap-1">
                          <button
                            onClick={() => restoreContract(contract.id)}
                            disabled={restoringId === contract.id}
                            className="p-1.5 sm:p-2 text-slate-400 hover:text-success-500 hover:bg-success-50 rounded-lg transition-all disabled:opacity-50"
                            title={t('contracts.restore')}
                          >
                            {restoringId === contract.id
                              ? <Loader2 size={15} className="animate-spin" />
                              : <RotateCcw size={15} className="sm:hidden" />}
                            <RotateCcw size={17} className="hidden sm:block" />
                          </button>
                          <button
                            onClick={() => purgeContractPermanently(contract.id, contract.contractNumber)}
                            disabled={purgingId === contract.id}
                            className="p-1.5 sm:p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all disabled:opacity-50"
                            title={t('contracts.deletePermanently')}
                          >
                            {purgingId === contract.id
                              ? <Loader2 size={15} className="animate-spin" />
                              : <Trash2 size={15} className="sm:hidden" />}
                            <Trash2 size={17} className="hidden sm:block" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {trashData.length === 0 && (
                    <tr><td colSpan={6} className="px-3 sm:px-5 py-6 sm:py-8 text-center text-slate-400 text-xs sm:text-sm">
                      <div className="flex flex-col items-center gap-2">
                        <Trash2 size={20} className="text-slate-300" />
                        {t('contracts.trashEmpty')}
                      </div>
                    </td></tr>
                  )}
                </tbody>
              </table>
            </div>
            {trashData.length > 0 && (
              <div className="px-3 sm:px-5 py-3 flex items-center gap-2 text-[11px] text-slate-400 border-t border-[#e8e6e1]/50">
                <AlertTriangle size={12} />
                {t('contracts.autoDeleteNotice')}
              </div>
            )}
          </div>
        )
      ) : loading ? (
        <div className="flex items-center justify-center py-12"><Loader2 size={32} className="animate-spin text-brand-500" /></div>
      ) : (
        <div className="data-surface">
          <div className="overflow-x-auto no-scrollbar">
            <table className="w-full text-left min-w-[600px]">
              <thead>
                <tr className="bg-[#f5f5f0]/60 text-slate-400 text-[10px] font-bold uppercase tracking-[0.08em]">
                  <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.contractHash')}</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.client')}</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.period')}</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.status')}</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4">{t('contracts.total')}</th>
                  <th className="px-3 sm:px-5 py-3 sm:py-4 text-right">{t('contracts.actions')}</th>
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
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-medium text-[#1e293b]">
                      {contract.clientFullName || 'N/A'}
                      {contract.vehicleMissing && (
                        <span className="ml-2 inline-flex items-center rounded-md bg-amber-50 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700" title={t('contracts.card.vehicleRemovedDesc')}>
                          {t('contracts.card.vehicleRemoved')}
                        </span>
                      )}
                    </td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm text-slate-400 font-normal">{new Date(contract.startDate).toLocaleDateString()} - {new Date(contract.endDate).toLocaleDateString()}</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4">{getStatusBadge(contract.status)}</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-xs sm:text-sm font-bold text-[#1e293b]">{contract.totalPrice || 0} MAD</td>
                    <td className="px-3 sm:px-5 py-3 sm:py-4 text-right">
                      <div className="flex items-center justify-end gap-0.5 sm:gap-1">
                        <button onClick={() => navigate(`/contracts/${contract.id}`)} className="p-1.5 sm:p-2 text-slate-400 hover:text-brand-500 hover:bg-brand-50 rounded-lg transition-all" title={t('contracts.view')}>
                          <Eye size={15} className="sm:hidden" />
                          <Eye size={17} className="hidden sm:block" />
                        </button>
                        <button onClick={() => handleGenerateQR(contract)} className="p-1.5 sm:p-2 text-slate-400 hover:text-emerald-500 hover:bg-emerald-50 rounded-lg transition-all" title="QR">
                          <QrCode size={15} className="sm:hidden" />
                          <QrCode size={17} className="hidden sm:block" />
                        </button>
                        {contract.status !== 'CANCELLED' && contract.status !== 'COMPLETED' && (
                          <button
                            onClick={() => cancelContract(contract.id)}
                            disabled={cancellingId === contract.id}
                            className="p-1.5 sm:p-2 text-slate-400 hover:text-amber-500 hover:bg-amber-50 rounded-lg transition-all disabled:opacity-50"
                            title={t('contracts.form.cancelContract')}
                          >
                            {cancellingId === contract.id
                              ? <Loader2 size={15} className="animate-spin" />
                              : <><XCircle size={15} className="sm:hidden" /><XCircle size={17} className="hidden sm:block" /></>
                            }
                          </button>
                        )}
                        <button onClick={() => deleteContract(contract.id)} className="p-1.5 sm:p-2 text-slate-400 hover:text-danger-500 hover:bg-danger-50 rounded-lg transition-all" title={t('contracts.moveToTrash')}>
                          <Trash2 size={15} className="sm:hidden" />
                          <Trash2 size={17} className="hidden sm:block" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredData.length === 0 && (
                  <tr><td colSpan={6} className="px-3 sm:px-5 py-6 sm:py-8 text-center text-slate-400 text-xs sm:text-sm">{t('common.noResults')}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Modal */}
      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={t('contracts.form.createContractTitle')} maxWidth="5xl">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main Form */}
          <div className="lg:col-span-2 space-y-5 pr-1">

            {selectedReservation && (
              <div className="p-3.5 bg-gradient-to-r from-brand-500 to-brand-600 rounded-xl text-white shadow-sm">
                <p className="text-sm font-bold">
                  {t('contracts.fromReservation.title')} #RES-{selectedReservation.id}
                </p>
                <p className="text-xs text-white/80 mt-0.5">{t('contracts.fromReservation.subtitle')}</p>
              </div>
            )}

            {/* Section 0: Select Reservation */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">{t('contracts.form.selectReservationOptional')}</span>
              </div>
              <select
                value={selectedReservation?.id || ''}
                onChange={(e) => {
                  const resId = Number(e.target.value);
                  handleSelectReservation(reservations.find((r) => r.id === resId) || null);
                }}
                className="w-full px-3 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              >
                <option value="">{t('contracts.form.walkInNoReservation')}</option>
                {reservations.map((res) => (
                  <option key={res.id} value={res.id}>
                    #{res.id} — {res.clientName} — {res.vehicleMarque} ({res.dateStart} to {res.dateEnd})
                  </option>
                ))}
              </select>
              {selectedReservation && (
                <div className="p-3 bg-brand-50/50 rounded-xl border border-brand-100 text-sm flex items-center gap-2 text-brand-600">
                  <CheckCircle2 size={14} />
                  {t('contracts.form.reservationSelectedHint')}
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
                {t('contracts.form.autoGenerate')}
              </button>
            </div>

            {/* Section 2: Client */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <User size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">{t('contracts.client')} <span className="text-danger-500">*</span></span>
              </div>

              {selectedReservation ? (
                clientData.clientFullName ? (
                  <ClientCard data={clientData} onClear={() => handleSelectReservation(null)} />
                ) : (
                  <div className="p-3 bg-warning-50 rounded-xl text-sm text-warning-600">{t('contracts.form.loadingClientFromReservation')}</div>
                )
              ) : (
                <>
                  {/* Mode toggle */}
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => { setClientMode('existing'); setNewClientErrors({}); setDuplicateClientAlert(null); }}
                      className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold border transition-all ${clientMode === 'existing' ? 'bg-brand-500 text-white border-brand-500' : 'bg-white text-slate-500 border-slate-200 hover:border-brand-300'}`}
                    >
                      {t('contracts.form.searchExistingClient')}
                    </button>
                    <button
                      type="button"
                      onClick={() => { setClientMode('new'); setClientData({}); setDuplicateClientAlert(null); }}
                      className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold border transition-all ${clientMode === 'new' ? 'bg-success-500 text-white border-success-500' : 'bg-white text-slate-500 border-slate-200 hover:border-success-300'}`}
                    >
                      {t('contracts.form.newClientToggle')}
                    </button>
                  </div>

                  {/* Existing client search */}
                  {clientMode === 'existing' && (
                    <>
                      <SmartClientSearch value={clientData} onSelect={setClientData} required />
                      {clientData.clientFullName && (
                        <ClientCard data={clientData} onClear={() => setClientData({})} />
                      )}
                    </>
                  )}

                  {/* Inline new client form */}
                  {clientMode === 'new' && (
                    <div className="rounded-xl border border-success-200 bg-success-50/40 p-3 space-y-3">
                      <p className="text-xs font-semibold text-success-700 uppercase tracking-wide">{t('contracts.form.newClientInfo')}</p>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.fullName')} <span className="text-danger-500">*</span></label>
                          <input
                            type="text" value={newClientForm.fullName} placeholder={t('contracts.form.fullNamePlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, fullName: e.target.value }))}
                            className={`w-full px-3 py-2 bg-white border rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 transition-all ${newClientErrors.fullName ? 'border-danger-400' : 'border-slate-200 focus:border-success-400'}`}
                          />
                          {newClientErrors.fullName && <p className="text-xs text-danger-500 mt-0.5">{newClientErrors.fullName}</p>}
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.phone')} <span className="text-danger-500">*</span></label>
                          <input
                            type="tel" value={newClientForm.phone} placeholder={t('contracts.form.phoneNumberPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, phone: e.target.value }))}
                            className={`w-full px-3 py-2 bg-white border rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 transition-all ${newClientErrors.phone ? 'border-danger-400' : 'border-slate-200 focus:border-success-400'}`}
                          />
                          {newClientErrors.phone && <p className="text-xs text-danger-500 mt-0.5">{newClientErrors.phone}</p>}
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.cinLabel')}</label>
                          <input
                            type="text" value={newClientForm.cin} placeholder={t('contracts.form.cinNumberPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, cin: e.target.value }))}
                            className={`w-full px-3 py-2 bg-white border rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 transition-all ${newClientErrors.cin ? 'border-danger-400' : 'border-slate-200 focus:border-success-400'}`}
                          />
                          {newClientErrors.cin && <p className="text-xs text-danger-500 mt-0.5">{newClientErrors.cin}</p>}
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.passportOptional')}</label>
                          <input
                            type="text" value={newClientForm.passportNumber} placeholder={t('contracts.form.passportNumberPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, passportNumber: e.target.value }))}
                            className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 focus:border-success-400 transition-all"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.driverLicenseNo')} <span className="text-danger-500">*</span></label>
                          <input
                            type="text" value={newClientForm.driverLicenseNumber} placeholder={t('contracts.form.licenseNumberPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, driverLicenseNumber: e.target.value }))}
                            className={`w-full px-3 py-2 bg-white border rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 transition-all ${newClientErrors.driverLicenseNumber ? 'border-danger-400' : 'border-slate-200 focus:border-success-400'}`}
                          />
                          {newClientErrors.driverLicenseNumber && <p className="text-xs text-danger-500 mt-0.5">{newClientErrors.driverLicenseNumber}</p>}
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.emailOptional')}</label>
                          <input
                            type="email" value={newClientForm.email} placeholder={t('contracts.form.emailAddressPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, email: e.target.value }))}
                            className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 focus:border-success-400 transition-all"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.addressOptional')}</label>
                          <input
                            type="text" value={newClientForm.address} placeholder={t('contracts.form.streetAddressPlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, address: e.target.value }))}
                            className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 focus:border-success-400 transition-all"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.dobOptional')}</label>
                          <input
                            type="date" value={newClientForm.dateOfBirth}
                            onChange={e => setNewClientForm(p => ({ ...p, dateOfBirth: e.target.value }))}
                            className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 focus:border-success-400 transition-all"
                          />
                        </div>
                        <div className="sm:col-span-2">
                          <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.nationalityOptional')}</label>
                          <input
                            type="text" value={newClientForm.nationality} placeholder={t('contracts.form.moroccanExamplePlaceholder')}
                            onChange={e => setNewClientForm(p => ({ ...p, nationality: e.target.value }))}
                            className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-success-100 focus:border-success-400 transition-all"
                          />
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Duplicate client conflict banner */}
                  {duplicateClientAlert && (
                    <div className="rounded-xl border border-warning-300 bg-warning-50 p-3 space-y-2">
                      <p className="text-sm font-semibold text-warning-800">{t('contracts.form.clientAlreadyExists')}</p>
                      <p className="text-xs text-warning-700">
                        {t('contracts.form.duplicateMatchPrefix')}{' '}
                        <strong>{duplicateClientAlert.matchedFields?.join(', ') || duplicateClientAlert.field}</strong> {t('contracts.form.duplicateMatchSuffix')}{' '}
                        <strong>{duplicateClientAlert.existingClientName}</strong>
                        {duplicateClientAlert.existingClientPhone ? ` — ${duplicateClientAlert.existingClientPhone}` : ''}
                      </p>
                      <button
                        type="button"
                        onClick={() => {
                          setClientData({ clientId: duplicateClientAlert.existingClientId, clientFullName: duplicateClientAlert.existingClientName, clientPhone: duplicateClientAlert.existingClientPhone });
                          setClientMode('existing');
                          setDuplicateClientAlert(null);
                        }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-warning-600 text-white text-xs font-semibold rounded-lg hover:bg-warning-700 transition-colors"
                      >
                        {t('contracts.form.useExistingClient')}
                      </button>
                    </div>
                  )}

                  {/* Vehicle availability conflict banner */}
                  {vehicleConflictAlert && (
                    <div className="rounded-xl border border-danger-300 bg-danger-50 p-3 space-y-1.5">
                      <p className="text-sm font-semibold text-danger-800">{t('contracts.form.vehicleNotAvailable')}</p>
                      <p className="text-xs text-danger-700">{vehicleConflictAlert.message}</p>
                      {vehicleConflictAlert.conflictStartDate && vehicleConflictAlert.conflictEndDate && (
                        <p className="text-xs text-danger-600">
                          {t('contracts.form.conflictLabel')} {vehicleConflictAlert.conflictStartDate} → {vehicleConflictAlert.conflictEndDate}
                          {vehicleConflictAlert.conflictSource ? ` (${vehicleConflictAlert.conflictSource.toLowerCase()})` : ''}
                        </p>
                      )}
                      <p className="text-xs text-danger-600">{t('contracts.form.chooseDifferentDates')}</p>
                    </div>
                  )}

                  {/* Paid amount exceeds total banner */}
                  {paidAmountAlert && (
                    <div className="rounded-xl border border-danger-300 bg-danger-50 p-3 space-y-1.5">
                      <p className="text-sm font-semibold text-danger-800">{t('contracts.form.amountPaidExceedsTotal')}</p>
                      <p className="text-xs text-danger-700">
                        {t('contracts.form.amountPaidExceedsMsg', { paid: paidAmountAlert.paidAmount.toLocaleString(), total: paidAmountAlert.totalAmount.toLocaleString() })}
                      </p>
                      <button
                        type="button"
                        onClick={() => { setPaidAmount(String(paidAmountAlert.totalAmount)); setPaidAmountAlert(null); }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-danger-600 text-white text-xs font-semibold rounded-lg hover:bg-danger-700 transition-colors"
                      >
                        {t('contracts.form.setToMax', { amount: paidAmountAlert.totalAmount.toLocaleString() })}
                      </button>
                    </div>
                  )}

                  {/* Contract number conflict banner */}
                  {contractNumberAlert && (
                    <div className="rounded-xl border border-warning-300 bg-warning-50 p-3 space-y-1.5">
                      <p className="text-sm font-semibold text-warning-800">{t('contracts.form.contractNumberExists')}</p>
                      <p className="text-xs text-warning-700">
                        {t('contracts.form.contractNumberInUse', { number: contractNumber })}
                      </p>
                      <button
                        type="button"
                        onClick={async () => { setContractNumberAlert(false); await generateNumber(); }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-warning-600 text-white text-xs font-semibold rounded-lg hover:bg-warning-700 transition-colors"
                      >
                        {t('contracts.form.generateNewNumber')}
                      </button>
                    </div>
                  )}

                  {/* Data conflict banner (fallback) */}
                  {dataConflictAlert && (
                    <div className="rounded-xl border border-danger-300 bg-danger-50 p-3 space-y-1.5">
                      <p className="text-sm font-semibold text-danger-800">{t('contracts.form.saveFailed')}</p>
                      <p className="text-xs text-danger-700">{dataConflictAlert.message}</p>
                      {dataConflictAlert.requestId && (
                        <p className="text-xs text-danger-500 font-mono">{t('contracts.form.requestIdLabel')} {dataConflictAlert.requestId}</p>
                      )}
                      <p className="text-xs text-danger-600">{t('contracts.form.checkInputTryAgain')}</p>
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 3: Rental Period & Vehicle */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Calendar size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">{t('contracts.form.rentalPeriodVehicle')}</span>
                {daysCount() > 0 && (
                  <span className="ms-auto text-xs font-bold text-brand-500 bg-brand-50 px-2 py-0.5 rounded-lg">{daysCount()} {t('contracts.days')}</span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.startDate')} <span className="text-danger-500">*</span></label>
                  <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.startTime')} <span className="text-danger-500">*</span></label>
                  <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.endDate')} <span className="text-danger-500">*</span></label>
                  <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} disabled={!!selectedReservation}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all disabled:opacity-60" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.endTime')} <span className="text-danger-500">*</span></label>
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
                    // Pre-fill deposit from vehicle default (admin can override)
                    if (v?.depositAmount != null && Number(v.depositAmount) > 0) {
                      setDepositAmount(String(v.depositAmount));
                    }
                  }}
                  onUnavailable={() => {
                    setSelectedVehicle(null);
                    showToast(t('contracts.form.vehicleNoLongerAvailable'), 'warning');
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
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">{t('contracts.form.pricingRentalDetails')}</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.discountMad')}</label>
                  <input type="number" autoComplete="off" value={discountAmount} onChange={(e) => { setDiscountAmount(e.target.value); setDiscountPercent(''); }}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.discountPct')}</label>
                  <input type="number" autoComplete="off" value={discountPercent} onChange={(e) => { setDiscountPercent(e.target.value); setDiscountAmount(''); }}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('common.paymentMethod')}</label>
                  <select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                    <option value="CASH">{t('contracts.form.paymentMethodCash')}</option>
                    <option value="CHECK">{t('contracts.form.paymentMethodCheck')}</option>
                    <option value="BANK_TRANSFER">{t('contracts.form.paymentMethodBankTransfer')}</option>
                    <option value="CARD">{t('contracts.form.paymentMethodCard')}</option>
                    <option value="ONLINE">{t('contracts.form.paymentMethodOnline')}</option>
                    <option value="OTHER">{t('contracts.form.paymentMethodOther')}</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.amountPaidMad')}</label>
                  <input type="number" min="0" autoComplete="off" value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              {/* Deposit / Guarantee */}
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">
                  {t('contracts.form.depositGuaranteeAmount')}
                </label>
                <input
                  type="number" min="0" autoComplete="off"
                  placeholder={t('contracts.form.depositExamplePlaceholder')}
                  value={depositAmount}
                  onChange={(e) => setDepositAmount(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                />
                <p className="text-[11px] text-slate-400 mt-1">
                  {t('contracts.form.depositGuaranteeDesc')}
                </p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.pickupLocation')}</label>
                  <input type="text" autoComplete="off" value={pickupLocation} onChange={(e) => setPickupLocation(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.returnLocation')}</label>
                  <input type="text" autoComplete="off" value={returnLocation} onChange={(e) => setReturnLocation(e.target.value)}
                    className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.contractTemplate')}</label>
                <select value={selectedTemplateId} onChange={(e) => setSelectedTemplateId(e.target.value)}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all">
                  <option value="">{t('contracts.form.systemDefault')}</option>
                  {templates.map((tpl: any) => (
                    <option key={tpl.id} value={tpl.id}>
                      {tpl.name}{tpl.default ? t('contracts.form.agencyDefaultSuffix') : ''}{tpl.templateType === 'SYSTEM_DEFAULT' ? t('contracts.form.systemSuffix') : ''}
                    </option>
                  ))}
                </select>
                {templates.length === 0 && (
                  <p className="text-xs text-slate-400 mt-1">{t('contracts.form.noTemplateConfigured')}</p>
                )}
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">{t('contracts.form.notes')}</label>
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                  className="w-full px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm resize-none focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              </div>
            </div>

            <div className="h-px bg-slate-100" />

            {/* Section 5: Documents */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-brand-500" />
                <span className="text-xs font-bold uppercase tracking-wider text-brand-500">{t('contracts.form.requiredDocuments')}</span>
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
              {t('contracts.form.advancedOptions')}
            </button>

            {showAdvanced && (
              <div className="space-y-4 animate-fade">
                {/* Additional Drivers */}
                <div className="border border-slate-100 rounded-2xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <User size={14} className="text-slate-400" />
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-400">{t('contracts.form.additionalDrivers')}</span>
                    </div>
                    <button onClick={addDriver} className="text-xs text-brand-500 hover:text-brand-600 font-medium">{t('contracts.form.addDriver')}</button>
                  </div>
                  {additionalDrivers.length === 0 && (
                    <p className="text-xs text-slate-400">{t('contracts.form.noAdditionalDrivers')}</p>
                  )}
                  {additionalDrivers.map((driver, idx) => (
                    <div key={idx} className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                      <input placeholder={t('contracts.form.fullNamePlaceholder')} value={driver.fullName} onChange={(e) => updateDriver(idx, 'fullName', e.target.value)}
                        className="px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm" />
                      <input placeholder={t('contracts.form.driverLicensePlaceholder')} value={driver.driverLicenseNumber} onChange={(e) => updateDriver(idx, 'driverLicenseNumber', e.target.value)}
                        className="px-3 py-2 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm" />
                      <div className="flex gap-2">
                        <input placeholder={t('contracts.form.phone')} value={driver.phone} onChange={(e) => updateDriver(idx, 'phone', e.target.value)}
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
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-400">{t('contracts.form.vehicleInspection')}</span>
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
                  <span className="text-xs font-bold uppercase tracking-wider">{t('contracts.form.reservationSummary')}</span>
                </div>

                {clientData.clientFullName && (
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-white/10 rounded-xl flex items-center justify-center text-lg font-bold">
                      {clientData.clientFullName.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold truncate">{clientData.clientFullName}</p>
                      <p className="text-xs text-white/50">{clientData.clientPhone || t('contracts.form.noPhone')}</p>
                    </div>
                  </div>
                )}

                {clientData.clientFullName && selectedVehicle && (
                  <div className="flex items-center justify-center">
                    <div className="h-px bg-white/20 flex-1" />
                    <div className="px-3 py-1 bg-white/10 rounded-full text-[10px] font-bold uppercase tracking-wider">{t('contracts.form.rentsBadge')}</div>
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
                      <span className="font-bold text-white">{daysCount()} {t('contracts.days')}</span>
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
                  {t('contracts.form.creatingContract')}
                </span>
              ) : t('contracts.form.createContractTitle')}
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

      {/* Restore Conflict Modal */}
      {restoreConflict.open && restoreConflict.contractId != null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setRestoreConflict({ open: false })} />
          <div className="relative bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 animate-fade">
            <div className="flex items-start gap-3 mb-5">
              <div className="w-10 h-10 bg-danger-50 rounded-xl flex items-center justify-center shrink-0">
                <AlertTriangle size={20} className="text-danger-500" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#1e293b]">{t('contracts.form.vehicleAlreadyBooked')}</h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  {t('contracts.contract')} <span className="font-mono font-bold text-slate-600">{restoreConflict.contractNumber}</span> {t('contracts.form.cannotBeRestored')}
                </p>
              </div>
            </div>

            <div className="bg-[#f8f8f6] rounded-xl p-4 space-y-3 mb-5 text-xs">
              <p className="text-slate-500">{t('contracts.form.alreadyBookedDesc')}</p>
              <div className="grid grid-cols-2 gap-2">
                {restoreConflict.conflictSource && (
                  <div>
                    <span className="text-slate-400 uppercase tracking-wider text-[10px]">{t('contracts.form.type')}</span>
                    <p className="font-bold text-[#1e293b] mt-0.5">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                        restoreConflict.conflictSource === 'CONTRACT' ? 'bg-brand-50 text-brand-600' :
                        restoreConflict.conflictSource === 'RESERVATION' ? 'bg-warning-50 text-warning-600' :
                        'bg-slate-100 text-slate-600'
                      }`}>{restoreConflict.conflictSource}</span>
                    </p>
                  </div>
                )}
                {restoreConflict.conflictNumber && (
                  <div>
                    <span className="text-slate-400 uppercase tracking-wider text-[10px]">{t('contracts.form.number')}</span>
                    <p className="font-mono font-bold text-[#1e293b] mt-0.5">{restoreConflict.conflictNumber}</p>
                  </div>
                )}
                {restoreConflict.conflictStatus && (
                  <div>
                    <span className="text-slate-400 uppercase tracking-wider text-[10px]">{t('contracts.status')}</span>
                    <p className="font-bold text-[#1e293b] mt-0.5">{restoreConflict.conflictStatus.replace(/_/g, ' ')}</p>
                  </div>
                )}
                {(restoreConflict.conflictStartDate || restoreConflict.conflictEndDate) && (
                  <div className="col-span-2">
                    <span className="text-slate-400 uppercase tracking-wider text-[10px]">{t('contracts.form.conflictDates')}</span>
                    <p className="font-medium text-[#1e293b] mt-0.5">
                      {restoreConflict.conflictStartDate} → {restoreConflict.conflictEndDate}
                    </p>
                  </div>
                )}
                {(restoreConflict.requestedStartDate || restoreConflict.requestedEndDate) && (
                  <div className="col-span-2">
                    <span className="text-slate-400 uppercase tracking-wider text-[10px]">{t('contracts.form.requestedDates')}</span>
                    <p className="font-medium text-[#1e293b] mt-0.5">
                      {restoreConflict.requestedStartDate} → {restoreConflict.requestedEndDate}
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className="bg-warning-50 border border-warning-200 rounded-xl p-3 mb-5 text-xs text-warning-700">
              <p className="font-semibold mb-1">{t('contracts.form.restoreAsDraft')}</p>
              <p>{t('contracts.form.restoreAsDraftDesc')}</p>
            </div>

            <div className="flex gap-2">
              <button
                onClick={() => setRestoreConflict({ open: false })}
                className="flex-1 px-4 py-2.5 rounded-xl border border-[#e8e6e1] text-sm font-medium text-slate-600 hover:bg-[#f5f5f0] transition-all"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => restoreContract(restoreConflict.contractId!, 'DRAFT_ONLY')}
                disabled={restoringId === restoreConflict.contractId}
                className="flex-1 px-4 py-2.5 rounded-xl bg-warning-500 hover:bg-warning-600 text-white text-sm font-semibold transition-all disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {restoringId === restoreConflict.contractId
                  ? <Loader2 size={15} className="animate-spin" />
                  : <RotateCcw size={15} />}
                {t('contracts.form.restoreAsDraft')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}


