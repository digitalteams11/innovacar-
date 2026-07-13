import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Save } from 'lucide-react';
import { FormField, TextInput, TextArea, ToggleSwitch, FilterSelect } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiSettingsPayload, AiProviderRow } from './types';

export default function AiSettingsTab({ onChanged }: { onChanged?: () => void }) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [settings, setSettings] = useState<AiSettingsPayload>({});
  const [providers, setProviders] = useState<AiProviderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [settingsRes, providersRes] = await Promise.all([
        aiService.getSettings(),
        aiService.getProviders(),
      ]);
      setSettings(settingsRes.data?.data || {});
      setProviders(providersRes.data?.data || []);
    } catch {
      showToast(t('superAdmin.ai.settings.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const update = (field: keyof AiSettingsPayload, value: any) => {
    setSettings((prev) => ({ ...prev, [field]: value }));
  };

  const save = async () => {
    setSaving(true);
    try {
      const { data } = await aiService.updateSettings(settings);
      setSettings(data?.data || settings);
      showToast(t('superAdmin.ai.settings.saveSuccess'), 'success');
      onChanged?.();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.settings.saveError'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const toggles: Array<{ key: keyof AiSettingsPayload; label: string }> = [
    { key: 'enableChat', label: t('superAdmin.ai.settings.featureChat') },
    { key: 'enableReports', label: t('superAdmin.ai.settings.featureReports') },
    { key: 'enableTranslations', label: t('superAdmin.ai.settings.featureTranslations') },
    { key: 'enableSupportAssistant', label: t('superAdmin.ai.settings.featureSupport') },
    { key: 'enableGuideGenerator', label: t('superAdmin.ai.settings.featureGuide') },
    { key: 'enableAutomationSuggestions', label: t('superAdmin.ai.settings.featureAutomationSuggestions') },
    { key: 'enableImageGeneration', label: t('superAdmin.ai.settings.featureImage') },
  ];

  if (loading) {
    return <p className="text-sm text-slate-400">{t('superAdmin.ai.common.loading')}</p>;
  }

  return (
    <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6 max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{t('superAdmin.ai.settings.title')}</h3>
        <button
          onClick={save}
          disabled={saving}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50"
        >
          <Save size={16} />
          {saving ? t('superAdmin.ai.common.saving') : t('superAdmin.ai.common.save')}
        </button>
      </div>

      <ToggleSwitch checked={settings.globalEnabled || false} onChange={(v) => update('globalEnabled', v)} label={t('superAdmin.ai.settings.globalEnabled')} />

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label={t('superAdmin.ai.settings.activeProvider')} hint={t('superAdmin.ai.settings.activeProviderHint')}>
          <TextInput value={settings.activeProviderName || t('superAdmin.ai.overview.none')} onChange={() => {}} disabled />
        </FormField>
        <FormField label={t('superAdmin.ai.settings.fallbackProvider')}>
          <FilterSelect
            options={providers.map((p) => ({ value: String(p.id), label: p.name }))}
            value={settings.fallbackProviderId ? String(settings.fallbackProviderId) : ''}
            onChange={(v) => update('fallbackProviderId', v ? Number(v) : undefined)}
            placeholder={t('superAdmin.ai.overview.none')}
          />
        </FormField>
      </div>

      <ToggleSwitch checked={settings.fallbackEnabled || false} onChange={(v) => update('fallbackEnabled', v)} label={t('superAdmin.ai.settings.fallbackEnabled')} />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <FormField label={t('superAdmin.ai.settings.temperature')}>
          <TextInput value={settings.temperature ?? ''} onChange={(v) => update('temperature', Number(v))} type="number" />
        </FormField>
        <FormField label={t('superAdmin.ai.settings.maxOutputTokens')}>
          <TextInput value={settings.maxOutputTokens ?? ''} onChange={(v) => update('maxOutputTokens', Number(v))} type="number" />
        </FormField>
        <FormField label={t('superAdmin.ai.settings.timeout')}>
          <TextInput value={settings.requestTimeoutSeconds ?? ''} onChange={(v) => update('requestTimeoutSeconds', Number(v))} type="number" />
        </FormField>
      </div>

      <FormField label={t('superAdmin.ai.settings.maxRetries')}>
        <TextInput value={settings.maxRetries ?? ''} onChange={(v) => update('maxRetries', Number(v))} type="number" />
      </FormField>

      <FormField label={t('superAdmin.ai.settings.systemPrompt')} hint={t('superAdmin.ai.settings.systemPromptHint')}>
        <TextArea value={settings.systemPrompt || ''} onChange={(v) => update('systemPrompt', v)} rows={4} />
      </FormField>

      <div className="pt-4 border-t border-[#e8e6e1]/40 dark:border-white/5 space-y-4">
        <h4 className="text-sm font-bold text-[#1e293b] dark:text-white">{t('superAdmin.ai.settings.featureToggles')}</h4>
        {toggles.map((toggle) => (
          <ToggleSwitch
            key={String(toggle.key)}
            checked={Boolean(settings[toggle.key])}
            onChange={(v) => update(toggle.key, v)}
            label={toggle.label}
          />
        ))}
      </div>

      <div className="pt-4 border-t border-[#e8e6e1]/40 dark:border-white/5 grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label={t('superAdmin.ai.settings.dailyRequestLimit')}>
          <TextInput value={settings.dailyRequestLimit ?? ''} onChange={(v) => update('dailyRequestLimit', Number(v))} type="number" />
        </FormField>
        <FormField label={t('superAdmin.ai.settings.monthlyTokenLimit')}>
          <TextInput value={settings.monthlyTokenLimit ?? ''} onChange={(v) => update('monthlyTokenLimit', Number(v))} type="number" />
        </FormField>
      </div>

      <ToggleSwitch checked={settings.auditAllActions || false} onChange={(v) => update('auditAllActions', v)} label={t('superAdmin.ai.settings.auditAllActions')} />
    </div>
  );
}
