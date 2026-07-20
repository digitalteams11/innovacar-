import { useEffect, useRef, useState } from 'react';
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

/**
 * Shared overflow ("...") menu for secondary actions on a card/row. Rows are
 * min-h-11 (44px) to meet the mobile touch-target requirement even though
 * this is also used on desktop. Uses the app's semantic tokens so it matches
 * both themes automatically instead of hardcoding light/dark literals.
 */
export default function ActionMenu({ items, ariaLabel = 'More actions' }: ActionMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={ariaLabel}
        aria-expanded={open}
        className="flex h-11 w-11 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
      >
        <MoreHorizontal size={18} />
      </button>
      {open && (
        <div className="absolute end-0 top-full z-50 mt-1 w-48 overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] py-1 shadow-elevated">
          {items.map((item, idx) => (
            <button
              key={idx}
              type="button"
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
        </div>
      )}
    </div>
  );
}
