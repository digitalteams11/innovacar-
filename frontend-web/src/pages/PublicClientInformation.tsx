import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertCircle, Loader2, ShieldCheck } from 'lucide-react';
import api from '../api/axios';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';

interface PublicView {
  temporaryName?: string;
  preferredLanguage?: string;
  agencyName?: string;
  agencyLogo?: string;
  expiresAt?: string;
  alreadySubmitted?: boolean;
}

type DocumentType = 'CIN' | 'PASSPORT' | 'RESIDENCE_PERMIT' | 'OTHER';

const emptyForm = {
  fullName: '', phone: '', secondaryPhone: '', email: '', gender: '', birthDate: '', nationality: '',
  documentType: 'CIN' as DocumentType, documentNumber: '',
  address: '', city: '', postalCode: '', country: 'Morocco',
  driverLicenseNumber: '',
  companyName: '', notes: '',
  privacyAccepted: false,
};

export default function PublicClientInformation() {
  const { token } = useParams<{ token: string }>();
  const { t, i18n } = useTranslation();

  const [loading, setLoading] = useState(true);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [view, setView] = useState<PublicView | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) { setErrorCode('CLIENT_INFO_LINK_INVALID'); setLoading(false); return; }
    api.get(`/public/client-information/${token}`)
      .then(({ data }) => {
        setView(data);
        if (data?.preferredLanguage) i18n.changeLanguage(data.preferredLanguage);
        if (data?.alreadySubmitted) setSubmitted(true);
      })
      .catch((err) => setErrorCode(err?.response?.data?.code || 'CLIENT_INFO_LINK_INVALID'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const update = (field: keyof typeof emptyForm, value: string | boolean) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const documentNumberLabel = () => {
    switch (form.documentType) {
      case 'CIN': return t('clientInfo.form.cinNumber', 'Numéro de CIN');
      case 'PASSPORT': return t('clientInfo.form.passportNumber', 'Numéro de passeport');
      case 'RESIDENCE_PERMIT': return t('clientInfo.form.residencePermitNumber', 'Numéro de carte de séjour');
      default: return t('clientInfo.form.otherDocumentNumber', 'Numéro du document');
    }
  };

  const submit = async () => {
    setValidationError(null);
    if (!form.fullName.trim() || !form.phone.trim() || !form.nationality.trim() || !form.address.trim() || !form.documentNumber.trim()) {
      setValidationError(t('clientInfo.form.requiredFields', 'Please fill all required fields.'));
      return;
    }
    if (form.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) {
      setValidationError(t('clientInfo.form.invalidEmail', 'Please enter a valid email address.'));
      return;
    }
    if (!form.privacyAccepted) {
      setValidationError(t('clientInfo.form.privacyRequired', 'Please accept the privacy notice to continue.'));
      return;
    }
    setSubmitting(true);
    try {
      await api.post(`/public/client-information/${token}/submit`, form);
      setSubmitted(true);
    } catch (err) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code;
      setErrorCode(code || 'CLIENT_INFO_LINK_INVALID');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-brand-500" />
      </div>
    );
  }

  if (errorCode) {
    const messages: Record<string, string> = {
      CLIENT_INFO_LINK_INVALID: t('clientInfo.errors.invalid', 'This link is invalid.'),
      CLIENT_INFO_LINK_EXPIRED: t('clientInfo.errors.expired', 'This link has expired. Please ask the agency for a new one.'),
      CLIENT_INFO_LINK_REVOKED: t('clientInfo.errors.revoked', 'This link is no longer active.'),
      CLIENT_INFO_ALREADY_APPROVED: t('clientInfo.errors.alreadyApproved', 'This request has already been processed.'),
      CLIENT_INFO_ALREADY_SUBMITTED: t('clientInfo.errors.alreadySubmitted', 'This information has already been submitted.'),
    };
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead title="Client Information" description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-danger-50 rounded-2xl flex items-center justify-center mx-auto">
            <AlertCircle size={28} className="text-danger-500" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('clientInfo.errors.title', 'Unable to open this link')}</h1>
          <p className="text-sm text-slate-400">{messages[errorCode] || messages.CLIENT_INFO_LINK_INVALID}</p>
        </div>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <SeoHead title="Client Information" description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 bg-success-50 rounded-2xl flex items-center justify-center mx-auto">
            <CheckCircle2 size={28} className="text-success-500" />
          </div>
          <h1 className="text-xl font-bold text-[#1e293b]">{t('clientInfo.confirmation.title', 'Information submitted')}</h1>
          <p className="text-sm text-slate-400">
            {t('clientInfo.confirmation.body', 'Thank you. The agency will review your information before approval.')}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 pb-10 animate-fade">
      <SeoHead title="Client Information" description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />
      <div className="bg-white border-b border-slate-100">
        <div className="max-w-lg mx-auto px-4 py-4 flex items-center gap-3">
          {view?.agencyLogo ? (
            <img src={view.agencyLogo} alt="" className="w-10 h-10 rounded-lg object-contain" />
          ) : (
            <div className="w-10 h-10 bg-brand-100 rounded-lg flex items-center justify-center">
              <ShieldCheck size={18} className="text-brand-500" />
            </div>
          )}
          <div className="min-w-0">
            <p className="text-sm font-bold text-[#1e293b] truncate">{view?.agencyName || 'Innovacar'}</p>
            <p className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">
              {t('clientInfo.header.subtitle', 'Client information form')}
            </p>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-5">
        <div className="bg-brand-50/60 border border-brand-100 rounded-2xl p-4 text-sm text-brand-700">
          {t('clientInfo.intro', 'Please complete your information so the agency can prepare your rental file. The agency will review the information before approval.')}
        </div>

        {/* Personal information */}
        <Section title={t('clientInfo.sections.personal', 'Personal information')}>
          <Field label={t('clientInfo.form.fullName', 'Full name')} required>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.fullName} onChange={(e) => update('fullName', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.phone', 'Primary phone')} required>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.phone} onChange={(e) => update('phone', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.secondaryPhone', 'Secondary phone')}>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.secondaryPhone} onChange={(e) => update('secondaryPhone', e.target.value)} />
            </Field>
          </div>
          <Field label={t('clientInfo.form.email', 'Email')}>
            <input type="email" className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.email} onChange={(e) => update('email', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.birthDate', 'Date of birth')}>
              <input type="date" className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.birthDate} onChange={(e) => update('birthDate', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.gender', 'Gender')}>
              <select className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.gender} onChange={(e) => update('gender', e.target.value)}>
                <option value="">{t('clientInfo.form.genderUnspecified', '—')}</option>
                <option value="MALE">{t('clientInfo.form.genderMale', 'Male')}</option>
                <option value="FEMALE">{t('clientInfo.form.genderFemale', 'Female')}</option>
              </select>
            </Field>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.nationality', 'Nationality')} required>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.nationality} onChange={(e) => update('nationality', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.companyName', 'Company (optional)')}>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.companyName} onChange={(e) => update('companyName', e.target.value)} />
            </Field>
          </div>
        </Section>

        {/* Identity document */}
        <Section title={t('clientInfo.sections.document', 'Identity document')}>
          <Field label={t('clientInfo.form.documentType', 'Document type')} required>
            <select className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.documentType} onChange={(e) => update('documentType', e.target.value)}>
              <option value="CIN">{t('clientInfo.documentTypes.cin', 'CIN')}</option>
              <option value="PASSPORT">{t('clientInfo.documentTypes.passport', 'Passport')}</option>
            </select>
          </Field>
          <Field label={documentNumberLabel()} required>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.documentNumber} onChange={(e) => update('documentNumber', e.target.value)} />
          </Field>
        </Section>

        {/* Driver license */}
        <Section title={t('clientInfo.sections.license', 'Driving licence')}>
          <Field label={t('clientInfo.form.licenseNumber', 'Driving licence number')}>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.driverLicenseNumber} onChange={(e) => update('driverLicenseNumber', e.target.value)} />
          </Field>
        </Section>

        {/* Address */}
        <Section title={t('clientInfo.sections.address', 'Address')}>
          <Field label={t('clientInfo.form.address', 'Address')} required>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.address} onChange={(e) => update('address', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.city', 'City')}>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.city} onChange={(e) => update('city', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.postalCode', 'Postal code')}>
              <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.postalCode} onChange={(e) => update('postalCode', e.target.value)} />
            </Field>
          </div>
          <Field label={t('clientInfo.form.country', 'Country')}>
            <input className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" value={form.country} onChange={(e) => update('country', e.target.value)} />
          </Field>
          <Field label={t('clientInfo.form.notes', 'Notes (optional)')}>
            <textarea className="w-full px-4 py-2.5 glass-input text-sm text-[#1e293b]" rows={2} value={form.notes} onChange={(e) => update('notes', e.target.value)} />
          </Field>
        </Section>

        {/* Privacy + submit */}
        <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-100 space-y-4">
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={form.privacyAccepted}
              onChange={(e) => update('privacyAccepted', e.target.checked)}
              className="mt-0.5"
            />
            <span className="text-sm text-slate-600">
              {t('clientInfo.privacyNotice', 'I confirm this information is accurate and I agree that the agency may process it, including my identity documents, to prepare my rental file.')}
            </span>
          </label>

          {validationError && (
            <div className="flex items-center gap-2 p-3 bg-danger-50 text-danger-600 rounded-xl text-xs">
              <AlertCircle size={14} />
              <span>{validationError}</span>
            </div>
          )}

          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all disabled:opacity-50"
          >
            {submitting ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {t('clientInfo.submit', 'Submit information')}
          </button>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-2xl p-4 shadow-sm border border-slate-100 space-y-3">
      <h2 className="text-xs font-bold uppercase tracking-wider text-brand-500">{title}</h2>
      {children}
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-[#1e293b]">
        {label}{required && <span className="text-danger-500"> *</span>}
      </span>
      {children}
    </label>
  );
}
