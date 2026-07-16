import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Save, Mail, Palette, Shield, Database, Upload, X, Plus, Send, Trash2 } from 'lucide-react';
import { PageHeader, TabGroup, FormField, TextInput, TextArea, ToggleSwitch } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';
import { generateId } from '../../lib/generateId';

const defaultSmtp = {
  smtpHost: '', smtpPort: 587, smtpUsername: '', smtpPassword: '',
  hasPassword: false, smtpUseTls: true, smtpSslEnabled: false, smtpEnabled: false,
  fromEmail: '', fromName: '',
};

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

  // SMTP lives on its own state/endpoint (PUT /email/settings) — kept separate from the
  // generic platform settings above, which no longer persists SMTP fields (see backend:
  // updatePlatformSettings no longer touches smtpHost/etc). This is the one canonical SMTP
  // form; it mirrors the fuller Email Center SMTP tab in the web app.
  const [smtp, setSmtp] = useState(defaultSmtp);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [smtpSaving, setSmtpSaving] = useState(false);

  useEffect(() => {
    fetchSettings();
    fetchSmtp();
  }, []);

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

  const fetchSmtp = async () => {
    setSmtpLoading(true);
    try {
      const res = await superAdminApi.getSmtpSettings();
      const d = res.data ?? {};
      setSmtp({
        smtpHost: d.smtpHost ?? '', smtpPort: d.smtpPort ?? 587,
        smtpUsername: d.smtpUsername ?? '', smtpPassword: '',
        hasPassword: Boolean(d.hasPassword),
        smtpUseTls: d.smtpUseTls !== undefined ? Boolean(d.smtpUseTls) : true,
        smtpSslEnabled: Boolean(d.smtpSslEnabled),
        smtpEnabled: Boolean(d.smtpEnabled),
        fromEmail: d.fromEmail ?? '', fromName: d.fromName ?? '',
      });
    } catch (err) {
      console.error(err);
    } finally {
      setSmtpLoading(false);
    }
  };

  const updateSmtpField = (field: string, value: any) => {
    setSmtp((prev) => ({ ...prev, [field]: value }));
  };

  const handleSaveSmtp = async () => {
    setSmtpSaving(true);
    try {
      const payload: any = { ...smtp };
      if (!payload.smtpPassword) delete payload.smtpPassword;
      delete payload.hasPassword;
      const res = await superAdminApi.updateSmtpSettings(payload);
      const d = res.data ?? {};
      setSmtp((prev) => ({
        ...prev,
        smtpHost: d.smtpHost ?? prev.smtpHost, smtpPort: d.smtpPort ?? prev.smtpPort,
        smtpUsername: d.smtpUsername ?? prev.smtpUsername, smtpPassword: '',
        hasPassword: Boolean(d.hasPassword),
        smtpUseTls: d.smtpUseTls !== undefined ? Boolean(d.smtpUseTls) : prev.smtpUseTls,
        smtpSslEnabled: Boolean(d.smtpSslEnabled),
        smtpEnabled: Boolean(d.smtpEnabled),
        fromEmail: d.fromEmail ?? prev.fromEmail, fromName: d.fromName ?? prev.fromName,
      }));
      showToast('SMTP settings saved successfully', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Unable to save SMTP settings', 'error');
    } finally {
      setSmtpSaving(false);
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
    { id: 'smtp', label: 'SMTP' },
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
        {activeTab === 'smtp' ? (
          <button
            onClick={handleSaveSmtp}
            disabled={smtpSaving}
            className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft disabled:opacity-60"
          >
            <Save size={16} />
            {smtpSaving ? t('superAdmin.settings.saving') : t('superAdmin.settings.saveChanges')}
          </button>
        ) : (
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors shadow-soft disabled:opacity-60"
          >
            <Save size={16} />
            {saving ? t('superAdmin.settings.saving') : t('superAdmin.settings.saveChanges')}
          </button>
        )}
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
            <div className="flex items-center gap-3 mb-6">
              <Palette size={20} className="text-[#0a0f2c] dark:text-white" />
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Branding & Appearance</h3>
            </div>
            <FormField label={t('superAdmin.settings.platformName')}>
              <TextInput value={settings.platformName || ''} onChange={(v) => updateField('platformName', v)} />
            </FormField>
            <FormField label={t('superAdmin.settings.logoUrl')}>
              <div className="space-y-3">
                {/* Preview */}
                {settings.logoUrl && (
                  <div className="relative inline-block">
                    <img
                      src={settings.logoUrl}
                      alt="Logo preview"
                      className="h-16 w-auto rounded-xl border border-[#e8e6e1] dark:border-white/10 object-contain bg-white dark:bg-[#1e293b]"
                      onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                    />
                    <button
                      onClick={() => updateField('logoUrl', '')}
                      className="absolute -top-2 -right-2 w-5 h-5 bg-rose-500 text-white rounded-full flex items-center justify-center hover:bg-rose-600 transition-colors shadow-sm"
                      title="Remove logo"
                    >
                      <X size={12} />
                    </button>
                  </div>
                )}
                {/* Upload + URL input */}
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-2 px-4 py-2.5 bg-[#f5f5f0] dark:bg-white/5 border border-[#e8e6e1] dark:border-white/10 rounded-xl text-sm font-medium text-[#1e293b] dark:text-white hover:bg-[#e8e6e1] dark:hover:bg-white/10 transition-colors cursor-pointer shrink-0">
                    <Upload size={16} />
                    Upload
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          const reader = new FileReader();
                          reader.onloadend = () => updateField('logoUrl', reader.result as string);
                          reader.readAsDataURL(file);
                        }
                      }}
                    />
                  </label>
                  <TextInput
                    value={settings.logoUrl?.startsWith('data:') ? 'Image uploaded (' + Math.round(settings.logoUrl.length / 1024) + ' KB)' : (settings.logoUrl || '')}
                    onChange={(v) => updateField('logoUrl', v)}
                    placeholder="https://... or upload"
                    className="flex-1"
                    disabled={settings.logoUrl?.startsWith('data:')}
                  />
                </div>
              </div>
            </FormField>
            <FormField label={t('superAdmin.settings.primaryColor')}>
              <div className="flex items-center gap-3">
                <input type="color" value={settings.primaryColor || '#1e3a5f'} onChange={(e) => updateField('primaryColor', e.target.value)} className="w-12 h-10 rounded-lg border border-[#e8e6e1] dark:border-white/5 cursor-pointer" />
                <TextInput value={settings.primaryColor || ''} onChange={(v) => updateField('primaryColor', v)} className="flex-1" />
              </div>
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

        {activeTab === 'smtp' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center gap-3 mb-6">
              <Mail size={20} className="text-[#0a0f2c] dark:text-white" />
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Email Configuration</h3>
            </div>
            {smtpLoading && <p className="text-xs text-slate-400">Loading SMTP settings…</p>}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField label={t('superAdmin.settings.smtpHost')}>
                <TextInput value={smtp.smtpHost} onChange={(v) => updateSmtpField('smtpHost', v)} placeholder="smtp.zoho.com" />
              </FormField>
              <FormField label={t('superAdmin.settings.smtpPort')}>
                <TextInput value={smtp.smtpPort} onChange={(v) => updateSmtpField('smtpPort', Number(v))} type="number" placeholder="587" />
              </FormField>
            </div>
            <FormField label={t('superAdmin.settings.smtpUsername')}>
              <TextInput value={smtp.smtpUsername} onChange={(v) => updateSmtpField('smtpUsername', v)} placeholder="contact@yourdomain.com" />
            </FormField>
            <FormField label="SMTP Password">
              <input
                type="password"
                value={smtp.smtpPassword}
                onChange={(e) => updateSmtpField('smtpPassword', e.target.value)}
                placeholder={smtp.hasPassword ? 'Stored securely — enter a new password to replace' : 'Enter SMTP password'}
                className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white placeholder:text-slate-400 outline-none"
              />
              {smtp.hasPassword && !smtp.smtpPassword && <p className="text-xs text-emerald-600 mt-1">Password stored securely — enter a new one to replace.</p>}
            </FormField>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField label={t('superAdmin.settings.fromEmail')}>
                <TextInput value={smtp.fromEmail} onChange={(v) => updateSmtpField('fromEmail', v)} placeholder="noreply@example.com" />
              </FormField>
              <FormField label={t('superAdmin.settings.fromName')}>
                <TextInput value={smtp.fromName} onChange={(v) => updateSmtpField('fromName', v)} placeholder="Innovax Technologies" />
              </FormField>
            </div>
            <div className="flex flex-wrap gap-4 pt-2">
              <ToggleSwitch
                checked={smtp.smtpUseTls}
                onChange={(v) => setSmtp((prev) => ({ ...prev, smtpUseTls: v, smtpSslEnabled: v ? false : prev.smtpSslEnabled }))}
                label="Use TLS (STARTTLS, port 587)"
              />
              <ToggleSwitch
                checked={smtp.smtpSslEnabled}
                onChange={(v) => setSmtp((prev) => ({ ...prev, smtpSslEnabled: v, smtpUseTls: v ? false : prev.smtpUseTls }))}
                label="Use SSL (implicit, port 465)"
              />
              <ToggleSwitch checked={smtp.smtpEnabled} onChange={(v) => updateSmtpField('smtpEnabled', v)} label="Platform email enabled" />
            </div>
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
