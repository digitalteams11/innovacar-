import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Lock, CheckCircle2 } from 'lucide-react';
import AnimatedStatusIcon from '../shared/AnimatedStatusIcon';
import Tooltip from '../shared/Tooltip';
import { cn } from '../../lib/utils';
import { logDevError, toFriendlyError } from '../../lib/errorMessages';
import type { GenerateReportResult, ReportType } from '../../types/reports';

type Phase = 'idle' | 'generating' | 'success' | 'error';

interface GenerateReportButtonProps {
  reportType: ReportType;
  periodLabel: string;
  /** Set when the target period already has a GENERATED/SENT report — server truth, always wins over local phase. */
  existingReportId?: number;
  onGenerate: () => Promise<GenerateReportResult>;
  onViewExisting: (reportId: number) => void;
  disabled?: boolean;
}

/**
 * Per-button state machine for manual report generation: IDLE -> GENERATING
 * -> SUCCESS or FAILED, plus two states driven entirely by server data rather
 * than the click itself — ALREADY_EXISTS (a neutral "view" affordance, never
 * an error) and a locked/upgrade state for a plan that doesn't include
 * reporting (402). Never fires a second POST while a request is in flight,
 * and never shows a generic global toast — the reason for a failure lives on
 * this button's own hover tooltip.
 */
export default function GenerateReportButton({
  reportType, periodLabel, existingReportId, onGenerate, onViewExisting, disabled,
}: GenerateReportButtonProps) {
  const { t } = useTranslation();
  const [phase, setPhase] = useState<Phase>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (settleTimer.current) clearTimeout(settleTimer.current);
  }, []);

  const alreadyExists = existingReportId !== undefined;

  const handleClick = async () => {
    if (phase === 'generating') return; // duplicate-click guard — never fires a second POST
    if (alreadyExists) {
      onViewExisting(existingReportId as number);
      return;
    }
    setPhase('generating');
    setErrorMessage(null);
    setLocked(false);
    try {
      const result = await onGenerate();
      if (result.success) {
        setPhase('success');
        settleTimer.current = setTimeout(() => setPhase('idle'), 2000);
        return;
      }
      if (result.errorCode === 'REPORT_ALREADY_EXISTS') {
        // A race with the scheduler/another tab — the caller's refetched
        // report list will surface this as `existingReportId` on next render;
        // no error state, this isn't a failure.
        setPhase('idle');
        return;
      }
      if (result.errorCode === 'REPORTING_FEATURE_NOT_INCLUDED') {
        setLocked(true);
        setPhase('idle');
        setErrorMessage(result.message || t('reports.generate.locked', 'Upgrade your plan to enable automated reports.'));
        return;
      }
      setPhase('error');
      setErrorMessage(result.message || t('reports.generate.failed', 'The report could not be generated.'));
    } catch (err) {
      logDevError(`generate-${reportType.toLowerCase()}-report`, err);
      setPhase('error');
      setErrorMessage(toFriendlyError(err).message);
    }
  };

  const tooltipLabel = alreadyExists
    ? t('reports.generate.alreadyExistsTooltip', '{{period}} was already generated — click to view it', { period: periodLabel })
    : locked
      ? errorMessage
      : phase === 'error'
        ? errorMessage
        : null;

  const buttonText = alreadyExists
    ? t('reports.generate.viewExisting', 'View {{period}}', { period: periodLabel })
    : phase === 'generating'
      ? t('reports.generate.generating', 'Generating…')
      : reportType === 'MONTHLY'
        ? t('reports.generateMonthly', 'Generate Monthly')
        : t('reports.generateYearly', 'Generate Yearly');

  return (
    <Tooltip label={tooltipLabel}>
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled || phase === 'generating'}
        aria-label={buttonText}
        className={cn(
          'flex min-h-[44px] items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60',
          alreadyExists && 'border border-[var(--border-subtle)] text-[var(--text-muted)] hover:bg-[var(--bg-hover)]',
          !alreadyExists && locked && 'border border-amber-400 text-amber-600',
          !alreadyExists && !locked && phase === 'error' && 'border border-red-400 text-red-600 ring-2 ring-red-400/40',
          !alreadyExists && !locked && phase !== 'error' && reportType === 'MONTHLY' && 'btn-primary',
          !alreadyExists && !locked && phase !== 'error' && reportType === 'YEARLY' && 'border border-[var(--border-subtle)]',
        )}
      >
        {alreadyExists ? (
          <CheckCircle2 className="w-4 h-4" />
        ) : locked ? (
          <Lock className="w-4 h-4" />
        ) : (
          <AnimatedStatusIcon
            phase={phase === 'generating' ? 'loading' : phase}
            idleIcon={Plus}
            className="w-4 h-4"
          />
        )}
        {buttonText}
      </button>
    </Tooltip>
  );
}
