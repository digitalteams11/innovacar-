import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { useNotificationSound } from './NotificationSoundContext';

export type ToastType = 'success' | 'warning' | 'error' | 'info';

interface Toast {
  id: number;
  message: string;
  type: ToastType;
  paused?: boolean;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
  /** Convenience API: toast.success('Saved'), toast.error('Could not save'), … */
  toast: Record<ToastType, (message: string) => void>;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

const duration: Record<ToastType, number> = {
  success: 4000,
  info: 4000,
  warning: 6000,
  error: 8000,
};

/**
 * Infer toast type from message content.
 * This is a fallback when type is not explicitly provided.
 * ALWAYS prefer explicit type over inference.
 */
function inferType(message: string): ToastType {
  const value = message.toLowerCase();
  // Error keywords — highest priority
  if (/(unable to|failed to|could not|cannot|error|offline|unavailable|denied|invalid|expired|unauthorized|forbidden|not found|not allowed|payment failed|generation failed|sync failed)/.test(value)) {
    return 'error';
  }
  // Warning keywords
  if (/(limit reached|limit almost reached|expires in|not configured|not assigned|waiting for|please select|please fill|missing|must |required|warning|caution)/.test(value)) {
    return 'warning';
  }
  // Success keywords
  if (/(success|created|saved|updated|deleted|downloaded|generated|completed|processed|copied|connected|synced|upgraded|renewed|marked|submitted|sent|exported|applied|finalized|removed|activated|extended|verified|duplicated|restored)/.test(value)) {
    return 'success';
  }
  // Default to info
  return 'info';
}

/**
 * Sanitize message to prevent leaking internal technical errors.
 * Replaces technical terms with user-friendly alternatives.
 */
function sanitizeMessage(message: string): string {
  if (!message || typeof message !== 'string') return 'Something went wrong. Please try again.';
  const lower = message.toLowerCase();
  // Check for technical leak patterns
  if (/(exception|stack trace|sql|jdbc|hibernate|nullpointer|null pointer|database|constraint|axios|fetch error|internal server|500|502|503|504|at com\.|at java\.|at org\.)/.test(lower)) {
    return 'Unable to complete this action. Please try again later.';
  }
  // Clean up common ugly patterns
  let clean = message.trim();
  // Remove "Error: " prefix if it's just decorative
  if (clean.toLowerCase().startsWith('error: ') && clean.length > 7) {
    clean = clean.slice(7);
  }
  // Cap length
  if (clean.length > 220) {
    clean = clean.slice(0, 217) + '...';
  }
  return clean || 'Something went wrong. Please try again.';
}

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within ToastProvider');
  return context;
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const { playSound } = useNotificationSound();
  const nextId = useRef(0);
  const timersRef = useRef<Map<number, number>>(new Map());
  const startedAtRef = useRef<Map<number, number>>(new Map());
  const remainingRef = useRef<Map<number, number>>(new Map());

  const dismiss = useCallback((id: number) => {
    setToasts(current => current.filter(toast => toast.id !== id));
    const timer = timersRef.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      timersRef.current.delete(id);
    }
    startedAtRef.current.delete(id);
    remainingRef.current.delete(id);
  }, []);

  const armTimer = useCallback((id: number, delay: number) => {
    startedAtRef.current.set(id, Date.now());
    remainingRef.current.set(id, delay);
    const timer = window.setTimeout(() => dismiss(id), delay);
    timersRef.current.set(id, timer);
  }, [dismiss]);

  // Pause the auto-dismiss countdown on hover/focus, resume on leave/blur —
  // lets a user actually read/click a toast instead of racing its timeout.
  const pauseToast = useCallback((id: number) => {
    const timer = timersRef.current.get(id);
    if (!timer) return;
    window.clearTimeout(timer);
    timersRef.current.delete(id);
    const startedAt = startedAtRef.current.get(id);
    const remaining = remainingRef.current.get(id);
    if (startedAt && remaining) {
      remainingRef.current.set(id, Math.max(1000, remaining - (Date.now() - startedAt)));
    }
  }, []);

  const resumeToast = useCallback((id: number) => {
    if (timersRef.current.has(id)) return;
    const remaining = remainingRef.current.get(id) ?? 3000;
    armTimer(id, remaining);
  }, [armTimer]);

  const showToast = useCallback((message: string, requestedType?: ToastType) => {
    const rawMessage = String(message || '').trim();
    if (!rawMessage) return;

    const type = requestedType || inferType(rawMessage);
    const cleanMessage = sanitizeMessage(rawMessage);
    const id = ++nextId.current;
    let inserted = false;

    setToasts(current => {
      // Deduplication: prevent exact same message+type within current stack
      if (current.some(toast => toast.message === cleanMessage && toast.type === type)) {
        return current;
      }
      inserted = true;
      // Max 3 toasts visible at once (keep newest)
      const trimmed = current.slice(-2);
      return [...trimmed, { id, message: cleanMessage, type }];
    });

    if (!inserted) return;

    if (type === 'error') {
      playSound('error');
    }

    armTimer(id, duration[type]);
  }, [armTimer, playSound]);

  const toast = useMemo<Record<ToastType, (message: string) => void>>(() => ({
    success: (message: string) => showToast(message, 'success'),
    error: (message: string) => showToast(message, 'error'),
    warning: (message: string) => showToast(message, 'warning'),
    info: (message: string) => showToast(message, 'info'),
  }), [showToast]);

  // Cleanup all timers on unmount
  useEffect(() => {
    return () => {
      timersRef.current.forEach(timer => window.clearTimeout(timer));
      timersRef.current.clear();
    };
  }, []);

  // Listen for global toast events from axios interceptor
  useEffect(() => {
    const handleToast = (event: Event) => {
      const detail = (event as CustomEvent<{ message: string; type?: ToastType }>).detail;
      if (detail?.message) showToast(detail.message, detail.type);
    };
    window.addEventListener('app-toast', handleToast);
    return () => window.removeEventListener('app-toast', handleToast);
  }, [showToast]);

  return (
    <ToastContext.Provider value={{ showToast, toast }}>
      {children}
      <div className="toast-viewport" aria-live="polite">
        {toasts.map(item => {
          const colorMap = {
            success: '#10b981',
            warning: '#f59e0b',
            error:   '#ef4444',
            info:    '#3b82f6',
          }[item.type];
          const Icon = item.type === 'success' ? CheckCircle2
            : item.type === 'warning' ? AlertTriangle
              : item.type === 'error' ? AlertCircle : Info;
          return (
            <div
              key={item.id}
              className="toast-item"
              role={item.type === 'error' ? 'alert' : 'status'}
              aria-live={item.type === 'error' ? 'assertive' : 'polite'}
              tabIndex={0}
              onMouseEnter={() => pauseToast(item.id)}
              onMouseLeave={() => resumeToast(item.id)}
              onFocus={() => pauseToast(item.id)}
              onBlur={() => resumeToast(item.id)}
              style={{
                background: 'var(--bg-card-solid)',
                boxShadow: 'var(--shadow-elevated)',
                borderInlineStart: `3px solid ${colorMap}`,
              }}
            >
              <Icon size={18} className="mt-0.5 shrink-0" style={{ color: colorMap }} />
              <span className="flex-1 text-sm font-semibold leading-5" style={{ color: 'var(--text-primary)' }}>{item.message}</span>
              <button
                onClick={() => dismiss(item.id)}
                className="p-1 opacity-60 hover:opacity-100 transition-opacity shrink-0"
                aria-label="Dismiss"
                style={{ color: 'var(--text-muted)' }}
              >
                <X size={14} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};
