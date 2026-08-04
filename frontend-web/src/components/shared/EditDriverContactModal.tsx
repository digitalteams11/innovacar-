import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Loader2, Save } from 'lucide-react';
import { useToast } from '../../context/ToastContext';
import { updateAdditionalDriver, type AdditionalDriverDto } from '../../api/additionalDrivers';

interface EditDriverContactModalProps {
  contractId: number;
  driver: AdditionalDriverDto;
  onClose: () => void;
  onSaved: (updated: AdditionalDriverDto) => void;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Focused "Modifier les coordonnées" editor — phone + email only. Sends the
 * full AdditionalDriverRequest payload (the backend PUT endpoint replaces
 * the whole record) but only exposes contact fields for editing here; every
 * other field is carried through unchanged from the existing driver so this
 * can never accidentally blank out identity data. The backend independently
 * blocks identity-field changes once a driver has signed — this modal never
 * touches those fields at all, so that guard is never even reached from here.
 */
export default function EditDriverContactModal({ contractId, driver, onClose, onSaved }: EditDriverContactModalProps) {
  const { t, i18n } = useTranslation();
  const { showToast } = useToast();
  const isRtl = i18n.dir() === 'rtl';

  const [phone, setPhone] = useState(driver.phone || '');
  const [email, setEmail] = useState(driver.email || '');
  const [errors, setErrors] = useState<{ phone?: string; email?: string }>({});
  const [saving, setSaving] = useState(false);

  const validate = () => {
    const next: { phone?: string; email?: string } = {};
    const normalizedPhone = phone.trim();
    if (!normalizedPhone) {
      next.phone = t('contracts.driverSignature.contactEdit.errors.phoneRequired');
    } else if (normalizedPhone.replace(/[^\d]/g, '').length < 8) {
      next.phone = t('contracts.driverSignature.contactEdit.errors.phoneInvalid');
    }
    const normalizedEmail = email.trim();
    if (normalizedEmail && !EMAIL_PATTERN.test(normalizedEmail)) {
      next.email = t('contracts.driverSignature.contactEdit.errors.emailInvalid');
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = async () => {
    if (saving) return;
    if (!validate()) return;
    setSaving(true);
    try {
      const { data } = await updateAdditionalDriver(contractId, driver.id, {
        fullName: driver.fullName,
        cin: driver.cin,
        passportNumber: driver.passportNumber,
        driverLicenseNumber: driver.driverLicenseNumber,
        nationality: driver.nationality,
        address: driver.address,
        phone: phone.trim(),
        birthDate: driver.birthDate,
        email: email.trim() || null,
        signatureRequired: driver.signatureRequired,
      });
      showToast(t('contracts.driverSignature.contactEdit.saved'), 'success');
      onSaved(data);
    } catch (err: any) {
      showToast(err?.userMessage || t('contracts.driverSignature.contactEdit.saveFailed'), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[var(--z-modal-overlay)] flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => !saving && onClose()} />
      <div dir={isRtl ? 'rtl' : 'ltr'} className="relative flex w-full max-h-[100dvh] flex-col overflow-hidden rounded-t-3xl bg-white shadow-2xl animate-scale-in sm:max-h-[90dvh] sm:max-w-sm sm:rounded-3xl">
        <div className="shrink-0 flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-lg font-bold text-[#1e293b]">{t('contracts.driverSignature.contactEdit.title')}</h3>
            <p className="text-xs text-slate-400 mt-0.5">{driver.fullName}</p>
          </div>
          <button onClick={() => !saving && onClose()} className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-6 space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              {t('contracts.driverSignature.contactEdit.phone')}
            </label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              disabled={saving}
              placeholder="+212 6XX XXX XXX"
              className={`w-full px-4 py-2.5 rounded-xl border text-sm outline-none ${errors.phone ? 'border-danger-400 ring-2 ring-danger-100' : 'border-slate-200 focus:border-brand-400'}`}
            />
            {errors.phone && <p className="text-xs text-danger-500">{errors.phone}</p>}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              {t('contracts.driverSignature.contactEdit.email')}
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={saving}
              placeholder="driver@example.com"
              className={`w-full px-4 py-2.5 rounded-xl border text-sm outline-none ${errors.email ? 'border-danger-400 ring-2 ring-danger-100' : 'border-slate-200 focus:border-brand-400'}`}
            />
            {errors.email && <p className="text-xs text-danger-500">{errors.email}</p>}
            <p className="text-[11px] text-slate-400">{t('contracts.driverSignature.contactEdit.emailOptionalHint')}</p>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              onClick={() => !saving && onClose()}
              disabled={saving}
              className="flex-1 py-2.5 bg-slate-100 text-slate-600 rounded-xl font-medium text-sm hover:bg-slate-200 disabled:opacity-50"
            >
              {t('actions.cancel', 'Cancel')}
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex-1 flex items-center justify-center gap-1.5 py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 disabled:opacity-50"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
              {t('common.save', 'Save')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
