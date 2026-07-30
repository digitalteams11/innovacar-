import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, Save, Archive } from 'lucide-react';
import { useReportPreferences } from '../../hooks/useReports';
import { useFeatureAccess } from '../../context/FeatureAccessContext';
import { useToast } from '../../context/ToastContext';
import LockedFeatureCard from '../LockedFeatureCard';

export default function ReportsTab() {
  const { t } = useTranslation();
  const { hasFeature, getFeature, loading: featureLoading } = useFeatureAccess();
  const { showToast } = useToast();
  const { preferences, loading, savePreferences } = useReportPreferences();
  const [form, setForm] = useState({
    reportEnabled: true,
    monthlyReportEnabled: true,
    yearlyReportEnabled: true,
    reportLanguage: 'fr',
    timezone: '',
    primaryRecipientEmail: '',
    additionalRecipientEmails: '',
    includeAiSummary: true,
    includeClientDebtDetail: true,
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (preferences) {
      setForm({
        reportEnabled: preferences.reportEnabled,
        monthlyReportEnabled: preferences.monthlyReportEnabled,
        yearlyReportEnabled: preferences.yearlyReportEnabled,
        reportLanguage: preferences.reportLanguage || 'fr',
        timezone: preferences.timezone || '',
        primaryRecipientEmail: preferences.primaryRecipientEmail || '',
        additionalRecipientEmails: preferences.additionalRecipientEmails || '',
        includeAiSummary: preferences.includeAiSummary,
        includeClientDebtDetail: preferences.includeClientDebtDetail,
      });
    }
  }, [preferences]);

  if (featureLoading || loading) {
    return <div className="min-h-[30vh] flex items-center justify-center"><Loader2 className="w-6 h-6 animate-spin text-brand-500" /></div>;
  }

  // Reporting features are Complete-plan-only; a Basic/Trial tenant sees the
  // upsell card instead of a settings form they cannot use — the backend
  // still independently enforces this on every generation/email call.
  if (!hasFeature('AUTOMATED_MONTHLY_REPORT') && !hasFeature('AUTOMATED_YEARLY_REPORT')) {
    const access = getFeature('AUTOMATED_MONTHLY_REPORT') || {
      code: 'AUTOMATED_MONTHLY_REPORT', enabled: false, name: 'Automated Reports', requiredPlan: 'Complete',
    };
    return <LockedFeatureCard feature={access} />;
  }

  const handleSave = async () => {
    setSaving(true);
    try {
      await savePreferences(form);
      showToast(t('settings.reports.saved', 'Report preferences saved'));
    } catch {
      showToast(t('settings.reports.saveFailed', 'Failed to save report preferences'), 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center gap-2 text-lg font-semibold">
        <Archive className="w-5 h-5" />
        {t('settings.reports.title', 'Automated Reports')}
      </div>

      <label className="flex items-center justify-between p-3 rounded-lg border border-[var(--border-subtle)]">
        <span>{t('settings.reports.enabled', 'Enable automated reporting')}</span>
        <input type="checkbox" checked={form.reportEnabled}
          onChange={(e) => setForm({ ...form, reportEnabled: e.target.checked })} />
      </label>

      {hasFeature('AUTOMATED_MONTHLY_REPORT') && (
        <label className="flex items-center justify-between p-3 rounded-lg border border-[var(--border-subtle)]">
          <span>{t('settings.reports.monthlyEnabled', 'Monthly report')}</span>
          <input type="checkbox" checked={form.monthlyReportEnabled}
            onChange={(e) => setForm({ ...form, monthlyReportEnabled: e.target.checked })} />
        </label>
      )}

      {hasFeature('AUTOMATED_YEARLY_REPORT') && (
        <label className="flex items-center justify-between p-3 rounded-lg border border-[var(--border-subtle)]">
          <span>{t('settings.reports.yearlyEnabled', 'Yearly report')}</span>
          <input type="checkbox" checked={form.yearlyReportEnabled}
            onChange={(e) => setForm({ ...form, yearlyReportEnabled: e.target.checked })} />
        </label>
      )}

      <div>
        <label className="block text-sm mb-1">{t('settings.reports.language', 'Report language')}</label>
        <select
          value={form.reportLanguage}
          onChange={(e) => setForm({ ...form, reportLanguage: e.target.value })}
          className="w-full p-2 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)]"
        >
          <option value="fr">Français</option>
          <option value="en">English</option>
          <option value="ar">العربية</option>
        </select>
      </div>

      <div>
        <label className="block text-sm mb-1">{t('settings.reports.timezone', 'Timezone')}</label>
        <input
          type="text"
          placeholder="Africa/Casablanca"
          value={form.timezone}
          onChange={(e) => setForm({ ...form, timezone: e.target.value })}
          className="w-full p-2 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)]"
        />
      </div>

      <div>
        <label className="block text-sm mb-1">{t('settings.reports.primaryRecipient', 'Primary recipient email')}</label>
        <input
          type="email"
          value={form.primaryRecipientEmail}
          onChange={(e) => setForm({ ...form, primaryRecipientEmail: e.target.value })}
          className="w-full p-2 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)]"
        />
      </div>

      <div>
        <label className="block text-sm mb-1">{t('settings.reports.additionalRecipients', 'Additional recipients (comma-separated)')}</label>
        <input
          type="text"
          value={form.additionalRecipientEmails}
          onChange={(e) => setForm({ ...form, additionalRecipientEmails: e.target.value })}
          className="w-full p-2 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)]"
        />
      </div>

      {hasFeature('AI_REPORT_SUMMARY') && (
        <label className="flex items-center justify-between p-3 rounded-lg border border-[var(--border-subtle)]">
          <span>{t('settings.reports.includeAiSummary', 'Include AI executive summary')}</span>
          <input type="checkbox" checked={form.includeAiSummary}
            onChange={(e) => setForm({ ...form, includeAiSummary: e.target.checked })} />
        </label>
      )}

      <label className="flex items-center justify-between p-3 rounded-lg border border-[var(--border-subtle)]">
        <span>{t('settings.reports.includeClientDebt', 'Include detailed client debt section')}</span>
        <input type="checkbox" checked={form.includeClientDebtDetail}
          onChange={(e) => setForm({ ...form, includeClientDebtDetail: e.target.checked })} />
      </label>

      <button onClick={handleSave} disabled={saving} className="btn-primary flex items-center gap-2 px-4 py-2 rounded-lg">
        {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
        {t('common.save', 'Save')}
      </button>
    </div>
  );
}
