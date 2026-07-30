import { useEffect, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';
import { useInlineAction } from '../../hooks/useInlineAction';
import AnimatedStatusIcon from './AnimatedStatusIcon';
import Tooltip from './Tooltip';

interface InlineActionButtonProps {
  icon: LucideIcon;
  /** Idle tooltip / aria-label — what the action does, e.g. "Delete client". */
  label: string;
  onAction: () => Promise<unknown>;
  successIcon?: LucideIcon;
  errorIcon?: LucideIcon;
  /** Tooltip shown briefly after success — defaults to `label`. */
  successLabel?: string;
  disabled?: boolean;
  /**
   * Replaces a native window.confirm() — first click arms the button (shows
   * a warning icon + confirm tooltip for 2.5s), second click within that
   * window actually runs the action.
   */
  requireConfirm?: boolean;
  confirmLabel?: string;
  variant?: 'default' | 'danger';
  className?: string;
  /** null = never auto-revert from success back to idle (e.g. a persistent "sent" state). */
  settleMs?: number | null;
  context?: string;
}

/**
 * Drop-in replacement for a plain icon button + toast pair: click → spinner
 * → green check (auto-settles) or red shake with a hover tooltip and
 * click-to-retry. This is the primitive every domain page (reservations,
 * maintenance, contracts, payments, clients, reports) uses instead of firing
 * a global toast for a specific row/action's outcome.
 */
export default function InlineActionButton({
  icon, label, onAction, successIcon, errorIcon, successLabel, disabled,
  requireConfirm, confirmLabel, variant = 'default', className, settleMs, context,
}: InlineActionButtonProps) {
  const [armed, setArmed] = useState(false);
  const armTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { phase, run, errorMessage } = useInlineAction(onAction, { settleMs, context: context ?? label });

  useEffect(() => () => {
    if (armTimerRef.current) clearTimeout(armTimerRef.current);
  }, []);

  const handleClick = () => {
    if (phase === 'loading') return;
    if (requireConfirm && !armed) {
      setArmed(true);
      armTimerRef.current = setTimeout(() => setArmed(false), 2500);
      return;
    }
    setArmed(false);
    if (armTimerRef.current) clearTimeout(armTimerRef.current);
    run();
  };

  const tooltipLabel = phase === 'error'
    ? (errorMessage || label)
    : phase === 'success'
      ? (successLabel || label)
      : armed
        ? (confirmLabel || 'Click again to confirm')
        : label;

  return (
    <Tooltip label={tooltipLabel}>
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled || phase === 'loading'}
        aria-label={tooltipLabel}
        className={cn(
          'relative flex min-h-[44px] min-w-[44px] items-center justify-center rounded-lg p-2 transition-colors',
          'hover:bg-[var(--bg-hover)] disabled:opacity-40 disabled:cursor-not-allowed',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2',
          phase === 'success' && 'text-emerald-600 dark:text-emerald-400',
          phase === 'error' && 'text-red-600 dark:text-red-400',
          armed && (variant === 'danger' ? 'text-red-600 dark:text-red-400' : 'text-amber-600 dark:text-amber-400'),
          className,
        )}
      >
        <AnimatedStatusIcon
          phase={armed ? 'confirm' : phase}
          idleIcon={icon}
          successIcon={successIcon}
          errorIcon={errorIcon}
        />
      </button>
    </Tooltip>
  );
}
