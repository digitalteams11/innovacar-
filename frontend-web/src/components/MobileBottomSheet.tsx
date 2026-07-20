import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

interface MobileBottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  /** Tailwind max-height class for the panel body area. Defaults to a comfortable ~62% of viewport. */
  maxHeightClass?: string;
  /** Shows a small centered grip bar above the header — a familiar "this sheet can be dismissed" affordance. */
  showDragHandle?: boolean;
}

/**
 * Generic mobile bottom sheet: dim backdrop + a panel docked to the bottom of
 * the viewport, safe-area aware. This is the shared primitive behind
 * MobileMoreMenu — any other short mobile action (filters, quick actions)
 * should build on this instead of hand-rolling another one-off overlay.
 *
 * Deliberately lower in the stacking order than AppModal/Modal.tsx (z-[1000])
 * since a bottom sheet is triggered from in-page controls (e.g. the bottom
 * nav's "More" button, itself z-50) and must sit above them but still below
 * a genuine full modal if one is ever opened from within a sheet.
 */
export default function MobileBottomSheet({ isOpen, onClose, title, children, maxHeightClass = 'max-h-[62vh]', showDragHandle = false }: MobileBottomSheetProps) {
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

  return createPortal(
    <>
      <button
        type="button"
        className="fixed inset-0 z-[60] bg-black/45 backdrop-blur-sm lg:hidden"
        onClick={onClose}
        aria-label="Close"
        tabIndex={-1}
      />
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        className={`fixed z-[70] flex flex-col inset-x-2 bottom-[calc(var(--mobile-nav-height,66px)+env(safe-area-inset-bottom)+10px)] overflow-hidden rounded-2xl border border-[var(--glass-border)] bg-[var(--bg-card-solid)] shadow-elevated lg:hidden ${maxHeightClass}`}
      >
        {showDragHandle && (
          <div className="flex shrink-0 justify-center pt-2" aria-hidden="true">
            <span className="h-1.5 w-10 rounded-full bg-[var(--border-medium)]" />
          </div>
        )}
        {title && (
          <header className="flex h-12 shrink-0 items-center justify-between border-b border-[var(--border-subtle)] px-4">
            <strong id={titleId} className="text-sm text-[var(--text-primary)]">{title}</strong>
            <button
              ref={closeButtonRef}
              type="button"
              onClick={onClose}
              aria-label="Close"
              className="flex h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
            >
              <X size={17} />
            </button>
          </header>
        )}
        {children}
      </section>
    </>,
    document.body,
  );
}
