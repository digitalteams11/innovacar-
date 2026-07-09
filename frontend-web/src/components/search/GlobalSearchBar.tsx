import { useCallback, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { useKeyboardShortcut } from '../../hooks/useKeyboardShortcut';
import { CommandPalette } from './CommandPalette';

function isMacPlatform() {
  if (typeof navigator === 'undefined') return false;
  return /mac|iphone|ipad|ipod/i.test(navigator.platform);
}

export function GlobalSearchBar({ className }: { className?: string }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const shortcut = useMemo(() => (isMacPlatform() ? '⌘ K' : 'Ctrl K'), []);
  const openPalette = useCallback(() => setOpen(true), []);
  useKeyboardShortcut(openPalette);

  return (
    <div className={cn('contents', className)}>
      <button
        type="button"
        onClick={openPalette}
        className="surface-control hidden h-9 w-full max-w-[460px] items-center gap-2 px-3 text-start md:flex"
        data-global-search-trigger="true"
      >
        <Search size={15} className="text-[var(--text-muted)]" />
        <span className="min-w-0 flex-1 truncate text-sm text-[var(--text-muted)]">
          {t('search.searchPlaceholder', 'Search vehicles, clients, reservations, contracts...')}
        </span>
        <kbd className="rounded border border-[var(--border-subtle)] px-1.5 py-0.5 text-[9px] text-[var(--text-muted)]">{shortcut}</kbd>
      </button>
      <button
        type="button"
        onClick={openPalette}
        className="flex h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)] md:hidden"
        aria-label={t('search.openGlobalSearch', 'Open global search')}
        data-global-search-trigger="true"
      >
        <Search size={18} />
      </button>
      <CommandPalette open={open} onClose={() => setOpen(false)} />
    </div>
  );
}
