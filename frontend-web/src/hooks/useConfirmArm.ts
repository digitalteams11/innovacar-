import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Replaces a native window.confirm() dialog with an inline two-click
 * confirm: the first call to `guard()` just arms the state (caller shows a
 * "click again to confirm" affordance) and returns false; a second call
 * within `windowMs` returns true and disarms. Native confirm() blocks the
 * whole page with a browser-chrome dialog — the opposite of the in-component
 * feedback this app now uses everywhere else.
 */
export function useConfirmArm(windowMs = 2500) {
  const [armed, setArmed] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const guard = useCallback(() => {
    if (armed) {
      if (timerRef.current) clearTimeout(timerRef.current);
      setArmed(false);
      return true;
    }
    setArmed(true);
    timerRef.current = setTimeout(() => setArmed(false), windowMs);
    return false;
  }, [armed, windowMs]);

  const cancel = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setArmed(false);
  }, []);

  return { armed, guard, cancel };
}
