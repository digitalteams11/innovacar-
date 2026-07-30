import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Send, RotateCcw } from 'lucide-react';
import AnimatedStatusIcon, { type StatusPhase } from '../shared/AnimatedStatusIcon';
import Tooltip from '../shared/Tooltip';
import { cn } from '../../lib/utils';
import { logDevError } from '../../lib/errorMessages';
import type { ReportRow, SendEmailResult } from '../../hooks/useReports';

type Phase = 'idle' | 'sending' | 'success' | 'error';

const ERROR_MESSAGE_KEYS: Record<string, string> = {
  REPORT_RECIPIENT_MISSING: 'reports.email.recipientMissing',
  REPORT_NOT_READY: 'reports.email.notReady',
  REPORT_FILE_MISSING: 'reports.email.fileMissing',
  REPORT_EMAIL_PROVIDER_REJECTED: 'reports.email.providerRejected',
  REPORT_ATTACHMENT_FAILED: 'reports.email.providerRejected',
  REPORT_EMAIL_SEND_IN_PROGRESS: 'reports.email.inProgress',
};

interface SendEmailButtonProps {
  report: ReportRow;
  onSend: (id: number) => Promise<SendEmailResult>;
  disabled?: boolean;
}

/**
 * Per-report-id send/resend action with a mobile-app-style state machine
 * (idle → sending → success/error), never optimistic: the icon only turns
 * green once the backend response confirms SENT. State lives per button
 * instance (keyed by report.id via React's list key), not a single global
 * flag or toast — the outcome lives entirely on this icon, with the reason
 * for a failure available on hover/focus (Tooltip) instead of a banner.
 */
export default function SendEmailButton({ report, onSend, disabled }: SendEmailButtonProps) {
  const { t } = useTranslation();
  const [phase, setPhase] = useState<Phase>('idle');
  const [armed, setArmed] = useState(false);
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const armTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (settleTimer.current) clearTimeout(settleTimer.current);
    if (armTimer.current) clearTimeout(armTimer.current);
  }, []);

  // Server truth wins once a request settles — the button doesn't stay stuck
  // on a locally-derived phase if the row's server state changes underneath it
  // (e.g. a background refetch), except for the brief "success"/"error" pulse
  // right after this exact button's own click, which the settle timer clears.
  const effectivePhase: Phase = phase !== 'idle'
    ? phase
    : report.emailStatus === 'SENT' ? 'success'
    : report.emailStatus === 'FAILED' ? 'error'
    : report.emailStatus === 'PENDING' ? 'sending'
    : 'idle';

  const isSent = effectivePhase === 'success';
  const isFailed = effectivePhase === 'error';
  const isSending = effectivePhase === 'sending';
  const isResend = report.status === 'SENT' || isSent;

  const handleClick = async () => {
    if (isSending) return; // frontend duplicate-click guard, mirrors the backend's own PENDING guard
    if (isSent && !armed) {
      // Inline click-to-confirm instead of a native window.confirm() dialog —
      // arms for 2.5s, a second click within that window actually re-sends.
      setArmed(true);
      armTimer.current = setTimeout(() => setArmed(false), 2500);
      return;
    }
    setArmed(false);
    setPhase('sending');
    let result: SendEmailResult;
    try {
      result = await onSend(report.id);
    } catch (err) {
      logDevError('SendEmailButton', err);
      result = { success: false, errorCode: 'REPORT_EMAIL_SEND_FAILED' };
    }
    setPhase(result.success ? 'success' : 'error');
    settleTimer.current = setTimeout(() => setPhase('idle'), 1500);
  };

  const failureLabel = () => {
    const messageKey = report.emailFailureCode ? ERROR_MESSAGE_KEYS[report.emailFailureCode] : undefined;
    return messageKey ? t(messageKey) : (report.emailFailureReason || t('reports.email.genericFailure'));
  };

  const label = armed
    ? t('reports.actions.resend') + '?'
    : isSent
      ? t('reports.email.sentAt', { date: report.emailSentAt ? new Date(report.emailSentAt).toLocaleString() : '' })
      : isFailed
        ? failureLabel()
        : isSending
          ? t('reports.email.sending')
          : isResend
            ? t('reports.actions.resend')
            : t('reports.actions.send');

  const displayPhase: StatusPhase = armed ? 'confirm' : effectivePhase === 'sending' ? 'loading' : effectivePhase;

  return (
    <Tooltip label={label}>
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled || isSending}
        aria-label={label}
        aria-live="polite"
        className={cn(
          'relative flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg p-2',
          'transition-colors hover:bg-[var(--bg-hover)] disabled:opacity-40 disabled:cursor-not-allowed',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2',
          isSent && 'text-emerald-600 dark:text-emerald-400',
          isFailed && 'text-red-600 dark:text-red-400',
          armed && 'text-amber-600 dark:text-amber-400',
        )}
      >
        <AnimatedStatusIcon
          phase={displayPhase}
          idleIcon={isResend ? RotateCcw : Send}
          className="w-4 h-4"
        />
      </button>
    </Tooltip>
  );
}
