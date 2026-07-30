import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import { resolveMediaUrl, cn } from '../lib/utils';
import { useInlineAction } from '../hooks/useInlineAction';
import AnimatedStatusIcon from '../components/shared/AnimatedStatusIcon';
import Tooltip from '../components/shared/Tooltip';
import SignaturePad from '../components/shared/SignaturePad';
import QRCodeModal from '../components/shared/QRCodeModal';
import VehicleInspection from '../components/shared/VehicleInspection';
import ReturnInspectionModal from '../components/shared/ReturnInspectionModal';
import InspectionGallery from '../components/InspectionGallery';
import Modal from '../components/Modal';
import AddClientEmailModal from '../components/shared/AddClientEmailModal';
import { normalizePhoneForWhatsApp } from '../lib/phone';
import { QRCodeSVG } from 'qrcode.react';
import {
  ArrowLeft, FileText, Calendar, User, Car, CheckCircle2, Clock,
  Printer, Download, QrCode, Shield,
  AlertCircle, Loader2, CreditCard,
  ClipboardCheck, Users, History, RefreshCw,
  MailPlus, Send, MessageCircle, Copy, Pencil, Check
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
  depositCurrency: string;
  depositStatus: string;
  paidAmount: number;
  remainingAmount: number;
  paymentMethod: string;
  paymentStatus: string;

  // Fuel
  fuelLevelStart: string;

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

const unwrapObject = <T,>(payload: unknown): T => {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    const response = payload as { data?: unknown };
    if (response.data && typeof response.data === 'object') return response.data as T;
  }
  return payload as T;
};
const safePdfFileName = (contractNumber: string) =>
  `contract-${(contractNumber || 'contract').replace(/[^a-zA-Z0-9._-]/g, '_')}.pdf`;

export default function ContractDetails() {
  const { t } = useTranslation();
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
  const [inspections, setInspections] = useState<any[]>([]);
  const [inspectionQr, setInspectionQr] = useState<any>(null);
  const [emailStatus, setEmailStatus] = useState<any>(null);
  const [, setSendingEmail] = useState(false);
  const [showAddEmailModal, setShowAddEmailModal] = useState(false);
  const [savingClientEmail, setSavingClientEmail] = useState(false);
  const [generatingShareLink, setGeneratingShareLink] = useState(false);

  const emailErrorLabel = (code?: string | null): string => {
    switch (code) {
      case 'EMAIL_TO_ADDRESS_MISSING':
      case 'CLIENT_EMAIL_MISSING':
        return t('contractDetails.emailErrors.clientEmailMissing');
      case 'EMAIL_CONFIGURATION_MISSING':
        return t('contractDetails.emailErrors.providerNotConfigured');
      case 'EMAIL_API_UNAUTHORIZED':
        return t('contractDetails.emailErrors.credentialsInvalid');
      case 'EMAIL_SENDER_NOT_VERIFIED':
        return t('contractDetails.emailErrors.senderNotVerified');
      case 'EMAIL_API_INVALID_PAYLOAD':
        return t('contractDetails.emailErrors.invalidRequest');
      case 'EMAIL_API_PROVIDER_UNAVAILABLE':
        return t('contractDetails.emailErrors.providerUnavailable');
      case 'EMAIL_API_TIMEOUT':
        return t('contractDetails.emailErrors.deliveryTimedOut');
      case 'EMAIL_API_RATE_LIMITED':
        return t('contractDetails.emailErrors.rateLimited');
      case 'EMAIL_API_ENDPOINT_INVALID':
        return t('contractDetails.emailErrors.endpointInvalid');
      case 'EMAIL_API_NETWORK_ERROR':
        return t('contractDetails.emailErrors.networkError');
      default:
        return code || t('contractDetails.emailErrors.sendFailed');
    }
  };

  const signingPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchEmailStatus = useCallback(async (contractId: number) => {
    try {
      const { data } = await api.get(`/contracts/${contractId}/email-status`);
      setEmailStatus(data);
    } catch {
      // non-critical
    }
  }, []);

  const fetchContract = useCallback(async () => {
    try {
      const { data } = await api.get(`/contracts/${id}`);
      const payload = unwrapObject<ContractDetail>(data);
      setContract(payload);
      fetchInspections(payload.id);
      fetchEmailStatus(payload.id);
      if (payload.clientId) {
        fetchClientBalance(payload.clientId);
      }
    } catch (err: any) {
      const status = err?.response?.status;
      showToast(status === 404 ? t('contractDetails.toasts.contractNotFoundOrRemoved') : t('contractDetails.toasts.loadFailed'), 'error');
    } finally {
      setLoading(false);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { fetchContract(); }, [id, fetchContract]);

  // Real-time auto-refresh via SSE contract_event / notification broadcast
  useEffect(() => {
    const handleContractUpdated = (e: Event) => {
      const detail = (e as CustomEvent).detail as { contractId?: number | string };
      if (detail?.contractId && String(detail.contractId) === String(id)) {
        fetchContract();
        showToast(t('contractDetails.toasts.contractSignedByClient'), 'success');
      }
    };
    window.addEventListener('contract:updated', handleContractUpdated);
    return () => window.removeEventListener('contract:updated', handleContractUpdated);
  }, [id, fetchContract, showToast]);

  // Fallback polling every 5 s while contract awaits client signature
  useEffect(() => {
    const awaitingSignature = contract?.ownerSigned && !contract?.clientSigned;
    if (awaitingSignature) {
      signingPollRef.current = setInterval(() => {
        fetchContract();
      }, 5000);
    } else {
      if (signingPollRef.current) {
        clearInterval(signingPollRef.current);
        signingPollRef.current = null;
      }
    }
    return () => {
      if (signingPollRef.current) {
        clearInterval(signingPollRef.current);
        signingPollRef.current = null;
      }
    };
  }, [contract?.ownerSigned, contract?.clientSigned, fetchContract]);
  const fetchInspections = async (contractId: number) => {
    try {
      const { data } = await api.get(`/contracts/${contractId}/inspections`);
      setInspections(Array.isArray(data) ? data : []);
    } catch {
      setInspections([]);
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

  const applyContractEnvelope = (payload: any, fallback?: Partial<ContractDetail>) => {
    const updatedContract = payload?.data?.contract || payload?.contract;
    if (updatedContract) {
      setContract(updatedContract);
      return;
    }
    setContract(current => current ? { ...current, ...fallback } : current);
  };

  const downloadPdfAction = useInlineAction(async () => {
    if (!contract) return;
    const response = await api.get(`/contracts/${contract.id}/pdf`, { responseType: 'blob' });
    if (!response.data || response.data.size === 0) {
      throw new Error(t('contractDetails.toasts.pdfEmpty'));
    }
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = safePdfFileName(contract.contractNumber);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  }, { context: 'contract-download-pdf' });
  const handleDownloadPdf = () => downloadPdfAction.run();

  const regeneratePdfAction = useInlineAction(async () => {
    if (!contract) return;
    console.log('[CONTRACT_PDF_REGENERATE_DEBUG]', {
      contractId: contract.id,
      contractNumber: contract.contractNumber,
      endpoint: `/contracts/${contract.id}/pdf/regenerate`,
      method: 'POST',
      currentAgencySettings: true,
    });
    const response = await api.post(`/contracts/${contract.id}/pdf/regenerate`);
    const refreshed = response.data?.data;
    if (refreshed?.pdfUrl) {
      setContract(c => c ? { ...c, pdfUrl: refreshed.pdfUrl } : c);
    } else {
      applyContractEnvelope(response.data, refreshed);
    }
  }, { context: 'contract-regenerate-pdf' });
  const handleRegeneratePdf = () => regeneratePdfAction.run();

  const handlePrintPdf = async () => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      const response = await api.get(`/contracts/${contract.id}/pdf`, { responseType: 'blob' });
      if (!response.data || response.data.size === 0) {
        showToast(t('contractDetails.toasts.pdfEmpty'), 'error');
        return;
      }
      const blob = new Blob([response.data], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => window.URL.revokeObjectURL(url), 60000);
      showToast(t('contractDetails.toasts.pdfOpenedForPrint'), 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || t('contractDetails.toasts.pdfOpenFailed'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOwnerSign = async (signatureData: string) => {
    if (!contract) return;
    if (contract.ownerSigned) {
      await fetchContract();
      setShowOwnerSign(false);
      showToast(t('contractDetails.toasts.agencySignatureAppliedInfo'), 'info');
      return;
    }
    setIsSubmitting(true);
    try {
      const { data } = await api.post(`/contracts/${contract.id}/sign`, {
        signatureType: 'AGENCY',
        signatureData,
        signedBy: 'admin',
        signerType: 'OWNER',
        deviceInfo: navigator.userAgent,
        ipAddress: '',
        userAgent: navigator.userAgent,
      });
      if (data?.success === false) {
        showToast(data.message || t('contractDetails.toasts.signatureSaveFailed'), 'error');
        return;
      }
      applyContractEnvelope(data, {
        ownerSigned: true,
        ownerSignature: signatureData,
        status: contract.clientSigned ? 'ACTIVE' : 'PENDING_SIGNATURE',
      });
      await fetchContract();
      setShowOwnerSign(false);
      showToast(data?.message || t('contractDetails.toasts.contractSigned'), 'success');
    } catch (err: any) {
      if (err?.response?.status === 409 && contract.ownerSigned) {
        await fetchContract();
        setShowOwnerSign(false);
        showToast(t('contractDetails.toasts.agencySignatureAppliedInfo'), 'info');
        return;
      }
      showToast((err as any).userMessage || t('contractDetails.toasts.signatureApplyFailed'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAutoAgencySign = async () => {
    if (contract?.ownerSigned) {
      await fetchContract();
      showToast(t('contractDetails.toasts.agencySignatureAppliedInfo'), 'info');
      return;
    }
    if (!contract || !tenant?.agencySignature) {
      showToast(t('contractDetails.toasts.noAgencySignatureWarning'), 'warning');
      setShowOwnerSign(true);
      return;
    }
    setIsSubmitting(true);
    try {
      const { data } = await api.post(`/contracts/${contract.id}/sign`, {
        signatureType: 'AGENCY',
        signatureData: tenant.agencySignature,
        signedBy: 'admin',
        signerType: 'OWNER',
        deviceInfo: navigator.userAgent,
        ipAddress: '',
        userAgent: navigator.userAgent,
      });
      if (data?.success === false) {
        showToast(data.message || t('contractDetails.toasts.signatureApplyFailed'), 'error');
        return;
      }
      applyContractEnvelope(data, {
        ownerSigned: true,
        ownerSignature: tenant.agencySignature,
        status: contract.clientSigned ? 'ACTIVE' : 'PENDING_SIGNATURE',
      });
      await fetchContract();
      showToast(data?.message || t('contractDetails.toasts.contractSigned'), 'success');
    } catch (err: any) {
      if (err?.response?.status === 409 && contract.ownerSigned) {
        await fetchContract();
        showToast(t('contractDetails.toasts.agencySignatureAppliedInfo'), 'info');
        return;
      }
      showToast((err as any).userMessage || t('contractDetails.toasts.signatureApplyFailed'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGenerateQR = async () => {
    if (!contract || isSubmitting) return;
    if (!contract.ownerSigned) {
      showToast(t('contractDetails.toasts.signBeforeQr'), 'warning');
      return;
    }
    setIsSubmitting(true);
    try {
      const { data } = await api.get(`/contracts/${contract.id}/qr`);
      if (data?.success === false) {
        showToast(data.message || t('contractDetails.toasts.qrGenerateFailed'), 'error');
        return;
      }
      const qrData = data?.data || data;
      applyContractEnvelope(data, {
        qrToken: qrData?.qrToken || contract.qrToken,
        publicSigningUrl: qrData?.signingUrl || qrData?.publicSigningUrl || contract.publicSigningUrl,
      });
      setShowQRModal(true);
      showToast(data?.message || t('contractDetails.toasts.qrGenerated'), 'success');
    } catch (err: any) {
      if (err?.response?.status === 409 && contract.qrToken && contract.publicSigningUrl) {
        setShowQRModal(true);
        showToast(t('contractDetails.toasts.qrExistingLoaded'), 'info');
        return;
      }
      showToast((err as any).userMessage || t('contractDetails.toasts.qrGenerateFailed'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  // ── Secure signature link: shared by WhatsApp share / copy link / QR ──────
  // All three delivery actions must work even when the client has no email —
  // they only depend on the agency having signed (which mints the token), not
  // on the client's contact email.
  const ensureSigningUrl = async (): Promise<string | null> => {
    if (!contract) return null;
    if (contract.qrToken && contract.publicSigningUrl) return contract.publicSigningUrl;
    if (!contract.ownerSigned) {
      showToast(t('contractDetails.toasts.signBeforeShare'), 'warning');
      return null;
    }
    setGeneratingShareLink(true);
    try {
      const { data } = await api.get(`/contracts/${contract.id}/qr`);
      if (data?.success === false) {
        showToast(data.message || t('contractDetails.toasts.signingLinkFailed'), 'error');
        return null;
      }
      const qrData = data?.data || data;
      const publicSigningUrl = qrData?.signingUrl || qrData?.publicSigningUrl || contract.publicSigningUrl;
      applyContractEnvelope(data, { qrToken: qrData?.qrToken || contract.qrToken, publicSigningUrl });
      return publicSigningUrl || null;
    } catch (err: any) {
      if (err?.response?.status === 409 && contract.qrToken && contract.publicSigningUrl) {
        return contract.publicSigningUrl;
      }
      showToast((err as any).userMessage || t('contractDetails.toasts.signingLinkFailed'), 'error');
      return null;
    } finally {
      setGeneratingShareLink(false);
    }
  };

  const handleShareWhatsApp = async () => {
    if (!contract) return;
    const url = await ensureSigningUrl();
    if (!url) return;
    const vehicle = `${contract.vehicleBrand || ''} ${contract.vehicleModel || ''}`.trim();
    const message = `Bonjour ${contract.clientFullName || ''},\n\n`
      + `Votre contrat de location ${contract.contractNumber} est pret.\n\n`
      + `Vehicule : ${vehicle}\n`
      + `Periode : ${new Date(contract.startDate).toLocaleDateString()} au ${new Date(contract.endDate).toLocaleDateString()}\n\n`
      + `Consultez et signez votre contrat ici :\n${url}\n\n`
      + `${tenant?.name || ''}`;
    const digits = normalizePhoneForWhatsApp(contract.clientPhone);
    window.open(`https://wa.me/${digits}?text=${encodeURIComponent(message)}`, '_blank');
  };

  // Success feedback is icon-only (button flips to a check mark) — a toast
  // is redundant for an action whose result is already obvious in context.
  const handleCopySigningLink = async (): Promise<boolean> => {
    const url = await ensureSigningUrl();
    if (!url) return false;
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(url);
      } else {
        const textArea = document.createElement('textarea');
        textArea.value = url;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
      return true;
    } catch {
      showToast(t('contracts.copyFailed') || t('contractDetails.toasts.copyLinkFailed'), 'error');
      return false;
    }
  };

  const handleShowQrShare = async () => {
    const url = await ensureSigningUrl();
    if (url) setShowQRModal(true);
  };

  // ── Add/edit the client's email directly from Contract Details ────────────
  const handleSaveClientEmail = async (email: string, sendAfterSave: boolean) => {
    if (!contract) return;
    setSavingClientEmail(true);
    try {
      await api.patch(`/clients/${contract.clientId}/email`, { email });
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('contractEmail.invalid') || t('contractDetails.toasts.emailSaveFailed'), 'error');
      setSavingClientEmail(false);
      throw err;
    }

    // Update contract view + email status immediately — no page refresh.
    setContract(current => current ? { ...current, clientEmail: email } : current);
    setEmailStatus((current: any) => ({ ...(current || {}), clientEmail: email, hasClientEmail: true }));
    setShowAddEmailModal(false);
    setSavingClientEmail(false);

    if (!sendAfterSave) {
      showToast(t('contractEmail.saved') || 'Email saved', 'success');
      fetchEmailStatus(contract.id);
      return;
    }

    setSendingEmail(true);
    try {
      const { data } = await api.post(`/contracts/${contract.id}/send-email`);
      if (data?.success) {
        showToast(t('contractEmail.savedAndSent') || 'Email saved and contract sent', 'success');
      } else {
        showToast(t('contractEmail.savedSendFailed') || 'Email saved, but the contract could not be sent.', 'error');
      }
    } catch {
      showToast(t('contractEmail.savedSendFailed') || 'Email saved, but the contract could not be sent.', 'error');
    } finally {
      setSendingEmail(false);
      fetchEmailStatus(contract.id);
    }
  };

  const sendEmailAction = useInlineAction(async () => {
    if (!contract) return;
    const { data } = await api.post(`/contracts/${contract.id}/send-email`);
    if (!data?.success) {
      throw new Error(data?.message || t('contractDetails.toasts.sendEmailFailed'));
    }
    fetchEmailStatus(contract.id);
  }, { context: 'contract-send-email' });

  const handleFinalize = async () => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      await api.post(`/contracts/${contract.id}/finalize`);
      showToast(t('contractDetails.toasts.contractFinalized'), 'success');
      fetchContract();
    } catch (err: any) {
      showToast((err as any).userMessage || t('contractDetails.toasts.finalizeFailed'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleStartInspection = async (type: 'BEFORE_DELIVERY' | 'AFTER_RETURN') => {
    if (!contract) return;
    setIsSubmitting(true);
    try {
      const { data } = await api.post('/inspections/create-token', {
        contractId: contract.id,
        type,
        frontendUrl: window.location.origin + '/#',
      });
      setInspectionQr(data);
      await fetchInspections(contract.id);
      showToast(type === 'BEFORE_DELIVERY'
        ? t('contractDetails.toasts.beforeDeliveryQrGenerated')
        : t('contractDetails.toasts.afterReturnQrGenerated'), 'success');
    } catch (err: any) {
      showToast((err as any).userMessage || t('contractDetails.toasts.inspectionStartFailed'), 'error');
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
        <p className="text-lg font-medium">{t('contractDetails.notFound')}</p>
        <button onClick={() => navigate('/contracts')} className="mt-4 px-3 sm:px-5 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl text-xs sm:text-sm font-medium hover:bg-brand-600 transition-all w-full sm:w-auto">
          {t('contractDetails.backToContracts')}
        </button>
      </div>
    );
  }

  const bothSigned = contract.clientSigned && contract.ownerSigned;
  const displayedStatus = bothSigned
    ? 'ACTIVE'
    : (contract.clientSigned || contract.ownerSigned) && contract.status !== 'CANCELLED' && contract.status !== 'COMPLETED'
      ? 'PENDING_SIGNATURE'
      : contract.status;
  const canFinalize = bothSigned && contract.status !== 'ACTIVE' && contract.status !== 'COMPLETED';
  const statusLabel = (status?: string) =>
    status ? t(`contracts.statusLabel.${status}`, { defaultValue: status.replace('_', ' ') }) : t('common.notAvailable');
  const paymentStatusLabel = (status?: string) =>
    status ? t(`subscription.statuses.${status}`, { defaultValue: status.replace('_', ' ') }) : t('common.notAvailable');

  const detailTabs = [
    { key: 'overview', label: t('contractDetails.tabs.overview'), icon: FileText },
    { key: 'client', label: t('contractDetails.tabs.client'), icon: User },
    { key: 'vehicle', label: t('contractDetails.tabs.vehicle'), icon: Car },
    { key: 'payment', label: t('contractDetails.tabs.payment'), icon: CreditCard },
    { key: 'inspection', label: t('contractDetails.tabs.inspection'), icon: Shield },
    { key: 'documents', label: t('contractDetails.tabs.documents'), icon: ClipboardCheck },
    { key: 'drivers', label: t('contractDetails.tabs.drivers'), icon: Users },
    { key: 'activity', label: t('contractDetails.tabs.activity'), icon: History },
  ];

  return (
    <div className="space-y-5 animate-fade p-3 sm:p-4 lg:p-6">
      {/* Header */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/contracts')} className="p-2 -ms-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all shrink-0">
            <ArrowLeft size={20} />
          </button>
          {tenant?.logoUrl && (
            <img
              src={resolveMediaUrl(tenant.logoUrl) || undefined}
              alt={tenant.name || 'Agency'}
              className="h-9 w-auto max-w-[120px] rounded-lg object-contain border border-slate-100 bg-white p-0.5 shrink-0"
            />
          )}
          <div className="min-w-0">
            <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] truncate">{contract.contractNumber}</h1>
            <p className="text-slate-500 text-xs sm:text-sm">{tenant?.name || t('contractDetails.pageTitle')}</p>
          </div>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <span className={`px-2.5 sm:px-3 py-1.5 rounded-lg text-[10px] sm:text-xs font-bold uppercase tracking-wider ${getStatusBadge(displayedStatus)}`}>
            {t(`contracts.statusLabel.${displayedStatus}`, { defaultValue: displayedStatus.replace('_', ' ') })}
          </span>
          <button onClick={handlePrintPdf} disabled={isSubmitting} className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 bg-white border border-slate-200 rounded-xl text-xs sm:text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-50">
            <Printer size={14} className="sm:hidden" />
            <Printer size={16} className="hidden sm:block" />
            <span className="hidden sm:inline">{t('contracts.print')}</span>
          </button>
          <Tooltip label={downloadPdfAction.phase === 'error' ? downloadPdfAction.errorMessage : null}>
            <button onClick={handleDownloadPdf} disabled={isSubmitting || downloadPdfAction.phase === 'loading'}
              className={cn(
                'flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 bg-white border border-slate-200 rounded-xl text-xs sm:text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-50',
                downloadPdfAction.phase === 'error' && 'ring-2 ring-red-400',
              )}>
              <AnimatedStatusIcon phase={downloadPdfAction.phase} idleIcon={Download} size={16} className="sm:hidden" />
              <AnimatedStatusIcon phase={downloadPdfAction.phase} idleIcon={Download} size={16} className="hidden sm:block" />
              <span className="hidden sm:inline">PDF</span>
            </button>
          </Tooltip>
          {contract.pdfUrl && (
            <Tooltip label={regeneratePdfAction.phase === 'error' ? regeneratePdfAction.errorMessage : t('contractDetails.regeneratePdfHint')}>
              <button onClick={handleRegeneratePdf} disabled={isSubmitting || regeneratePdfAction.phase === 'loading'}
                className={cn(
                  'flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 bg-white border border-slate-200 rounded-xl text-xs sm:text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-50',
                  regeneratePdfAction.phase === 'error' && 'ring-2 ring-red-400',
                )}>
                <AnimatedStatusIcon phase={regeneratePdfAction.phase} idleIcon={RefreshCw} size={16} className="sm:hidden" />
                <AnimatedStatusIcon phase={regeneratePdfAction.phase} idleIcon={RefreshCw} size={16} className="hidden sm:block" />
                <span className="hidden sm:inline">{t('contractDetails.regeneratePdf')}</span>
              </button>
            </Tooltip>
          )}
          <button
            onClick={contract.ownerSigned ? handleGenerateQR : () => showToast(t('contractDetails.toasts.signBeforeQrShort'), 'warning')}
            disabled={isSubmitting}
            className={`flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 rounded-xl text-xs sm:text-sm font-medium transition-all disabled:opacity-50 whitespace-nowrap ${
              contract.ownerSigned
                ? 'bg-brand-500 text-white hover:bg-brand-600'
                : 'bg-slate-200 text-slate-400 cursor-not-allowed'
            }`}>
            <QrCode size={14} className="sm:hidden" />
            <QrCode size={16} className="hidden sm:block" />
            {contract.qrToken ? t('contracts.showQR') : t('contracts.generateQR')}
          </button>
          <button
            onClick={() => handleStartInspection('BEFORE_DELIVERY')}
            disabled={isSubmitting}
            className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-4 py-2 sm:py-2.5 rounded-xl text-xs sm:text-sm font-medium bg-emerald-500 text-white hover:bg-emerald-600 transition-all disabled:opacity-50 whitespace-nowrap">
            <CameraIcon />
            {t('contractDetails.startInspection')}
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
                <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.signatureStatus')}</h3>
                <div className="grid grid-cols-3 gap-2 sm:gap-4">
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.clientSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <User size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.clientSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.tabs.client')}</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.clientSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.clientSigned ? t('contracts.signed') : t('contracts.waiting')}
                    </p>
                  </div>
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.ownerSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <Shield size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.ownerSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.roleOwner')}</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.ownerSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.ownerSigned ? t('contracts.signed') : t('contracts.waiting')}
                    </p>
                  </div>
                  <div className={`p-2.5 sm:p-4 rounded-xl sm:rounded-2xl text-center ${contract.employeeSigned ? 'bg-success-50 border border-success-100' : 'bg-slate-50 border border-slate-100'}`}>
                    <FileText size={18} className={`mx-auto mb-1.5 sm:mb-2 ${contract.employeeSigned ? 'text-success-500' : 'text-slate-300'}`} />
                    <p className="text-[10px] sm:text-xs font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.roleEmployee')}</p>
                    <p className={`text-xs sm:text-sm font-bold mt-0.5 sm:mt-1 ${contract.employeeSigned ? 'text-success-600' : 'text-slate-400'}`}>
                      {contract.employeeSigned ? t('contracts.signed') : t('contracts.waiting')}
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
                          {t('contractDetails.applyAgencySignature')}
                        </button>
                      ) : (
                        <div className="p-4 bg-amber-50 border border-amber-100 rounded-xl text-sm text-amber-700">
                          {t('contractDetails.noAgencySignature')} <button onClick={() => navigate('/settings')} className="font-semibold underline">{t('contractDetails.setInSettings')}</button> {t('contractDetails.orDrawBelowSuffix')}
                        </div>
                      )}
                      {!showOwnerSign ? (
                        <button onClick={() => setShowOwnerSign(true)}
                          className="w-full py-3 bg-brand-50 text-brand-500 rounded-xl font-semibold text-sm hover:bg-brand-100 transition-all">
                          {tenant?.agencySignature ? t('contractDetails.drawCustomSignature') : t('contractDetails.signAsOwner')}
                        </button>
                      ) : (
                        <SignaturePad onSave={handleOwnerSign} label={t('contractDetails.ownerSignatureLabel')} autoSaveKey={`owner_${contract.id}`} />
                      )}
                    </div>
                  ) : (
                    <div className="flex items-start sm:items-center gap-3 p-3 sm:p-4 bg-success-50 border border-success-100 rounded-xl">
                      <CheckCircle2 size={18} className="text-success-500 shrink-0 mt-0.5 sm:mt-0" />
                      <div className="min-w-0">
                        <p className="text-xs sm:text-sm font-semibold text-success-700">{t('contractDetails.agencySignatureApplied')}</p>
                        {contract.ownerSignature ? (
                          <img src={contract.ownerSignature} alt={t('contractDetails.agencySignatureAlt')} className="h-12 sm:h-16 mt-2 signature-paper rounded-lg border border-success-200 p-1" />
                        ) : (
                          <p className="text-xs mt-2" style={{ color: 'var(--text-muted)' }}>{t('contractDetails.signatureUnavailable', 'Signature image unavailable.')}</p>
                        )}
                      </div>
                    </div>
                  )}
                </div>

                {canFinalize && (
                  <button onClick={handleFinalize} disabled={isSubmitting}
                    className="w-full py-3 bg-success-500 text-white rounded-xl font-semibold text-sm hover:bg-success-600 transition-all disabled:opacity-50 flex items-center justify-center gap-2">
                    {isSubmitting ? <Loader2 size={18} className="animate-spin" /> : <CheckCircle2 size={18} />}
                    {t('contractDetails.finalizeContract')}
                  </button>
                )}
              </div>

              {/* Signature Previews — always rendered (not gated on either
                  side being signed): each side independently shows the
                  actual signature when both the "signed" flag and the image
                  data are present, or a plain "Not signed yet" note otherwise
                  (also covers the data-inconsistency edge case of signed=true
                  with no stored image) — never silently absent. */}
              <div className="card-premium space-y-4 p-3 sm:p-5">
                  <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.signatures')}</h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <p className="text-xs font-bold text-slate-500">{t('contractDetails.agencyRepresentative')}</p>
                      {contract.ownerSigned && contract.ownerSignature ? (
                        <div className="p-2 signature-paper rounded-xl border border-slate-200 flex items-end gap-3">
                          <img src={contract.ownerSignature} alt={t('contractDetails.agencySignatureAlt')} className="h-16 sm:h-20 flex-1 object-contain" />
                          {tenant?.agencyStampUrl && (
                            <img src={resolveMediaUrl(tenant.agencyStampUrl) || undefined} alt={t('contractDetails.agencyStampAlt')} className="h-14 sm:h-16 w-14 sm:w-16 object-contain opacity-80" />
                          )}
                        </div>
                      ) : (
                        <div className="p-3 rounded-xl border text-xs" style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-muted)' }}>
                          {t('contractDetails.notSignedYet', 'Not signed yet.')}
                        </div>
                      )}
                    </div>
                    <div className="space-y-2">
                      <p className="text-xs font-bold text-slate-500">{t('contractDetails.tabs.client')}</p>
                      {contract.clientSigned && contract.clientSignature ? (
                        <div className="p-2 signature-paper rounded-xl border border-slate-200">
                          <img src={contract.clientSignature} alt={t('contractDetails.clientSignatureAlt')} className="h-16 sm:h-20 w-full object-contain" />
                        </div>
                      ) : (
                        <div className="p-3 rounded-xl border text-xs" style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-muted)' }}>
                          {t('contractDetails.notSignedYet', 'Not signed yet.')}
                        </div>
                      )}
                    </div>
                  </div>
                  {contract.pdfUrl && (
                    <button
                      type="button"
                      onClick={handleDownloadPdf}
                      disabled={isSubmitting}
                      className="flex items-center justify-center gap-2 w-full py-2.5 bg-slate-100 text-slate-700 rounded-xl text-sm font-medium hover:bg-slate-200 transition-all disabled:opacity-50"
                    >
                      <FileText size={16} />
                      {t('contractDetails.viewSignedPdf')}
                    </button>
                  )}
              </div>

              {/* Client Email Status */}
              <div className="card-premium space-y-3 p-3 sm:p-5">
                <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
                  <AlertCircle size={14} /> {t('contractEmail.title')}
                </h3>

                {emailStatus?.hasClientEmail ? (
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="space-y-1">
                        <p className="text-xs text-slate-500">{t('contractEmail.clientEmailLabel')}</p>
                        <p className="text-sm font-semibold text-[#1e293b] dark:text-white">{emailStatus.clientEmail}</p>
                        {emailStatus?.lastStatus && (
                          <div className="flex items-center gap-1.5 mt-1">
                            {emailStatus.lastStatus === 'SENT' ? (
                              <CheckCircle2 size={13} className="text-success-500" />
                            ) : (
                              <AlertCircle size={13} className="text-amber-500" />
                            )}
                            <span className={`text-xs font-semibold ${emailStatus.lastStatus === 'SENT' ? 'text-success-600' : 'text-amber-600'}`}>
                              {emailStatus.lastStatus === 'SENT' ? t('contractEmail.emailSent') : emailErrorLabel(emailStatus.lastErrorCode)}
                            </span>
                            {emailStatus.lastSentAt && (
                              <span className="text-xs text-slate-400">
                                · {new Date(emailStatus.lastSentAt).toLocaleDateString()}
                              </span>
                            )}
                          </div>
                        )}
                        {!emailStatus?.lastStatus && (
                          <p className="text-xs text-slate-400">{t('contractEmail.noEmailSentYet')}</p>
                        )}
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          onClick={() => setShowAddEmailModal(true)}
                          className="flex items-center gap-1.5 px-3 py-2 min-h-[44px] bg-slate-100 dark:bg-white/5 text-[#1e293b] dark:text-white rounded-xl text-xs font-semibold hover:bg-slate-200 dark:hover:bg-white/10 transition-all"
                        >
                          <Pencil size={13} /> {t('contractEmail.edit')}
                        </button>
                        <Tooltip label={sendEmailAction.phase === 'error' ? sendEmailAction.errorMessage : null}>
                          <button
                            onClick={() => sendEmailAction.run()}
                            disabled={sendEmailAction.phase === 'loading'}
                            className={cn(
                              'flex items-center gap-2 px-4 py-2 min-h-[44px] bg-brand-50 dark:bg-brand-500/10 text-brand-600 dark:text-brand-400 rounded-xl text-xs font-semibold hover:bg-brand-100 dark:hover:bg-brand-500/20 transition-all disabled:opacity-50',
                              sendEmailAction.phase === 'error' && 'ring-2 ring-red-400',
                            )}
                          >
                            <AnimatedStatusIcon phase={sendEmailAction.phase} idleIcon={Send} size={13} />
                            {emailStatus?.lastStatus === 'SENT' ? t('contractEmail.resend') : t('contractEmail.send')}
                          </button>
                        </Tooltip>
                      </div>
                    </div>
                    {!(contract.ownerSigned && contract.clientSigned) && (
                      // The backend only sends the *signed* contract PDF by
                      // email — it can't send one that doesn't exist yet.
                      // Surfacing that here, before the user clicks, is what
                      // actually fixes "resend does nothing": previously the
                      // button stayed enabled and looked normal, and the only
                      // signal a click failed was a transient toast easy to
                      // miss. The button itself stays enabled/clickable per
                      // spec — this is informational, not a second disabled
                      // state — so a real permission/email-validity failure
                      // still surfaces through the actual click attempt.
                      <p className="text-xs flex items-center gap-1.5" style={{ color: 'var(--text-muted)' }}>
                        <AlertCircle size={12} className="shrink-0" />
                        {t('contractEmail.awaitingSignaturesForEmail', 'Both signatures are required before the signed contract can be emailed.')}
                      </p>
                    )}
                    <ContractShareActions
                      onWhatsApp={handleShareWhatsApp}
                      onCopyLink={handleCopySigningLink}
                      onShowQr={handleShowQrShare}
                      busy={generatingShareLink}
                      t={t}
                    />
                  </div>
                ) : (
                  <div className="space-y-3 rounded-2xl border border-amber-200 dark:border-amber-500/20 bg-amber-50/70 dark:bg-amber-500/10 p-3 sm:p-4">
                    <div>
                      <p className="text-sm font-bold text-amber-800 dark:text-amber-300">{t('contractEmail.missing.title')}</p>
                      <p className="text-xs text-amber-700 dark:text-amber-400/90 mt-1">{t('contractEmail.missing.description')}</p>
                    </div>
                    <button
                      onClick={() => setShowAddEmailModal(true)}
                      className="flex items-center justify-center gap-2 w-full sm:w-auto px-4 py-2.5 min-h-[44px] bg-brand-600 text-white rounded-xl text-sm font-semibold hover:bg-brand-700 transition-all"
                    >
                      <MailPlus size={15} /> {t('contractEmail.add')}
                    </button>
                    <ContractShareActions
                      onWhatsApp={handleShareWhatsApp}
                      onCopyLink={handleCopySigningLink}
                      onShowQr={handleShowQrShare}
                      busy={generatingShareLink}
                      t={t}
                    />
                  </div>
                )}
              </div>

              <AddClientEmailModal
                isOpen={showAddEmailModal}
                onClose={() => setShowAddEmailModal(false)}
                clientName={contract.clientFullName || ''}
                clientPhone={contract.clientPhone || ''}
                currentEmail={emailStatus?.hasClientEmail ? emailStatus.clientEmail : ''}
                saving={savingClientEmail}
                onSave={handleSaveClientEmail}
              />

              {/* Security Deposit */}
              {contract.deposit && (
                <div className="card-premium space-y-4 p-3 sm:p-5">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.securityDeposit')}</h3>
                    <span className={`text-xs font-bold px-2 py-1 rounded-lg ${
                      contract.deposit.status === 'RETURNED' || contract.deposit.status === 'PARTIALLY_RETURNED'
                        ? 'bg-success-100 text-success-600'
                        : contract.deposit.status === 'DEDUCTED'
                        ? 'bg-danger-100 text-danger-600'
                        : contract.deposit.status === 'HELD'
                        ? 'bg-brand-100 text-brand-600'
                        : 'bg-slate-100 text-slate-600'
                    }`}>
                      {t(`contractDetails.depositStatusValues.${contract.deposit.status}`, { defaultValue: contract.deposit.status })}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3 bg-slate-50 rounded-xl">
                      <p className="text-[10px] text-slate-400 uppercase font-bold">{t('contractDetails.type')}</p>
                      <p className="text-sm font-bold text-[#1e293b]">{contract.deposit.depositType || t('contractDetails.cash')}</p>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-xl">
                      <p className="text-[10px] text-slate-400 uppercase font-bold">{t('contractDetails.amount')}</p>
                      <p className="text-sm font-bold text-brand-600">{contract.deposit.amount} MAD</p>
                    </div>
                    {contract.deposit.reference && (
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <p className="text-[10px] text-slate-400 uppercase font-bold">{t('contractDetails.reference')}</p>
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
                      {t('contractDetails.processReturn')}
                    </button>
                  )}
                  {(contract.deposit.status === 'RETURNED' || contract.deposit.status === 'PARTIALLY_RETURNED' || contract.deposit.status === 'DEDUCTED') && (
                    <div className="space-y-2 p-3 bg-slate-50 rounded-xl">
                      <p className="text-xs font-bold text-slate-500 uppercase">{t('contractDetails.returnSummary')}</p>
                      <div className="flex justify-between text-sm">
                        <span className="text-slate-500">{t('contractDetails.depositLabel')}</span>
                        <span className="font-medium">{contract.deposit.amount} MAD</span>
                      </div>
                      {(contract.deposit.damageDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>{t('contractDetails.damage')}</span>
                          <span className="font-medium">- {contract.deposit.damageDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.cleaningDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>{t('contractDetails.cleaning')}</span>
                          <span className="font-medium">- {contract.deposit.cleaningDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.lateFeeDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>{t('contractDetails.lateFee')}</span>
                          <span className="font-medium">- {contract.deposit.lateFeeDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.fuelDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>{t('contractDetails.fuel')}</span>
                          <span className="font-medium">- {contract.deposit.fuelDeduction} MAD</span>
                        </div>
                      )}
                      {(contract.deposit.otherDeduction || 0) > 0 && (
                        <div className="flex justify-between text-sm text-danger-600">
                          <span>{t('contractDetails.other')}</span>
                          <span className="font-medium">- {contract.deposit.otherDeduction} MAD</span>
                        </div>
                      )}
                      <div className="h-px bg-slate-200" />
                      <div className="flex justify-between text-sm font-bold">
                        <span className="text-slate-700">{t('contractDetails.returned')}</span>
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
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">{t('contractDetails.tabs.client')}</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b] truncate">{contract.clientFullName || t('common.notAvailable')}</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 truncate">{contract.clientPhone} • {contract.clientEmail}</p>
                </div>
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <Car size={13} className="sm:hidden" />
                    <Car size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">{t('contractDetails.tabs.vehicle')}</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b] truncate">{contract.vehicleBrand} {contract.vehicleModel}</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 truncate">{contract.vehicleRegistration} • {contract.vehicleCategory}</p>
                </div>
                <div className="card-premium p-4 sm:p-5 space-y-1.5 sm:space-y-2">
                  <div className="flex items-center gap-2 text-brand-500">
                    <Calendar size={13} className="sm:hidden" />
                    <Calendar size={14} className="hidden sm:block" />
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">{t('contractDetails.period')}</span>
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
                    <span className="text-[10px] sm:text-xs font-bold uppercase tracking-wider">{t('contractDetails.tabs.payment')}</span>
                  </div>
                  <p className="text-base sm:text-lg font-bold text-[#1e293b]">{contract.totalPrice || 0} MAD</p>
                  <p className="text-[10px] sm:text-xs text-slate-400 capitalize">{contract.paymentMethod} • {contract.paymentStatus}</p>
                </div>
              </div>
            </>
          )}

          {activeTab === 'client' && (
            <div className="card-premium p-4 sm:p-6 space-y-4 sm:space-y-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.clientInfo')}</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <InfoRow label={t('contractDetails.fields.fullName')} value={contract.clientFullName} />
                <InfoRow label={t('contractDetails.fields.nationality')} value={contract.clientNationality} />
                <InfoRow label={t('contractDetails.fields.gender')} value={contract.clientGender} />
                <InfoRow label={t('contractDetails.fields.birthDate')} value={contract.clientBirthDate ? new Date(contract.clientBirthDate).toLocaleDateString() : ''} />
                <InfoRow label={t('contractDetails.fields.cinId')} value={contract.clientCin} />
                <InfoRow label={t('contractDetails.fields.passport')} value={contract.clientPassportNumber} />
                <InfoRow label={t('contractDetails.fields.driverLicense')} value={contract.clientDriverLicense} />
                <InfoRow label={t('contractDetails.fields.phone')} value={contract.clientPhone} />
                <InfoRow label={t('contractDetails.fields.email')} value={contract.clientEmail} />
                <InfoRow label={t('contractDetails.fields.address')} value={`${contract.clientAddress}, ${contract.clientCity}, ${contract.clientCountry}`} />
                <InfoRow label={t('contractDetails.fields.emergencyContact')} value={`${contract.emergencyContactName} ${contract.emergencyContactPhone}`} />
              </div>
            </div>
          )}

          {activeTab === 'vehicle' && (
            <div className="card-premium p-4 sm:p-6 space-y-4 sm:space-y-6">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.vehicleInfo')}</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                <InfoRow label={t('contractDetails.fields.brandModel')} value={`${contract.vehicleBrand} ${contract.vehicleModel}`} />
                <InfoRow label={t('contractDetails.fields.category')} value={contract.vehicleCategory} />
                <InfoRow label={t('contractDetails.fields.year')} value={contract.vehicleYear?.toString()} />
                <InfoRow label={t('contractDetails.fields.color')} value={contract.vehicleColor} />
                <InfoRow label={t('contractDetails.fields.registration')} value={contract.vehicleRegistration} />
                <InfoRow label={t('contractDetails.fields.transmission')} value={contract.vehicleTransmission} />
                <InfoRow label={t('contractDetails.fields.fuelType')} value={contract.fuelType} />
                <InfoRow label={t('contractDetails.fields.fuelLevel')} value={contract.fuelLevelStart} />
              </div>
            </div>
          )}

          {activeTab === 'payment' && (
            <div className="card-premium p-4 sm:p-6 space-y-5 sm:space-y-6">
              {/* ── Rental Payment ─────────────────────────────────────────── */}
              <div>
                <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400 mb-3">{t('contractDetails.rentalPayment')}</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <InfoRow label={t('contractDetails.fields.totalPrice')} value={`${(contract.totalPrice || 0).toLocaleString()} MAD`} />
                  <InfoRow label={t('contractDetails.fields.dailyPrice')} value={`${(contract.dailyPrice || 0).toLocaleString()} MAD`} />
                  <InfoRow label={t('contractDetails.fields.paidAmount')} value={`${(contract.paidAmount || 0).toLocaleString()} MAD`} />
                  <InfoRow label={t('contractDetails.remaining')} value={`${(contract.remainingAmount || 0).toLocaleString()} MAD`} />
                  <InfoRow label={t('contractDetails.fields.paymentMethod')} value={contract.paymentMethod} />
                  <InfoRow label={t('contractDetails.fields.paymentStatus')} value={contract.paymentStatus} />
                </div>
              </div>
              {/* ── Deposit / Guarantee (Caution) ────────────────────────────── */}
              <div className="border-t border-[var(--border-subtle)] pt-4">
                <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400 mb-3">
                  {t('contractDetails.depositGuaranteeCaution')}
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                  <InfoRow
                    label={t('contractDetails.fields.depositRequired')}
                    value={`${(contract.depositAmount || 0).toLocaleString()} ${contract.depositCurrency || 'MAD'}`}
                  />
                  <InfoRow
                    label={t('contractDetails.fields.depositStatus')}
                    value={t(`contractDetails.depositStatusValues.${contract.depositStatus}`, {
                      defaultValue: contract.depositStatus || t('contractDetails.depositStatusValues.NOT_REQUIRED'),
                    })}
                  />
                  {contract.deposit && (
                    <>
                      <InfoRow label={t('contractDetails.fields.depositHeld')} value={`${(contract.deposit.amount || 0).toLocaleString()} MAD`} />
                      {contract.deposit.returnedAmount != null && (
                        <InfoRow label={t('contractDetails.fields.depositReturned')} value={`${(contract.deposit.returnedAmount || 0).toLocaleString()} MAD`} />
                      )}
                    </>
                  )}
                </div>
                {contract.depositAmount > 0 && (
                  <p className="text-[11px] text-slate-400 mt-2">
                    {t('contractDetails.depositSeparateNote')}
                  </p>
                )}
              </div>
            </div>
          )}

          {activeTab === 'inspection' && (
            <div className="card-premium p-4 sm:p-6 space-y-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.inspectionMedia')}</h3>
                  <p className="mt-1 text-xs text-slate-400">{t('contractDetails.inspectionMediaDesc')}</p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <button onClick={() => handleStartInspection('BEFORE_DELIVERY')} disabled={isSubmitting}
                    className="rounded-xl bg-emerald-500 px-4 py-2 text-xs font-bold text-white hover:bg-emerald-600 disabled:opacity-50">
                    {t('contractDetails.beforeDeliveryQr')}
                  </button>
                  <button onClick={() => handleStartInspection('AFTER_RETURN')} disabled={isSubmitting}
                    className="rounded-xl bg-[var(--brand-primary)] px-4 py-2 text-xs font-bold text-[var(--brand-primary-foreground)] hover:opacity-90 disabled:opacity-50">
                    {t('contractDetails.afterReturnQr')}
                  </button>
                  <button onClick={() => contract && fetchInspections(contract.id)} disabled={isSubmitting}
                    className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                    {t('contractDetails.refreshMedia')}
                  </button>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <InspectionStatusCard title={t('contractDetails.beforeDelivery')} inspection={inspections.find((item) => item.type === 'BEFORE_DELIVERY')} />
                <InspectionStatusCard title={t('contractDetails.afterReturn')} inspection={inspections.find((item) => item.type === 'AFTER_RETURN')} />
              </div>
              <InspectionGallery
                inspections={inspections}
                onRefresh={() => { if (contract?.id) fetchInspections(contract.id); }}
              />
              {contract.vehicleCondition && (
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                  <h4 className="mb-3 text-xs font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.legacyDamageMarkers')}</h4>
                  <VehicleInspection
                    value={[
                      { id: 'front', label: t('contractDetails.zones.front'), damaged: contract.vehicleCondition.frontDamage || false, notes: '' },
                      { id: 'rear', label: t('contractDetails.zones.rear'), damaged: contract.vehicleCondition.rearDamage || false, notes: '' },
                      { id: 'leftSide', label: t('contractDetails.zones.leftSide'), damaged: contract.vehicleCondition.leftSideDamage || false, notes: '' },
                      { id: 'rightSide', label: t('contractDetails.zones.rightSide'), damaged: contract.vehicleCondition.rightSideDamage || false, notes: '' },
                      { id: 'windshield', label: t('contractDetails.zones.windshield'), damaged: contract.vehicleCondition.windshieldDamage || false, notes: '' },
                      { id: 'interior', label: t('contractDetails.zones.interior'), damaged: contract.vehicleCondition.interiorDamage || false, notes: '' },
                      { id: 'roof', label: t('contractDetails.zones.roof'), damaged: contract.vehicleCondition.roofDamage || false, notes: '' },
                      { id: 'bumperFront', label: t('contractDetails.zones.frontBumper'), damaged: contract.vehicleCondition.bumperFrontDamage || false, notes: '' },
                      { id: 'bumperRear', label: t('contractDetails.zones.rearBumper'), damaged: contract.vehicleCondition.bumperRearDamage || false, notes: '' },
                      { id: 'hood', label: t('contractDetails.zones.hood'), damaged: contract.vehicleCondition.hoodDamage || false, notes: '' },
                      { id: 'trunk', label: t('contractDetails.zones.trunk'), damaged: contract.vehicleCondition.trunkDamage || false, notes: '' },
                    ]}
                    onChange={() => {}}
                    readOnly
                  />
                </div>
              )}
            </div>
          )}

          {activeTab === 'documents' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.documentChecklist')}</h3>
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
                <p className="text-sm text-slate-400 text-center py-8">{t('contractDetails.noDocumentsRecorded')}</p>
              )}
            </div>
          )}

          {activeTab === 'drivers' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.additionalDrivers')}</h3>
              {contract.additionalDrivers && contract.additionalDrivers.length > 0 ? (
                <div className="space-y-3">
                  {contract.additionalDrivers.map((driver: any) => (
                    <div key={driver.id} className="p-4 bg-slate-50 rounded-2xl">
                      <p className="font-bold text-[#1e293b]">{driver.fullName}</p>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-1 text-xs text-slate-400">
                        {driver.driverLicenseNumber && <span>{t('contractDetails.licenseShort')}: {driver.driverLicenseNumber}</span>}
                        {driver.phone && <span>{t('contractDetails.fields.phone')}: {driver.phone}</span>}
                        {driver.nationality && <span>{t('contractDetails.fields.nationality')}: {driver.nationality}</span>}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-slate-400 text-center py-8">{t('contractDetails.noAdditionalDriversFound')}</p>
              )}
            </div>
          )}

          {activeTab === 'activity' && (
            <div className="card-premium p-4 sm:p-6 space-y-3 sm:space-y-4">
              <h3 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-slate-400">{t('contractDetails.activityLog')}</h3>
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
                <p className="text-sm text-slate-400 text-center py-8">{t('contractDetails.noActivityRecorded')}</p>
              )}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-5">
          {/* Timeline */}
          <div className="card-premium p-3 sm:p-5 space-y-4">
            <h4 className="text-sm font-bold text-[#1e293b]">{t('contractDetails.contractTimeline')}</h4>
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
                        {statusLabel(s)}
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
                <h4 className="text-sm font-bold text-[#1e293b]">{t('contractDetails.clientBalance')}</h4>
                <span className={`px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase tracking-wider ${clientBalance.paymentStatus === 'PAID' ? 'bg-success-50 text-success-500' : clientBalance.paymentStatus === 'PARTIALLY_PAID' ? 'bg-warning-50 text-warning-500' : 'bg-danger-50 text-danger-500'}`}>
                  {paymentStatusLabel(clientBalance.paymentStatus)}
                </span>
              </div>

              {/* Money row */}
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-success-50 rounded-xl p-3 text-center">
                  <p className="text-[10px] font-bold uppercase tracking-wider text-success-400 mb-1">{paymentStatusLabel('PAID')}</p>
                  <p className="text-lg font-black text-success-600">{Number(clientBalance.totalPaid || 0).toLocaleString()}</p>
                  <p className="text-[10px] text-success-400 font-medium">MAD</p>
                </div>
                <div className={`rounded-xl p-3 text-center ${(clientBalance.remainingBalance || 0) > 0 ? 'bg-danger-50' : 'bg-success-50'}`}>
                  <p className={`text-[10px] font-bold uppercase tracking-wider mb-1 ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-400' : 'text-success-400'}`}>{t('contractDetails.remaining')}</p>
                  <p className={`text-lg font-black ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-600' : 'text-success-600'}`}>{Number(clientBalance.remainingBalance || 0).toLocaleString()}</p>
                  <p className={`text-[10px] font-medium ${(clientBalance.remainingBalance || 0) > 0 ? 'text-danger-400' : 'text-success-400'}`}>MAD</p>
                </div>
              </div>

              {/* Stats row */}
              <div className="grid grid-cols-3 gap-2">
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.totalRentals ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">{t('contractDetails.rentals')}</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.openInvoices ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">{t('contractDetails.invoices')}</p>
                </div>
                <div className="bg-[#f5f5f0] rounded-xl p-2.5 text-center">
                  <p className="text-lg font-bold text-[#1e293b]">{clientBalance.activeContracts ?? 0}</p>
                  <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">{t('subscription.statuses.ACTIVE')}</p>
                </div>
              </div>
            </div>
          )}

          {/* Terms */}
          <div className="card-premium p-5 space-y-3">
            <h4 className="text-sm font-bold text-[#1e293b]">{t('contractDetails.termsConditions')}</h4>
            <div className={`flex items-center gap-3 p-3 rounded-xl ${contract.termsAccepted ? 'bg-success-50 text-success-600' : 'bg-slate-50 text-slate-500'}`}>
              <Shield size={18} />
              <span className="text-sm font-medium">
                {contract.termsAccepted ? t('contracts.termsAccepted') : t('contracts.termsPending')}
              </span>
            </div>
          </div>

          {/* Metadata */}
          <div className="card-premium p-3 sm:p-5 space-y-2">
            <h4 className="text-sm font-bold text-[#1e293b]">{t('contractDetails.metadata')}</h4>
            <div className="text-xs text-slate-400 space-y-1">
              <p>{t('contractDetails.created')}: {contract.createdAt ? new Date(contract.createdAt).toLocaleString() : t('common.notAvailable')}</p>
              <p>{t('contractDetails.updated')}: {contract.updatedAt ? new Date(contract.updatedAt).toLocaleString() : t('common.notAvailable')}</p>
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
          clientSigned={contract.clientSigned}
          signedAt={contract.signedAt}
          clientEmail={contract.clientEmail}
          clientPhone={contract.clientPhone}
        />
      )}

      {showReturnModal && contract?.deposit && (
        <ReturnInspectionModal
          isOpen={showReturnModal}
          onClose={() => setShowReturnModal(false)}
          depositId={contract.deposit.id!}
          depositAmount={contract.deposit.amount || 0}
          contractId={contract.id}
          onSuccess={() => { fetchContract(); showToast(t('contractDetails.toasts.returnProcessed'), 'success'); }}
        />
      )}

      <Modal isOpen={!!inspectionQr} onClose={() => setInspectionQr(null)} title={t('contractDetails.inspectionQr')} maxWidth="md">
        {inspectionQr && (
          <div className="space-y-4 text-center">
            <div className="mx-auto w-fit rounded-3xl bg-white p-5 shadow-sm">
              <QRCodeSVG value={inspectionQr.captureUrl || ''} size={230} level="H" includeMargin />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-800">
                {t('contractDetails.inspectionTypeLabel', { type: inspectionQr.type === 'BEFORE_DELIVERY' ? t('contractDetails.beforeDelivery') : t('contractDetails.afterReturn') })}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {t('contractDetails.scanToOpenChecklist', { date: new Date(inspectionQr.tokenExpiresAt).toLocaleString() })}
              </p>
            </div>
            <input readOnly value={inspectionQr.captureUrl || ''} className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500" />
          </div>
        )}
      </Modal>
    </div>
  );
}

function CameraIcon() {
  return <Shield size={14} className="sm:hidden" />;
}

function InspectionStatusCard({ title, inspection }: { title: string; inspection?: any }) {
  const { t } = useTranslation();
  const photoCount = (inspection?.media || []).filter((item: any) => item.type === 'PHOTO').length;
  const requiredPhotoCount = 13;
  const completed = inspection?.status === 'COMPLETED' || photoCount >= requiredPhotoCount;
  const displayStatusKey = photoCount === 0 ? (inspection ? inspection.status : 'PENDING') : completed ? 'COMPLETED' : 'IN_PROGRESS';
  return (
    <div className={`rounded-2xl border p-4 ${completed ? 'border-emerald-100 bg-emerald-50' : 'border-amber-100 bg-amber-50'}`}>
      <p className="text-xs font-bold uppercase tracking-wider text-slate-500">{title}</p>
      <p className={`mt-1 text-lg font-black ${completed ? 'text-emerald-700' : 'text-amber-700'}`}>
        {t(`contractDetails.inspectionStatus.${displayStatusKey}`, { defaultValue: displayStatusKey.replace('_', ' ') })}
      </p>
      {inspection && <p className="mt-1 text-xs text-slate-500">{t('contractDetails.photosUploadedCount', { count: photoCount, required: requiredPhotoCount })}</p>}
      {inspection?.mediaExpiresAt && <p className="mt-1 text-xs text-slate-500">{t('contractDetails.mediaExpires', { date: new Date(inspection.mediaExpiresAt).toLocaleDateString() })}</p>}
    </div>
  );
}

/**
 * WhatsApp / copy-link / QR delivery actions, shown regardless of whether
 * the client has an email — email is one delivery channel among several,
 * never a gate on the others.
 */
function ContractShareActions({ onWhatsApp, onCopyLink, onShowQr, busy, t }: {
  onWhatsApp: () => void; onCopyLink: () => Promise<boolean>; onShowQr: () => void; busy: boolean;
  t: (key: string) => string;
}) {
  const [copied, setCopied] = useState(false);
  const handleCopyClick = async () => {
    const ok = await onCopyLink();
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    }
  };
  return (
    <div className="flex flex-wrap gap-2">
      <button
        onClick={onWhatsApp}
        disabled={busy}
        className="flex items-center gap-1.5 px-3 py-2 min-h-[44px] bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 rounded-xl text-xs font-semibold hover:bg-emerald-100 dark:hover:bg-emerald-500/20 transition-all disabled:opacity-50"
      >
        <MessageCircle size={14} /> {t('contractEmail.shareWhatsapp')}
      </button>
      <button
        onClick={handleCopyClick}
        disabled={busy}
        className="flex items-center gap-1.5 px-3 py-2 min-h-[44px] bg-slate-100 dark:bg-white/5 text-[#1e293b] dark:text-white rounded-xl text-xs font-semibold hover:bg-slate-200 dark:hover:bg-white/10 transition-all disabled:opacity-50"
      >
        {copied ? <Check size={14} className="text-emerald-500" /> : <Copy size={14} />}
        {copied ? t('toast.copied') : t('contractEmail.copyLink')}
      </button>
      <button
        onClick={onShowQr}
        disabled={busy}
        className="flex items-center gap-1.5 px-3 py-2 min-h-[44px] bg-slate-100 dark:bg-white/5 text-[#1e293b] dark:text-white rounded-xl text-xs font-semibold hover:bg-slate-200 dark:hover:bg-white/10 transition-all disabled:opacity-50"
      >
        {busy ? <Loader2 size={14} className="animate-spin" /> : <QrCode size={14} />} {t('contractEmail.showQr')}
      </button>
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


