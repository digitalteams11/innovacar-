import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import SignaturePad from '../components/shared/SignaturePad';
import QRCodeModal from '../components/shared/QRCodeModal';
import VehicleInspection from '../components/shared/VehicleInspection';
import ReturnInspectionModal from '../components/shared/ReturnInspectionModal';
import {
  ArrowLeft, FileText, Calendar, User, Car, CheckCircle2, Clock,
  Printer, Download, QrCode, Shield,
  AlertCircle, Loader2, CreditCard,
  ClipboardCheck, Users, History
} from 'lucide-react';

interface ContractDetail {
  id: number;
  contractNumber: string;
  status: string;
  contractType: string;
  contractLanguage: string;
  clientId: number;

  // Client
  clientFullName: string;
  clientFirstName: string;
  clientLastName: string;
  clientNationality: string;
  clientGender: string;
  clientBirthDate: string;
  clientCin: string;
  clientPassportNumber: string;
  clientDriverLicense: string;
  clientAddress: string;
  clientCity: string;
  clientCountry: string;
  clientPhone: string;
  clientEmail: string;
  emergencyContactName: string;
  emergencyContactPhone: string;

  // Vehicle
  vehicleBrand: string;
  vehicleModel: string;
  vehicleCategory: string;
  vehicleYear: number;
  vehicleColor: string;
  vehicleRegistration: string;
  vehicleTransmission: string;
  fuelType: string;

  // Dates
  startDate: string;
  endDate: string;
  pickupLocation: string;
  returnLocation: string;

  // Payment
  totalPrice: number;
  dailyPrice: number;
  depositAmount: number;
  paidAmount: number;
  remainingAmount: number;
  paymentMethod: string;
  paymentStatus: string;

  // Fuel & Mileage
  fuelLevelStart: string;
  mileageStart: number;

  // Signatures
  clientSigned: boolean;
  ownerSigned: boolean;
  ownerSignature?: string;
  clientSignature?: string;
  employeeSigned: boolean;
  termsAccepted: boolean;
  signedAt: string;

  // QR
  qrToken: string;
  publicSigningUrl: string;
  pdfUrl: string;

  // Deposit
  deposit?: {
    id?: number;
    depositType?: string;
    amount?: number;
    reference?: string;
    status?: string;
    notes?: string;
    conditionsText?: string;
    damageDeduction?: number;
    cleaningDeduction?: number;
    lateFeeDeduction?: number;
    fuelDeduction?: number;
    otherDeduction?: number;
    returnedAmount?: number;
    totalDeductions?: number;
    calculatedReturnAmount?: number;
    fuelLevelEnd?: string;
    mileageEnd?: number;
    interiorCondition?: string;
    exteriorCondition?: string;
    missingItems?: string;
    returnNotes?: string;
  };

  // Related
  notes: string;
  additionalDrivers: any[];
  vehicleCondition: any;
  documents: any[];
  auditLogs: any[];
  createdAt: string;
  updatedAt: string;
}

export default function ContractDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { tenant } = useAuth();

  const [contract, setContract] = useState<ContractDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview');
  const [showQRModal, setShowQRModal] = useState(false);
  const [showOwnerSign, setShowOwnerSign] = useState(false);
  const [showReturnModal, setShowReturnModal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [clientBalance, setClientBalance] = useState<any>(null);

  useEffect(() => { fetchContract(); }, [id]);

  const fetchContract = async () => {
    try {
      const { data } = await api.get(`/contracts/${id}`);
      setContract(data);
      if (data.clientId) {
        fetchClientBalance(data.clientId);
      }
    } catch (err) {
      showToast('Failed to load contract');
    } finally {
      setLoading(false);
    }
  };

  const fetchClientBalance = async (clientId: number) => {
    try {
      const { data } = await api.get(`/clients/${clientId}/balance`);
      setClientBalance(data);
    } catch (err) {
      // Silently fail — balance is optional enhancement
    }
  };

  const handleDownloadPdf = async () => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      const response = await api.get(`/contracts/${contract.id}/pdf`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `contract-${contract.contractNumber}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast('PDF downloaded');
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to download PDF');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOwnerSign = async (signatureData: string) => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      await api.post(`/contracts/${contract.id}/sign`, {
        signatureData, signerType: 'OWNER',
        ipAddress: '',
        userAgent: navigator.userAgent,
      });
      showToast('Agency signature applied');
      setShowOwnerSign(false);
      fetchContract();
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to save signature', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAutoAgencySign = async () => {
    if (!contract || !tenant?.agencySignature) {
      showToast('No agency signature configured. Please set it in Settings > Agency.', 'error');
      setShowOwnerSign(true);
      return;
    }
    setIsSubmitting(true);
    try {
      await api.post(`/contracts/${contract.id}/sign`, {
        signatureData: tenant.agencySignature,
        signerType: 'OWNER',
        ipAddress: '',
        userAgent: navigator.userAgent,
      });
      showToast('Agency signature applied automatically');
      fetchContract();
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to apply signature', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGenerateQR = async () => {
    if (!contract) return;
    if (!contract.ownerSigned) {
      showToast('Agency must sign the contract before generating a QR code', 'error');
      return;
    }
    setIsSubmitting(true);
    try {
      await api.post(`/contracts/${contract.id}/qr`, { frontendUrl: window.location.origin + '/#' });
      await fetchContract();
      setShowQRModal(true);
      showToast('QR code generated');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Failed to generate QR', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFinalize = async () => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      await api.post(`/contracts/${contract.id}/finalize`);
      showToast('Contract finalized');
      fetchContract();
    } catch (err: any) {
      showToast((err as any).userMessage || 'Failed to finalize');
    } finally {
      setIsSubmitting(false);
    }
  };

  const getStatusBadge = (status: string) => {
    const configs: Record<string, string> = {
      ACTIVE: 'bg-success-50 text-success-500',
      DRAFT: 'bg-slate-50 text-slate-500',
      PENDING_SIGNATURE: 'bg-warning-50 text-warning-500',
      PARTIALLY_SIGNED: 'bg-brand-50 text-brand-500',
      SIGNED: 'bg-emerald-50 text-emerald-500',
      COMPLETED: 'bg-brand-50 text-brand-500',
      CANCELLED: 'bg-danger-50 text-danger-500',
      EXPIRED: 'bg-slate-50 text-slate-400',
    };
    return configs[status] || configs.DRAFT;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (!contract) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-slate-400">
        <AlertCircle size={48} className="mb-4" />
        <p className="text-lg font-medium">Contract not found</p>
        <button onClick={() => navigate('/contracts')} className="mt-4 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl text-xs sm:text-sm font-medium hover:bg-brand-600 transition-all w-full sm:w-auto">
          Back to Contracts
        </button>
      </div>
    );
  }

  const bothSigned = contract.clientSigned && contract.ownerSigned;
  const canFinalize = bothSigned && contract.status !== 'ACTIVE' && contract.status !== 'COMPLETED';

  const detailTabs = [
    { key: 'overview', label: 'Overview', icon: FileText },
    { key: 'client', label: 'Client', icon: User },
    { key: 'vehicle', label: 'Vehicle', icon: Car },
    { key: 'payment', label: 'Payment', icon: CreditCard },
    { key: 'inspection', label: 'Inspection', icon: Shield },
    { key: 'documents', label: 'Documents', icon: ClipboardCheck },
    { key: 'drivers', label: 'Drivers', icon: Users },
    { key: 'activity', label: 'Activity', icon: History },
  ];

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      {/* Header */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/contracts')} className="p-2 -ml-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all shrink-0">
            <ArrowLeft size={20} />
          </button>
          <div className="min-w-0">
            <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] truncate">{contract.contractNumber}</h1>
            <p className="text-slate-500 text-xs sm:text-sm">Contract Details</p>
          </div>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <span className={`px-2.5 sm:px-3 py-1.5 rounded-lg text-[10px] sm:text-xs font-bold uppercase tracking-wider ${getStatusBadge(contract.status)}`}>
            {contract.status.replace('_', ' ')}
          </span>
          <button onClick={() => window.print()} className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 bg-white border border-slate-200 rounded-xl text-xs sm:text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all">
            <Printer size={14} className="sm:hidden" />
            <Printer size={16} className="hidden sm:block" />
            <span className="hidden sm:inline">Print</span>
          </button>
          <button onClick={handleDownloadPdf} disabled={isSubmitting}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 bg-white border border-slate-200 rounded-xl text-xs sm:text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-50">
            <Download size={14} className="sm:hidden" />
            <Download size={16} className="hidden sm:block" />
            <span className="hidden sm:inline">PDF</span>
          </button>
          <button
            onClick={contract.ownerSigned ? handleGenerateQR : () => showToast('Agency must sign first before generating QR', 'error')}
            disabled={isSubmitting}
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 rounded-xl text-xs sm:text-sm font-medium transition-all disabled:opacity-50 whitespace-nowrap ${
              contract.ownerSigned
                ? 'bg-brand-500 text-white hover:bg-brand-600'
                : 'bg-slate-200 text-slate-400 cursor-not-allowed'
            }`}>
            <QrCode size={14} className="sm:hidden" />
            <QrCode size={16} className="hidden sm:block" />
            {contract.qrToken ? 'Show QR' : 'Generate QR'}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex p-1 bg-[#f5f5f0] rounded-xl overflow-x-auto no-scrollbar">
        {detailTabs.map((tab) => {
          const Icon = tab.icon;
          return (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-2.5 sm:px-4 py-1.5 sm:py-2 rounded-lg text-xs sm:text-sm font-medium transition-all whitespace-nowrap flex-shrink-0 ${activeTab === tab.key ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}>
              <Icon size={13} className="sm:hidden" />
              <Icon size={14} className="hidden sm:block" />
              {tab.label}
            </button>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-5">
          {activeTab === 'overview' && (
            <>
              {/* Signature Status */}
              <div className="card-premium space-y-4 p-3 sm:p-5">
                <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">Signature Status</h3>
                <div className="grid grid-cols-3 gap-2 sm:gap-4">
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.clientSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <User size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.clientSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">Client</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.clientSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.clientSigned ? 'Signed' : 'Pending'}
                    </p>
                  </div>
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.ownerSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <Shield size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.ownerSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">Owner</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.ownerSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.ownerSigned ? 'Signed' : 'Pending'}
                    </p>
                  </div>
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.employeeSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <FileText size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.employeeSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">Employee</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.employeeSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.employeeSigned ? 'Signed' : 'Pending'}
                    </p>
                  </div>
                </div>

                {/* Agency Signature */}
                <div className="pt-4 border-t border-slate-100">
                  {!contract.ownerSigned ? (
                    <div className="space-y-3">
                      {tenant?.agencySignature ? (
                        <button onClick={handleAutoAgencySign} disabled={isSubmitting}
                          className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all disabled:opacity-50 flex items-center justify-center gap-2">
                          {isSubmitting ? <Loader2 size={18} className="animate-spin" /> : <Shield size={18} />}
                          Apply Agency Signature
                        </button>
                      ) : (
                        <div className="p-4 bg-amber-50 border border-amber-100 rounded-xl text-sm text-amber-700">
                          No agency signature configured. <button onClick={() => navigate('/settings')} className="font-semibold underline">Set it in Settings</button> or draw below.
                        </div>
                      )}
                      {!showOwnerSign ? (
                        <button onClick={() => setShowOwnerSign(true)}
                          className="w-full py-3 bg-brand-50 text-brand-500 rounded-xl font-semibold text-sm hover:bg-brand-100 transition-all">
                          {tenant?.agencySignature ? 'Draw Custom Signature Instead' : 'Sign as Owner'}
                        </button>
                      ) : (
                        <SignaturePad onSave={handleOwnerSign} label="Owner Signature" autoSaveKey={`owner_${contract.id}`} />
                      )}
                    </div>
                  ) : (
                    <div className="flex items-start sm:items-center gap-3 p-3 sm:p-4 bg-success-50 border border-success-100 rounded-xl">
                      <CheckCircle2 size={18} className="text-success-500 shrink-0 mt-0.5 sm:mt-0" />
                      <div className="min-w-0">
                        <p className="text-xs sm:text-sm font-semibold text-success-700">Agency Signature Applied</p>
                        {contract.ownerSignature && (
                          <img src={contract.ownerSignature} alt="Agency Signature" className="h-12 sm:h-16 mt-2 bg-white rounded-lg border border-success-200 p-1" />
                        )}
                      </div>
                    </div>
                  )}
                </div>

                {canFinalize && (
                  <button onClick={handleFinalize} disabled={isSubmitting}
                    className="w-full py-3 bg-success-500 text-white rounded-xl font-semibold text-sm hover:bg-success-600 transition-all disabled:opacity-50 flex items-center justify-center gap-2">
                    {isSubmitting ? <Loader2 size={18} className="animate-spin" /> : <CheckCircle2 size={18} />}
                    Finalize Contract
                  </button>
                )}
              </div>

              {/* Signature Previews */}
              {(contract.ownerSigned || contract.clientSigned) && (
                <div className="card-premium space-y-4 p-3 sm:p-5">
                  <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">Signatures</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {contract.ownerSigned && contract.ownerSignature && (
                      <div className="space-y-2">
                        <p className="text-xs font-bold text-slate-500">Agency Representative</p>
                        <div className="p-2 bg-white rounded-xl border border-slate-200">
                          <img src={contract.ownerSignature} alt="Agency Signature" className="h-16 sm:h-20 w-full object-contain" />
                        </div>
                      </div>
                    )}
                    {contract.clientSigned && contract.clientSignature && (
                      <div className="space-y-2">
                        <p className="text-xs font-bold text-slate-500">Client</p>
                        <div className="p-2 bg-white rounded-xl border border-slate-200">
                          <img src={contract.clientSignature} alt="Client Signature" className="h-16 sm:h-20 w-full object-contain" />
                        </div>
                      </div>
                    )}
                  </div>
                  {contract.pdfUrl && (
                    <a
                      href={`${import.meta.env.VITE_API_BASE_URL || ''}${contract.pdfUrl}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center gap-2 w-full py-2.5 bg-slate-100 text-slate-700 rounded-xl text-sm font-medium hover:bg-slate-200 transition-all"
                    >
                      <FileText size={16} />
                      View Signed PDF
                    </a>
                  )}
                </div>
              )}

              {/* Security Deposit */}
              {contract.deposit && (
                <div className="card-premium space-y-4 p-3 sm:p-5">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">Security Deposit</h3>
                    <span className={`text-xs font-bold px-2 py-1 rounded-lg ${
                      contract.deposit.status === 'RETURNED' || contract.deposit.status === 'PARTIALLY_RETURNED'
                        ? 'bg-success-100 text-success-600'
                        : contract.deposit.status === 'DEDUCTED'
                        ? 'bg-danger-100 text-danger-600'
                        : contract.deposit.status === 'HELD'
                        ? 'bg-brand-100 text-brand-600'
                        : 'bg-slate-100 text-slate-600'
                    }`}>
                      {contract.deposit.status}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3 bg-slate-50 rounded-xl">
                      <p className="text-[10px] text-slate-400 uppercase font-bold">Type</p>
                      <p className="text-sm font-bold text-[#1e293b]">{contract.deposit.depositType || 'Cash'}</p>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-xl">
                      <p className="text-[10px] text-slate-400 uppercase font-bold">Amount</p>
                      <p className="text-sm font-bold text-brand-600">{contract.deposit.amount} MAD</p>
                    </div>
                    {contract.deposit.reference && (
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <p className="text-[10px] text-slate-400 uppercase font-bold">Reference</p>
                        <p className="text-sm font-bold text-[#1e293b]">{contract.deposit.reference}</p>
                      </div>
                    )}
                  </div>
                  {contract.deposit.status === 'HELD' && contract.clientSigned && (
                    <button
                      onClick={() => setShowReturnModal(true)}
                      className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all flex items-center justify-center gap-2"
                    >
                      <Shield size={18} />
                      Process Vehicle Return
                    </button>
                  )}
                  {(contract.deposit.status === 'RETURNED' || contract.deposit.status === 'PARTIALLY_RETURNED' || contract.deposit.status === 'DEDUCTED') && (
                    <div className="space-y-2 p-3 bg-slate-50 rounded-xl">
                      <p className="text-xs font-bold text-slate-500 uppercase">Return Summary</p>
                      <div className="flex justify-between text-sm">
                        <span className="text-slate-500">Deposit</span>
                        <span className="font-medium">{contract.deposit.amount} MAD</span>
                      </div>
                      {(contract.deposit.damageDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>Damage</span>
                          <span className="font-medium">- {contract.deposit.damageDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.cleaningDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>Cleaning</span>
                          <span className="font-medium">- {contract.deposit.cleaningDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.lateFeeDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>Late Fee</span>
                          <span className="font-medium">- {contract.deposit.lateFeeDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.fuelDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>Fuel</span>
                          <span className="font-medium">- {contract.deposit.fuelDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.otherDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>Other</span>
                          <span className="font-medium">- {contract.deposit.otherDeduction} MAD</span>
                        </div>
                      )}
                      <div className="h-px bg-slate-200" />
                      <div className="flex justify-between text-sm font-bold">
                        <span className="text-slate-700">Returned</span>
                        <span className={contract.deposit.returnedAmount && contract.deposit.returnedAmount > 0 ? 'text-success-600' : 'text-slate-400'}>
                          {contract.deposit.returnedAmount || 0} MAD
                        </span>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Quick Info Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <User size={13} className="sm:hidden" />
                    <User size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">Client</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b] truncate">{contract.clientFullName || 'N/A'}</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 truncate">{contract.clientPhone} • {contract.clientEmail}</p>
                </div>
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <Car size={13} className="sm:hidden" />
                    <Car size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">Vehicle</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b] truncate">{contract.vehicleBrand} {contract.vehicleModel}</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 truncate">{contract.vehicleRegistration} • {contract.vehicleCategory}</p>
                </div>
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <Calendar size={13} className="sm:hidden" />
                    <Calendar size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">Period</span>
                  </div>
                  <p className="text-xs sm:text-sm font-bold text-[#1e293b]">
                    {new Date(contract.startDate).toLocaleDateString()} — {new Date(contract.endDate).toLocaleDateString()}
                  </p>
                  <p className="text-[10px] sm:text-xs text-slate-400">{contract.pickupLocation}</p>
                </div>
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <CreditCard size={13} className="sm:hidden" />
                    <CreditCard size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">Payment</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b]">{contract.totalPrice || 0} MAD</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 capitalize">{contract.paymentMethod} • {contract.paymentStatus}</p>
                </div>
              </div>
            </>
          )}

          {activeTab === 'client' && (
            <div className="card-premium p-4 sm:p-6 space-y-4 sm:space-y-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Client Information</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <InfoRow label="Full Name" value={contract.clientFullName} />
                <InfoRow label="Nationality" value={contract.clientNationality} />
                <InfoRow label="Gender" value={contract.clientGender} />
                <InfoRow label="Birth Date" value={contract.clientBirthDate ? new Date(contract.clientBirthDate).toLocaleDateString() : ''} />
                <InfoRow label="CIN / ID" value={contract.clientCin} />
                <InfoRow label="Passport" value={contract.clientPassportNumber} />
                <InfoRow label="Driver License" value={contract.clientDriverLicense} />
                <InfoRow label="Phone" value={contract.clientPhone} />
                <InfoRow label="Email" value={contract.clientEmail} />
                <InfoRow label="Address" value={`${contract.clientAddress}, ${contract.clientCity}, ${contract.clientCountry}`} />
                <InfoRow label="Emergency Contact" value={`${contract.emergencyContactName} ${contract.emergencyContactPhone}`} />
              </div>
            </div>
          )}

          {activeTab === 'vehicle' && (
            <div className="card-premium p-4 sm:p-6 space-y-4 sm:space-y-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Vehicle Information</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <InfoRow label="Brand / Model" value={`${contract.vehicleBrand} ${contract.vehicleModel}`} />
                <InfoRow label="Category" value={contract.vehicleCategory} />
                <InfoRow label="Year" value={contract.vehicleYear?.toString()} />
                <InfoRow label="Color" value={contract.vehicleColor} />
                <InfoRow label="Registration" value={contract.vehicleRegistration} />
                <InfoRow label="Transmission" value={contract.vehicleTransmission} />
                <InfoRow label="Fuel Type" value={contract.fuelType} />
                <InfoRow label="Fuel Level" value={contract.fuelLevelStart} />
                <InfoRow label="Mileage Start" value={contract.mileageStart?.toString()} />
              </div>
            </div>
          )}

          {activeTab === 'payment' && (
            <div className="card-premium p-4 sm:p-6 space-y-4 sm:space-y-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Payment Details</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <InfoRow label="Total Price" value={`${contract.totalPrice || 0} MAD`} />
                <InfoRow label="Daily Price" value={`${contract.dailyPrice || 0} MAD`} />
                <InfoRow label="Deposit" value={`${contract.depositAmount || 0} MAD`} />
                <InfoRow label="Paid Amount" value={`${contract.paidAmount || 0} MAD`} />
                <InfoRow label="Remaining" value={`${contract.remainingAmount || 0} MAD`} />
                <InfoRow label="Payment Method" value={contract.paymentMethod} />
                <InfoRow label="Payment Status" value={contract.paymentStatus} />
              </div>
            </div>
          )}

          {activeTab === 'inspection' && (
            <div className="card-premium p-4 sm:p-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400 mb-3 sm:mb-4">Vehicle Inspection</h3>
              {contract.vehicleCondition ? (
                <VehicleInspection
                  value={[
                    { id: 'front', label: 'Front', damaged: contract.vehicleCondition.frontDamage || false, notes: '' },
                    { id: 'rear', label: 'Rear', damaged: contract.vehicleCondition.rearDamage || false, notes: '' },
                    { id: 'leftSide', label: 'Left Side', damaged: contract.vehicleCondition.leftSideDamage || false, notes: '' },
                    { id: 'rightSide', label: 'Right Side', damaged: contract.vehicleCondition.rightSideDamage || false, notes: '' },
                    { id: 'windshield', label: 'Windshield', damaged: contract.vehicleCondition.windshieldDamage || false, notes: '' },
                    { id: 'interior', label: 'Interior', damaged: contract.vehicleCondition.interiorDamage || false, notes: '' },
                    { id: 'roof', label: 'Roof', damaged: contract.vehicleCondition.roofDamage || false, notes: '' },
                    { id: 'bumperFront', label: 'Front Bumper', damaged: contract.vehicleCondition.bumperFrontDamage || false, notes: '' },
                    { id: 'bumperRear', label: 'Rear Bumper', damaged: contract.vehicleCondition.bumperRearDamage || false, notes: '' },
                    { id: 'hood', label: 'Hood', damaged: contract.vehicleCondition.hoodDamage || false, notes: '' },
                    { id: 'trunk', label: 'Trunk', damaged: contract.vehicleCondition.trunkDamage || false, notes: '' },
                  ]}
                  onChange={() => {}}
                  readOnly
                />
              ) : (
                <p className="text-sm text-slate-400 text-center py-8">No inspection recorded</p>
              )}
            </div>
          )}

          {activeTab === 'documents' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Document Checklist</h3>
              {contract.documents && contract.documents.length > 0 ? (
                <div className="space-y-2">
                  {contract.documents.map((doc: any) => (
                    <div key={doc.id} className={`flex items-center gap-3 p-3 rounded-xl ${doc.isPresent ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                      {doc.isPresent ? <CheckCircle2 size={16} className="text-success-500" /> : <Clock size={16} className="text-slate-300" />}
                      <span className={`text-sm ${doc.isPresent ? 'text-success-700' : 'text-slate-500'}`}>{doc.documentName}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-400 text-center py-8">No documents recorded</p>
              )}
            </div>
          )}

          {activeTab === 'drivers' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Additional Drivers</h3>
              {contract.additionalDrivers && contract.additionalDrivers.length > 0 ? (
                <div className="space-y-3">
                  {contract.additionalDrivers.map((driver: any) => (
                    <div key={driver.id} className="p-4 bg-slate-50 rounded-2xl">
                      <p className="font-bold text-[#1e293b]">{driver.fullName}</p>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-1 text-xs text-slate-400">
                        {driver.driverLicenseNumber && <span>License: {driver.driverLicenseNumber}</span>}
                        {driver.phone && <span>Phone: {driver.phone}</span>}
                        {driver.nationality && <span>Nationality: {driver.nationality}</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-400 text-center py-8">No additional drivers</p>
              )}
            </div>
          )}

          {activeTab === 'activity' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">Activity Log</h3>
              {contract.auditLogs && contract.auditLogs.length > 0 ? (
                <div className="space-y-3">
                  {contract.auditLogs.map((log: any) => (
                    <div key={log.id} className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl">
                      <History size={14} className="text-slate-400 mt-0.5" />
                      <div>
                        <p className="text-sm font-medium text-[#1e293b]">{log.action}</p>
                        <p className="text-xs text-slate-400">{log.description}</p>
                        <p className="text-[10px] text-slate-300 mt-0.5">{log.performedBy} • {new Date(log.createdAt).toLocaleString()}</p>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-400 text-center py-8">No activity recorded</p>
              )}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-5">
          {/* Timeline */}
          <div className="card-premium p-3 sm:p-5 space-y-4">
            <h4 className="text-sm font-bold text-[#1e293b]">Contract Timeline</h4>
            <div className="space-y-3">
              {['DRAFT', 'PENDING_SIGNATURE', 'SIGNED', 'ACTIVE', 'COMPLETED'].map((s, idx) => {
                const isDone = getStatusIndex(contract.status) >= idx;
                const isCurrent = contract.status === s;
                return (
                  <div key={s} className="flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                      isDone ? 'bg-success-100 text-success-500' : isCurrent ? 'bg-brand-100 text-brand-500' : 'bg-slate-100 text-slate-300'
                    }`}>
                      {isDone ? <CheckCircle2 size={14} /> : idx + 1}
                    </div>
                    <div className="flex-1">
                      <p className={`text-sm font-medium ${isDone || isCurrent ? 'text-[#1e293b]' : 'text-slate-400'}`}>
                        {s.replace('_', ' ')}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Client Balance */}
          {clientBalance && (
            <div className="card-premium p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="text-sm font-bold text-[#1e293b]">Client Balance</h4>
                <span className={`px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase tracking-wider ${clientBalance.paymentStatus === 'PAID' ? 'bg-success-50 text-success-500' : clientBalance.paymentStatus === 'PARTIALLY_PAID' ? 'bg-warning-50 text-warning-500' : 'bg-danger-50 text-danger-500'}`}>
                  {clientBalance.paymentStatus?.replace('_', ' ')}
                </span>
              </div>

              {/* Money row */}
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-success-50 rounded-xl p-3 text-center">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-success-400 mb-1">Paid</p>
                  <p className="text-lg font-black text-success-600">{Number(clientBalance.totalPaid || 0).toLocaleString()}</p>
                  <p className="text-[10px] text-success-400 font-medium">MAD</p>
                </div>
                <div className={`rounded-xl p-3 text-center ${(clientBalance.remainingBalance || 0) > 0 ? 'bg-danger-50' : 'bg-success-50'}`}>
                  <p className={`text-[10px] font-bold uppercase tracking-wider mb-1 ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-400' : 'text-success-400'}`}>Remaining</p>
                  <p className={`text-lg font-black ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-600' : 'text-success-600'}`}>{Number(clientBalance.remainingBalance || 0).toLocaleString()}</p>
                  <p className={`text-[10px] font-medium ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-400' : 'text-success-400'}`}>MAD</p>
                </div>
              </div>

              {/* Stats row */}
              <div className="grid grid-cols-3 gap-2">
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.totalRentals ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Rentals</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.openInvoices ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Invoices</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.activeContracts ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">Active</p>
                </div>
              </div>
            </div>
          )}

          {/* Terms */}
          <div className="card-premium p-5 space-y-3">
            <h4 className="text-sm font-bold text-[#1e293b]">Terms & Conditions</h4>
            <div className={`flex items-center gap-3 p-3 rounded-xl ${contract.termsAccepted ? 'bg-success-50 text-success-600' : 'bg-slate-50 text-slate-500'}`}>
              <Shield size={18} />
              <span className="text-sm font-medium">
                {contract.termsAccepted ? 'Terms accepted' : 'Terms not yet accepted'}
              </span>
            </div>
          </div>

          {/* Metadata */}
          <div className="card-premium p-3 sm:p-5 space-y-2">
            <h4 className="text-sm font-bold text-[#1e293b]">Metadata</h4>
            <div className="text-xs text-slate-400 space-y-1">
              <p>Created: {contract.createdAt ? new Date(contract.createdAt).toLocaleString() : 'N/A'}</p>
              <p>Updated: {contract.updatedAt ? new Date(contract.updatedAt).toLocaleString() : 'N/A'}</p>
            </div>
          </div>
        </div>
      </div>

      {/* QR Modal */}
      {contract && (
        <QRCodeModal
          isOpen={showQRModal}
          onClose={() => setShowQRModal(false)}
          qrToken={contract.qrToken || ''}
          signingUrl={contract.publicSigningUrl || ''}
          contractNumber={contract.contractNumber}
          clientName={contract.clientFullName || ''}
          contractId={contract.id}
        />
      )}

      {showReturnModal && contract?.deposit && (
        <ReturnInspectionModal
          isOpen={showReturnModal}
          onClose={() => setShowReturnModal(false)}
          depositId={contract.deposit.id!}
          depositAmount={contract.deposit.amount || 0}
          contractId={contract.id}
          onSuccess={() => { fetchContract(); showToast('Return processed successfully'); }}
        />
      )}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <div>
      <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{label}</p>
      <p className="text-sm font-medium text-[#1e293b] mt-0.5">{value}</p>
    </div>
  );
}

function getStatusIndex(status: string): number {
  const order = ['DRAFT', 'PENDING_SIGNATURE', 'PARTIALLY_SIGNED', 'SIGNED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED'];
  return order.indexOf(status);
}
