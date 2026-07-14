import { useEffect, useState } from 'react';
import { Globe2, Palette, Upload, CheckCircle2, XCircle, Clock, ShieldAlert } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import { useToast } from '../context/ToastContext';
import { useTheme } from '../context/ThemeContext';

interface DnsInstructions {
  txtRecordName: string;
  txtRecordValue: string;
  cnameRecordName: string;
  cnameRecordValue: string;
}

interface WhiteLabelForm {
  logoUrl: string;
  primaryColor: string;
  accentColor: string;
  customDomain: string;
  subdomain: string;
  subdomainFull: string;
  domainStatus: string;
  lastCheckedAt: string | null;
  lastCheckError: string | null;
  dnsVerifiedAt: string | null;
  dnsInstructions: DnsInstructions | null;
}

const DEFAULT_FORM: WhiteLabelForm = {
  logoUrl: '',
  primaryColor: '#0b1437',
  accentColor: '#c9a96e',
  customDomain: '',
  subdomain: '',
  subdomainFull: '',
  domainStatus: 'NONE',
  lastCheckedAt: null,
  lastCheckError: null,
  dnsVerifiedAt: null,
  dnsInstructions: null,
};

export default function WhiteLabel() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const { refreshBranding } = useTheme();
  const [form, setForm] = useState<WhiteLabelForm>(DEFAULT_FORM);
  const [domainMode, setDomainMode] = useState<'subdomain' | 'custom'>('subdomain');
  const [subdomainInput, setSubdomainInput] = useState('');
  const [customDomainInput, setCustomDomainInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [logoError, setLogoError] = useState('');

  useEffect(() => {
    api.get('/white-label').then(({ data }) => {
      if (!data) return;
      setForm((current) => ({ ...current, ...data }));
      setSubdomainInput(data.subdomain || '');
      setCustomDomainInput(data.customDomain || '');
      setDomainMode(data.customDomain ? 'custom' : 'subdomain');
    }).catch(console.error);
  }, []);

  const uploadLogo = (file?: File) => {
    if (!file) return;
    setLogoError('');
    if (file.size > 2 * 1024 * 1024) {
      setLogoError(t('agencyBranding.logoTooLarge'));
      return;
    }
    if (!['image/png', 'image/jpeg', 'image/svg+xml'].includes(file.type)) {
      setLogoError(t('agencyBranding.logoInvalidType'));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setForm((current) => ({ ...current, logoUrl: String(reader.result) }));
    reader.readAsDataURL(file);
  };

  const save = async () => {
    setSaving(true);
    try {
      const payload: Record<string, string> = {
        logoUrl: form.logoUrl,
        primaryColor: form.primaryColor,
        accentColor: form.accentColor,
      };
      if (domainMode === 'subdomain') {
        payload.subdomain = subdomainInput;
        payload.customDomain = '';
      } else {
        payload.customDomain = customDomainInput;
        payload.subdomain = '';
      }
      const { data } = await api.post('/white-label', payload);
      setForm((current) => ({ ...current, ...data }));
      setSubdomainInput(data.subdomain || '');
      setCustomDomainInput(data.customDomain || '');
      showToast(t('agencyBranding.saved'));
      refreshBranding();
    } catch (error: any) {
      showToast(error.userMessage || error?.response?.data?.message || t('agencyBranding.saveFailed'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const verifyDomain = async () => {
    setVerifying(true);
    try {
      const { data } = await api.post('/white-label/domain/verify');
      setForm((current) => ({ ...current, ...data }));
      if (data.domainStatus === 'DNS_VERIFIED') {
        showToast(t('agencyBranding.dnsVerifiedToast'));
      } else {
        showToast(data.lastCheckError || t('agencyBranding.dnsFailedToast'), 'error');
      }
    } catch (error: any) {
      showToast(error.userMessage || error?.response?.data?.message || t('agencyBranding.verifyFailed'), 'error');
    } finally {
      setVerifying(false);
    }
  };

  const statusBadge = () => {
    const map: Record<string, { icon: LucideIcon; classes: string; label: string }> = {
      NONE: { icon: Globe2, classes: 'bg-slate-100 text-slate-500', label: t('agencyBranding.statusNone') },
      PENDING: { icon: Clock, classes: 'bg-amber-100 text-amber-700', label: t('agencyBranding.statusPending') },
      DNS_VERIFIED: { icon: ShieldAlert, classes: 'bg-blue-100 text-blue-700', label: t('agencyBranding.statusDnsVerified') },
      FAILED: { icon: XCircle, classes: 'bg-rose-100 text-rose-700', label: t('agencyBranding.statusFailed') },
      ACTIVE: { icon: CheckCircle2, classes: 'bg-emerald-100 text-emerald-700', label: t('agencyBranding.statusActive') },
    };
    const entry = map[form.domainStatus] || map.NONE;
    const Icon = entry.icon;
    return (
      <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold ${entry.classes}`}>
        <Icon size={14} /> {entry.label}
      </span>
    );
  };

  return (
    <div className="space-y-5 animate-fade">
      <div>
        <h1 className="text-xl font-bold text-[#1e293b]">{t('agencyBranding.title')}</h1>
        <p className="text-sm text-slate-500 mt-1">{t('agencyBranding.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <section className="bg-white border border-[#e8e6e1] p-5">
          <div className="flex items-center gap-2 mb-5"><Upload size={18} /><h2 className="font-bold text-[#1e293b]">{t('agencyBranding.logoTitle')}</h2></div>
          <div className="h-28 border border-dashed border-slate-300 flex items-center justify-center bg-slate-50 mb-4">
            {form.logoUrl ? <img src={form.logoUrl} alt="Agency logo" className="max-h-20 max-w-[80%] object-contain" /> : <span className="text-sm text-slate-400">{t('agencyBranding.noLogo')}</span>}
          </div>
          <input type="file" accept="image/png,image/jpeg,image/svg+xml" onChange={(e) => uploadLogo(e.target.files?.[0])} className="text-sm" />
          {logoError && <p className="text-xs text-rose-600 mt-2">{logoError}</p>}
          <p className="text-xs text-slate-400 mt-2">{t('agencyBranding.logoHint')}</p>
        </section>

        <section className="bg-white border border-[#e8e6e1] p-5">
          <div className="flex items-center gap-2 mb-5"><Palette size={18} /><h2 className="font-bold text-[#1e293b]">{t('agencyBranding.colorsTitle')}</h2></div>
          <div className="grid grid-cols-2 gap-4 mb-4">
            {([
              ['primaryColor', t('agencyBranding.primaryColor')],
              ['accentColor', t('agencyBranding.accentColor')],
            ] as const).map(([key, label]) => (
              <label key={key} className="text-xs font-semibold text-slate-500">
                {label}
                <div className="mt-2 flex items-center gap-2">
                  <input type="color" value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} className="w-10 h-10 border-0 bg-transparent" />
                  <input value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} className="w-full px-3 py-2 border border-[#e8e6e1] rounded-lg text-sm" />
                </div>
              </label>
            ))}
          </div>

          <div className="rounded-lg overflow-hidden border border-[#e8e6e1]">
            <div className="px-3 py-2 text-xs font-bold text-white flex items-center justify-between" style={{ backgroundColor: form.primaryColor }}>
              <span className="flex items-center gap-2">
                {form.logoUrl && <img src={form.logoUrl} alt="" className="h-5 w-5 object-contain rounded bg-white/20 p-0.5" />}
                {t('agencyBranding.previewLabel')}
              </span>
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: form.accentColor }} />
            </div>
            <div className="p-3 bg-slate-50 flex items-center gap-2">
              <button type="button" className="px-3 py-1.5 rounded-md text-xs font-semibold text-white" style={{ backgroundColor: form.primaryColor }}>{t('agencyBranding.previewButtonPrimary')}</button>
              <button type="button" className="px-3 py-1.5 rounded-md text-xs font-semibold text-white" style={{ backgroundColor: form.accentColor }}>{t('agencyBranding.previewButtonAccent')}</button>
            </div>
          </div>
        </section>
      </div>

      <section className="bg-white border border-[#e8e6e1] p-5">
        <div className="flex items-center justify-between mb-5 flex-wrap gap-2">
          <div className="flex items-center gap-2"><Globe2 size={18} /><h2 className="font-bold text-[#1e293b]">{t('agencyBranding.domainTitle')}</h2></div>
          {statusBadge()}
        </div>

        <div className="flex gap-2 mb-4">
          <button type="button" onClick={() => setDomainMode('subdomain')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${domainMode === 'subdomain' ? 'bg-brand-500 text-white border-brand-500' : 'border-[#e8e6e1] text-slate-500'}`}>
            {t('agencyBranding.useSubdomain')}
          </button>
          <button type="button" onClick={() => setDomainMode('custom')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${domainMode === 'custom' ? 'bg-brand-500 text-white border-brand-500' : 'border-[#e8e6e1] text-slate-500'}`}>
            {t('agencyBranding.useCustomDomain')}
          </button>
        </div>

        {domainMode === 'subdomain' ? (
          <div className="flex items-center border border-[#e8e6e1] rounded-lg overflow-hidden max-w-md">
            <input value={subdomainInput} onChange={(e) => setSubdomainInput(e.target.value.toLowerCase())}
              placeholder="myagency" className="px-3 py-2.5 flex-1 text-sm outline-none" />
            <span className="px-3 py-2.5 bg-slate-100 text-slate-500 text-xs font-semibold whitespace-nowrap">.innovacar.app</span>
          </div>
        ) : (
          <>
            <input value={customDomainInput} onChange={(e) => setCustomDomainInput(e.target.value)}
              placeholder="rent.myagency.com" className="w-full px-3 py-2.5 border border-[#e8e6e1] rounded-lg text-sm outline-none focus:border-brand-400 mb-4" />

            {form.dnsInstructions && (
              <div className="rounded-lg border border-[#e8e6e1] bg-slate-50 p-4 mb-4 space-y-3">
                <p className="text-xs font-bold text-slate-600">{t('agencyBranding.dnsInstructionsTitle')}</p>
                <div className="text-xs font-mono bg-white border border-[#e8e6e1] rounded-md p-3 space-y-1 overflow-x-auto">
                  <div><span className="text-slate-400">TXT</span> {form.dnsInstructions.txtRecordName} &rarr; {form.dnsInstructions.txtRecordValue}</div>
                  <div><span className="text-slate-400">CNAME</span> {form.dnsInstructions.cnameRecordName} &rarr; {form.dnsInstructions.cnameRecordValue}</div>
                </div>
                <p className="text-xs text-slate-500">{t('agencyBranding.dnsInstructionsHint')}</p>
                <button type="button" onClick={verifyDomain} disabled={verifying}
                  className="px-4 py-2 bg-slate-800 text-white text-xs font-semibold rounded-lg disabled:opacity-50">
                  {verifying ? t('agencyBranding.verifying') : t('agencyBranding.verifyDomain')}
                </button>
                {form.lastCheckedAt && (
                  <p className="text-xs text-slate-400">
                    {t('agencyBranding.lastChecked', { date: new Date(form.lastCheckedAt).toLocaleString() })}
                  </p>
                )}
                {form.lastCheckError && form.domainStatus === 'FAILED' && (
                  <p className="text-xs text-rose-600">{form.lastCheckError}</p>
                )}
              </div>
            )}
          </>
        )}

        {form.domainStatus === 'DNS_VERIFIED' && (
          <p className="text-xs text-blue-700 bg-blue-50 border border-blue-100 rounded-lg p-3 mt-2">
            {t('agencyBranding.dnsVerifiedNotice')}
          </p>
        )}
        {form.domainStatus === 'ACTIVE' && (
          <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg p-3 mt-2">
            {t('agencyBranding.activeNotice')}
          </p>
        )}
      </section>

      <div className="flex justify-end">
        <button onClick={save} disabled={saving} className="px-5 py-2.5 bg-brand-500 text-white rounded-lg text-sm font-semibold hover:bg-brand-600 disabled:opacity-60">
          {saving ? t('agencyBranding.saving') : t('agencyBranding.saveBranding')}
        </button>
      </div>
    </div>
  );
}
