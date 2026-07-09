import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { useNotificationSound } from './NotificationSoundContext';

export type ToastType = 'success' | 'warning' | 'error' | 'info';

interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
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

  const dismiss = useCallback((id: number) => {
    setToasts(current => current.filter(toast => toast.id !== id));
    const timer = timersRef.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const showToast = useCallback((message: string, requestedType?: ToastType) => {
    const rawMessage = String(message || '').trim();
    if (!rawMessage) return;

    const type = requestedType || inferType(rawMessage);
    const cleanMessage = sanitizeMessage(rawMessage);
    const id = ++nextId.current;

    setToasts(current => {
      // Deduplication: prevent exact same message+type within current stack
      if (current.some(toast => toast.message === cleanMessage && toast.type === type)) {
        return current;
      }
      // Max 3 toasts visible at once (keep newest)
      const trimmed = current.slice(-2);
      return [...trimmed, { id, message: cleanMessage, type }];
    });

    if (type === 'error') {
      playSound('error');
    }

    const timer = window.setTimeout(() => {
      dismiss(id);
    }, duration[type]);
    timersRef.current.set(id, timer);
  }, [dismiss, playSound]);

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
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="fixed bottom-8 end-8 z-[160] flex w-[min(420px,calc(100vw-2rem))] flex-col gap-3" aria-live="polite">
        {toasts.map(toast => {
          const colorMap = {
            success: { light: '#10b981', bg: 'rgba(16,185,129,0.09)', border: 'rgba(16,185,129,0.25)', text: '#065f46' },
            warning: { light: '#f59e0b', bg: 'rgba(245,158,11,0.09)', border: 'rgba(245,158,11,0.28)', text: '#92400e' },
            error:   { light: '#ef4444', bg: 'rgba(239,68,68,0.09)',  border: 'rgba(239,68,68,0.28)',  text: '#991b1b' },
            info:    { light: '#3b82f6', bg: 'rgba(59,130,246,0.09)', border: 'rgba(59,130,246,0.25)', text: '#1e40af' },
          }[toast.type];
          const Icon = toast.type === 'success' ? CheckCircle2
            : toast.type === 'warning' ? AlertTriangle
              : toast.type === 'error' ? AlertCircle : Info;
          return (
            <div
              key={toast.id}
              className="flex items-start gap-3 px-4 py-3 animate-in slide-in-from-right duration-300 rounded-xl"
              role={toast.type === 'error' ? 'alert' : 'status'}
              style={{
                background: 'var(--bg-card-solid)',
                border: `1px solid ${colorMap.border}`,
                boxShadow: 'var(--shadow-elevated)',
                borderInlineStart: `4px solid ${colorMap.light}`,
              }}
            >
              <Icon size={20} className="mt-0.5 shrink-0" style={{ color: colorMap.light }} />
              <span className="flex-1 text-sm font-semibold leading-5" style={{ color: 'var(--text-primary)' }}>{toast.message}</span>
              <button
                onClick={() => dismiss(toast.id)}
                className="p-1 opacity-60 hover:opacity-100 transition-opacity"
                aria-label="Dismiss"
                style={{ color: 'var(--text-muted)' }}
              >
                <X size={15} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};
