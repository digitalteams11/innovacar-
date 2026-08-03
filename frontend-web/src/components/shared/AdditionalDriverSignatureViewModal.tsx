import { useTranslation } from 'react-i18next';
import { X, CheckCircle2, XCircle } from 'lucide-react';
import type { AdditionalDriverSignatureView } from '../../api/additionalDrivers';

interface AdditionalDriverSignatureViewModalProps {
  isOpen: boolean;
  onClose: () => void;
  data: AdditionalDriverSignatureView | null;
  loading?: boolean;
}

const DECLARATION_KEYS = ['identityCorrect', 'licenseValid', 'responsibilityAccepted', 'termsRead', 'dataConsent'] as const;

/** Admin "View signature / audit" modal — the one place the full signature
 * image is shown (AdditionalDriverSignatureView is never included in list views). */
export default function AdditionalDriverSignatureViewModal({ isOpen, onClose, data, loading }: AdditionalDriverSignatureViewModalProps) {
  const { t, i18n } = useTranslation();
  const isRtl = i18n.dir() === 'rtl';

  if (!isOpen) return null;

  let acceptedMap: Record<string, boolean> = {};
  if (data?.declarationsAccepted) {
    try {
      acceptedMap = JSON.parse(data.declarationsAccepted);
    } catch {
      acceptedMap = {};
    }
  }

  return (
    <div className="fixed inset-0 z-[var(--z-modal-overlay)] flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div dir={isRtl ? 'rtl' : 'ltr'} className="relative flex w-full max-h-[100dvh] flex-col overflow-hidden rounded-t-3xl bg-white shadow-2xl animate-scale-in sm:max-h-[90dvh] sm:max-w-md sm:rounded-3xl">
        <div className="shrink-0 flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <h3 className="text-lg font-bold text-[#1e293b]">{t('contracts.driverSignature.viewSignature')}</h3>
          <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-6 space-y-4">
          {loading || !data ? (
            <p className="text-sm text-slate-400 text-center py-8">{t('common.loading')}</p>
          ) : data.signatureStatus === 'DECLINED' ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-danger-600 font-semibold text-sm">
                <XCircle size={18} /> {t('contracts.driverSignature.status.DECLINED')}
              </div>
              <div className="p-3 bg-slate-50 rounded-xl">
                <p className="text-xs font-semibold text-slate-500 mb-1">{t('contracts.driverSignature.declineReasonLabel')}</p>
                <p className="text-sm text-slate-700">{data.declineReason || t('contracts.driverSignature.noDeclineReason')}</p>
              </div>
              {data.declinedAt && (
                <p className="text-xs text-slate-400">{new Date(data.declinedAt).toLocaleString(i18n.language)}</p>
              )}
            </div>
          ) : (
            <>
              {data.signatureData && (
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200">
                  <img src={data.signatureData} alt={t('contracts.driverSignature.signatureImageAlt')} className="h-24 w-full object-contain bg-white rounded-lg" />
                </div>
              )}
              <div className="grid grid-cols-1 gap-2 text-sm">
                {data.signedAt && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">{t('contracts.driverSignature.signedAt')}</span>
                    <span className="font-medium text-[#1e293b]">{new Date(data.signedAt).toLocaleString(i18n.language)}</span>
                  </div>
                )}
                {data.signedIp && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">{t('contracts.driverSignature.auditIp')}</span>
                    <span className="font-mono text-xs text-[#1e293b]">{data.signedIp}</span>
                  </div>
                )}
                {data.declarationVersion && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">{t('contracts.driverSignature.auditDeclarationVersion')}</span>
                    <span className="font-medium text-[#1e293b]">{data.declarationVersion}</span>
                  </div>
                )}
              </div>
              {Object.keys(acceptedMap).length > 0 && (
                <div className="space-y-1.5 pt-2 border-t border-slate-100">
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{t('contracts.driverSignature.auditDeclarationsAccepted')}</p>
                  {DECLARATION_KEYS.filter((k) => k in acceptedMap).map((key) => (
                    <div key={key} className="flex items-center gap-2 text-sm">
                      {acceptedMap[key] ? <CheckCircle2 size={14} className="text-success-500 shrink-0" /> : <XCircle size={14} className="text-danger-400 shrink-0" />}
                      <span className="text-slate-600">{t(`contracts.driverSignature.declarationLabels.${key}`)}</span>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
