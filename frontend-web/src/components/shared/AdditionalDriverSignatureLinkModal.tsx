import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, Check, MessageCircle, Mail, X, CheckCircle2 } from 'lucide-react';
import { useToast } from '../../context/ToastContext';
import { normalizePhoneForWhatsApp } from '../../lib/phone';

interface AdditionalDriverSignatureLinkModalProps {
  isOpen: boolean;
  onClose: () => void;
  signingUrl: string;
  expiresAt?: string | null;
  driverName: string;
  driverPhone?: string | null;
  driverEmail?: string | null;
  contractNumber: string;
  agencyName?: string | null;
}

/**
 * Shows the freshly-generated (or resent) additional-driver signing link once,
 * with copy / WhatsApp / email share actions — modeled on
 * SendClientInfoRequestModal's channel pattern. The raw link only ever lives
 * here in memory for the duration of this modal; it is never persisted or
 * logged, matching the backend's hashed-token security design.
 */
export default function AdditionalDriverSignatureLinkModal({
  isOpen, onClose, signingUrl, expiresAt, driverName, driverPhone, driverEmail, contractNumber, agencyName,
}: AdditionalDriverSignatureLinkModalProps) {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();
  const [copied, setCopied] = useState(false);
  const isRtl = i18n.dir() === 'rtl';

  if (!isOpen) return null;

  const buildMessage = () => t('contracts.driverSignature.whatsappMessage', {
    driverName,
    contractNumber,
    agencyName: agencyName || '',
    secureSigningUrl: signingUrl,
  });

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(signingUrl);
      setCopied(true);
      showToast(t('contracts.driverSignature.linkCopied'), 'success');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast(t('contracts.driverSignature.copyFailed'), 'error');
    }
  };

  const handleWhatsApp = () => {
    const digits = normalizePhoneForWhatsApp(driverPhone || '');
    const text = encodeURIComponent(buildMessage());
    window.open(`https://wa.me/${digits}?text=${text}`, '_blank', 'noopener,noreferrer');
  };

  const handleEmail = () => {
    const subject = encodeURIComponent(t('contracts.driverSignature.emailSubject', { contractNumber }));
    const body = encodeURIComponent(buildMessage());
    window.open(`mailto:${driverEmail || ''}?subject=${subject}&body=${body}`, '_blank');
  };

  return (
    <div className="fixed inset-0 z-[var(--z-modal-overlay)] flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div dir={isRtl ? 'rtl' : 'ltr'} className="relative flex w-full max-h-[100dvh] flex-col overflow-hidden rounded-t-3xl bg-white shadow-2xl animate-scale-in sm:max-h-[90dvh] sm:max-w-md sm:rounded-3xl">
        <div className="shrink-0 flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-lg font-bold text-[#1e293b]">{t('contracts.driverSignature.modalTitle')}</h3>
            <p className="text-xs text-slate-400 mt-0.5">{contractNumber} • {driverName}</p>
          </div>
          <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-6 space-y-5">
          <div className="flex items-center gap-2 text-success-600 font-semibold text-sm">
            <CheckCircle2 size={18} /> {t('contracts.driverSignature.linkGenerated')}
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              {t('contracts.driverSignature.signingUrl')}
            </label>
            <div className="flex items-center gap-2">
              <div className="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600 truncate">
                {signingUrl}
              </div>
              <button
                onClick={handleCopy}
                className={`p-3 rounded-xl transition-all ${copied ? 'bg-success-50 text-success-500' : 'bg-brand-50 text-brand-500 hover:bg-brand-100'}`}
              >
                {copied ? <Check size={18} /> : <Copy size={18} />}
              </button>
            </div>
          </div>

          {expiresAt && (
            <p className="text-xs text-slate-500">
              {t('contracts.driverSignature.expiresAt', { date: new Date(expiresAt).toLocaleString(i18n.language) })}
            </p>
          )}

          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={handleWhatsApp}
              className="flex flex-col items-center gap-2 p-3 bg-emerald-50 text-emerald-600 rounded-xl hover:bg-emerald-100 transition-all active:scale-95"
            >
              <MessageCircle size={22} />
              <span className="text-xs font-medium">{t('contracts.driverSignature.whatsapp')}</span>
            </button>
            <button
              onClick={handleEmail}
              className="flex flex-col items-center gap-2 p-3 bg-amber-50 text-amber-600 rounded-xl hover:bg-amber-100 transition-all active:scale-95"
            >
              <Mail size={22} />
              <span className="text-xs font-medium">{t('contracts.driverSignature.email')}</span>
            </button>
          </div>

          <button
            onClick={onClose}
            className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all"
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}
