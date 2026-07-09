import { X } from 'lucide-react';
import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  maxWidth?: string;
}

const modalWidthClasses: Record<string, string> = {
  md: 'max-w-md',
  'max-w-md': 'max-w-md',
  lg: 'max-w-lg',
  'max-w-lg': 'max-w-lg',
  xl: 'max-w-xl',
  'max-w-xl': 'max-w-xl',
  '2xl': 'max-w-2xl',
  'max-w-2xl': 'max-w-2xl',
  '3xl': 'max-w-3xl',
  'max-w-3xl': 'max-w-3xl',
  '4xl': 'max-w-4xl',
  'max-w-4xl': 'max-w-4xl',
  '5xl': 'max-w-5xl',
  'max-w-5xl': 'max-w-5xl',
};

export default function Modal({ isOpen, onClose, title, children, maxWidth = 'max-w-lg' }: ModalProps) {
  const overlayRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
    };
    if (isOpen) {
      const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const previousOverflow = document.body.style.overflow;
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
      window.requestAnimationFrame(() => closeButtonRef.current?.focus());
      return () => {
        document.removeEventListener('keydown', handleEscape);
        document.body.style.overflow = previousOverflow;
        previouslyFocused?.focus();
      };
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const widthClass = modalWidthClasses[maxWidth] || modalWidthClasses['max-w-lg'];

  return createPortal(
    <div
      ref={overlayRef}
      className="fixed inset-0 z-[1000] flex items-stretch justify-center overflow-y-auto p-0 sm:items-center sm:p-4"
      onClick={(e) => {
        if (e.target === overlayRef.current) onClose();
      }}
    >
      <button
        type="button"
        className="fixed inset-0 cursor-default bg-black/50 backdrop-blur-sm animate-fade"
        onClick={onClose}
        aria-label="Close dialog"
        tabIndex={-1}
      />
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`relative z-10 flex h-[100dvh] min-h-0 w-full flex-col overflow-hidden bg-white shadow-2xl animate-scale-in dark:bg-[#17202e] sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:rounded-2xl ${widthClass}`}
      >
        <div className="flex shrink-0 items-center justify-between border-b border-[#e8e6e1] p-4 dark:border-white/10 sm:p-5">
          <h3 id={titleId} className="text-base font-bold text-[#1e293b] dark:text-white">{title}</h3>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            className="rounded-xl p-2 text-slate-400 transition-all hover:bg-[#f5f5f0] hover:text-[#1e293b] focus:outline-none focus:ring-2 focus:ring-brand-500/40 dark:hover:bg-white/10 dark:hover:text-white"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 sm:p-5">
          {children}
        </div>
      </section>
    </div>,
    document.body,
  );
}
