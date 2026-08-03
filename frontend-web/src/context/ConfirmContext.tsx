import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { AlertOctagon, AlertTriangle, CheckCircle2, HelpCircle, Info, X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { translateApiError } from '../api/axios';

export type ConfirmTone = 'default' | 'info' | 'warning' | 'danger' | 'success';

export interface ConfirmOptions {
  title: React.ReactNode;
  description?: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: ConfirmTone;
  /**
   * When provided, the dialog stays open, disables both actions, and shows a
   * spinner in the confirm button while this runs. On success the dialog
   * closes and `resolve(true)` fires; on failure the dialog stays open with a
   * classified inline error and a Retry affordance instead of closing on a
   * guess (spec: "do not close the modal before the backend succeeds").
   */
  action?: () => Promise<void>;
}

export interface PromptOptions {
  title: React.ReactNode;
  description?: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: ConfirmTone;
  placeholder?: string;
  defaultValue?: string;
  /** When true, the confirm button stays disabled until the field is non-empty. */
  required?: boolean;
}

interface PendingConfirm {
  kind: 'confirm';
  options: ConfirmOptions;
  resolve: (value: boolean) => void;
  triggerEl: HTMLElement | null;
}

interface PendingPrompt {
  kind: 'prompt';
  options: PromptOptions;
  resolve: (value: string | null) => void;
  triggerEl: HTMLElement | null;
}

type Pending = PendingConfirm | PendingPrompt;

interface ConfirmContextValue {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  promptText: (options: PromptOptions) => Promise<string | null>;
}

const ConfirmContext = createContext<ConfirmContextValue | undefined>(undefined);

export const useConfirm = () => {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used within ConfirmProvider');
  return ctx.confirm;
};

export const usePromptText = () => {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('usePromptText must be used within ConfirmProvider');
  return ctx.promptText;
};

const TONE_ICON: Record<ConfirmTone, LucideIcon> = {
  default: HelpCircle,
  info: Info,
  warning: AlertTriangle,
  danger: AlertOctagon,
  success: CheckCircle2,
};

const TONE_CLASSES: Record<ConfirmTone, { badge: string; icon: string; confirmBtn: string }> = {
  default: {
    badge: 'bg-slate-500/15',
    icon: 'text-slate-500 dark:text-slate-300',
    confirmBtn: '',
  },
  info: {
    badge: 'bg-blue-500/15',
    icon: 'text-blue-600 dark:text-blue-300',
    confirmBtn: '',
  },
  warning: {
    badge: 'bg-amber-500/15',
    icon: 'text-amber-600 dark:text-amber-300',
    confirmBtn: 'bg-amber-500 hover:bg-amber-600 text-white',
  },
  danger: {
    badge: 'bg-rose-500/15',
    icon: 'text-rose-600 dark:text-rose-400',
    confirmBtn: 'bg-rose-600 hover:bg-rose-700 text-white',
  },
  success: {
    badge: 'bg-emerald-500/15',
    icon: 'text-emerald-600 dark:text-emerald-300',
    confirmBtn: 'bg-emerald-600 hover:bg-emerald-700 text-white',
  },
};

export const ConfirmProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { t } = useTranslation();
  const [pending, setPending] = useState<Pending | null>(null);
  const [phase, setPhase] = useState<'idle' | 'loading' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const [inputValue, setInputValue] = useState('');
  const cancelBtnRef = useRef<HTMLButtonElement>(null);
  const confirmBtnRef = useRef<HTMLButtonElement>(null);
  const mountedRef = useRef(true);
  const triggerElRef = useRef<HTMLElement | null>(null);

  useEffect(() => () => { mountedRef.current = false; }, []);

  const openPending = useCallback((next: Pending) => {
    setPhase('idle');
    setErrorMessage('');
    setInputValue(next.kind === 'prompt' ? (next.options.defaultValue || '') : '');
    triggerElRef.current = next.triggerEl;
    setPending(next);
  }, []);

  const closeAndSettle = useCallback((settle: () => void) => {
    setPending(null);
    setPhase('idle');
    setErrorMessage('');
    settle();
    // Restore focus to whatever triggered the dialog — accessibility spec:
    // "restore focus to the triggering button".
    const triggerEl = triggerElRef.current;
    window.requestAnimationFrame(() => triggerEl?.focus?.());
  }, []);

  const confirm = useCallback((options: ConfirmOptions): Promise<boolean> => {
    const triggerEl = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return new Promise<boolean>((resolve) => {
      openPending({ kind: 'confirm', options, resolve, triggerEl });
    });
  }, [openPending]);

  const promptText = useCallback((options: PromptOptions): Promise<string | null> => {
    const triggerEl = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return new Promise<string | null>((resolve) => {
      openPending({ kind: 'prompt', options, resolve, triggerEl });
    });
  }, [openPending]);

  useEffect(() => {
    if (!pending) return;
    window.requestAnimationFrame(() => cancelBtnRef.current?.focus());
  }, [pending]);

  useEffect(() => {
    if (!pending) return;
    const handleEscape = (e: KeyboardEvent) => {
      // "Close on Escape when safe" — not while a backend call is in flight.
      if (e.key === 'Escape' && phase !== 'loading') {
        e.stopPropagation();
        handleCancel();
      }
    };
    document.addEventListener('keydown', handleEscape, true);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleEscape, true);
      document.body.style.overflow = previousOverflow;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pending, phase]);

  const handleCancel = () => {
    if (!pending || phase === 'loading') return;
    if (pending.kind === 'confirm') closeAndSettle(() => pending.resolve(false));
    else closeAndSettle(() => pending.resolve(null));
  };

  const handleConfirm = async () => {
    if (!pending || phase === 'loading') return;

    if (pending.kind === 'prompt') {
      if (pending.options.required && !inputValue.trim()) return;
      closeAndSettle(() => pending.resolve(inputValue));
      return;
    }

    const { resolve } = pending;
    const action = pending.options.action;
    if (!action) {
      closeAndSettle(() => resolve(true));
      return;
    }

    setPhase('loading');
    setErrorMessage('');
    try {
      await action();
      if (!mountedRef.current) return;
      closeAndSettle(() => resolve(true));
    } catch (err) {
      if (!mountedRef.current) return;
      setPhase('error');
      setErrorMessage(translateApiError(err, t));
    }
  };

  const value = useMemo(() => ({ confirm, promptText }), [confirm, promptText]);

  if (!pending) {
    return <ConfirmContext.Provider value={value}>{children}</ConfirmContext.Provider>;
  }

  const { options } = pending;
  const tone = options.tone || 'default';
  const Icon = TONE_ICON[tone];
  const toneClasses = TONE_CLASSES[tone];
  const submitting = phase === 'loading';
  const confirmDisabled = submitting || (pending.kind === 'prompt' && pending.options.required && !inputValue.trim());
  const defaultCancelLabel = t('actions.cancel', 'Cancel');
  const defaultConfirmLabel = t('actions.confirm', 'Confirm');

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {createPortal(
        <div
          className="fixed inset-0 z-[var(--z-confirm-overlay)] flex items-end justify-center overflow-y-auto p-0 sm:items-center sm:p-4"
          onMouseDown={(e) => { if (e.target === e.currentTarget && !submitting) handleCancel(); }}
        >
          <button
            type="button"
            className="fixed inset-0 cursor-default bg-slate-950/55 backdrop-blur-xl animate-fade"
            onClick={() => { if (!submitting) handleCancel(); }}
            aria-label={t('actions.cancel', 'Cancel') as string}
            tabIndex={-1}
          />
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-dialog-title"
            aria-describedby={options.description ? 'confirm-dialog-description' : undefined}
            className="relative z-10 flex w-full max-w-md flex-col overflow-hidden border border-[var(--glass-border)] p-5 animate-scale-in sm:rounded-3xl"
            style={{
              background: 'var(--glass-bg)',
              backdropFilter: 'blur(var(--glass-blur))',
              WebkitBackdropFilter: 'blur(var(--glass-blur))',
              boxShadow: 'var(--shadow-elevated)',
              paddingBottom: 'max(1.25rem, env(safe-area-inset-bottom))',
            }}
          >
            <div className="flex items-start gap-3">
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${toneClasses.badge}`}>
                <Icon size={19} className={toneClasses.icon} />
              </div>
              <div className="min-w-0 flex-1 pt-1">
                <h3 id="confirm-dialog-title" className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                  {options.title}
                </h3>
                {options.description && (
                  <p id="confirm-dialog-description" className="mt-1.5 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                    {options.description}
                  </p>
                )}
              </div>
              {!submitting && (
                <button
                  type="button"
                  onClick={handleCancel}
                  className="rounded-xl p-1.5 text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
                  aria-label={t('actions.cancel', 'Cancel') as string}
                >
                  <X size={16} />
                </button>
              )}
            </div>

            {pending.kind === 'prompt' && (
              <input
                type="text"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={pending.options.placeholder}
                disabled={submitting}
                className="mt-4 w-full rounded-xl px-4 py-2.5 text-sm outline-none"
                style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
              />
            )}

            {phase === 'error' && (
              <div className="mt-4 flex items-start gap-2 rounded-xl border border-rose-500/25 bg-rose-500/10 p-2.5">
                <AlertOctagon size={14} className="mt-0.5 shrink-0 text-rose-500" />
                <p className="text-[12px] font-medium text-rose-600 dark:text-rose-300">{errorMessage}</p>
              </div>
            )}

            <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                ref={cancelBtnRef}
                type="button"
                onClick={handleCancel}
                disabled={submitting}
                className="min-h-11 flex-1 rounded-xl px-4 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-60 sm:flex-none"
                style={{ background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}
              >
                {options.cancelLabel || defaultCancelLabel}
              </button>
              <button
                ref={confirmBtnRef}
                type="button"
                onClick={handleConfirm}
                disabled={confirmDisabled}
                className={`flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition-all disabled:cursor-not-allowed disabled:opacity-60 sm:flex-none ${toneClasses.confirmBtn}`}
                style={toneClasses.confirmBtn ? undefined : { background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)' }}
              >
                {submitting && (
                  <span className="h-3.5 w-3.5 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent" />
                )}
                {options.confirmLabel || defaultConfirmLabel}
              </button>
            </div>
          </section>
        </div>,
        document.body,
      )}
    </ConfirmContext.Provider>
  );
};
