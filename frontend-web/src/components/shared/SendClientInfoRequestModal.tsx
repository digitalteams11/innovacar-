import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, Loader2, Send } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import Modal from '../Modal';

interface SendClientInfoRequestModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Optional — links the request to an in-progress contract draft (spec section 16/17). */
  contractId?: number;
}

/**
 * The "Send to client" half of the client self-fill workflow (spec section
 * 1/3/26) — an ADDITIVE alternative next to the existing manual client
 * entry, deliberately kept as its own standalone modal so it never touches
 * the existing create-contract form/state.
 */
export default function SendClientInfoRequestModal({ isOpen, onClose, contractId }: SendClientInfoRequestModalProps) {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();

  const [temporaryName, setTemporaryName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [secureLink, setSecureLink] = useState<string | null>(null);

  const reset = () => {
    setTemporaryName(''); setPhone(''); setEmail(''); setSecureLink(null);
  };

  const handleClose = () => { reset(); onClose(); };

  const send = async () => {
    if (!temporaryName.trim() || !phone.trim()) {
      showToast(t('clientInfoAdmin.form.required', 'Please enter at least a name and phone number.'), 'warning');
      return;
    }
    setSubmitting(true);
    try {
      const { data } = await api.post('/client-information-requests', {
        temporaryName: temporaryName.trim(),
        phone: phone.trim(),
        email: email.trim() || undefined,
        preferredLanguage: i18n.language,
        contractId,
      });
      setSecureLink(data.secureLink);
    } catch (err: any) {
      showToast(err?.userMessage || t('clientInfoAdmin.actionFailed', 'Action failed. Please try again.'), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const copyLink = () => {
    if (!secureLink) return;
    navigator.clipboard?.writeText(secureLink);
    showToast(t('clientInfoAdmin.linkCopied', 'Link copied to clipboard.'), 'success');
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={t('clientInfoAdmin.sendToClient', 'Send form to client')} maxWidth="max-w-md">
      {secureLink ? (
        <div className="space-y-4">
          <p className="text-sm text-slate-500">
            {t('clientInfoAdmin.linkReady', 'Share this secure link with the client. It expires in 48 hours.')}
          </p>
          <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl border border-slate-200">
            <span className="flex-1 text-xs font-mono truncate">{secureLink}</span>
            <button onClick={copyLink} className="shrink-0 p-2 rounded-lg hover:bg-slate-200 transition-colors" aria-label={t('clientInfoAdmin.copyLink', 'Copy link')}>
              <Copy size={15} />
            </button>
          </div>
          <button onClick={handleClose} className="w-full py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all">
            {t('common.close', 'Close')}
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-slate-500">
            {t('clientInfoAdmin.form.intro', 'Enter the minimum information needed to contact the client. They will fill in the rest themselves.')}
          </p>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('clientInfoAdmin.form.name', 'Client name')} *</label>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={temporaryName} onChange={(e) => setTemporaryName(e.target.value)} />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('clientInfoAdmin.form.phone', 'Phone number')} *</label>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('clientInfoAdmin.form.email', 'Email (optional)')}</label>
            <input type="email" className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <button
            onClick={send}
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all disabled:opacity-50"
          >
            {submitting ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            {t('clientInfoAdmin.generateLink', 'Generate secure link')}
          </button>
        </div>
      )}
    </Modal>
  );
}
