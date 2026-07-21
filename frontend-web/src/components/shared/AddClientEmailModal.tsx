import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '../Modal';
import { Loader2 } from 'lucide-react';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface AddClientEmailModalProps {
  isOpen: boolean;
  onClose: () => void;
  clientName: string;
  clientPhone: string;
  currentEmail?: string | null;
  saving: boolean;
  /** Resolves once the client's email has been persisted; rejects on failure. */
  onSave: (email: string, sendAfterSave: boolean) => Promise<void>;
}

export default function AddClientEmailModal({
  isOpen, onClose, clientName, clientPhone, currentEmail, saving, onSave,
}: AddClientEmailModalProps) {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [sendAfterSave, setSendAfterSave] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setEmail(currentEmail || '');
      setSendAfterSave(false);
      setError('');
    }
  }, [isOpen, currentEmail]);

  const isEditing = Boolean(currentEmail);

  const validate = (): string | null => {
    const trimmed = email.trim();
    if (!trimmed) return t('contractEmail.invalid') as string;
    if (trimmed.length > 150) return t('contractEmail.invalid') as string;
    if (/\s/.test(trimmed)) return t('contractEmail.invalid') as string;
    if (!EMAIL_RE.test(trimmed)) return t('contractEmail.invalid') as string;
    return null;
  };

  const handleSubmit = async () => {
    if (saving) return;
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError('');
    try {
      await onSave(email.trim().toLowerCase(), sendAfterSave);
    } catch {
      // onSave/caller is responsible for surfacing its own error toast;
      // keep the modal open so the user can retry without re-typing.
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={(isEditing ? t('contractEmail.modal.titleEdit') : t('contractEmail.modal.titleAdd')) as string}
      maxWidth="max-w-md"
    >
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
              {t('contractEmail.modal.clientName')}
            </label>
            <p className="mt-1 truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{clientName || '—'}</p>
          </div>
          <div>
            <label className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
              {t('contractEmail.modal.phone')}
            </label>
            <p className="mt-1 truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{clientPhone || '—'}</p>
          </div>
        </div>

        <div>
          <label htmlFor="client-email-input" className="text-xs font-semibold uppercase tracking-wide" style={{ color: 'var(--text-muted)' }}>
            {t('contractEmail.modal.emailLabel')} *
          </label>
          <input
            id="client-email-input"
            type="email"
            autoComplete="email"
            inputMode="email"
            required
            value={email}
            onChange={(e) => { setEmail(e.target.value); if (error) setError(''); }}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSubmit(); }}
            placeholder={t('contractEmail.modal.emailPlaceholder') as string}
            disabled={saving}
            className="mt-1.5 w-full rounded-xl border px-3.5 py-3 text-base outline-none transition-colors disabled:opacity-60"
            style={{
              borderColor: error ? 'var(--color-danger, #ef4444)' : 'var(--border-subtle)',
              background: 'var(--bg-page-raised)',
              color: 'var(--text-primary)',
            }}
          />
          {error && <p className="mt-1.5 text-xs font-medium text-rose-500">{error}</p>}
        </div>

        <label className="flex items-center gap-2.5 text-sm" style={{ color: 'var(--text-primary)' }}>
          <input
            type="checkbox"
            checked={sendAfterSave}
            onChange={(e) => setSendAfterSave(e.target.checked)}
            disabled={saving}
            className="h-4 w-4 shrink-0 rounded"
          />
          {t('contractEmail.modal.sendImmediately')}
        </label>

        <div className="flex flex-col-reverse gap-2.5 pt-1 sm:flex-row">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="min-h-[44px] flex-1 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50"
            style={{ background: 'var(--bg-hover)', color: 'var(--text-primary)' }}
          >
            {t('contractEmail.modal.cancel')}
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={saving}
            className="flex min-h-[44px] flex-1 items-center justify-center gap-2 rounded-xl bg-brand-600 text-sm font-semibold text-white transition-colors hover:bg-brand-700 disabled:opacity-50"
          >
            {saving && <Loader2 size={15} className="animate-spin" />}
            {sendAfterSave ? t('contractEmail.saveAndSend') : t('contractEmail.save')}
          </button>
        </div>
      </div>
    </Modal>
  );
}
