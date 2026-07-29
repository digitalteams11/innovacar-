import { Link } from 'react-router-dom';
import { MoreHorizontal } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '../lib/utils';

export interface BottomNavItem {
  to: string;
  icon: LucideIcon;
  label: string;
}

interface BottomNavigationProps {
  items: BottomNavItem[];
  isActive: (to: string) => boolean;
  moreLabel: string;
  onMoreClick: () => void;
}

/**
 * Fixed mobile bottom navigation — maximum 5 items (4 primary routes + one
 * "More" trigger), safe-area aware, hidden at the lg breakpoint where the
 * desktop sidebar takes over. Height is driven by --mobile-nav-height so the
 * page content's bottom padding (set in Layout.tsx) can never drift out of
 * sync with the bar's actual height.
 */
export default function BottomNavigation({ items, isActive, moreLabel, onMoreClick }: BottomNavigationProps) {
  return (
    <nav
      className="fixed lg:hidden z-[var(--z-bottom-nav)] bottom-0 inset-x-0 min-h-[var(--mobile-nav-height,66px)] pb-[env(safe-area-inset-bottom)] px-1 flex items-center justify-around bg-[var(--glass-bg)] backdrop-blur-2xl border-t border-[var(--glass-border)]"
      aria-label="Mobile navigation"
    >
      {items.map((item) => {
        const active = isActive(item.to);
        return (
          <Link
            key={item.to}
            to={item.to}
            aria-current={active ? 'page' : undefined}
            aria-label={item.label}
            className={cn(
              'relative min-w-[54px] min-h-[54px] flex flex-col items-center justify-center gap-1 text-[10px] rounded-xl transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)]',
              active
                ? 'font-bold text-[var(--mobile-nav-active)] bg-[var(--mobile-nav-active-bg)]'
                : 'text-[var(--mobile-nav-inactive)] hover:bg-[var(--bg-hover)] active:bg-[var(--bg-hover)]',
            )}
          >
            {active && <span className="absolute top-0 w-6 h-0.5 rounded-full bg-[var(--mobile-nav-active)]" />}
            <item.icon size={20} strokeWidth={active ? 2.6 : 2} />
            <span className={cn('max-w-[68px] truncate', active && 'font-bold')}>{item.label}</span>
          </Link>
        );
      })}
      <button
        type="button"
        onClick={onMoreClick}
        aria-label={moreLabel}
        className="min-w-[54px] min-h-[54px] flex flex-col items-center justify-center gap-1 text-[10px] text-[var(--mobile-nav-inactive)] rounded-xl transition-colors hover:bg-[var(--bg-hover)] active:bg-[var(--bg-hover)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)]"
      >
        <MoreHorizontal size={20} />
        {moreLabel}
      </button>
    </nav>
  );
}
