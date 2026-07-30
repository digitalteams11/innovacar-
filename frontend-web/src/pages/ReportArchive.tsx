import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Archive, Download, Loader2, RefreshCw, AlertCircle, Plus } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { FilterChips } from '../components/FilterChips';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';
import SendEmailButton from '../components/reports/SendEmailButton';
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
  NOT_SENT: 'bg-slate-500/15 text-slate-500',
};

export default function ReportArchive() {
  const { t } = useTranslation();
  const { hasFeature } = useFeatureAccess();
  const { showToast } = useToast();
  const { reports, loading, error, fetchReports, generateReport, sendReportEmail, downloadReport } = useReports();
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [downloadBusyId, setDownloadBusyId] = useState<number | null>(null);
  const [generating, setGenerating] = useState(false);

  const canManualGenerate = hasFeature('MANUAL_REPORT_EXPORT');
  const canSendEmail = hasFeature('REPORT_ARCHIVE');

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
    setDownloadBusyId(r.id);
    try {
      await downloadReport(r.id, `report_${r.reportType.toLowerCase()}_${periodLabel(r)}.pdf`);
    } catch {
      showToast(t('reports.downloadFailed', 'Unable to download report'), 'error');
    } finally {
      setDownloadBusyId(null);
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

  const statusBadge = (status: string) => (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE[status] || 'bg-slate-500/15 text-slate-500'}`}>
      {t(`reports.status.${status}`, status)}
    </span>
  );

  const emailBadge = (status: string) => (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE[status] || 'bg-slate-500/15 text-slate-500'}`}>
      {t(`reports.emailStatusValue.${status}`, status)}
    </span>
  );

  const downloadButton = (r: ReportRow) => (
    <button
      onClick={() => handleDownload(r)}
      disabled={downloadBusyId === r.id || !['GENERATED', 'SENT', 'EMAIL_PENDING'].includes(r.status)}
      aria-label={t('reports.actions.download')}
      title={t('reports.actions.download')}
      className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg p-2 hover:bg-[var(--bg-hover)]
        disabled:opacity-30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
    >
      {downloadBusyId === r.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
    </button>
  );

  const rowActions = (r: ReportRow) => (
    <div className="flex items-center gap-1">
      {downloadButton(r)}
      {canSendEmail && (
        <SendEmailButton
          report={r}
          onSend={sendReportEmail}
          disabled={!['GENERATED', 'SENT', 'EMAIL_PENDING', 'FAILED'].includes(r.status)}
        />
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
                    <th className="p-3">{t('reports.columns.period', 'Period')}</th>
                    <th className="p-3">{t('reports.columns.type', 'Type')}</th>
                    <th className="p-3">{t('reports.columns.status', 'Status')}</th>
                    <th className="p-3">{t('reports.columns.email', 'Email')}</th>
                    <th className="p-3">{t('reports.columns.generated', 'Generated')}</th>
                    <th className="p-3 text-right">{t('reports.columns.actions', 'Actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r) => (
                    <tr key={r.id} className="border-t border-[var(--border-subtle)]">
                      <td className="p-3 font-medium">{periodLabel(r)}</td>
                      <td className="p-3">{t(`reports.type.${r.reportType}`, r.reportType)}</td>
                      <td className="p-3">{statusBadge(r.status)}</td>
                      <td className="p-3">
                        <div className="flex flex-col gap-0.5">
                          {emailBadge(r.emailStatus)}
                          {r.emailStatus === 'SENT' && r.emailSentAt && (
                            <span className="text-xs text-[var(--text-muted)] whitespace-normal break-words">
                              {new Date(r.emailSentAt).toLocaleString()}
                            </span>
                          )}
                        </div>
                      </td>
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
                    {statusBadge(r.status)}
                  </div>
                  <div className="flex items-center justify-between text-sm text-[var(--text-muted)]">
                    <span>{t(`reports.type.${r.reportType}`, r.reportType)}</span>
                    <div className="flex flex-col items-end gap-0.5">
                      {emailBadge(r.emailStatus)}
                      {r.emailStatus === 'SENT' && r.emailSentAt && (
                        <span className="text-xs whitespace-normal break-words text-right">
                          {new Date(r.emailSentAt).toLocaleString()}
                        </span>
                      )}
                    </div>
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
