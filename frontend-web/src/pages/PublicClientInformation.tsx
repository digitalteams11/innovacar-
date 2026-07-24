import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertCircle, Loader2, ShieldCheck, Clock } from 'lucide-react';
import api from '../api/axios';
import { resolveMediaUrl } from '../utils/mediaUrl';
import ThemeToggle from '../components/ThemeToggle';
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

const SUPPORTED_LANGUAGES = ['ar', 'fr', 'en'] as const;
type SupportedLanguage = typeof SUPPORTED_LANGUAGES[number];

const LANGUAGE_LABELS: Record<SupportedLanguage, string> = {
  ar: 'العربية',
  fr: 'Français',
  en: 'English',
};

function isSupportedLanguage(value: string | null | undefined): value is SupportedLanguage {
  return !!value && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/**
 * Language resolution order (spec section 8): the secure request's own
 * stored preference wins when present (set by the agency at creation time),
 * then the ?lang= URL param, then the browser's language, then French as the
 * final fallback. Never the agency's app-wide default — this page has no
 * access to that without an extra authenticated call, and French is already
 * the project-wide default for unauthenticated/public pages.
 */
function resolveInitialLanguage(storedPreference?: string | null): SupportedLanguage {
  if (isSupportedLanguage(storedPreference)) return storedPreference;
  const urlLang = typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('lang') : null;
  if (isSupportedLanguage(urlLang)) return urlLang;
  const browserLang = typeof navigator !== 'undefined' ? navigator.language?.slice(0, 2) : null;
  if (isSupportedLanguage(browserLang)) return browserLang;
  return 'fr';
}

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
  const isRtl = i18n.language === 'ar';

  const [loading, setLoading] = useState(true);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [view, setView] = useState<PublicView | null>(null);
  const [logoFailed, setLogoFailed] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [wasAlreadySubmitted, setWasAlreadySubmitted] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    // Resolves a usable language immediately (URL/browser/French) so the
    // page never renders in the wrong language for the instant before the
    // request's stored preference (if any) arrives from the server below.
    i18n.changeLanguage(resolveInitialLanguage());

    if (!token) { setErrorCode('CLIENT_INFO_LINK_INVALID'); setLoading(false); return; }
    api.get(`/public/client-information/${token}`)
      .then(({ data }) => {
        setView(data);
        i18n.changeLanguage(resolveInitialLanguage(data?.preferredLanguage));
        if (data?.alreadySubmitted) { setSubmitted(true); setWasAlreadySubmitted(true); }
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

  // Language switching must never clear form data — this only changes the
  // i18n instance's active language; `form` is untouched React state.
  const switchLanguage = (lang: SupportedLanguage) => i18n.changeLanguage(lang);

  const resolvedLogo = resolveMediaUrl(view?.agencyLogo);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg-page)' }}>
        <div className="text-center space-y-3" role="status" aria-live="polite">
          <Loader2 size={28} className="animate-spin mx-auto" style={{ color: 'var(--brand-primary)' }} />
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{t('clientInfo.loadingPage', 'Loading your form...')}</p>
        </div>
      </div>
    );
  }

  if (errorCode) {
    const messages: Record<string, string> = {
      CLIENT_INFO_LINK_INVALID: t('clientInfo.errors.invalid', 'This link is invalid.'),
      CLIENT_INFO_LINK_EXPIRED: t('clientInfo.errors.expired', 'This secure link has expired.'),
      CLIENT_INFO_LINK_REVOKED: t('clientInfo.errors.revoked', 'This link is no longer active.'),
      CLIENT_INFO_ALREADY_APPROVED: t('clientInfo.errors.alreadyApproved', 'This request has already been processed.'),
      CLIENT_INFO_ALREADY_SUBMITTED: t('clientInfo.errors.alreadySubmitted', 'This form has already been submitted.'),
    };
    return (
      <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen flex items-center justify-center p-6" style={{ background: 'var(--bg-page)' }}>
        <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 rounded-2xl flex items-center justify-center mx-auto" style={{ background: 'var(--bg-hover)' }}>
            <AlertCircle size={28} style={{ color: 'var(--danger)' }} />
          </div>
          <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('clientInfo.errors.title', 'Unable to open this link')}</h1>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{messages[errorCode] || messages.CLIENT_INFO_LINK_INVALID}</p>
        </div>
      </div>
    );
  }

  if (submitted) {
    return (
      <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen flex items-center justify-center p-6" style={{ background: 'var(--bg-page)' }}>
        <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 rounded-2xl flex items-center justify-center mx-auto" style={{ background: 'rgba(16,185,129,0.12)' }}>
            <CheckCircle2 size={28} style={{ color: 'var(--success)' }} />
          </div>
          <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
            {wasAlreadySubmitted
              ? t('clientInfo.errors.alreadySubmitted', 'This form has already been submitted.')
              : t('clientInfo.confirmation.title', 'Your information has been submitted successfully.')}
          </h1>
          {!wasAlreadySubmitted && (
            <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
              {t('clientInfo.confirmation.body', 'Thank you. The agency will review your information before approval.')}
            </p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen pb-10 animate-fade" style={{ background: 'var(--bg-page)' }}>
      <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : 'https://innovacar.app/'} robots={ROBOTS_PRIVATE} />

      {/* Header — logo/name, page title, language selector, theme toggle */}
      <div className="sticky top-0 z-10 backdrop-blur-xl" style={{ background: 'var(--glass-bg)', borderBottom: '1px solid var(--border-subtle)' }}>
        <div className="max-w-lg mx-auto px-4 py-3 flex items-center gap-3">
          {resolvedLogo && !logoFailed ? (
            <img
              src={resolvedLogo}
              alt=""
              className="w-10 h-10 rounded-lg object-contain shrink-0 bg-white"
              onError={() => {
                setLogoFailed(true);
                console.warn('[CLIENT_INFO] agency logo failed to load — showing fallback');
              }}
            />
          ) : (
            <div className="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" style={{ background: 'var(--bg-active)' }}>
              <ShieldCheck size={18} style={{ color: 'var(--brand-primary)' }} />
            </div>
          )}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-bold truncate" style={{ color: 'var(--text-primary)' }}>{view?.agencyName || 'Innovacar'}</p>
            <p className="text-[10px] uppercase tracking-wider font-bold" style={{ color: 'var(--text-muted)' }}>
              {t('clientInfo.pageTitle', 'Client Information Form')}
            </p>
          </div>

          <LanguageSelector current={i18n.language} onChange={switchLanguage} label={t('clientInfo.language', 'Language')} />
          <div className="hidden sm:block">
            <ThemeToggle />
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-5">
        {/* Intro card — explanation + secure-link indicator + expiry notice */}
        <div className="rounded-2xl p-4 space-y-2" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)' }}>
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--brand-primary)' }}>
            <ShieldCheck size={14} />
            {t('clientInfo.secureLink', 'Secure link')}
          </div>
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('clientInfo.intro', 'Please complete your information so the agency can prepare your rental file. The agency will review the information before approval.')}
          </p>
          {view?.expiresAt && (
            <div className="flex items-center gap-1.5 text-xs pt-1" style={{ color: 'var(--text-muted)' }}>
              <Clock size={12} />
              {t('clientInfo.expiresOn', 'Expires on {{date}}', { date: new Date(view.expiresAt).toLocaleString(i18n.language) })}
            </div>
          )}
        </div>

        {/* Personal information */}
        <Section title={t('clientInfo.sections.personal', 'Personal information')}>
          <Field label={t('clientInfo.form.fullName', 'Full name')} required>
            <input className="form-input" value={form.fullName} onChange={(e) => update('fullName', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.phone', 'Primary phone')} required>
              <input dir="ltr" className="form-input text-start" value={form.phone} onChange={(e) => update('phone', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.secondaryPhone', 'Secondary phone')}>
              <input dir="ltr" className="form-input text-start" value={form.secondaryPhone} onChange={(e) => update('secondaryPhone', e.target.value)} />
            </Field>
          </div>
          <Field label={t('clientInfo.form.email', 'Email')}>
            <input dir="ltr" type="email" className="form-input text-start" value={form.email} onChange={(e) => update('email', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.birthDate', 'Date of birth')}>
              <input type="date" dir="ltr" className="form-input text-start" value={form.birthDate} onChange={(e) => update('birthDate', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.gender', 'Gender')}>
              <select className="form-input" value={form.gender} onChange={(e) => update('gender', e.target.value)}>
                <option value="">{t('clientInfo.form.genderUnspecified', '—')}</option>
                <option value="MALE">{t('clientInfo.form.genderMale', 'Male')}</option>
                <option value="FEMALE">{t('clientInfo.form.genderFemale', 'Female')}</option>
              </select>
            </Field>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.nationality', 'Nationality')} required>
              <input className="form-input" value={form.nationality} onChange={(e) => update('nationality', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.companyName', 'Company')}>
              <input className="form-input" value={form.companyName} onChange={(e) => update('companyName', e.target.value)} />
            </Field>
          </div>
        </Section>

        {/* Identity document */}
        <Section title={t('clientInfo.sections.document', 'Identity document')}>
          <Field label={t('clientInfo.form.documentType', 'Document type')} required>
            <select className="form-input" value={form.documentType} onChange={(e) => update('documentType', e.target.value)}>
              <option value="CIN">{t('clientInfo.documentTypes.cin', 'CIN')}</option>
              <option value="PASSPORT">{t('clientInfo.documentTypes.passport', 'Passport')}</option>
            </select>
          </Field>
          <Field label={documentNumberLabel()} required>
            <input dir="ltr" className="form-input text-start" value={form.documentNumber} onChange={(e) => update('documentNumber', e.target.value)} />
          </Field>
        </Section>

        {/* Driving licence */}
        <Section title={t('clientInfo.sections.license', 'Driving licence')}>
          <Field label={t('clientInfo.form.licenseNumber', 'Driving licence number')}>
            <input dir="ltr" className="form-input text-start" value={form.driverLicenseNumber} onChange={(e) => update('driverLicenseNumber', e.target.value)} />
          </Field>
        </Section>

        {/* Address */}
        <Section title={t('clientInfo.sections.address', 'Address')}>
          <Field label={t('clientInfo.form.address', 'Address')} required>
            <input className="form-input" value={form.address} onChange={(e) => update('address', e.target.value)} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.city', 'City')}>
              <input className="form-input" value={form.city} onChange={(e) => update('city', e.target.value)} />
            </Field>
            <Field label={t('clientInfo.form.postalCode', 'Postal code')}>
              <input dir="ltr" className="form-input text-start" value={form.postalCode} onChange={(e) => update('postalCode', e.target.value)} />
            </Field>
          </div>
          <Field label={t('clientInfo.form.country', 'Country')}>
            <input className="form-input" value={form.country} onChange={(e) => update('country', e.target.value)} />
          </Field>
          <Field label={t('clientInfo.form.notes', 'Notes')}>
            <textarea className="form-input" rows={2} value={form.notes} onChange={(e) => update('notes', e.target.value)} />
          </Field>
        </Section>

        {/* Review and submit */}
        <Section title={t('clientInfo.sections.review', 'Review and submit')}>
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={form.privacyAccepted}
              onChange={(e) => update('privacyAccepted', e.target.checked)}
              className="mt-0.5 h-5 w-5 shrink-0"
            />
            <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>
              {t('clientInfo.privacyNotice', 'I confirm this information is accurate and I agree that the agency may process it, including my identity documents, to prepare my rental file.')}
            </span>
          </label>

          {validationError && (
            <div className="flex items-center gap-2 p-3 rounded-xl text-xs" style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)' }} role="alert">
              <AlertCircle size={14} />
              <span>{validationError}</span>
            </div>
          )}

          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="w-full flex items-center justify-center gap-2 py-3 rounded-xl font-semibold text-sm transition-all disabled:opacity-50"
            style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground, #ffffff)' }}
          >
            {submitting ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {submitting ? t('clientInfo.submitting', 'Submitting...') : t('clientInfo.submit', 'Submit information')}
          </button>
        </Section>
      </div>
    </div>
  );
}

function LanguageSelector({ current, onChange, label }: { current: string; onChange: (lang: SupportedLanguage) => void; label: string }) {
  return (
    <div className="flex items-center gap-1 rounded-xl p-1 shrink-0" style={{ background: 'var(--bg-hover)' }} role="group" aria-label={label}>
      {SUPPORTED_LANGUAGES.map((lang) => (
        <button
          key={lang}
          type="button"
          onClick={() => onChange(lang)}
          aria-pressed={current === lang}
          aria-label={LANGUAGE_LABELS[lang]}
          className="min-h-[36px] px-2.5 rounded-lg text-[11px] font-bold transition-colors"
          style={{
            background: current === lang ? 'var(--brand-primary)' : 'transparent',
            color: current === lang ? 'var(--brand-primary-foreground, #ffffff)' : 'var(--text-muted)',
          }}
        >
          {lang.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl p-4 space-y-3" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)' }}>
      <h2 className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>{title}</h2>
      {children}
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  const { t } = useTranslation();
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
        {label}
        {required ? (
          <span aria-hidden="true" style={{ color: 'var(--danger)' }}> *</span>
        ) : (
          <span className="text-xs font-normal" style={{ color: 'var(--text-muted)' }}> ({t('common.optional', 'optional')})</span>
        )}
      </span>
      {children}
    </label>
  );
}
