import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertCircle, Loader2, ShieldCheck, Clock, Pencil, Check, Plus, PenLine } from 'lucide-react';
import api from '../api/axios';
import { resolveMediaUrl } from '../utils/mediaUrl';
import { PUBLIC_APP_URL } from '../lib/publicUrl';
import ThemeToggle from '../components/ThemeToggle';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';
import SearchableSelect from '../components/shared/SearchableSelect';
import { COUNTRIES, countryLabel, findCountry } from '../data/countries';
import { hasCityDataset, loadCitiesForCountry } from '../data/cities';
import { normalizePhone, isValidPhone } from '../utils/phone';

interface PublicView {
  temporaryName?: string;
  preferredLanguage?: string;
  agencyName?: string;
  agencyLogo?: string;
  expiresAt?: string;
  alreadySubmitted?: boolean;
  defaultCountryCode?: string;
  // Progressive disclosure — see PublicClientInformationView.java on the backend.
  hasKnownClient?: boolean;
  knownFullName?: string;
  knownPhone?: string;
  knownSecondaryPhone?: string;
  knownEmail?: string;
  knownGender?: string;
  knownBirthDate?: string;
  knownNationality?: string;
  knownAddress?: string;
  knownCity?: string;
  knownCountry?: string;
  knownDocumentType?: 'CIN' | 'PASSPORT';
  knownDocumentNumber?: string;
  knownDriverLicenseNumber?: string;
  knownCompanyName?: string;
  missingFields?: string[];
}

type DocumentType = 'CIN' | 'PASSPORT';

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

const DEFAULT_COUNTRY_CODE = 'MA';

const emptyForm = {
  fullName: '', phone: '', secondaryPhone: '', email: '', gender: '', birthDate: '', nationality: '',
  documentType: 'CIN' as DocumentType, documentNumber: '',
  address: '', city: '', countryCode: DEFAULT_COUNTRY_CODE,
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
  const [retryCount, setRetryCount] = useState(0);
  const [view, setView] = useState<PublicView | null>(null);
  const [logoFailed, setLogoFailed] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  // Display-first / edit-on-demand: fields the client explicitly opened for editing.
  // Everything else renders as a read-only row (see startEditing/stopEditing below).
  const [editingFields, setEditingFields] = useState<Set<string>>(new Set());
  const [submitted, setSubmitted] = useState(false);
  const [wasAlreadySubmitted, setWasAlreadySubmitted] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const [cityOptions, setCityOptions] = useState<string[]>([]);
  const [cityStatus, setCityStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [cityManualMode, setCityManualMode] = useState(false);
  const [cityReloadKey, setCityReloadKey] = useState(0);

  useEffect(() => {
    // Resolves a usable language immediately (URL/browser/French) so the
    // page never renders in the wrong language for the instant before the
    // request's stored preference (if any) arrives from the server below.
    i18n.changeLanguage(resolveInitialLanguage());

    if (!token) { setErrorCode('CLIENT_INFO_LINK_INVALID'); setLoading(false); return; }
    setLoading(true);
    setErrorCode(null);
    api.get(`/public/client-information/${token}`)
      .then(({ data }) => {
        setView(data);
        i18n.changeLanguage(resolveInitialLanguage(data?.preferredLanguage));
        if (data?.defaultCountryCode && findCountry(data.defaultCountryCode)) {
          setForm((prev) => ({ ...prev, countryCode: data.defaultCountryCode }));
        }
        // Pre-fill from what the agency already has on file — the client never sees an
        // empty field for information that's already known (see PublicView javadoc).
        if (data?.hasKnownClient) {
          setForm((prev) => ({
            ...prev,
            fullName: data.knownFullName || prev.fullName,
            phone: data.knownPhone || prev.phone,
            secondaryPhone: data.knownSecondaryPhone || prev.secondaryPhone,
            email: data.knownEmail || prev.email,
            gender: data.knownGender || prev.gender,
            birthDate: data.knownBirthDate || prev.birthDate,
            nationality: data.knownNationality || prev.nationality,
            address: data.knownAddress || prev.address,
            city: data.knownCity || prev.city,
            documentType: (data.knownDocumentType as DocumentType) || prev.documentType,
            documentNumber: data.knownDocumentNumber || prev.documentNumber,
            driverLicenseNumber: data.knownDriverLicenseNumber || prev.driverLicenseNumber,
            companyName: data.knownCompanyName || prev.companyName,
          }));
        }
        if (data?.alreadySubmitted) { setSubmitted(true); setWasAlreadySubmitted(true); }
      })
      .catch((err) => {
        // No `err.response` means the request never reached the server (offline,
        // DNS failure, timeout, CORS) — that's a recoverable connection problem,
        // distinct from the server explicitly rejecting the token. Surfacing it
        // as "link invalid" would send a real client down a dead end instead of
        // just asking them to retry.
        setErrorCode(err?.response ? (err.response.data?.code || 'CLIENT_INFO_LINK_INVALID') : 'CLIENT_INFO_NETWORK_ERROR');
        if (import.meta.env.DEV) console.error('[CLIENT_INFO] failed to load request', err);
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, retryCount]);

  // Country change (spec section 4): clear the old city, load the new
  // country's city list, and drop back to select mode so a manual entry
  // typed for the previous country isn't silently kept for the new one.
  useEffect(() => {
    let cancelled = false;
    setCityStatus('loading');
    setCityManualMode(false);
    setForm((prev) => ({ ...prev, city: '' }));
    loadCitiesForCountry(form.countryCode)
      .then((list) => {
        if (cancelled) return;
        setCityOptions(list);
        setCityStatus('ready');
        if (list.length === 0) setCityManualMode(true);
      })
      .catch(() => { if (!cancelled) setCityStatus('error'); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.countryCode, cityReloadKey]);

  const update = (field: keyof typeof emptyForm, value: string | boolean) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const countryOptions = useMemo(
    () => COUNTRIES.map((c) => ({ value: c.code, label: countryLabel(c, i18n.language) })),
    [i18n.language],
  );
  const cityOptionsForSelect = useMemo(() => cityOptions.map((c) => ({ value: c, label: c })), [cityOptions]);

  const documentNumberLabel = () => (
    form.documentType === 'PASSPORT'
      ? t('clientInfo.form.passportNumber', 'Passport number')
      : t('clientInfo.form.cinNumber', 'CIN number')
  );

  // Display-first / edit-on-demand: every field defaults to a read-only row (its value,
  // or "Not provided" when empty) with an Edit/Add action — never a bare input by
  // default, known or missing. Clicking Edit/Add reveals an input scoped to that one
  // field, with its own Cancel/Save; Save commits the draft into `form` and returns to
  // display mode, Cancel discards the draft and returns to display mode unchanged.
  const startEditing = (key: string) => setEditingFields((prev) => new Set(prev).add(key));
  const stopEditing = (key: string) => setEditingFields((prev) => {
    const next = new Set(prev);
    next.delete(key);
    return next;
  });
  const saveField = (key: keyof typeof emptyForm, value: string) => {
    update(key, value);
    stopEditing(key as string);
  };

  const ALL_FIELD_KEYS = [
    'fullName', 'phone', 'secondaryPhone', 'email', 'birthDate', 'gender', 'nationality',
    'companyName', 'documentNumber', 'driverLicenseNumber', 'address',
  ];
  const editAllFields = () => setEditingFields(new Set(ALL_FIELD_KEYS));

  const submit = async () => {
    setValidationError(null);
    if (
      !form.fullName.trim() || !form.phone.trim() || !form.nationality.trim()
      || !form.documentNumber.trim() || !form.countryCode.trim() || !form.city.trim() || !form.address.trim()
    ) {
      setValidationError(t('clientInfo.form.requiredFields', 'Please fill all required fields.'));
      return;
    }
    if (!isValidPhone(form.phone)) {
      setValidationError(t('clientInfo.form.invalidPhone', 'Please enter a valid phone number.'));
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
    const country = findCountry(form.countryCode);
    const payload = {
      fullName: form.fullName.trim(),
      phone: normalizePhone(form.phone, form.countryCode),
      secondaryPhone: form.secondaryPhone.trim() ? normalizePhone(form.secondaryPhone, form.countryCode) : null,
      email: form.email.trim() || null,
      gender: form.gender || null,
      birthDate: form.birthDate || null,
      nationality: form.nationality.trim(),
      documentType: form.documentType,
      documentNumber: form.documentNumber.trim(),
      address: form.address.trim(),
      city: form.city.trim(),
      countryCode: form.countryCode,
      country: country ? country.en : form.countryCode,
      driverLicenseNumber: form.driverLicenseNumber.trim() || null,
      companyName: form.companyName.trim() || null,
      notes: form.notes.trim() || null,
      privacyAccepted: form.privacyAccepted,
    };
    setSubmitting(true);
    try {
      await api.post(`/public/client-information/${token}/submit`, payload);
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
      CLIENT_INFO_NETWORK_ERROR: t('clientInfo.errors.network', 'We could not connect to the server. Please check your connection.'),
    };
    // Only the connection-failure case is actually recoverable by trying
    // again — a rejected/expired/consumed token will fail identically no
    // matter how many times the same request is retried.
    const isRetryable = errorCode === 'CLIENT_INFO_NETWORK_ERROR';
    return (
      <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen flex items-center justify-center p-6" style={{ background: 'var(--bg-page)' }}>
        <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />
        <div className="text-center space-y-4 max-w-sm">
          <div className="w-16 h-16 rounded-2xl flex items-center justify-center mx-auto" style={{ background: 'var(--bg-hover)' }}>
            <AlertCircle size={28} style={{ color: 'var(--danger)' }} />
          </div>
          <h1 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('clientInfo.errors.title', 'Unable to open this link')}</h1>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>{messages[errorCode] || messages.CLIENT_INFO_LINK_INVALID}</p>
          {isRetryable && (
            <button
              type="button"
              onClick={() => setRetryCount((n) => n + 1)}
              className="px-5 py-2.5 rounded-xl text-sm font-semibold transition-colors"
              style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-contrast, #fff)' }}
            >
              {t('clientInfo.errors.retry', 'Retry')}
            </button>
          )}
        </div>
      </div>
    );
  }

  if (submitted) {
    return (
      <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen flex items-center justify-center p-6" style={{ background: 'var(--bg-page)' }}>
        <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />
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
    <div dir={isRtl ? 'rtl' : 'ltr'} className="min-h-screen pb-28 sm:pb-10 animate-fade" style={{ background: 'var(--bg-page)' }}>
      <SeoHead title={t('clientInfo.pageTitle', 'Client Information Form')} description="Secure client information form." canonical={typeof window !== 'undefined' ? window.location.href : `${PUBLIC_APP_URL}/`} robots={ROBOTS_PRIVATE} />

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
        <Section
          title={t('clientInfo.sections.personal', 'Personal information')}
          action={<EditAllButton onClick={editAllFields} />}
        >
          <ProgressiveField
            label={t('clientInfo.form.fullName', 'Full name')} required
            value={form.fullName}
            editing={editingFields.has('fullName')}
            onStartEdit={() => startEditing('fullName')}
            onCancel={() => stopEditing('fullName')}
            onSave={(v) => saveField('fullName', v)}
            renderInput={(draft, setDraft) => <input className="form-input" value={draft} onChange={(e) => setDraft(e.target.value)} />}
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <ProgressiveField
              label={t('clientInfo.form.phone', 'Primary phone')} required
              value={form.phone}
              editing={editingFields.has('phone')}
              onStartEdit={() => startEditing('phone')}
              onCancel={() => stopEditing('phone')}
              onSave={(v) => saveField('phone', v)}
              renderInput={(draft, setDraft) => <input dir="ltr" className="form-input text-start" value={draft} onChange={(e) => setDraft(e.target.value)} />}
            />
            <ProgressiveField
              label={t('clientInfo.form.secondaryPhone', 'Secondary phone')}
              value={form.secondaryPhone}
              editing={editingFields.has('secondaryPhone')}
              onStartEdit={() => startEditing('secondaryPhone')}
              onCancel={() => stopEditing('secondaryPhone')}
              onSave={(v) => saveField('secondaryPhone', v)}
              renderInput={(draft, setDraft) => <input dir="ltr" className="form-input text-start" value={draft} onChange={(e) => setDraft(e.target.value)} />}
            />
          </div>
          <ProgressiveField
            label={t('clientInfo.form.email', 'Email')}
            value={form.email}
            editing={editingFields.has('email')}
            onStartEdit={() => startEditing('email')}
            onCancel={() => stopEditing('email')}
            onSave={(v) => saveField('email', v)}
            renderInput={(draft, setDraft) => <input dir="ltr" type="email" className="form-input text-start" value={draft} onChange={(e) => setDraft(e.target.value)} />}
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <ProgressiveField
              label={t('clientInfo.form.birthDate', 'Date of birth')}
              value={form.birthDate}
              editing={editingFields.has('birthDate')}
              onStartEdit={() => startEditing('birthDate')}
              onCancel={() => stopEditing('birthDate')}
              onSave={(v) => saveField('birthDate', v)}
              renderInput={(draft, setDraft) => <input type="date" dir="ltr" className="form-input text-start" value={draft} onChange={(e) => setDraft(e.target.value)} />}
            />
            <ProgressiveField
              label={t('clientInfo.form.gender', 'Gender')}
              value={form.gender}
              displayValue={form.gender === 'MALE' ? t('clientInfo.form.genderMale', 'Male') : form.gender === 'FEMALE' ? t('clientInfo.form.genderFemale', 'Female') : form.gender}
              editing={editingFields.has('gender')}
              onStartEdit={() => startEditing('gender')}
              onCancel={() => stopEditing('gender')}
              onSave={(v) => saveField('gender', v)}
              renderInput={(draft, setDraft) => (
                <select className="form-input" value={draft} onChange={(e) => setDraft(e.target.value)}>
                  <option value="">{t('clientInfo.form.genderUnspecified', '—')}</option>
                  <option value="MALE">{t('clientInfo.form.genderMale', 'Male')}</option>
                  <option value="FEMALE">{t('clientInfo.form.genderFemale', 'Female')}</option>
                </select>
              )}
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <ProgressiveField
              label={t('clientInfo.form.nationality', 'Nationality')} required
              value={form.nationality}
              editing={editingFields.has('nationality')}
              onStartEdit={() => startEditing('nationality')}
              onCancel={() => stopEditing('nationality')}
              onSave={(v) => saveField('nationality', v)}
              renderInput={(draft, setDraft) => <input className="form-input" value={draft} onChange={(e) => setDraft(e.target.value)} />}
            />
            <ProgressiveField
              label={t('clientInfo.form.companyName', 'Company')}
              value={form.companyName}
              editing={editingFields.has('companyName')}
              onStartEdit={() => startEditing('companyName')}
              onCancel={() => stopEditing('companyName')}
              onSave={(v) => saveField('companyName', v)}
              renderInput={(draft, setDraft) => <input className="form-input" value={draft} onChange={(e) => setDraft(e.target.value)} />}
            />
          </div>
        </Section>

        {/* Identity document */}
        <Section title={t('clientInfo.sections.document', 'Identity document')}>
          <DocumentProgressiveField
            documentType={form.documentType}
            documentNumber={form.documentNumber}
            editing={editingFields.has('documentNumber')}
            onStartEdit={() => startEditing('documentNumber')}
            onCancel={() => stopEditing('documentNumber')}
            onSave={(type, number) => {
              update('documentType', type);
              update('documentNumber', number);
              stopEditing('documentNumber');
            }}
            documentNumberLabel={documentNumberLabel}
          />
        </Section>

        {/* Driving licence */}
        <Section title={t('clientInfo.sections.license', 'Driving licence')}>
          <ProgressiveField
            label={t('clientInfo.form.licenseNumber', 'Driving licence number')}
            value={form.driverLicenseNumber}
            editing={editingFields.has('driverLicenseNumber')}
            onStartEdit={() => startEditing('driverLicenseNumber')}
            onCancel={() => stopEditing('driverLicenseNumber')}
            onSave={(v) => saveField('driverLicenseNumber', v)}
            renderInput={(draft, setDraft) => <input dir="ltr" className="form-input text-start" value={draft} onChange={(e) => setDraft(e.target.value)} />}
          />
        </Section>

        {/* Address */}
        <Section title={t('clientInfo.sections.address', 'Address')}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label={t('clientInfo.form.country', 'Country')} required>
              <SearchableSelect
                value={form.countryCode}
                onChange={(code) => update('countryCode', code)}
                options={countryOptions}
                placeholder={t('clientInfo.form.countryPlaceholder', 'Select a country')}
                searchPlaceholder={t('clientInfo.form.countrySearchPlaceholder', 'Search countries')}
                emptyMessage={t('clientInfo.form.countryEmpty', 'No country found')}
              />
            </Field>
            <Field label={t('clientInfo.form.city', 'City')} required>
              {cityManualMode ? (
                <div className="space-y-1.5">
                  <input
                    className="form-input"
                    value={form.city}
                    placeholder={t('clientInfo.form.cityManualPlaceholder', 'Enter city name')}
                    onChange={(e) => update('city', e.target.value)}
                  />
                  {hasCityDataset(form.countryCode) && (
                    <button
                      type="button"
                      className="text-xs font-semibold"
                      style={{ color: 'var(--brand-primary)' }}
                      onClick={() => setCityManualMode(false)}
                    >
                      {t('clientInfo.form.cityManualBack', 'Choose from list instead')}
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-1.5">
                  <SearchableSelect
                    value={form.city}
                    onChange={(city) => update('city', city)}
                    options={cityOptionsForSelect}
                    disabled={!form.countryCode}
                    placeholder={t('clientInfo.form.cityPlaceholder', 'Select a city')}
                    searchPlaceholder={t('clientInfo.form.citySearchPlaceholder', 'Search cities')}
                    emptyMessage={cityStatus === 'error'
                      ? t('clientInfo.errors.title', 'Unable to open this link')
                      : t('clientInfo.form.cityEmpty', 'City not found')}
                    status={cityStatus}
                    onRetry={() => setCityReloadKey((k) => k + 1)}
                    retryLabel={t('clientInfo.form.cityRetry', 'Retry')}
                    loading={cityStatus === 'loading'}
                  />
                  <button
                    type="button"
                    className="text-xs font-semibold"
                    style={{ color: 'var(--brand-primary)' }}
                    onClick={() => setCityManualMode(true)}
                  >
                    {t('clientInfo.form.cityManualPrompt', 'Enter city manually')}
                  </button>
                </div>
              )}
            </Field>
          </div>
          <ProgressiveField
            label={t('clientInfo.form.address', 'Address')} required
            value={form.address}
            editing={editingFields.has('address')}
            onStartEdit={() => startEditing('address')}
            onCancel={() => stopEditing('address')}
            onSave={(v) => saveField('address', v)}
            renderInput={(draft, setDraft) => (
              <textarea
                className="form-input"
                rows={3}
                placeholder={t('clientInfo.form.addressPlaceholder', 'Street, district and additional address details')}
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
              />
            )}
          />
          <Field label={t('clientInfo.form.notes', 'Notes')}>
            <textarea
              className="form-input"
              rows={3}
              placeholder={t('clientInfo.form.notesPlaceholder', 'Additional notes (optional)')}
              value={form.notes}
              onChange={(e) => update('notes', e.target.value)}
            />
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
            className="hidden sm:flex w-full items-center justify-center gap-2 py-3 rounded-xl font-semibold text-sm transition-all disabled:opacity-50"
            style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground, #ffffff)' }}
          >
            {submitting ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {submitting ? t('clientInfo.submitting', 'Submitting...') : t('clientInfo.submit', 'Submit information')}
          </button>
        </Section>
      </div>

      {/* Sticky mobile submit (spec section 9) */}
      <div className="sm:hidden fixed bottom-0 left-0 right-0 z-20 p-3 backdrop-blur-xl" style={{ background: 'var(--glass-bg)', borderTop: '1px solid var(--border-subtle)' }}>
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

function Section({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl p-4 space-y-3" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)' }}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-xs font-bold uppercase tracking-wider" style={{ color: 'var(--text-primary)' }}>{title}</h2>
        {action}
      </div>
      {children}
    </div>
  );
}

/** "Edit information" — the global action near the section header (spec) that puts
 *  every field with a value into edit mode at once. Individual fields still have
 *  their own Edit/Add action regardless. */
function EditAllButton({ onClick }: { onClick: () => void }) {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onClick}
      className="text-xs font-semibold flex items-center gap-1 shrink-0"
      style={{ color: 'var(--brand-primary)' }}
    >
      <PenLine size={12} />
      {t('clientInfo.editInformation', 'Edit information')}
    </button>
  );
}

function LabelRow({ label, required }: { label: string; required?: boolean }) {
  const { t } = useTranslation();
  return (
    <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
      {label}
      {required ? (
        <span aria-hidden="true" style={{ color: 'var(--danger)' }}> *</span>
      ) : (
        <span className="text-xs font-normal" style={{ color: 'var(--text-muted)' }}> ({t('common.optional', 'optional')})</span>
      )}
    </span>
  );
}

/**
 * Display-first / edit-on-demand field. DEFAULT state is always a read-only row —
 * the field's value with an Edit action when it has one, or "Not provided" with an
 * Add action when it doesn't — never a bare input, known or missing (spec: "Do NOT
 * display an empty input by default"). Clicking Edit/Add reveals an input holding a
 * local DRAFT, independent of the committed form value, with its own Cancel (discard
 * the draft, return to display unchanged) and Save (commit the draft, return to
 * display) — editing one field never puts any other field into edit mode.
 */
function ProgressiveField({
  label, required, value, displayValue, editing, onStartEdit, onCancel, onSave, renderInput,
}: {
  label: string;
  required?: boolean;
  value: string;
  /** How to render the value in display mode when it differs from the raw stored value (e.g. a translated gender label). */
  displayValue?: string;
  editing: boolean;
  onStartEdit: () => void;
  onCancel: () => void;
  onSave: (newValue: string) => void;
  renderInput: (draft: string, setDraft: (v: string) => void) => React.ReactNode;
}) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    if (editing) setDraft(value);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  const hasValue = value.trim().length > 0;

  if (!editing) {
    return (
      <div className="space-y-1.5">
        <LabelRow label={label} required={required} />
        <div
          className="flex items-center justify-between gap-3 px-3.5 py-2.5 rounded-xl"
          style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)' }}
        >
          <span dir="auto" className="text-sm flex items-center gap-1.5 min-w-0 truncate" style={{ color: hasValue ? 'var(--text-secondary)' : 'var(--text-muted)' }}>
            {hasValue && <Check size={13} className="shrink-0" style={{ color: 'var(--success)' }} />}
            <span className="truncate">{hasValue ? (displayValue || value) : t('clientInfo.notProvided', 'Not provided')}</span>
          </span>
          <button
            type="button"
            onClick={onStartEdit}
            className="text-xs font-semibold flex items-center gap-1 shrink-0"
            style={{ color: 'var(--brand-primary)' }}
          >
            {hasValue ? <Pencil size={12} /> : <Plus size={12} />}
            {hasValue ? t('clientInfo.edit', 'Edit') : t('clientInfo.add', 'Add')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      <LabelRow label={label} required={required} />
      {renderInput(draft, setDraft)}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 py-2 rounded-lg text-xs font-semibold border"
          style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
        >
          {t('clientInfo.cancel', 'Cancel')}
        </button>
        <button
          type="button"
          onClick={() => onSave(draft)}
          className="flex-1 py-2 rounded-lg text-xs font-semibold"
          style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground, #ffffff)' }}
        >
          {t('clientInfo.save', 'Save')}
        </button>
      </div>
    </div>
  );
}

/** Document type + number are inherently paired (a type with no number is meaningless)
 *  so they share one display row / one edit session, same Cancel/Save shell as
 *  {@link ProgressiveField} but with a combined two-field draft. */
function DocumentProgressiveField({
  documentType, documentNumber, editing, onStartEdit, onCancel, onSave, documentNumberLabel,
}: {
  documentType: string;
  documentNumber: string;
  editing: boolean;
  onStartEdit: () => void;
  onCancel: () => void;
  onSave: (type: string, number: string) => void;
  documentNumberLabel: () => string;
}) {
  const { t } = useTranslation();
  const [draftType, setDraftType] = useState(documentType);
  const [draftNumber, setDraftNumber] = useState(documentNumber);

  useEffect(() => {
    if (editing) { setDraftType(documentType); setDraftNumber(documentNumber); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  const hasValue = documentNumber.trim().length > 0;
  const typeLabel = (type: string) => (type === 'PASSPORT' ? t('clientInfo.documentTypes.passport', 'Passport') : t('clientInfo.documentTypes.cin', 'CIN'));

  if (!editing) {
    return (
      <div className="space-y-1.5">
        <LabelRow label={t('clientInfo.form.documentLabel', 'Identity document')} required />
        <div
          className="flex items-center justify-between gap-3 px-3.5 py-2.5 rounded-xl"
          style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)' }}
        >
          <span dir="auto" className="text-sm flex items-center gap-1.5 min-w-0 truncate" style={{ color: hasValue ? 'var(--text-secondary)' : 'var(--text-muted)' }}>
            {hasValue && <Check size={13} className="shrink-0" style={{ color: 'var(--success)' }} />}
            <span className="truncate">{hasValue ? `${typeLabel(documentType)} — ${documentNumber}` : t('clientInfo.notProvided', 'Not provided')}</span>
          </span>
          <button
            type="button"
            onClick={onStartEdit}
            className="text-xs font-semibold flex items-center gap-1 shrink-0"
            style={{ color: 'var(--brand-primary)' }}
          >
            {hasValue ? <Pencil size={12} /> : <Plus size={12} />}
            {hasValue ? t('clientInfo.edit', 'Edit') : t('clientInfo.add', 'Add')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      <LabelRow label={t('clientInfo.form.documentLabel', 'Identity document')} required />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <select className="form-input" value={draftType} onChange={(e) => setDraftType(e.target.value)}>
          <option value="CIN">{t('clientInfo.documentTypes.cin', 'CIN')}</option>
          <option value="PASSPORT">{t('clientInfo.documentTypes.passport', 'Passport')}</option>
        </select>
        <input
          dir="ltr"
          className="form-input text-start"
          placeholder={documentNumberLabel()}
          value={draftNumber}
          onChange={(e) => setDraftNumber(e.target.value)}
        />
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 py-2 rounded-lg text-xs font-semibold border"
          style={{ borderColor: 'var(--border-subtle)', color: 'var(--text-secondary)' }}
        >
          {t('clientInfo.cancel', 'Cancel')}
        </button>
        <button
          type="button"
          onClick={() => onSave(draftType, draftNumber)}
          className="flex-1 py-2 rounded-lg text-xs font-semibold"
          style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground, #ffffff)' }}
        >
          {t('clientInfo.save', 'Save')}
        </button>
      </div>
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
