import {
  Calendar,
  Car,
  CreditCard,
  FileText,
  MapPin,
  Plus,
  Settings,
  User,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import type { GlobalSearchResult } from '../../types/globalSearch';

const iconMap: Record<string, LucideIcon> = {
  car: Car,
  user: User,
  users: Users,
  calendar: Calendar,
  'file-text': FileText,
  'credit-card': CreditCard,
  plus: Plus,
  gps: MapPin,
  settings: Settings,
};

export function SearchResultItem({
  result,
  active,
  onSelect,
}: {
  result: GlobalSearchResult;
  active: boolean;
  onSelect: () => void;
}) {
  const Icon = iconMap[result.icon || ''] || FileText;
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-start transition-all',
        active
          ? 'bg-[var(--brand-primary)] text-[var(--brand-primary-foreground)] shadow-lg'
          : 'text-[var(--text-primary)] hover:bg-[var(--bg-hover)]',
      )}
    >
      <span className={cn(
        'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border',
        active
          ? 'border-white/20 bg-white/15 text-white'
          : 'border-[var(--border-subtle)] bg-[var(--bg-card)] text-[var(--brand-primary)]',
      )}>
        <Icon size={17} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-bold">{result.title}</span>
        {result.subtitle && (
          <span className={cn('block truncate text-xs', active ? 'text-white/75' : 'text-[var(--text-muted)]')}>
            {result.subtitle}
          </span>
        )}
      </span>
      <span className={cn(
        'hidden shrink-0 rounded-full border px-2 py-1 text-[10px] font-bold sm:inline-flex',
        active ? 'border-white/25 text-white/80' : 'border-[var(--border-subtle)] text-[var(--text-muted)]',
      )}>
        {result.type}
      </span>
    </button>
  );
}
