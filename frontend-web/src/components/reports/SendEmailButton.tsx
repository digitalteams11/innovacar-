import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Send, Loader2, CheckCircle2, XCircle, RotateCcw } from 'lucide-react';
import { useToast } from '../../context/ToastContext';
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
 * instance (keyed by report.id via React's list key), not a single global flag.
 */
export default function SendEmailButton({ report, onSend, disabled }: SendEmailButtonProps) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const reduceMotion = useReducedMotion();
  const [phase, setPhase] = useState<Phase>('idle');
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (settleTimer.current) clearTimeout(settleTimer.current);
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
    if (isSent) {
      // Explicit confirmation before re-sending an already-delivered report (spec section 9/17).
      const confirmed = window.confirm(t('reports.actions.resend') + '?');
      if (!confirmed) return;
    }
    setPhase('sending');
    const result = await onSend(report.id);
    if (result.success) {
      setPhase('success');
      showToast(
        t('reports.email.sentSuccess', { recipient: result.data?.recipient || '' }),
        'success',
      );
    } else {
      setPhase('error');
      const messageKey = result.errorCode ? ERROR_MESSAGE_KEYS[result.errorCode] : undefined;
      const reason = messageKey ? t(messageKey) : (result.message || t('reports.email.genericFailure'));
      showToast(t('reports.email.sendFailed', { reason }), 'error');
    }
    // Let the row's own badge (driven by report.emailStatus from the parent's
    // refreshed state) take over as the steady-state source of truth; this
    // button's local phase only needs to own the transient click animation.
    settleTimer.current = setTimeout(() => setPhase('idle'), 1500);
  };

  const label = isSent
    ? t('reports.email.sentAt', { date: report.emailSentAt ? new Date(report.emailSentAt).toLocaleString() : '' })
    : isFailed
      ? (report.emailFailureReason || t('reports.email.genericFailure'))
      : isSending
        ? t('reports.email.sending')
        : isResend
          ? t('reports.actions.resend')
          : t('reports.actions.send');

  const iconMotionProps = reduceMotion
    ? { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 }, transition: { duration: 0.12 } }
    : undefined;

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled || isSending}
      aria-label={label}
      aria-live="polite"
      title={label}
      className={`relative flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg p-2
        transition-colors hover:bg-[var(--bg-hover)] disabled:opacity-40 disabled:cursor-not-allowed
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2
        ${isSent ? 'text-emerald-600 dark:text-emerald-400' : ''}
        ${isFailed ? 'text-red-600 dark:text-red-400' : ''}`}
    >
      <AnimatePresence mode="wait" initial={false}>
        {isSending && (
          <motion.span key="sending" {...(iconMotionProps ?? {
            initial: { opacity: 0, x: -6, scale: 0.9 },
            animate: { opacity: 1, x: 0, scale: 1 },
            exit: { opacity: 0, x: 10, y: -10, scale: 0.7 },
            transition: { duration: 0.25 },
          })}>
            <Loader2 className="w-4 h-4 animate-spin" />
          </motion.span>
        )}
        {isSent && (
          <motion.span
            key="sent"
            initial={reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.6 }}
            animate={reduceMotion ? { opacity: 1 } : { opacity: 1, scale: [0.6, 1.25, 1] }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0.12 : 0.45 }}
          >
            <CheckCircle2 className="w-4 h-4" />
          </motion.span>
        )}
        {isFailed && (
          <motion.span key="failed" {...(iconMotionProps ?? {
            initial: { opacity: 0, scale: 0.8 },
            animate: { opacity: 1, scale: 1 },
            exit: { opacity: 0 },
            transition: { duration: 0.2 },
          })}>
            <XCircle className="w-4 h-4" />
          </motion.span>
        )}
        {effectivePhase === 'idle' && (
          <motion.span key="idle" {...(iconMotionProps ?? {
            initial: { opacity: 0, scale: 0.85 },
            animate: { opacity: 1, scale: 1 },
            exit: { opacity: 0, x: 14, y: -14, scale: 0.7 },
            transition: { duration: 0.2 },
          })}
            whileHover={reduceMotion ? undefined : { scale: 1.08 }}
          >
            {isResend ? <RotateCcw className="w-4 h-4" /> : <Send className="w-4 h-4" />}
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}
