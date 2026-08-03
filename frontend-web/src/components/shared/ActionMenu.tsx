import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { MoreHorizontal } from 'lucide-react';
import { cn } from '../../lib/utils';

export interface ActionMenuItem {
  label: string;
  onClick: () => void;
  icon?: React.ReactNode;
  danger?: boolean;
  disabled?: boolean;
}

interface ActionMenuProps {
  items: ActionMenuItem[];
  /** Accessible label for the trigger button — required since it's icon-only. */
  ariaLabel?: string;
}

const MENU_WIDTH = 192; // w-48
const VIEWPORT_MARGIN = 8;

/**
 * Shared overflow ("...") menu for secondary actions on a card/row. Rows are
 * min-h-11 (44px) to meet the mobile touch-target requirement even though
 * this is also used on desktop. Uses the app's semantic tokens so it matches
 * both themes automatically instead of hardcoding light/dark literals.
 *
 * The popover renders through a document.body portal (like Modal.tsx) rather
 * than as a normal absolutely-positioned child. Several callers (e.g.
 * DashboardVehicleCard's .glass-card) set `overflow: clip` on the trigger's
 * ancestor for the card's rounded gradient edge — an inline `absolute`
 * popover got silently clipped into invisibility there, which read as "the
 * More menu button does nothing" even though the click handler fired and
 * `open` really did flip to true.
 */
export default function ActionMenu({ items, ariaLabel = 'More actions' }: ActionMenuProps) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const recalculate = () => {
    const trigger = triggerRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const isRtl = document.documentElement.dir === 'rtl';
    let left = isRtl ? rect.right - MENU_WIDTH : rect.left;
    left = Math.min(Math.max(left, VIEWPORT_MARGIN), window.innerWidth - MENU_WIDTH - VIEWPORT_MARGIN);
    const top = Math.min(rect.bottom + 4, window.innerHeight - VIEWPORT_MARGIN);
    setCoords({ top, left });
  };

  useLayoutEffect(() => {
    if (!open) return;
    recalculate();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: MouseEvent) => {
      const target = e.target as Node;
      if (triggerRef.current?.contains(target)) return;
      if (menuRef.current?.contains(target)) return;
      setOpen(false);
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    const handleReposition = () => recalculate();
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleEscape);
    window.addEventListener('scroll', handleReposition, true);
    window.addEventListener('resize', handleReposition);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleEscape);
      window.removeEventListener('scroll', handleReposition, true);
      window.removeEventListener('resize', handleReposition);
    };
  }, [open]);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={ariaLabel}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-11 w-11 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
      >
        <MoreHorizontal size={18} />
      </button>
      {open && coords && createPortal(
        <div
          ref={menuRef}
          role="menu"
          className="fixed z-[var(--z-popover)] w-48 overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] py-1 shadow-elevated"
          style={{ top: coords.top, left: coords.left }}
        >
          {items.map((item, idx) => (
            <button
              key={idx}
              type="button"
              role="menuitem"
              onClick={() => { item.onClick(); setOpen(false); }}
              disabled={item.disabled}
              className={cn(
                'flex min-h-11 w-full items-center gap-2.5 px-3 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-40',
                item.danger
                  ? 'text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-500/10'
                  : 'text-[var(--text-primary)] hover:bg-[var(--bg-hover)]',
              )}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </>
  );
}
