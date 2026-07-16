import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Save, Palette, Shield, Database, Upload, Plus, Send, Trash2, RotateCcw, Image as ImageIcon } from 'lucide-react';
import { PageHeader, TabGroup, FormField, TextInput, TextArea, SelectInput, ToggleSwitch } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';
import { generateId } from '../../lib/generateId';
import { resolveMediaUrl } from '../../lib/utils';

const MAX_BRANDING_IMAGE_BYTES = 2 * 1024 * 1024;
const ALLOWED_BRANDING_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];

interface BrandingSettings {
  platformName?: string;
  companyName?: string;
  platformLogoUrl?: string;
  faviconUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  defaultCurrency?: string;
  defaultLanguage?: string;
  defaultTimezone?: string;
  supportEmail?: string;
  supportPhone?: string;
  legalCompanyName?: string;
  legalAddress?: string;
  websiteUrl?: string;
}

export default function SuperAdminSettings() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [settings, setSettings] = useState<any>({});
  const [, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [pushingTheme, setPushingTheme] = useState(false);
  const [usingFallbackSettings, setUsingFallbackSettings] = useState(false);
  const [activeTab, setActiveTab] = useState('branding');
  const [agencies, setAgencies] = useState<Array<{ id: number; name: string }>>([]);
  const [themeTarget, setThemeTarget] = useState('all');

  const [branding, setBranding] = useState<BrandingSettings>({});
  const [brandingLoading, setBrandingLoading] = useState(true);
  const [brandingSaving, setBrandingSaving] = useState(false);
  const [brandingResetting, setBrandingResetting] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [uploadingFavicon, setUploadingFavicon] = useState(false);

  useEffect(() => {
    fetchSettings();
    fetchBranding();
  }, []);

  const fetchBranding = async () => {
    setBrandingLoading(true);
    try {
      const res = await superAdminApi.getBrandingSettings();
      setBranding(res.data?.data || {});
    } catch (err) {
      console.error(err);
      showToast('Unable to load branding settings. Please try again later.', 'error');
    } finally {
      setBrandingLoading(false);
    }
  };

  const updateBrandingField = (field: keyof BrandingSettings, value: string) => {
    setBranding((prev) => ({ ...prev, [field]: value }));
  };

  const saveBranding = async () => {
    setBrandingSaving(true);
    try {
      const res = await superAdminApi.updateBrandingSettings(branding);
      setBranding(res.data?.data || branding);
      window.dispatchEvent(new Event('platform-branding-updated'));
      showToast('Branding settings saved successfully.', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err?.userMessage || 'Unable to save branding settings. Please try again later.', 'error');
    } finally {
      setBrandingSaving(false);
    }
  };

  const resetBranding = async () => {
    setBrandingResetting(true);
    try {
      const res = await superAdminApi.resetBrandingSettings();
      setBranding(res.data?.data || {});
      window.dispatchEvent(new Event('platform-branding-updated'));
      showToast('Branding settings reset to default.', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err?.userMessage || 'Unable to reset branding settings. Please try again later.', 'error');
    } finally {
      setBrandingResetting(false);
    }
  };

  const validateImageFile = (file: File) => {
    if (!ALLOWED_BRANDING_IMAGE_TYPES.includes(file.type)) {
      showToast('Only JPG, PNG, WebP, or SVG images are allowed.', 'error');
      return false;
    }
    if (file.size > MAX_BRANDING_IMAGE_BYTES) {
      showToast('Image exceeds the 2MB limit.', 'error');
      return false;
    }
    return true;
  };

  const handleLogoUpload = async (file: File) => {
    if (!validateImageFile(file)) return;
    setUploadingLogo(true);
    try {
      const res = await superAdminApi.uploadBrandingLogo(file);
      setBranding(res.data?.data || branding);
      window.dispatchEvent(new Event('platform-branding-updated'));
      showToast('Logo uploaded successfully.', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err?.userMessage || 'Unable to upload logo. Please try again later.', 'error');
    } finally {
      setUploadingLogo(false);
    }
  };

  const handleFaviconUpload = async (file: File) => {
    if (!validateImageFile(file)) return;
    setUploadingFavicon(true);
    try {
      const res = await superAdminApi.uploadBrandingFavicon(file);
      setBranding(res.data?.data || branding);
      window.dispatchEvent(new Event('platform-branding-updated'));
      showToast('Favicon uploaded successfully.', 'success');
    } catch (err: any) {
      console.error(err);
      showToast(err?.userMessage || 'Unable to upload favicon. Please try again later.', 'error');
    } finally {
      setUploadingFavicon(false);
    }
  };

  const fetchSettings = async () => {
    try {
      const [res, agenciesResponse] = await Promise.all([
        superAdminApi.getPlatformSettings(),
        superAdminApi.getAgencies(),
      ]);
      setSettings(res.data);
      setAgencies(agenciesResponse.data);
      setUsingFallbackSettings(false);
    } catch (err) {
      console.error(err);
      setUsingFallbackSettings(true);
      setSettings((current: any) => Object.keys(current || {}).length > 0 ? current : {
        platformName: 'RentCar',
        primaryColor: '#0a0f2c',
        maintenanceMode: false,
        themePresetsJson: '[]',
      });
      setAgencies([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await superAdminApi.updatePlatformSettings(settings);
      showToast(t('superAdmin.settings.saved'), 'success');
    } catch (err) {
      console.error(err);
      showToast('Unable to save settings. Please try again later.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const tabs = [
    { id: 'branding', label: 'Branding' },
    { id: 'themes', label: 'Agency Themes' },
    { id: 'security', label: 'Security' },
    { id: 'features', label: 'Features' },
  ];

  const updateField = (field: string, value: any) => {
    setSettings((prev: any) => ({ ...prev, [field]: value }));
  };

  const defaultPreset = {
    id: generateId('theme'),
    name: 'Luxury Gold',
    appearance: {
      mode: 'auto',
      preset: 'luxury-gold',
      primaryColor: '#b88a46',
      secondaryColor: '#258269',
      sidebarColor: '#181510',
      glassColor: '#fffaf0',
      glassIntensity: 70,
      blur: 28,
      opacity: 74,
      depth: 68,
      shadowStrength: 58,
      animationSpeed: 100,
      cornerRadius: 8,
      fontFamily: 'Inter',
      cardDensity: 'comfortable',
      buttonStyle: 'solid',
      sidebarStyle: 'floating',
      whiteLabelMode: false,
    },
  };

  const themePresets = (() => {
    try {
      const parsed = JSON.parse(settings.themePresetsJson || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  })() as Array<typeof defaultPreset>;

  const setThemePresets = (presets: Array<typeof defaultPreset>) => {
    updateField('themePresetsJson', JSON.stringify(presets));
  };

  const addThemePreset = () => {
    setThemePresets([...themePresets, { ...defaultPreset, id: generateId('theme'), name: `Theme ${themePresets.length + 1}` }]);
  };

  const updateThemePreset = (id: string, key: string, value: string | number) => {
    setThemePresets(themePresets.map((preset) => preset.id === id
      ? {
          ...preset,
          ...(key === 'name'
            ? { name: String(value) }
            : { appearance: { ...preset.appearance, [key]: value } }),
        }
      : preset));
  };

  const pushTheme = async (preset: typeof defaultPreset) => {
    setPushingTheme(true);
    try {
      const applyToAll = themeTarget === 'all';
      await superAdminApi.applyTheme({
        appearance: preset.appearance,
        applyToAll,
        tenantIds: applyToAll ? undefined : [Number(themeTarget)],
      });
      showToast(`"${preset.name}" applied successfully`, 'success');
    } catch (err) {
      console.error(err);
      showToast('Unable to apply this theme. Please try again later.', 'error');
    } finally {
      setPushingTheme(false);
    }
  };

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title={t('superAdmin.settings.title')} subtitle={t('superAdmin.settings.subtitle')}>
        <button
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft disabled:opacity-60"
        >
          <Save size={16} />
          {saving ? t('superAdmin.settings.saving') : t('superAdmin.settings.saveChanges')}
        </button>
      </PageHeader>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      {usingFallbackSettings && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
          Settings service is temporarily unavailable. Default values are being used.
        </div>
      )}

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'branding' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center justify-between gap-3 mb-6">
              <div className="flex items-center gap-3">
                <Palette size={20} className="text-[#0a0f2c] dark:text-white" />
                <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Branding & Appearance</h3>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={resetBranding}
                  disabled={brandingResetting || brandingLoading}
                  className="flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-semibold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors disabled:opacity-50"
                >
                  <RotateCcw size={14} />
                  {brandingResetting ? 'Resetting...' : 'Reset to default'}
                </button>
                <button
                  type="button"
                  onClick={saveBranding}
                  disabled={brandingSaving || brandingLoading}
                  className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2 rounded-xl text-xs font-semibold transition-colors disabled:opacity-60"
                >
                  <Save size={14} />
                  {brandingSaving ? 'Saving...' : 'Save Branding'}
                </button>
              </div>
            </div>

            <FormField label="Platform Name">
              <TextInput value={branding.platformName || ''} onChange={(v) => updateBrandingField('platformName', v)} />
            </FormField>
            <FormField label="Company Name (legal)">
              <TextInput value={branding.companyName || ''} onChange={(v) => updateBrandingField('companyName', v)} placeholder="Innovax Technologies" />
            </FormField>

            <FormField label="Platform Logo">
              <div className="space-y-3">
                {branding.platformLogoUrl && (
                  <div className="relative inline-block">
                    <img
                      src={resolveMediaUrl(branding.platformLogoUrl) || ''}
                      alt="Logo preview"
                      className="h-16 w-auto rounded-xl border border-[#e8e6e1] dark:border-white/10 object-contain bg-white dark:bg-[#1e293b]"
                      onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                    />
                  </div>
                )}
                <label className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#f5f5f0] dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm font-medium text-[#1e293b] dark:text-white hover:bg-[#e8e6e1] dark:hover:bg-white/10 transition-colors cursor-pointer">
                  <Upload size={16} />
                  {uploadingLogo ? 'Uploading...' : 'Upload logo'}
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/svg+xml"
                    className="hidden"
                    disabled={uploadingLogo}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) handleLogoUpload(file);
                      e.target.value = '';
                    }}
                  />
                </label>
              </div>
            </FormField>

            <FormField label="Favicon">
              <div className="space-y-3">
                {branding.faviconUrl && (
                  <div className="relative inline-block">
                    <img
                      src={resolveMediaUrl(branding.faviconUrl) || ''}
                      alt="Favicon preview"
                      className="h-10 w-10 rounded-lg border border-[#e8e6e1] dark:border-white/10 object-contain bg-white dark:bg-[#1e293b]"
                      onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                    />
                  </div>
                )}
                <label className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#f5f5f0] dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm font-medium text-[#1e293b] dark:text-white hover:bg-[#e8e6e1] dark:hover:bg-white/10 transition-colors cursor-pointer">
                  <ImageIcon size={16} />
                  {uploadingFavicon ? 'Uploading...' : 'Upload favicon'}
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/svg+xml"
                    className="hidden"
                    disabled={uploadingFavicon}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) handleFaviconUpload(file);
                      e.target.value = '';
                    }}
                  />
                </label>
              </div>
            </FormField>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <FormField label="Primary Color">
                <div className="flex items-center gap-2">
                  <input type="color" value={branding.primaryColor || '#0a0f2c'} onChange={(e) => updateBrandingField('primaryColor', e.target.value)} className="w-10 h-10 rounded-lg border border-[#e8e6e1] dark:border-white/5 cursor-pointer" />
                  <TextInput value={branding.primaryColor || ''} onChange={(v) => updateBrandingField('primaryColor', v)} className="flex-1" />
                </div>
              </FormField>
              <FormField label="Secondary Color">
                <div className="flex items-center gap-2">
                  <input type="color" value={branding.secondaryColor || '#b69152'} onChange={(e) => updateBrandingField('secondaryColor', e.target.value)} className="w-10 h-10 rounded-lg border border-[#e8e6e1] dark:border-white/5 cursor-pointer" />
                  <TextInput value={branding.secondaryColor || ''} onChange={(v) => updateBrandingField('secondaryColor', v)} className="flex-1" />
                </div>
              </FormField>
              <FormField label="Accent Color">
                <div className="flex items-center gap-2">
                  <input type="color" value={branding.accentColor || '#22c55e'} onChange={(e) => updateBrandingField('accentColor', e.target.value)} className="w-10 h-10 rounded-lg border border-[#e8e6e1] dark:border-white/5 cursor-pointer" />
                  <TextInput value={branding.accentColor || ''} onChange={(v) => updateBrandingField('accentColor', v)} className="flex-1" />
                </div>
              </FormField>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <FormField label="Default Currency">
                <SelectInput
                  value={branding.defaultCurrency || 'MAD'}
                  onChange={(v) => updateBrandingField('defaultCurrency', v)}
                  options={[
                    { value: 'MAD', label: 'MAD (Dirham)' },
                    { value: 'EUR', label: 'EUR (Euro)' },
                    { value: 'USD', label: 'USD (Dollar)' },
                  ]}
                />
              </FormField>
              <FormField label="Default Language">
                <SelectInput
                  value={branding.defaultLanguage || 'en'}
                  onChange={(v) => updateBrandingField('defaultLanguage', v)}
                  options={[
                    { value: 'en', label: 'English' },
                    { value: 'fr', label: 'Français' },
                    { value: 'ar', label: 'العربية' },
                  ]}
                />
              </FormField>
              <FormField label="Default Timezone">
                <TextInput value={branding.defaultTimezone || ''} onChange={(v) => updateBrandingField('defaultTimezone', v)} placeholder="Africa/Casablanca" />
              </FormField>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField label="Support Email">
                <TextInput value={branding.supportEmail || ''} onChange={(v) => updateBrandingField('supportEmail', v)} placeholder="support@innovax.tech" />
              </FormField>
              <FormField label="Support Phone">
                <TextInput value={branding.supportPhone || ''} onChange={(v) => updateBrandingField('supportPhone', v)} placeholder="+212 ..." />
              </FormField>
            </div>

            <FormField label="Legal Company Name">
              <TextInput value={branding.legalCompanyName || ''} onChange={(v) => updateBrandingField('legalCompanyName', v)} placeholder="Innovax Technologies" />
            </FormField>
            <FormField label="Legal Address">
              <TextArea value={branding.legalAddress || ''} onChange={(v) => updateBrandingField('legalAddress', v)} rows={2} />
            </FormField>
            <FormField label="Website URL">
              <TextInput value={branding.websiteUrl || ''} onChange={(v) => updateBrandingField('websiteUrl', v)} placeholder="https://innovax.tech" />
            </FormField>

            <div className="pt-4 border-t border-[#e8e6e1]/40 dark:border-white/5">
              <ToggleSwitch checked={settings.maintenanceMode || false} onChange={(v) => updateField('maintenanceMode', v)} label={t('superAdmin.settings.maintenanceMode')} />
              <p className="text-xs text-slate-500 mt-1 ml-14">{t('superAdmin.settings.maintenanceDesc')}</p>
            </div>
            {settings.maintenanceMode && (
              <FormField label={t('superAdmin.settings.maintenanceMessage')}>
                <TextArea value={settings.maintenanceMessage || ''} onChange={(v) => updateField('maintenanceMessage', v)} rows={3} />
              </FormField>
            )}
          </div>
        )}

        {activeTab === 'themes' && (
          <div className="space-y-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <div className="flex items-center gap-3">
                  <Palette size={20} className="text-[#b69152]" />
                  <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Agency Theme Studio</h3>
                </div>
                <p className="mt-2 max-w-2xl text-sm text-slate-500">
                  Create governed visual presets and deliver them directly to agency workspaces.
                </p>
              </div>
              <div className="flex flex-col gap-3 sm:flex-row">
                <select
                  value={themeTarget}
                  onChange={(event) => setThemeTarget(event.target.value)}
                  className="min-w-52 rounded-lg border border-[#d8d5cd] bg-white px-3 py-2.5 text-sm text-[#242722] outline-none dark:border-white/10 dark:bg-white/5 dark:text-white"
                >
                  <option value="all">All agencies</option>
                  {agencies.map((agency) => (
                    <option key={agency.id} value={agency.id}>{agency.name}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={addThemePreset}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-[#242722] px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-[#343830]"
                >
                  <Plus size={16} />
                  New preset
                </button>
              </div>
            </div>

            {themePresets.length === 0 ? (
              <button
                type="button"
                onClick={() => setThemePresets([defaultPreset])}
                className="data-surface flex min-h-52 w-full flex-col items-center justify-center gap-3 p-8 text-center"
              >
                <Palette size={28} className="text-[#b69152]" />
                <span className="font-semibold text-[#242722] dark:text-white">Create your first corporate theme</span>
                <span className="text-sm text-slate-500">Colors, glass depth, radius and layout behavior stay synchronized.</span>
              </button>
            ) : (
              <div className="grid gap-4 xl:grid-cols-2">
                {themePresets.map((preset) => (
                  <section key={preset.id} className="data-surface overflow-hidden">
                    <div
                      className="h-24 border-b border-black/5"
                      style={{
                        background: `linear-gradient(120deg, ${preset.appearance.sidebarColor}, ${preset.appearance.primaryColor} 65%, ${preset.appearance.secondaryColor})`,
                      }}
                    />
                    <div className="space-y-5 p-5">
                      <div className="flex items-center gap-3">
                        <input
                          value={preset.name}
                          onChange={(event) => updateThemePreset(preset.id, 'name', event.target.value)}
                          className="min-w-0 flex-1 bg-transparent text-lg font-semibold text-[#242722] outline-none dark:text-white"
                          aria-label="Theme name"
                        />
                        <button
                          type="button"
                          onClick={() => setThemePresets(themePresets.filter((item) => item.id !== preset.id))}
                          className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10"
                          title="Delete theme"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>

                      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                        {[
                          ['primaryColor', 'Primary'],
                          ['secondaryColor', 'Accent'],
                          ['sidebarColor', 'Sidebar'],
                        ].map(([key, label]) => (
                          <label key={key} className="space-y-2 text-xs font-medium text-slate-500">
                            {label}
                            <div className="flex items-center gap-2">
                              <input
                                type="color"
                                value={String(preset.appearance[key as keyof typeof preset.appearance])}
                                onChange={(event) => updateThemePreset(preset.id, key, event.target.value)}
                                className="h-9 w-11 cursor-pointer rounded-lg border-0 bg-transparent"
                              />
                              <span className="truncate font-mono text-[11px]">{String(preset.appearance[key as keyof typeof preset.appearance])}</span>
                            </div>
                          </label>
                        ))}
                      </div>

                      <div className="grid gap-4 sm:grid-cols-3">
                        {[
                          ['glassIntensity', 'Glass', 0, 100],
                          ['blur', 'Blur', 0, 40],
                          ['cornerRadius', 'Radius', 0, 16],
                        ].map(([key, label, min, max]) => (
                          <label key={String(key)} className="space-y-2 text-xs font-medium text-slate-500">
                            <span className="flex justify-between"><span>{label}</span><span>{String(preset.appearance[key as keyof typeof preset.appearance])}</span></span>
                            <input
                              type="range"
                              min={Number(min)}
                              max={Number(max)}
                              value={Number(preset.appearance[key as keyof typeof preset.appearance])}
                              onChange={(event) => updateThemePreset(preset.id, String(key), Number(event.target.value))}
                              className="w-full accent-[#b69152]"
                            />
                          </label>
                        ))}
                      </div>

                      <button
                        type="button"
                        disabled={pushingTheme}
                        onClick={() => pushTheme(preset)}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-[#b69152] px-4 py-2.5 text-sm font-semibold text-[#171817] transition hover:bg-[#c5a265] disabled:opacity-50"
                      >
                        <Send size={16} />
                        {pushingTheme ? 'Applying theme...' : `Push to ${themeTarget === 'all' ? 'all agencies' : 'selected agency'}`}
                      </button>
                    </div>
                  </section>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'security' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center gap-3 mb-6">
              <Shield size={20} className="text-[#0a0f2c] dark:text-white" />
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Security Settings</h3>
            </div>
            <FormField label={t('superAdmin.settings.apiRateLimit')}>
              <TextInput value={settings.apiRateLimit || ''} onChange={(v) => updateField('apiRateLimit', Number(v))} type="number" placeholder="1000" />
            </FormField>
            <FormField label={t('superAdmin.settings.sessionTimeout')}>
              <TextInput value={settings.sessionTimeoutMinutes || ''} onChange={(v) => updateField('sessionTimeoutMinutes', Number(v))} type="number" placeholder="60" />
            </FormField>
            <FormField label={t('superAdmin.settings.maxLoginAttempts')}>
              <TextInput value={settings.maxLoginAttempts || ''} onChange={(v) => updateField('maxLoginAttempts', Number(v))} type="number" placeholder="5" />
            </FormField>
            <FormField label={t('superAdmin.settings.lockoutDuration')}>
              <TextInput value={settings.lockoutDurationMinutes || ''} onChange={(v) => updateField('lockoutDurationMinutes', Number(v))} type="number" placeholder="30" />
            </FormField>
            <ToggleSwitch checked={settings.require2fa || false} onChange={(v) => updateField('require2fa', v)} label="Require 2FA for Super Admins" />
          </div>
        )}

        {activeTab === 'features' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center gap-3 mb-6">
              <Database size={20} className="text-[#0a0f2c] dark:text-white" />
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Feature Toggles</h3>
            </div>
            <div className="space-y-4">
              {[
                { key: 'featureGps', label: 'GPS Tracking Module' },
                { key: 'featureContracts', label: 'Digital Contracts' },
                { key: 'featureAnalytics', label: 'Advanced Analytics' },
                { key: 'featureWhiteLabel', label: 'White Label Features' },
                { key: 'featureApi', label: 'Public API Access' },
                { key: 'featureAi', label: 'AI Features (Beta)' },
              ].map((feature) => (
                <div key={feature.key} className="flex items-center justify-between p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                  <div>
                    <p className="text-sm font-medium text-[#1e293b] dark:text-white">{feature.label}</p>
                    <p className="text-xs text-slate-500">Toggle availability across all tenants</p>
                  </div>
                  <ToggleSwitch checked={settings[feature.key] !== false} onChange={(v) => updateField(feature.key, v)} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
