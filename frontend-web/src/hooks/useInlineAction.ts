import { useCallback, useEffect, useRef, useState } from 'react';
import { logDevError, toFriendlyError } from '../lib/errorMessages';

export type ActionPhase = 'idle' | 'loading' | 'success' | 'error';

interface UseInlineActionOptions {
  /** How long the success icon stays before reverting to idle. Pass null to never auto-revert (e.g. a persistent "Sent" state driven by server truth). */
  settleMs?: number | null;
  /** Label used in the dev console log so failures are traceable to their call site. */
  context?: string;
}

/**
 * The state machine behind every inline icon-state action in the app —
 * click → loading (spinner) → success (checkmark, auto-settles) or error
 * (shake, stays until retried). Never shows a blocking banner; the raw error
 * is only ever sent to the console (see errorMessages.ts), the UI only ever
 * sees a short, friendly message meant for a tooltip.
 */
export function useInlineAction<T = void>(action: () => Promise<T>, opts: UseInlineActionOptions = {}) {
  const [phase, setPhase] = useState<ActionPhase>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const actionRef = useRef(action);
  actionRef.current = action;

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const run = useCallback(async (): Promise<T | undefined> => {
    if (phase === 'loading') return undefined;
    if (timerRef.current) clearTimeout(timerRef.current);
    setPhase('loading');
    setErrorMessage(null);
    try {
      const result = await actionRef.current();
      setPhase('success');
      const settleMs = opts.settleMs === undefined ? 2000 : opts.settleMs;
      if (settleMs !== null) {
        timerRef.current = setTimeout(() => setPhase('idle'), settleMs);
      }
      return result;
    } catch (err) {
      logDevError(opts.context ?? 'InlineAction', err);
      setErrorMessage(toFriendlyError(err).message);
      setPhase('error');
      return undefined;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, opts.settleMs, opts.context]);

  const reset = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setPhase('idle');
    setErrorMessage(null);
  }, []);

  return { phase, run, errorMessage, reset };
}
