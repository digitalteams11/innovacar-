import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Save, Mail, Palette, Shield, Database, Upload, X } from 'lucide-react';
import { PageHeader, TabGroup, FormField, TextInput, TextArea, ToggleSwitch } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminSettings() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [settings, setSettings] = useState<any>({});
  const [, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('branding');

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    try {
      const res = await superAdminApi.getPlatformSettings();
      setSettings(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await superAdminApi.updatePlatformSettings(settings);
      showToast(t('superAdmin.settings.saved'));
    } catch (err) {
      console.error(err);
      showToast('Failed to save settings', 'error');
    } finally {
      setSaving(false);
    }
  };

  const tabs = [
    { id: 'branding', label: 'Branding' },
    { id: 'smtp', label: 'SMTP' },
    { id: 'security', label: 'Security' },
    { id: 'features', label: 'Features' },
  ];

  const updateField = (field: string, value: any) => {
    setSettings((prev: any) => ({ ...prev, [field]: value }));
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

        {activeTab === 'smtp' && (
          <div className="max-w-2xl space-y-6">
            <div className="flex items-center gap-3 mb-6">
              <Mail size={20} className="text-[#0a0f2c] dark:text-white" />
              <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Email Configuration</h3>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField label={t('superAdmin.settings.smtpHost')}>
                <TextInput value={settings.smtpHost || ''} onChange={(v) => updateField('smtpHost', v)} placeholder="smtp.example.com" />
              </FormField>
              <FormField label={t('superAdmin.settings.smtpPort')}>
                <TextInput value={settings.smtpPort || ''} onChange={(v) => updateField('smtpPort', Number(v))} type="number" placeholder="587" />
              </FormField>
            </div>
            <FormField label={t('superAdmin.settings.smtpUsername')}>
              <TextInput value={settings.smtpUsername || ''} onChange={(v) => updateField('smtpUsername', v)} />
            </FormField>
            <FormField label="SMTP Password">
              <input type="password" value={settings.smtpPassword || ''} onChange={(e) => updateField('smtpPassword', e.target.value)} placeholder="••••••••" className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white placeholder:text-slate-400 outline-none" />
            </FormField>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField label={t('superAdmin.settings.fromEmail')}>
                <TextInput value={settings.fromEmail || ''} onChange={(v) => updateField('fromEmail', v)} placeholder="noreply@example.com" />
              </FormField>
              <FormField label={t('superAdmin.settings.fromName')}>
                <TextInput value={settings.fromName || ''} onChange={(v) => updateField('fromName', v)} placeholder="Innovax Technologies" />
              </FormField>
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
