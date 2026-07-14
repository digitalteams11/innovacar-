import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { API_ORIGIN } from '../lib/api';
import api from '../api/axios';

const resolveAsset = (url?: string | null) => {
  if (!url) return url ?? undefined;
  if (url.startsWith('http') || url.startsWith('data:')) return url;
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`;
};

import SignaturePad from '../components/shared/SignaturePad';
import {
  Calendar, User, Car, Shield, CheckCircle2, Loader2, Building2,
  MapPin, Phone, Mail, Landmark, AlertCircle, Check, FileText
} from 'lucide-react';

interface PublicContractData {
  contractNumber: string;
  clientFullName?: string;
  clientName?: string;
  clientEmail?: string;
  clientPhone?: string;
  clientCin?: string;
  clientAddress?: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  vehicleRegistration?: string;
  vehiclePlate?: string;
  vehicleCategory?: string;
  startDate: string;
  endDate: string;
  status: string;
  rentalDays?: number;
  dailyPrice?: number;
  totalPrice?: number;
  totalAmount?: number;
  depositAmount?: number;
  deliveryFees?: number;
  returnFees?: number;
  lateFees?: number;
  cleaningFees?: number;
  fuelCharges?: number;
  discountAmount?: number;
  fuelLevel?: string;
  mileageStart?: number;
  clientSigned?: boolean;
  ownerSigned?: boolean;
  termsAccepted?: boolean;
  signedAt?: string;
  ownerSignature?: string;
  clientSignature?: string;
  ownerSignedAt?: string;
  clientSignedAt?: string;
  agencyStampUrl?: string;
  agencyName?: string;
  pdfUrl?: string;
  agencyAddress?: string;
  agencyPhone?: string;
  agencyEmail?: string;
  agencyLogo?: string;
  terms?: string[];
  deposit?: {
    depositType?: string;
    amount?: number;
    currency?: string;
    reference?: string;
    status?: string;
    conditionsText?: string;
  };
}

export default function PublicContract() {
  const { contractId, token: qrToken } = useParams<{ contractId?: string; token?: string }>();
  const { t } = useTranslation();

  const [contract, setContract] = useState<PublicContractData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [termsChecked, setTermsChecked] = useState(false);
  const [depositAcknowledged, setDepositAcknowledged] = useState(false);
  const [isSigned, setIsSigned] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  // Build the appropriate API path based on available params (relative to the
  // shared axios instance's baseURL — never concatenate a full URL by hand).
  const getContractPath = () => {
    if (contractId && qrToken) {
      return `/public/contracts/${contractId}/${qrToken}`;
    }
    return `/public/contracts/${qrToken}`;
  };

  const getSignPath = () => {
    if (contractId && qrToken) {
      return `/public/contracts/${contractId}/${qrToken}/sign`;
    }
    return `/public/contracts/${qrToken}/sign`;
  };

  useEffect(() => {
    if (!qrToken) {
      setError('Invalid contract link');
      setLoading(false);
      return;
    }
    fetchContract();
  }, [qrToken, contractId]);

  const fetchContract = async () => {
    const path = getContractPath();
    try {
      const { data } = await api.get(path);
      setContract(data);
      setIsSigned(data.clientSigned || false);
      setTermsChecked(data.termsAccepted || false);
    } catch (err: any) {
      const status = err?.response?.status;
      const detail = err?.response?.data?.error || err?.response?.data?.message || '';
      console.error('[PublicContract] fetch failed:', path, err);
      setError(
        status
          ? `Server error (HTTP ${status}${detail ? ': ' + detail : ''}). Please try again or contact support.`
          : 'This contract link is invalid or has expired.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleSignatureSave = async (signatureData: string) => {
    if (!contract || !termsChecked) return;
    setIsSubmitting(true);

    try {
      const { data } = await api.post(getSignPath(), {
        signatureData,
        signerType: 'CLIENT',
        termsAccepted: true,
        ipAddress: '',
        userAgent: navigator.userAgent,
      });
      setContract(data);
      setIsSigned(true);
      setShowSuccess(true);
    } catch (err) {
      setError('Failed to save your signature. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const downloadSignedPdf = async () => {
    if (!contract?.pdfUrl) return;
    setIsSubmitting(true);
    try {
      const res = await fetch(`${API_ORIGIN}${contract.pdfUrl}`);
      if (!res.ok) throw new Error('Unable to download PDF');
      const blob = await res.blob();
      if (!blob || blob.size === 0) throw new Error('PDF file is empty');
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `contract-${(contract.contractNumber || 'contract').replace(/[^a-zA-Z0-9._-]/g, '_')}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      setError('Unable to generate contract PDF.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 size={32} className="animate-spin text-brand-500 mx-auto" />
          <p className="text-sm text-slate-400">Loading contract...</p>
        </div>
      </div>
    );
  }

  if (error || !contract) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-danger-50 rounded-2xl flex items-center justify-center mx-auto">
            <AlertCircle size={28} className="text-danger-500" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">Invalid Link</h1>
          <p className="text-sm text-slate-400">{error}</p>
          <p className="text-xs text-slate-300">Please contact your rental agency for assistance.</p>
        </div>
      </div>
    );
  }

  if (showSuccess) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <div className="text-center space-y-6 max-w-sm animate-scale-in">
          <div className="relative w-24 h-24 mx-auto">
            <div className="absolute inset-0 bg-success-100 rounded-full animate-ping opacity-30" />
            <div className="relative w-24 h-24 bg-success-50 rounded-full flex items-center justify-center">
              <CheckCircle2 size={40} className="text-success-500" />
            </div>
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-bold text-[#1e293b]">Contract Signed!</h1>
            <p className="text-sm text-slate-400">
              Your signature has been securely recorded and synced to the agency dashboard.
            </p>
          </div>
          <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-100 space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">Contract</span>
              <span className="font-mono font-bold text-[#1e293b]">{contract.contractNumber}</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">Status</span>
              <span className="font-bold text-success-500">Signed</span>
            </div>
            {contract.ownerSigned && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-400">Final Status</span>
                <span className="font-bold text-success-500">Active</span>
              </div>
            )}
          </div>
          <p className="text-xs text-slate-300">
            You can now close this page. A confirmation email will be sent shortly.
          </p>
        </div>
      </div>
    );
  }

  const days = Math.ceil(
    (new Date(contract.endDate).getTime() - new Date(contract.startDate).getTime()) / (1000 * 60 * 60 * 24)
  );

  return (
    <div className="min-h-screen bg-slate-50 pb-8 md:pb-0 animate-fade">
      {/* Header */}
      <div className="bg-white border-b border-slate-100 sticky top-0 z-10">
        <div className="max-w-lg mx-auto px-4 py-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {contract.agencyLogo ? (
              <img src={resolveAsset(contract.agencyLogo)} alt="" className="w-8 h-8 rounded-lg object-contain" />
            ) : (
              <div className="w-8 h-8 bg-brand-100 rounded-lg flex items-center justify-center">
                <Building2 size={16} className="text-brand-500" />
              </div>
            )}
            <div className="min-w-0">
              <p className="text-sm font-bold text-[#1e293b]">{contract.agencyName || 'Agency'}</p>
              <p className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">Digital Contract</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${contract.ownerSigned ? 'bg-success-500' : 'bg-warning-500'} animate-pulse`} />
            <span className="text-xs font-medium text-slate-500">
              {contract.ownerSigned ? 'Active' : 'Pending'}
            </span>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-5">
        {/* Contract Number */}
        <div className="text-center space-y-1">
          <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">Contract Number</p>
          <p className="font-mono text-base sm:text-lg font-bold text-[#1e293b]">{contract.contractNumber}</p>
        </div>

        {/* Client Info Card */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <User size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('contracts.client') || 'Client'}</span>
          </div>
          <div>
            <p className="text-base sm:text-lg font-bold text-[#1e293b]">{contract.clientFullName || contract.clientName || 'Client'}</p>
            <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-slate-400">
              {contract.clientEmail && (
                <span className="flex items-center gap-1"><Mail size={11} /> {contract.clientEmail}</span>
              )}
              {contract.clientPhone && (
                <span className="flex items-center gap-1"><Phone size={11} /> {contract.clientPhone}</span>
              )}
              {contract.clientCin && (
                <span className="flex items-center gap-1"><User size={11} /> CIN: {contract.clientCin}</span>
              )}
              {contract.clientAddress && (
                <span className="flex items-center gap-1 w-full mt-1"><MapPin size={11} className="shrink-0"/> {contract.clientAddress}</span>
              )}
            </div>
          </div>
        </div>

        {/* Vehicle Info Card */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Car size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('contracts.vehicle') || 'Vehicle'}</span>
          </div>
          <div>
            <p className="text-base sm:text-lg font-bold text-[#1e293b]">{contract.vehicleBrand || contract.vehicleModel || 'Vehicle'}</p>
            <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-slate-400">
              {(contract.vehicleRegistration || contract.vehiclePlate) && (
                <span className="flex items-center gap-1"><Shield size={11} /> {contract.vehicleRegistration || contract.vehiclePlate}</span>
              )}
              {contract.vehicleCategory && (
                <span className="flex items-center gap-1"><Car size={11} /> {contract.vehicleCategory}</span>
              )}
            </div>
          </div>
        </div>

        {/* Rental Period Card */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Calendar size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('contracts.period') || 'Rental Period'}</span>
          </div>
          <div className="flex items-center justify-between">
            <div className="text-center">
              <p className="text-xs text-slate-400">Start</p>
              <p className="text-sm font-bold text-[#1e293b]">{new Date(contract.startDate).toLocaleDateString()}</p>
            </div>
            <div className="flex-1 flex items-center justify-center px-4">
              <div className="h-px bg-slate-200 flex-1" />
              <div className="px-3 py-1 bg-brand-50 rounded-full text-xs font-bold text-brand-500 whitespace-nowrap">
                {days} days
              </div>
              <div className="h-px bg-slate-200 flex-1" />
            </div>
            <div className="text-center">
              <p className="text-xs text-slate-400">End</p>
              <p className="text-sm font-bold text-[#1e293b]">{new Date(contract.endDate).toLocaleDateString()}</p>
            </div>
          </div>
        </div>

        {/* Pricing Card */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Landmark size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">Payment Summary</span>
          </div>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Rental Price ({days} days)</span>
              <span className="font-medium text-[#1e293b]">{(contract.dailyPrice || 0) * days} MAD</span>
            </div>
            {(contract.depositAmount || 0) > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Deposit</span>
                <span className="font-medium text-[#1e293b]">{contract.depositAmount} MAD</span>
              </div>
            )}
            {((contract.deliveryFees || 0) + (contract.returnFees || 0) + (contract.cleaningFees || 0) + (contract.lateFees || 0) + (contract.fuelCharges || 0)) > 0 && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Additional Fees</span>
                <span className="font-medium text-[#1e293b]">{((contract.deliveryFees || 0) + (contract.returnFees || 0) + (contract.cleaningFees || 0) + (contract.lateFees || 0) + (contract.fuelCharges || 0))} MAD</span>
              </div>
            )}
            {(contract.discountAmount || 0) > 0 && (
              <div className="flex justify-between text-sm text-success-600">
                <span>Discount</span>
                <span className="font-medium">-{contract.discountAmount} MAD</span>
              </div>
            )}
            <div className="h-px bg-slate-100 my-2" />
            <div className="flex justify-between text-base">
              <span className="font-bold text-slate-800">Total Amount</span>
              <span className="font-black text-brand-600">{contract.totalPrice || contract.totalAmount || 0} MAD</span>
            </div>
          </div>
        </div>

        {/* Agency Signature */}
        {contract.ownerSigned && contract.ownerSignature && (
          <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
            <div className="flex items-center gap-2 text-success-500">
              <CheckCircle2 size={14} />
              <span className="text-xs font-bold uppercase tracking-wider">Agency Signed</span>
            </div>
            <div className="flex items-center gap-3 p-3 bg-success-50 rounded-xl">
              <img src={contract.ownerSignature} alt="Agency Signature" className="h-16 bg-white rounded-lg border border-success-200 p-2" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-success-700">{contract.agencyName || 'Agency'}</p>
                <p className="text-xs text-success-500">
                  Signed on {contract.ownerSignedAt ? new Date(contract.ownerSignedAt).toLocaleString() : 'N/A'}
                </p>
              </div>
              {contract.agencyStampUrl && (
                <img
                  src={resolveAsset(contract.agencyStampUrl)}
                  alt="Agency Stamp"
                  className="h-16 w-16 object-contain rounded-lg border border-success-200 bg-white p-1 shrink-0"
                />
              )}
            </div>
            <p className="text-xs text-slate-400">
              This contract has already been signed by the agency. Please review and sign below to complete the agreement.
            </p>
          </div>
        )}

        {/* Security Deposit */}
        {contract.deposit && contract.deposit.amount && contract.deposit.amount > 0 && (
          <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
            <div className="flex items-center gap-2 text-brand-500">
              <Landmark size={14} />
              <span className="text-xs font-bold uppercase tracking-wider">Security Deposit</span>
            </div>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Type</span>
                <span className="font-medium text-[#1e293b]">{contract.deposit.depositType || 'Cash'}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Amount</span>
                <span className="font-bold text-brand-600">{contract.deposit.amount} {contract.deposit.currency || 'MAD'}</span>
              </div>
              {contract.deposit.reference && (
                <div className="flex justify-between text-sm">
                  <span className="text-slate-500">Reference</span>
                  <span className="font-medium text-[#1e293b]">{contract.deposit.reference}</span>
                </div>
              )}
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Status</span>
                <span className="font-medium text-[#1e293b]">{contract.deposit.status || 'Pending'}</span>
              </div>
            </div>
            <p className="text-xs text-slate-500 leading-relaxed bg-slate-50 p-3 rounded-xl">
              {contract.deposit.conditionsText || 'The deposit will be returned after inspection of the vehicle and validation of all contractual obligations.'}
            </p>
            {!isSigned && (
              <label className="flex items-start gap-3 p-3 bg-amber-50 rounded-xl cursor-pointer transition-all hover:bg-amber-100 border border-amber-200">
                <div className="relative mt-0.5">
                  <input
                    type="checkbox"
                    checked={depositAcknowledged}
                    onChange={(e) => setDepositAcknowledged(e.target.checked)}
                    className="peer sr-only"
                  />
                  <div className="w-5 h-5 border-2 border-amber-300 rounded-md peer-checked:bg-brand-500 peer-checked:border-brand-500 transition-all" />
                  {depositAcknowledged && (
                    <Check size={12} className="absolute inset-0 m-auto text-white pointer-events-none" />
                  )}
                </div>
                <span className="text-sm text-amber-800 font-medium">
                  I understand and accept the deposit conditions.
                </span>
              </label>
            )}
          </div>
        )}

        {/* Terms & Conditions */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Shield size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">Terms & Conditions</span>
          </div>
          <ul className="space-y-3">
            {(contract.terms || []).map((term, idx) => (
              <li key={idx} className="flex gap-3 text-sm text-slate-600 leading-relaxed">
                <span className="font-bold text-slate-300 shrink-0">{idx + 1}.</span>
                {term}
              </li>
            ))}
          </ul>

          {/* Terms Checkbox */}
          {!isSigned && (
            <label className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl cursor-pointer transition-all hover:bg-slate-100">
              <div className="relative mt-0.5">
                <input
                  type="checkbox"
                  checked={termsChecked}
                  onChange={(e) => setTermsChecked(e.target.checked)}
                  className="peer sr-only"
                />
                <div className="w-5 h-5 border-2 border-slate-300 rounded-md peer-checked:bg-brand-500 peer-checked:border-brand-500 transition-all" />
                {termsChecked && (
                  <Check size={12} className="absolute inset-0 m-auto text-white pointer-events-none" />
                )}
              </div>
              <span className="text-sm text-slate-600">
                I have read and agree to the terms and conditions above.
              </span>
            </label>
          )}
        </div>

        {/* Signature Section */}
        {!isSigned ? (
          <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
            <div className="flex items-center gap-2 text-brand-500">
              <Shield size={14} />
              <span className="text-xs font-bold uppercase tracking-wider">Your Signature</span>
            </div>

            {!termsChecked && (
              <div className="flex items-center gap-2 p-3 bg-warning-50 text-warning-600 rounded-xl text-xs">
                <AlertCircle size={14} />
                <span>Please accept the terms and conditions above before signing.</span>
              </div>
            )}

            {!depositAcknowledged && contract.deposit && contract.deposit.amount && contract.deposit.amount > 0 && (
              <div className="flex items-center gap-2 p-3 bg-warning-50 text-warning-600 rounded-xl text-xs">
                <AlertCircle size={14} />
                <span>Please acknowledge the security deposit conditions above before signing.</span>
              </div>
            )}
            <div className={termsChecked && (!contract.deposit || !contract.deposit.amount || depositAcknowledged) ? '' : 'opacity-50 pointer-events-none'}>
              <SignaturePad
                onSave={handleSignatureSave}
                label="Sign with your finger or stylus"
                penColor="#0f172a"
                autoSaveKey={`public_contract_${qrToken}`}
              />
            </div>

            {isSubmitting && (
              <div className="flex items-center justify-center gap-2 py-3 text-sm text-brand-500">
                <Loader2 size={16} className="animate-spin" />
                <span>Syncing your signature...</span>
              </div>
            )}
          </div>
        ) : (
          <div className="bg-success-50 rounded-2xl p-3 sm:p-5 border border-success-100 text-center space-y-4">
            <CheckCircle2 size={32} className="text-success-500 mx-auto" />
            <div>
              <p className="text-sm font-bold text-success-600">Contract Signed!</p>
              <p className="text-xs text-success-400 mt-1">
                You signed this contract on {contract.clientSignedAt ? new Date(contract.clientSignedAt).toLocaleString() : 'N/A'}
              </p>
            </div>
            {contract.clientSignature && (
              <div className="p-2 bg-white rounded-xl border border-success-200 mx-auto max-w-xs">
                <p className="text-[10px] font-bold text-slate-400 mb-1 uppercase">Your Signature</p>
                <img src={contract.clientSignature} alt="Your Signature" className="h-16 w-full object-contain" />
              </div>
            )}
            {contract.pdfUrl && (
              <button
                type="button"
                onClick={downloadSignedPdf}
                disabled={isSubmitting}
                className="inline-flex items-center gap-2 px-4 py-2.5 bg-white text-success-600 rounded-xl text-sm font-medium border border-success-200 hover:bg-success-100 transition-all"
              >
                <FileText size={16} />
                Download Signed Contract
              </button>
            )}
          </div>
        )}

        {/* Agency Footer */}
        <div className="text-center space-y-2 pt-4 border-t border-slate-200">
          <p className="text-xs font-bold text-slate-400">{contract.agencyName || 'Agency'}</p>
          <div className="flex flex-wrap justify-center gap-x-4 gap-y-1 text-[10px] text-slate-300">
            {contract.agencyAddress && (
              <span className="flex items-center gap-1"><MapPin size={9} /> {contract.agencyAddress}</span>
            )}
            {contract.agencyPhone && (
              <span className="flex items-center gap-1"><Phone size={9} /> {contract.agencyPhone}</span>
            )}
            {contract.agencyEmail && (
              <span className="flex items-center gap-1"><Mail size={9} /> {contract.agencyEmail}</span>
            )}
          </div>
          <p className="text-[10px] text-slate-300 mt-2">
            This document is digitally signed and timestamped.
          </p>
        </div>
      </div>
    </div>
  );
}
