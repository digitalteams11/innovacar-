import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, ExternalLink, Loader2, Mail, MessageCircle, Send, CheckCircle2, XCircle, AlertTriangle, RotateCw } from 'lucide-react';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { isValidMoroccanWhatsAppPhone, normalizePhoneForWhatsApp } from '../../lib/phone';
import Modal from '../Modal';

interface SendClientInfoRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Optional — links the request to an in-progress contract draft. */
  contractId?: number;
  /** Optional — prefills from a known client and validates tenant ownership server-side. */
  clientId?: number;
  initialName?: string;
  initialPhone?: string;
  initialEmail?: string;
}

type ChannelStatus = { attempted: boolean; sent: boolean; message?: string; status?: string };
type DeliveryResponse = {
  id: number;
  secureLink?: string;
  publicUrl?: string;
  expiresAt?: string;
  emailResult?: ChannelStatus;
  whatsappResult?: ChannelStatus;
};

/** The "Send to client" half of the client self-fill workflow — generates a secure
 * link and automatically delivers it by email and/or WhatsApp, replacing the old
 * copy-only flow. Manual copy remains available only as a fallback. */
export default function SendClientInfoRequestModal({
  isOpen, onClose, contractId, clientId, initialName, initialPhone, initialEmail,
}: SendClientInfoRequestModalProps) {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();
  const { tenant } = useAuth();
  const isRtl = i18n.dir() === 'rtl';

  const [step, setStep] = useState<1 | 2>(1);
  const [temporaryName, setTemporaryName] = useState(initialName || '');
  const [phone, setPhone] = useState(initialPhone || '');
  const [email, setEmail] = useState(initialEmail || '');
  const [emailChannel, setEmailChannel] = useState(true);
  const [whatsappChannel, setWhatsappChannel] = useState(true);
  const [errors, setErrors] = useState<{ name?: string; phone?: string; email?: string }>({});
  const [submitting, setSubmitting] = useState(false);
  const [retrying, setRetrying] = useState<'EMAIL' | 'WHATSAPP' | null>(null);
  const [result, setResult] = useState<DeliveryResponse | null>(null);

  const reset = () => {
    setStep(1);
    setTemporaryName(initialName || ''); setPhone(initialPhone || ''); setEmail(initialEmail || '');
    setEmailChannel(true); setWhatsappChannel(true);
    setErrors({}); setResult(null);
  };

  const handleClose = () => { reset(); onClose(); };

  const emailAvailable = email.trim().length > 0;
  const phoneAvailable = phone.trim().length > 0;

  const goToChannels = () => {
    const nextErrors: typeof errors = {};
    if (!temporaryName.trim()) nextErrors.name = t('clientInfoAdmin.form.nameRequired', 'Client name is required.');
    if (!phone.trim() && !email.trim()) {
      nextErrors.phone = t('clientInfoAdmin.form.destinationRequired', 'Enter at least a phone number or an email address.');
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setEmailChannel(emailAvailable);
    setWhatsappChannel(phoneAvailable);
    setStep(2);
  };

  const send = async () => {
    const channels: string[] = [];
    if (emailChannel && emailAvailable) channels.push('EMAIL');
    if (whatsappChannel && phoneAvailable) channels.push('WHATSAPP');
    if (channels.length === 0) {
      showToast(t('clientInfoAdmin.form.selectChannel', 'Select at least one delivery channel.'), 'warning');
      return;
    }
    setSubmitting(true);
    try {
      const { data } = await api.post('/client-information-requests', {
        clientId,
        temporaryName: temporaryName.trim(),
        phone: phone.trim() || undefined,
        email: email.trim() || undefined,
        preferredLanguage: i18n.language,
        contractId,
        deliveryChannels: channels,
      });
      setResult(data);
    } catch (err: any) {
      showToast(translateErrorCode(err?.response?.data?.code, t) || err?.userMessage || t('clientInfoAdmin.actionFailed', 'Action failed. Please try again.'), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const retryChannel = async (channel: 'EMAIL' | 'WHATSAPP') => {
    if (!result?.id) return;
    setRetrying(channel);
    try {
      const { data } = await api.post(`/client-information-requests/${result.id}/resend`, { channels: [channel] });
      setResult((prev) => prev ? {
        ...prev,
        secureLink: data.secureLink ?? prev.secureLink,
        publicUrl: data.publicUrl ?? prev.publicUrl,
        expiresAt: data.expiresAt ?? prev.expiresAt,
        emailResult: channel === 'EMAIL' ? data.emailResult : prev.emailResult,
        whatsappResult: channel === 'WHATSAPP' ? data.whatsappResult : prev.whatsappResult,
      } : data);
      const channelResult = channel === 'EMAIL' ? data.emailResult : data.whatsappResult;
      if (channelResult?.sent) {
        showToast(t('clientInfoAdmin.result.retrySuccess', 'Message sent successfully.'), 'success');
      } else {
        showToast(t('clientInfoAdmin.result.retryFailed', 'Still could not deliver. Please try again later.'), 'warning');
      }
    } catch (err: any) {
      showToast(err?.userMessage || t('clientInfoAdmin.actionFailed', 'Action failed. Please try again.'), 'error');
    } finally {
      setRetrying(null);
    }
  };

  const copyLink = () => {
    const link = result?.secureLink || result?.publicUrl;
    if (!link) return;
    navigator.clipboard?.writeText(link);
    showToast(t('clientInfoAdmin.linkCopied', 'Link copied to clipboard.'), 'success');
  };

  const openLink = () => {
    const link = result?.secureLink || result?.publicUrl;
    if (link) window.open(link, '_blank', 'noopener,noreferrer');
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={t('clientInfoAdmin.sendToClient', 'Send form to client')} maxWidth="max-w-md">
      <div dir={isRtl ? 'rtl' : 'ltr'}>
        {result ? (
          <DeliveryResultCard
            result={result}
            clientName={temporaryName}
            clientPhone={phone}
            agencyName={tenant?.name}
            language={i18n.language}
            onCopy={copyLink}
            onOpen={openLink}
            onRetry={retryChannel}
            retrying={retrying}
            onClose={handleClose}
          />
        ) : step === 1 ? (
          <div className="space-y-4">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              {t('clientInfoAdmin.form.intro', 'Enter the minimum information needed to contact the client. They will fill in the rest themselves.')}
            </p>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] dark:text-slate-200 mb-2">
                {t('clientInfoAdmin.form.name', 'Client name')} *
              </label>
              <input
                className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b] dark:text-white"
                value={temporaryName}
                onChange={(e) => setTemporaryName(e.target.value)}
              />
              {errors.name && <p className="mt-1 text-xs text-danger-500">{errors.name}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] dark:text-slate-200 mb-2">
                {t('clientInfoAdmin.form.phone', 'Phone number')}
              </label>
              <input
                className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b] dark:text-white"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="06XXXXXXXX"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1e293b] dark:text-slate-200 mb-2">
                {t('clientInfoAdmin.form.email', 'Email')}
              </label>
              <input
                type="email"
                className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b] dark:text-white"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              {errors.phone && <p className="mt-1 text-xs text-danger-500">{errors.phone}</p>}
            </div>
            <button
              onClick={goToChannels}
              className="w-full flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all"
            >
              {t('common.next', 'Next')}
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              {t('clientInfoAdmin.form.channelsIntro', 'Choose how to send the secure link to the client.')}
            </p>
            <label className={`flex items-center gap-3 p-3 rounded-xl border ${emailAvailable ? 'border-[var(--border-subtle)] cursor-pointer' : 'border-[var(--border-subtle)] opacity-50'}`}>
              <input
                type="checkbox"
                disabled={!emailAvailable}
                checked={emailChannel && emailAvailable}
                onChange={(e) => setEmailChannel(e.target.checked)}
                className="w-4 h-4"
              />
              <Mail size={16} className="text-slate-400 shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-[#1e293b] dark:text-white truncate">{email || t('clientInfoAdmin.form.email', 'Email')}</p>
                {!emailAvailable && <p className="text-xs text-slate-400">{t('clientInfoAdmin.form.emailUnavailable', 'Email not available')}</p>}
              </div>
            </label>
            <label className={`flex items-center gap-3 p-3 rounded-xl border ${phoneAvailable ? 'border-[var(--border-subtle)] cursor-pointer' : 'border-[var(--border-subtle)] opacity-50'}`}>
              <input
                type="checkbox"
                disabled={!phoneAvailable}
                checked={whatsappChannel && phoneAvailable}
                onChange={(e) => setWhatsappChannel(e.target.checked)}
                className="w-4 h-4"
              />
              <MessageCircle size={16} className="text-slate-400 shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-[#1e293b] dark:text-white truncate">{phone || t('clientInfoAdmin.form.whatsapp', 'WhatsApp')}</p>
                {!phoneAvailable && <p className="text-xs text-slate-400">{t('clientInfoAdmin.form.phoneUnavailable', 'Phone number not available')}</p>}
              </div>
            </label>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setStep(1)}
                disabled={submitting}
                className="flex-1 py-2.5 rounded-xl font-medium text-sm border border-[var(--border-subtle)] text-[#1e293b] dark:text-white"
              >
                {t('common.back', 'Back')}
              </button>
              <button
                onClick={send}
                disabled={submitting || (!emailChannel && !whatsappChannel)}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all disabled:opacity-50"
              >
                {submitting ? (
                  <>
                    <Loader2 size={16} className="animate-spin" />
                    {t('clientInfoAdmin.form.sending', 'Generating secure link and sending…')}
                  </>
                ) : (
                  <>
                    <Send size={16} />
                    {t('clientInfoAdmin.generateAndSend', 'Generate and send')}
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}

function ChannelRow({
  icon, label, result, channel, onRetry, retrying,
}: {
  icon: React.ReactNode; label: string; result?: ChannelStatus; channel: 'EMAIL' | 'WHATSAPP';
  onRetry: (channel: 'EMAIL' | 'WHATSAPP') => void; retrying: 'EMAIL' | 'WHATSAPP' | null;
}) {
  const { t } = useTranslation();
  if (!result?.attempted) return null;
  return (
    <div className="flex items-center justify-between p-3 rounded-xl border border-[var(--border-subtle)]">
      <div className="flex items-center gap-2 min-w-0">
        {icon}
        <span className="text-sm font-medium text-[#1e293b] dark:text-white">{label}</span>
      </div>
      {result.sent ? (
        <span className="flex items-center gap-1 text-xs font-semibold text-success-600">
          <CheckCircle2 size={14} /> {t('clientInfoAdmin.result.sent', 'Sent')}
        </span>
      ) : result.status === 'NOT_CONFIGURED' ? (
        <span className="flex items-center gap-1 text-xs font-semibold text-slate-400">
          <AlertTriangle size={14} /> {t('clientInfoAdmin.result.notConfigured', 'Not configured')}
        </span>
      ) : (
        <button
          onClick={() => onRetry(channel)}
          disabled={retrying === channel}
          className="flex items-center gap-1 text-xs font-semibold text-danger-500 hover:text-danger-600"
        >
          {retrying === channel ? <Loader2 size={13} className="animate-spin" /> : <XCircle size={14} />}
          {t('clientInfoAdmin.result.failedRetry', 'Failed — Retry')}
          <RotateCw size={12} />
        </button>
      )}
    </div>
  );
}

type WhatsAppShareStatus = 'AVAILABLE' | 'OPENED' | 'PHONE_MISSING' | 'INVALID_PHONE';

/** Builds the translated invitation message sent through the manual wa.me share link. */
function buildWhatsAppMessage(params: {
  clientName: string; agencyName?: string; secureUrl: string; expiresAt?: string; language: string;
}): string {
  const { clientName, agencyName, secureUrl, expiresAt, language } = params;
  const name = clientName || '';
  const agency = agencyName || '';
  const expiresLabel = expiresAt ? new Date(expiresAt).toLocaleString(language) : '';
  const lang = language?.slice(0, 2);

  if (lang === 'ar') {
    return `مرحباً ${name}،\n\nتدعوك وكالة ${agency} إلى إكمال معلوماتك من أجل إعداد ملف الكراء.\n\nالرابط الآمن:\n${secureUrl}\n\nتنتهي صلاحية هذا الرابط في ${expiresLabel}.\n\nشكراً.`;
  }
  if (lang === 'en') {
    return `Hello ${name},\n\n${agency} invites you to complete your information so the agency can prepare your rental file.\n\nSecure link:\n${secureUrl}\n\nThis link expires on ${expiresLabel}.\n\nThank you.`;
  }
  return `Bonjour ${name},\n\nL'agence ${agency} vous invite à compléter vos informations afin de préparer votre dossier de location.\n\nLien sécurisé :\n${secureUrl}\n\nCe lien expire le ${expiresLabel}.\n\nMerci.`;
}

/** Manual, client-side WhatsApp share — no Cloud API, no backend provider. Opens
 * wa.me with the client's number and a prefilled message; the admin still has to
 * press Send inside WhatsApp, so this never marks the message as delivered. */
function WhatsAppShareRow({
  clientName, clientPhone, agencyName, language, secureUrl, expiresAt,
}: {
  clientName: string; clientPhone: string; agencyName?: string; language: string; secureUrl: string; expiresAt?: string;
}) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [status, setStatus] = useState<WhatsAppShareStatus>(() => (
    !clientPhone.trim() ? 'PHONE_MISSING' : !isValidMoroccanWhatsAppPhone(clientPhone) ? 'INVALID_PHONE' : 'AVAILABLE'
  ));

  const disabled = status === 'PHONE_MISSING' || status === 'INVALID_PHONE';

  const handleClick = () => {
    if (!clientPhone.trim()) { setStatus('PHONE_MISSING'); return; }
    const normalized = normalizePhoneForWhatsApp(clientPhone);
    if (!isValidMoroccanWhatsAppPhone(clientPhone)) { setStatus('INVALID_PHONE'); return; }

    const message = buildWhatsAppMessage({ clientName, agencyName, secureUrl, expiresAt, language });
    const whatsappUrl = `https://wa.me/${normalized}?text=${encodeURIComponent(message)}`;
    const win = window.open(whatsappUrl, '_blank', 'noopener,noreferrer');

    if (!win) {
      navigator.clipboard?.writeText(`${message}`);
      showToast(t('clientInfoAdmin.result.whatsappBlocked', 'WhatsApp could not be opened. The message was copied.'), 'warning');
      return;
    }
    setStatus('OPENED');
    showToast(t('clientInfoAdmin.result.whatsappOpened', 'WhatsApp opened'), 'success');
  };

  return (
    <div className="flex items-center justify-between gap-2 p-3 rounded-xl border border-[var(--border-subtle)]">
      <div className="flex items-center gap-2 min-w-0">
        <MessageCircle size={16} className="text-slate-400 shrink-0" />
        <span className="text-sm font-medium text-[#1e293b] dark:text-white">{t('clientInfoAdmin.form.whatsapp', 'WhatsApp')}</span>
      </div>
      {status === 'PHONE_MISSING' ? (
        <span className="flex items-center gap-1 text-xs font-semibold text-slate-400">
          <AlertTriangle size={14} /> {t('clientInfoAdmin.result.phoneMissing', 'Client phone number unavailable')}
        </span>
      ) : status === 'INVALID_PHONE' ? (
        <span className="flex items-center gap-1 text-xs font-semibold text-slate-400">
          <AlertTriangle size={14} /> {t('clientInfoAdmin.result.phoneMissing', 'Client phone number unavailable')}
        </span>
      ) : status === 'OPENED' ? (
        <button
          type="button"
          onClick={handleClick}
          aria-label={t('clientInfoAdmin.result.openWhatsapp', 'Open WhatsApp')}
          className="min-h-[44px] flex items-center gap-1.5 px-3 text-xs font-semibold text-success-600 hover:text-success-700 transition-colors"
        >
          <CheckCircle2 size={14} /> {t('clientInfoAdmin.result.whatsappOpened', 'WhatsApp opened')}
        </button>
      ) : (
        <button
          type="button"
          onClick={handleClick}
          disabled={disabled}
          aria-label={t('clientInfoAdmin.result.openWhatsapp', 'Open WhatsApp')}
          className="min-h-[44px] flex items-center gap-1.5 px-4 rounded-lg bg-[#25D366] text-white text-xs font-semibold hover:bg-[#1ea952] active:bg-[#178a43] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <MessageCircle size={14} /> {t('clientInfoAdmin.result.openWhatsapp', 'Open WhatsApp')}
        </button>
      )}
    </div>
  );
}

function DeliveryResultCard({
  result, clientName, clientPhone, agencyName, language, onCopy, onOpen, onRetry, retrying, onClose,
}: {
  result: DeliveryResponse; clientName: string; clientPhone: string; agencyName?: string; language: string;
  onCopy: () => void; onOpen: () => void;
  onRetry: (channel: 'EMAIL' | 'WHATSAPP') => void; retrying: 'EMAIL' | 'WHATSAPP' | null; onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const expiresAtLabel = result.expiresAt
    ? new Date(result.expiresAt).toLocaleString(i18n.language)
    : null;
  const secureUrl = result.secureLink || result.publicUrl || '';

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-success-600 font-semibold text-sm">
        <CheckCircle2 size={18} /> {t('clientInfoAdmin.result.linkCreated', 'Secure link created')}
      </div>

      <div className="space-y-2">
        <ChannelRow icon={<Mail size={16} className="text-slate-400" />} label={t('clientInfoAdmin.form.email', 'Email')}
          result={result.emailResult} channel="EMAIL" onRetry={onRetry} retrying={retrying} />
        <WhatsAppShareRow
          clientName={clientName}
          clientPhone={clientPhone}
          agencyName={agencyName}
          language={language}
          secureUrl={secureUrl}
          expiresAt={result.expiresAt}
        />
      </div>

      {expiresAtLabel && (
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {t('clientInfoAdmin.result.expiresAt', 'Expires on {{date}}', { date: expiresAtLabel })}
        </p>
      )}

      <div className="flex items-center gap-2 p-3 bg-slate-50 dark:bg-white/5 rounded-xl border border-slate-200 dark:border-white/10">
        <span className="flex-1 text-xs font-mono truncate text-[#1e293b] dark:text-slate-200">{result.secureLink || result.publicUrl}</span>
        <button onClick={onCopy} className="shrink-0 p-2 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors" aria-label={t('clientInfoAdmin.copyLink', 'Copy link')}>
          <Copy size={15} />
        </button>
        <button onClick={onOpen} className="shrink-0 p-2 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors" aria-label={t('clientInfoAdmin.openLink', 'Open link')}>
          <ExternalLink size={15} />
        </button>
      </div>

      <button onClick={onClose} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all">
        {t('common.close', 'Close')}
      </button>
    </div>
  );
}

function translateErrorCode(code: string | undefined, t: (key: string, fallback: string) => string): string | null {
  if (!code) return null;
  const map: Record<string, string> = {
    INVALID_PHONE: t('clientInfoAdmin.errors.invalidPhone', 'This phone number is not valid.'),
    INVALID_EMAIL: t('clientInfoAdmin.errors.invalidEmail', 'This email address is not valid.'),
    NO_CHANNEL_AVAILABLE: t('clientInfoAdmin.errors.noChannel', 'At least a valid email or phone number is required.'),
    TENANT_ACCESS_DENIED: t('clientInfoAdmin.errors.tenantAccessDenied', 'This client could not be found in your agency.'),
  };
  return map[code] || null;
}
