import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Archive, Download, Loader2, RefreshCw, AlertCircle } from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { FilterChips } from '../components/FilterChips';
import ResponsiveDataView from '../components/shared/ResponsiveDataView';
import SendEmailButton from '../components/reports/SendEmailButton';
import GenerateReportButton from '../components/reports/GenerateReportButton';
import InlineActionButton from '../components/shared/InlineActionButton';
import { useReports, type ReportRow } from '../hooks/useReports';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import { periodLabelFor, reportCoversPeriod, targetPeriodFor } from '../lib/reportPeriods';
import type { ReportType } from '../types/reports';

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

/** Reports whose status counts as "this period is already covered" — matches the backend's ALREADY_DONE set. */
const ALREADY_DONE_STATUSES = new Set(['GENERATED', 'EMAIL_PENDING', 'SENT']);

export default function ReportArchive() {
  const { t } = useTranslation();
  const { hasFeature } = useFeatureAccess();
  const { reports, loading, error, fetchReports, generateReport, sendReportEmail, downloadReport } = useReports();
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [highlightedId, setHighlightedId] = useState<number | null>(null);
  const rowRefs = useRef<Map<number, HTMLElement>>(new Map());
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const canManualGenerate = hasFeature('MANUAL_REPORT_EXPORT');
  const canSendEmail = hasFeature('REPORT_ARCHIVE');

  useEffect(() => () => {
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
  }, []);

  const filtered = useMemo(
    () => reports.filter((r) => typeFilter === 'ALL' || r.reportType === typeFilter),
    [reports, typeFilter],
  );

  const periodLabel = (r: ReportRow) => {
    const start = new Date(r.periodStart);
    if (r.reportType === 'YEARLY') return `${start.getFullYear()}`;
    return start.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  };

  // The default target period each button would generate — computed the same
  // way the backend resolves "the previous closed month/year" by default, so
  // an already-present report can be detected *before* sending a duplicate
  // POST (spec sections 3-5). The backend's 409 remains the authoritative
  // guard for anything this client-side check misses (races, timezone edge
  // cases, force-regenerate).
  const monthlyTarget = useMemo(() => targetPeriodFor('MONTHLY'), []);
  const yearlyTarget = useMemo(() => targetPeriodFor('YEARLY'), []);
  const monthlyLabel = periodLabelFor('MONTHLY', monthlyTarget);
  const yearlyLabel = periodLabelFor('YEARLY', yearlyTarget);

  const existingReportFor = (type: ReportType, period: { year: number; month?: number }) =>
    reports.find((r) => ALREADY_DONE_STATUSES.has(r.status) && reportCoversPeriod(r, type, period));

  const existingMonthly = existingReportFor('MONTHLY', monthlyTarget);
  const existingYearly = existingReportFor('YEARLY', yearlyTarget);

  const handleViewExisting = (reportId: number) => {
    setHighlightedId(reportId);
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    highlightTimerRef.current = setTimeout(() => setHighlightedId(null), 2500);
    rowRefs.current.get(reportId)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const generateMonthly = () => generateReport({ reportType: 'MONTHLY', ...monthlyTarget });
  const generateYearly = () => generateReport({ reportType: 'YEARLY', year: yearlyTarget.year });

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
    <InlineActionButton
      icon={Download}
      label={t('reports.actions.download')}
      onAction={() => downloadReport(r.id, `report_${r.reportType.toLowerCase()}_${periodLabel(r)}.pdf`)}
      disabled={!['GENERATED', 'SENT', 'EMAIL_PENDING'].includes(r.status)}
      context="download-report"
    />
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
    // No max-width/padding here — page-canvas (Layout.tsx) already provides
    // both for every authenticated page; this page previously duplicated
    // both (its own p-4 lg:p-6 on top of page-canvas's padding, and a
    // max-w-6xl cap well below page-canvas's own width) which is exactly the
    // "narrow centered container on a full-width page" bug, matching the
    // established root-wrapper convention used by Contracts.tsx/Vehicles.tsx.
    <div className="space-y-5 animate-fade">
      <GlassPageHeader
        title={t('reports.archiveTitle', 'Report Archive')}
        subtitle={t('reports.archiveSubtitle', 'Monthly and yearly financial reports, generated automatically or on demand')}
        icon={Archive}
        actions={canManualGenerate ? (
          <div className="flex flex-wrap gap-2">
            <GenerateReportButton
              reportType="MONTHLY"
              periodLabel={monthlyLabel}
              existingReportId={existingMonthly?.id}
              onGenerate={generateMonthly}
              onViewExisting={handleViewExisting}
            />
            <GenerateReportButton
              reportType="YEARLY"
              periodLabel={yearlyLabel}
              existingReportId={existingYearly?.id}
              onGenerate={generateYearly}
              onViewExisting={handleViewExisting}
            />
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
                    <tr
                      key={r.id}
                      ref={(el) => { if (el) rowRefs.current.set(r.id, el); }}
                      className={`border-t border-[var(--border-subtle)] transition-colors duration-500 ${highlightedId === r.id ? 'bg-brand-500/10' : ''}`}
                    >
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
                <div
                  key={r.id}
                  ref={(el) => { if (el) rowRefs.current.set(r.id, el); }}
                  className={`p-4 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] transition-colors duration-500 ${highlightedId === r.id ? 'ring-2 ring-brand-500 bg-brand-500/10' : ''}`}
                >
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
