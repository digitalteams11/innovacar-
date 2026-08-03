import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Calendar, User, Car, Shield, CheckCircle2, Loader2, Building2,
  AlertCircle, Check, XCircle, Ban,
} from 'lucide-react';

import { API_ORIGIN } from '../lib/api';
import { PUBLIC_APP_URL } from '../lib/publicUrl';
import SignaturePad from '../components/shared/SignaturePad';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';
import {
  getPublicAdditionalDriverSigning,
  markPublicAdditionalDriverOpened,
  submitPublicAdditionalDriverSignature,
  declinePublicAdditionalDriverSignature,
  type PublicAdditionalDriverSigningResponse,
  type PublicSigningErrorBody,
  type AdditionalDriverDeclarationsAccepted,
} from '../api/additionalDrivers';

const resolveAsset = (url?: string | null) => {
  if (!url) return url ?? undefined;
  if (url.startsWith('http') || url.startsWith('data:')) return url;
  return `${API_ORIGIN}${url.startsWith('/') ? url : `/${url}`}`;
};

type ErrorState = 'INVALID' | 'EXPIRED' | 'REVOKED' | 'ALREADY_SIGNED' | 'CANCELLED' | 'NETWORK' | 'GENERIC' | null;

const DECLARATION_KEYS: (keyof AdditionalDriverDeclarationsAccepted)[] = [
  'identityCorrect', 'licenseValid', 'responsibilityAccepted', 'termsRead', 'dataConsent',
];

export default function PublicAdditionalDriverSign() {
  const { token } = useParams<{ token: string }>();
  const { t } = useTranslation();

  const [data, setData] = useState<PublicAdditionalDriverSigningResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorState, setErrorState] = useState<ErrorState>(null);
  const [checks, setChecks] = useState<AdditionalDriverDeclarationsAccepted>({
    identityCorrect: false, licenseValid: false, responsibilityAccepted: false, termsRead: false, dataConsent: false,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSigned, setIsSigned] = useState(false);
  const [showDeclineConfirm, setShowDeclineConfirm] = useState(false);
  const [declineReason, setDeclineReason] = useState('');
  const [isDeclined, setIsDeclined] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setErrorState('INVALID');
      setLoading(false);
      return;
    }
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const load = async () => {
    if (!token) return;
    try {
      const { data: resp } = await getPublicAdditionalDriverSigning(token);
      setData(resp);
      setIsSigned(resp.signatureStatus === 'SIGNED');
      setIsDeclined(resp.signatureStatus === 'DECLINED');
      if (!resp.contractCancelled && resp.signatureStatus !== 'SIGNED' && resp.signatureStatus !== 'DECLINED') {
        try {
          await markPublicAdditionalDriverOpened(token);
        } catch {
          // Non-fatal — opening notification is best-effort, the page still works.
        }
      }
    } catch (err: any) {
      const body: PublicSigningErrorBody | undefined = err?.response?.data;
      const status = err?.response?.status;
      if (!err?.response) {
        setErrorState('NETWORK');
      } else if (body?.code === 'EXPIRED') {
        setErrorState('EXPIRED');
      } else if (body?.code === 'REVOKED') {
        setErrorState('REVOKED');
      } else if (body?.code === 'ALREADY_SIGNED') {
        setErrorState('ALREADY_SIGNED');
      } else if (body?.code === 'CANCELLED') {
        setErrorState('CANCELLED');
      } else if (status === 404 || body?.code === 'INVALID') {
        setErrorState('INVALID');
      } else {
        setErrorState('GENERIC');
      }
    } finally {
      setLoading(false);
    }
  };

  const allChecked = DECLARATION_KEYS.every((k) => checks[k]);

  const handleSignatureSave = async (signatureData: string) => {
    if (!token || !allChecked) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await submitPublicAdditionalDriverSignature(token, {
        declarationsAccepted: checks,
        signatureData,
        deviceInfo: navigator.userAgent,
      });
      setIsSigned(true);
    } catch (err: any) {
      const body: PublicSigningErrorBody | undefined = err?.response?.data;
      if (body?.code === 'ALREADY_SIGNED') {
        setIsSigned(true);
      } else {
        setSubmitError(t('publicAdditionalDriverSign.errors.submitFailed'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDecline = async () => {
    if (!token) return;
    setIsSubmitting(true);
    try {
      await declinePublicAdditionalDriverSignature(token, { reason: declineReason.trim() || undefined });
      setIsDeclined(true);
      setShowDeclineConfirm(false);
    } catch {
      setSubmitError(t('publicAdditionalDriverSign.errors.declineFailed'));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 size={32} className="animate-spin text-brand-500 mx-auto" />
          <p className="text-sm text-slate-400">{t('publicAdditionalDriverSign.loading')}</p>
        </div>
      </div>
    );
  }

  if (errorState) {
    const titleKey = `publicAdditionalDriverSign.errorTitles.${errorState}`;
    const bodyKey = `publicAdditionalDriverSign.errors.${errorState}`;
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead
          title={t('publicAdditionalDriverSign.seo.title')}
          description={t('publicAdditionalDriverSign.seo.description')}
          canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`}
          robots={ROBOTS_PRIVATE}
        />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-danger-50 rounded-2xl flex items-center justify-center mx-auto">
            <AlertCircle size={28} className="text-danger-500" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t(titleKey)}</h1>
          <p className="text-sm text-slate-400">{t(bodyKey)}</p>
          <p className="text-xs text-slate-300">{t('publicAdditionalDriverSign.contactAgencyForAssistance')}</p>
        </div>
      </div>
    );
  }

  if (!data) return null;

  if (data.contractCancelled) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead title={t('publicAdditionalDriverSign.seo.title')} description={t('publicAdditionalDriverSign.seo.description')} canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-slate-100 rounded-2xl flex items-center justify-center mx-auto">
            <Ban size={28} className="text-slate-400" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('publicAdditionalDriverSign.errorTitles.CANCELLED')}</h1>
          <p className="text-sm text-slate-400">{t('publicAdditionalDriverSign.errors.CANCELLED')}</p>
        </div>
      </div>
    );
  }

  if (isDeclined) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead title={t('publicAdditionalDriverSign.seo.title')} description={t('publicAdditionalDriverSign.seo.description')} canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-slate-100 rounded-2xl flex items-center justify-center mx-auto">
            <XCircle size={28} className="text-slate-400" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('publicAdditionalDriverSign.declinedTitle')}</h1>
          <p className="text-sm text-slate-400">{t('publicAdditionalDriverSign.declinedBody')}</p>
        </div>
      </div>
    );
  }

  if (isSigned) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead title={t('publicAdditionalDriverSign.seo.title')} description={t('publicAdditionalDriverSign.seo.description')} canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-6 max-w-sm animate-scale-in">
          <div className="relative w-24 h-24 mx-auto">
            <div className="absolute inset-0 bg-success-100 rounded-full animate-ping opacity-30" />
            <div className="relative w-24 h-24 bg-success-50 rounded-full flex items-center justify-center">
              <CheckCircle2 size={40} className="text-success-500" />
            </div>
          </div>
          <div className="space-y-2">
            <h1 className="text-2xl font-bold text-[#1e293b]">{t('publicAdditionalDriverSign.successTitle')}</h1>
            <p className="text-sm text-slate-400">{t('publicAdditionalDriverSign.successBody')}</p>
          </div>
          <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-100 space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">{t('contracts.contractNumber')}</span>
              <span className="font-mono font-bold text-[#1e293b]">{data.contractNumber}</span>
            </div>
          </div>
          <p className="text-xs text-slate-300">{t('publicAdditionalDriverSign.canCloseNowNote')}</p>
        </div>
      </div>
    );
  }

  const days = data.rentalStartDate && data.rentalEndDate
    ? Math.max(1, Math.ceil((new Date(data.rentalEndDate).getTime() - new Date(data.rentalStartDate).getTime()) / (1000 * 60 * 60 * 24)))
    : null;

  return (
    <div className="min-h-screen bg-slate-50 pb-32 sm:pb-8 animate-fade">
      <SeoHead
        title={t('publicAdditionalDriverSign.seo.title')}
        description={t('publicAdditionalDriverSign.seo.description')}
        canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`}
        robots={ROBOTS_PRIVATE}
      />
      {/* Header */}
      <div className="bg-white border-b border-slate-100 sticky top-0 z-10">
        <div className="max-w-lg mx-auto px-4 py-3 flex items-center gap-3">
          {data.agencyLogo ? (
            <img src={resolveAsset(data.agencyLogo)} alt="" className="w-8 h-8 rounded-lg object-contain" />
          ) : (
            <div className="w-8 h-8 bg-brand-100 rounded-lg flex items-center justify-center">
              <Building2 size={16} className="text-brand-500" />
            </div>
          )}
          <div className="min-w-0">
            <p className="text-sm font-bold text-[#1e293b]">{data.agencyName || t('publicAdditionalDriverSign.agencyFallback')}</p>
            <p className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">{t('publicAdditionalDriverSign.pageLabel')}</p>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-5">
        {/* Contract Number */}
        <div className="text-center space-y-1">
          <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{t('contracts.contractNumber')}</p>
          <p className="font-mono text-base sm:text-lg font-bold text-[#1e293b]">{data.contractNumber}</p>
        </div>

        {/* Driver / role card */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-3">
          <div className="flex items-center gap-2 text-brand-500">
            <User size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('publicAdditionalDriverSign.roleLabel')}</span>
          </div>
          <p className="text-base sm:text-lg font-bold text-[#1e293b]">{data.driverName}</p>
          <p className="text-sm text-slate-500">{t('publicAdditionalDriverSign.roleExplanation')}</p>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-400">
            {data.maskedLicense && (
              <span className="flex items-center gap-1"><Shield size={11} /> {t('publicAdditionalDriverSign.maskedLicenseLabel')}: {data.maskedLicense}</span>
            )}
            {data.mainClientName && (
              <span className="flex items-center gap-1"><User size={11} /> {t('publicAdditionalDriverSign.mainClientLabel')}: {data.mainClientName}</span>
            )}
          </div>
        </div>

        {/* Vehicle / dates */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Car size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('contracts.vehicle')}</span>
          </div>
          <p className="text-sm font-medium text-[#1e293b]">{data.vehicleSummary || t('publicAdditionalDriverSign.vehicleFallback')}</p>
          {data.rentalStartDate && data.rentalEndDate && (
            <div className="flex items-center justify-between">
              <div className="text-center">
                <p className="text-xs text-slate-400">{t('publicAdditionalDriverSign.start')}</p>
                <p className="text-sm font-bold text-[#1e293b]">{new Date(data.rentalStartDate).toLocaleDateString()}</p>
              </div>
              <div className="flex-1 flex items-center justify-center px-4">
                <div className="h-px bg-slate-200 flex-1" />
                <div className="px-3 py-1 bg-brand-50 rounded-full text-xs font-bold text-brand-500 whitespace-nowrap flex items-center gap-1">
                  <Calendar size={11} />{days !== null ? t('publicAdditionalDriverSign.daysCount', { count: days }) : ''}
                </div>
                <div className="h-px bg-slate-200 flex-1" />
              </div>
              <div className="text-center">
                <p className="text-xs text-slate-400">{t('publicAdditionalDriverSign.end')}</p>
                <p className="text-sm font-bold text-[#1e293b]">{new Date(data.rentalEndDate).toLocaleDateString()}</p>
              </div>
            </div>
          )}
        </div>

        {/* Declarations */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-3">
          <div className="flex items-center gap-2 text-brand-500">
            <Shield size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('publicAdditionalDriverSign.declarationsTitle')}</span>
          </div>
          <div className="space-y-2">
            {DECLARATION_KEYS.map((key) => (
              <label key={key} className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl cursor-pointer transition-all hover:bg-slate-100">
                <div className="relative mt-0.5 shrink-0">
                  <input
                    type="checkbox"
                    checked={checks[key]}
                    onChange={(e) => setChecks((prev) => ({ ...prev, [key]: e.target.checked }))}
                    className="peer sr-only"
                  />
                  <div className="w-5 h-5 border-2 border-slate-300 rounded-md peer-checked:bg-brand-500 peer-checked:border-brand-500 transition-all" />
                  {checks[key] && <Check size={12} className="absolute inset-0 m-auto text-white pointer-events-none" />}
                </div>
                <span className="text-sm text-slate-600">{t(`publicAdditionalDriverSign.declarations.${key}`)}</span>
              </label>
            ))}
          </div>
        </div>

        {/* Signature */}
        <div className="bg-white rounded-2xl p-3 sm:p-5 shadow-sm border border-slate-100 space-y-4">
          <div className="flex items-center gap-2 text-brand-500">
            <Shield size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">{t('publicAdditionalDriverSign.yourSignature')}</span>
          </div>
          {!allChecked && (
            <div className="flex items-center gap-2 p-3 bg-warning-50 text-warning-600 rounded-xl text-xs">
              <AlertCircle size={14} />
              <span>{t('publicAdditionalDriverSign.acceptAllBeforeSigning')}</span>
            </div>
          )}
          <div className={allChecked ? '' : 'opacity-50 pointer-events-none'}>
            <SignaturePad
              onSave={handleSignatureSave}
              label={t('publicAdditionalDriverSign.signWithFingerOrStylus')}
              penColor="#0f172a"
              disabled={isSubmitting}
              autoSaveKey={`additional-driver-${token}`}
            />
          </div>
          {isSubmitting && (
            <div className="flex items-center justify-center gap-2 py-2 text-sm text-brand-500">
              <Loader2 size={16} className="animate-spin" />
              <span>{t('publicAdditionalDriverSign.syncingSignature')}</span>
            </div>
          )}
          {submitError && (
            <div className="flex items-center gap-2 p-3 bg-danger-50 text-danger-600 rounded-xl text-xs">
              <AlertCircle size={14} className="shrink-0" />
              <span>{submitError}</span>
            </div>
          )}
        </div>

        {/* Decline */}
        <div className="text-center">
          <button
            type="button"
            onClick={() => setShowDeclineConfirm(true)}
            className="text-xs text-slate-400 underline hover:text-danger-500"
          >
            {t('publicAdditionalDriverSign.cannotSignLink')}
          </button>
        </div>
      </div>

      {showDeclineConfirm && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm bg-white rounded-2xl p-5 space-y-4 shadow-2xl">
            <h2 className="text-base font-bold text-[#1e293b]">{t('publicAdditionalDriverSign.declineConfirmTitle')}</h2>
            <p className="text-sm text-slate-500">{t('publicAdditionalDriverSign.declineConfirmBody')}</p>
            <textarea
              value={declineReason}
              onChange={(e) => setDeclineReason(e.target.value)}
              placeholder={t('publicAdditionalDriverSign.declineReasonPlaceholder')}
              rows={3}
              className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm resize-none"
            />
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setShowDeclineConfirm(false)}
                disabled={isSubmitting}
                className="flex-1 py-2.5 rounded-xl text-sm font-medium border border-slate-200 text-slate-600"
              >
                {t('common.cancel')}
              </button>
              <button
                type="button"
                onClick={handleDecline}
                disabled={isSubmitting}
                className="flex-1 py-2.5 rounded-xl text-sm font-medium bg-danger-500 text-white hover:bg-danger-600 disabled:opacity-50"
              >
                {isSubmitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : t('publicAdditionalDriverSign.declineConfirmButton')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
