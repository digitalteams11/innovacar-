import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Loader2, CheckCircle2, XCircle, AlertTriangle } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';

export type StatusPhase = 'idle' | 'loading' | 'success' | 'error' | 'confirm';

interface AnimatedStatusIconProps {
  phase: StatusPhase;
  idleIcon: LucideIcon;
  successIcon?: LucideIcon;
  errorIcon?: LucideIcon;
  confirmIcon?: LucideIcon;
  size?: number;
  className?: string;
}

/**
 * The single shared icon-state visual language used across the app for any
 * async action: idle → loading (spin) → success (scale-pop + settle) or
 * error (shake), plus a "confirm" phase for inline click-to-confirm actions.
 * Purely presentational/controlled — pairs with useInlineAction for the
 * state machine. Respects prefers-reduced-motion by swapping every transform
 * for a plain, fast opacity crossfade.
 */
export default function AnimatedStatusIcon({
  phase, idleIcon: Idle, successIcon, errorIcon, confirmIcon, size = 16, className,
}: AnimatedStatusIconProps) {
  const reduceMotion = useReducedMotion();
  const Success = successIcon ?? CheckCircle2;
  const Error = errorIcon ?? XCircle;
  const Confirm = confirmIcon ?? AlertTriangle;
  const fadeOnly = { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 }, transition: { duration: 0.12 } };

  return (
    <AnimatePresence mode="wait" initial={false}>
      {phase === 'loading' && (
        <motion.span key="loading" {...(reduceMotion ? fadeOnly : {
          initial: { opacity: 0, scale: 0.9 }, animate: { opacity: 1, scale: 1 },
          exit: { opacity: 0, scale: 0.8 }, transition: { duration: 0.2 },
        })}>
          <Loader2 size={size} className={cn('animate-spin', className)} />
        </motion.span>
      )}
      {phase === 'success' && (
        <motion.span
          key="success"
          initial={reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.6 }}
          animate={reduceMotion ? { opacity: 1 } : { opacity: 1, scale: [0.6, 1.25, 1] }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduceMotion ? 0.12 : 0.45 }}
        >
          <Success size={size} className={className} />
        </motion.span>
      )}
      {phase === 'error' && (
        <motion.span
          key="error"
          initial={{ opacity: 0 }}
          animate={reduceMotion ? { opacity: 1 } : { opacity: 1, x: [0, -4, 4, -4, 4, 0] }}
          exit={{ opacity: 0 }}
          transition={{ duration: reduceMotion ? 0.12 : 0.4 }}
        >
          <Error size={size} className={className} />
        </motion.span>
      )}
      {phase === 'confirm' && (
        <motion.span key="confirm" {...(reduceMotion ? fadeOnly : {
          initial: { opacity: 0, scale: 0.85 }, animate: { opacity: 1, scale: 1 },
          exit: { opacity: 0 }, transition: { duration: 0.15 },
        })}>
          <Confirm size={size} className={className} />
        </motion.span>
      )}
      {phase === 'idle' && (
        <motion.span
          key="idle"
          {...(reduceMotion ? fadeOnly : {
            initial: { opacity: 0, scale: 0.85 }, animate: { opacity: 1, scale: 1 },
            exit: { opacity: 0, x: 14, y: -14, scale: 0.7 }, transition: { duration: 0.2 },
          })}
          whileHover={reduceMotion ? undefined : { scale: 1.08 }}
        >
          <Idle size={size} className={className} />
        </motion.span>
      )}
    </AnimatePresence>
  );
}
