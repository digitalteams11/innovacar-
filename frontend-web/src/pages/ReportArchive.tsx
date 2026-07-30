import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Archive, Download, Loader2, RefreshCw, Send, AlertCircle, Plus } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { FilterChips } from '../components/FilterChips';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';
import { useReports, type ReportRow } from '../hooks/useReports';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import { useToast } from '../context/ToastContext';

const STATUS_BADGE: Record<string, string> = {
  SENT: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400',
  GENERATED: 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  EMAIL_PENDING: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  GENERATING: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  PENDING: 'bg-slate-500/15 text-slate-500',
  FAILED: 'bg-red-500/15 text-red-600 dark:text-red-400',
  CANCELLED: 'bg-slate-500/15 text-slate-500',
};

export default function ReportArchive() {
  const { t } = useTranslation();
  const { hasFeature } = useFeatureAccess();
  const { showToast } = useToast();
  const { reports, loading, error, fetchReports, generateReport, resendReport, downloadReport } = useReports();
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [generating, setGenerating] = useState(false);

  const canManualGenerate = hasFeature('MANUAL_REPORT_EXPORT');

  const filtered = useMemo(
    () => reports.filter((r) => typeFilter === 'ALL' || r.reportType === typeFilter),
    [reports, typeFilter],
  );

  const periodLabel = (r: ReportRow) => {
    const start = new Date(r.periodStart);
    if (r.reportType === 'YEARLY') return `${start.getFullYear()}`;
    return start.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  };

  const handleDownload = async (r: ReportRow) => {
    setBusyId(r.id);
    try {
      await downloadReport(r.id, `report_${r.reportType.toLowerCase()}_${periodLabel(r)}.pdf`);
    } catch {
      showToast(t('reports.downloadFailed', 'Unable to download report'), 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleResend = async (r: ReportRow) => {
    setBusyId(r.id);
    try {
      const result = await resendReport(r.id);
      showToast(result.success
        ? t('reports.resendSuccess', 'Report re-sent')
        : t('reports.resendFailed', 'Failed to send email'));
    } catch {
      showToast(t('reports.resendFailed', 'Failed to send email'), 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleGenerate = async (type: 'MONTHLY' | 'YEARLY') => {
    setGenerating(true);
    try {
      const result = await generateReport(type);
      if (result.success) {
        showToast(t('reports.generateSuccess', 'Report generated'));
      } else {
        showToast(t('reports.generateSkipped', 'Report not generated: {{reason}}', { reason: result.reason }), 'error');
      }
    } catch {
      showToast(t('reports.generateFailed', 'Failed to generate report'), 'error');
    } finally {
      setGenerating(false);
    }
  };

  const badge = (status: string) => (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE[status] || 'bg-slate-500/15 text-slate-500'}`}>
      {t(`reports.status.${status}`, status)}
    </span>
  );

  const rowActions = (r: ReportRow) => (
    <div className="flex items-center gap-2">
      <button
        onClick={() => handleDownload(r)}
        disabled={busyId === r.id || !['GENERATED', 'SENT', 'EMAIL_PENDING'].includes(r.status)}
        className="p-2 rounded-lg hover:bg-[var(--bg-hover)] disabled:opacity-30"
        title={t('reports.download', 'Download')}
      >
        {busyId === r.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
      </button>
      {hasFeature('REPORT_ARCHIVE') && (
        <button
          onClick={() => handleResend(r)}
          disabled={busyId === r.id || !['GENERATED', 'SENT', 'EMAIL_PENDING'].includes(r.status)}
          className="p-2 rounded-lg hover:bg-[var(--bg-hover)] disabled:opacity-30"
          title={t('reports.resend', 'Resend email')}
        >
          <Send className="w-4 h-4" />
        </button>
      )}
    </div>
  );

  return (
    <div className="p-4 lg:p-6 max-w-6xl mx-auto">
      <GlassPageHeader
        title={t('reports.archiveTitle', 'Report Archive')}
        subtitle={t('reports.archiveSubtitle', 'Monthly and yearly financial reports, generated automatically or on demand')}
        icon={Archive}
        actions={canManualGenerate ? (
          <div className="flex gap-2">
            <button
              onClick={() => handleGenerate('MONTHLY')}
              disabled={generating}
              className="btn-primary flex items-center gap-2 px-3 py-2 rounded-lg text-sm"
            >
              {generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
              {t('reports.generateMonthly', 'Generate Monthly')}
            </button>
            <button
              onClick={() => handleGenerate('YEARLY')}
              disabled={generating}
              className="px-3 py-2 rounded-lg text-sm border border-[var(--border-subtle)]"
            >
              {t('reports.generateYearly', 'Generate Yearly')}
            </button>
          </div>
        ) : undefined}
      />

      <FilterChips
        className="mb-4"
        options={[
          { id: 'ALL', label: t('reports.all', 'All') },
          { id: 'MONTHLY', label: t('reports.monthly', 'Monthly') },
          { id: 'YEARLY', label: t('reports.yearly', 'Yearly') },
        ]}
        activeId={typeFilter}
        onChange={setTypeFilter}
      />

      {loading && (
        <div className="min-h-[30vh] flex items-center justify-center">
          <Loader2 className="w-6 h-6 animate-spin text-brand-500" />
        </div>
      )}

      {!loading && error && (
        <div className="flex items-center gap-2 p-4 rounded-lg bg-red-500/10 text-red-600 dark:text-red-400">
          <AlertCircle className="w-4 h-4" />
          <span>{error}</span>
          <button onClick={() => fetchReports()} className="ml-auto underline text-sm flex items-center gap-1">
            <RefreshCw className="w-3 h-3" /> {t('common.retry', 'Retry')}
          </button>
        </div>
      )}

      {!loading && !error && filtered.length === 0 && (
        <div className="text-center py-16 text-[var(--text-muted)]">
          {t('reports.empty', 'No reports yet.')}
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <ResponsiveDataView
          desktop={
            <div className="overflow-x-auto rounded-xl border border-[var(--border-subtle)]">
              <table className="w-full text-sm">
                <thead className="bg-[var(--bg-card)]">
                  <tr className="text-left text-[var(--text-muted)]">
                    <th className="p-3">{t('reports.period', 'Period')}</th>
                    <th className="p-3">{t('reports.type', 'Type')}</th>
                    <th className="p-3">{t('reports.status', 'Status')}</th>
                    <th className="p-3">{t('reports.emailStatus', 'Email')}</th>
                    <th className="p-3">{t('reports.generatedAt', 'Generated')}</th>
                    <th className="p-3 text-right">{t('common.actions', 'Actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r) => (
                    <tr key={r.id} className="border-t border-[var(--border-subtle)]">
                      <td className="p-3 font-medium">{periodLabel(r)}</td>
                      <td className="p-3">{t(`reports.type.${r.reportType}`, r.reportType)}</td>
                      <td className="p-3">{badge(r.status)}</td>
                      <td className="p-3">{badge(r.emailStatus)}</td>
                      <td className="p-3 text-[var(--text-muted)]">
                        {r.generatedAt ? new Date(r.generatedAt).toLocaleString() : '-'}
                      </td>
                      <td className="p-3 text-right">{rowActions(r)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          }
          mobile={
            <div className="space-y-3">
              {filtered.map((r) => (
                <div key={r.id} className="p-4 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)]">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-semibold">{periodLabel(r)}</span>
                    {badge(r.status)}
                  </div>
                  <div className="flex items-center justify-between text-sm text-[var(--text-muted)]">
                    <span>{t(`reports.type.${r.reportType}`, r.reportType)}</span>
                    {badge(r.emailStatus)}
                  </div>
                  <div className="mt-3 flex justify-end">{rowActions(r)}</div>
                </div>
              ))}
            </div>
          }
        />
      )}
    </div>
  );
}
