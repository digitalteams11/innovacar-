import { cn } from '../lib/utils';
import { type LucideIcon } from 'lucide-react';

export type StatusVariant =
  | 'success' | 'warning' | 'danger' | 'info'
  | 'neutral' | 'gold' | 'blue' | 'purple'
  | 'available' | 'rented' | 'maintenance' | 'pending' | 'confirmed' | 'cancelled'
  | 'paid' | 'unpaid' | 'overdue' | 'outOfService';

interface StatusBadgeProps {
  variant: StatusVariant;
  children: React.ReactNode;
  icon?: LucideIcon;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  dot?: boolean;
}

// "600 in light / 300 in dark" — a fixed -500 shade reads as low-contrast
// green-on-teal / blue-on-navy once the surface goes dark (production
// screenshot showed near-invisible status badges), so every variant needs
// an explicit dark: shift rather than relying on the same mid-tone in both
// themes.
const variantStyles: Record<StatusVariant, string> = {
  success: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/25',
  available: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/25',
  paid: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/25',
  confirmed: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/25',
  warning: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/25',
  pending: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/25',
  danger: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/25',
  cancelled: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/25',
  overdue: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/25',
  info: 'bg-blue-500/15 text-blue-600 dark:text-blue-300 border-blue-500/25',
  blue: 'bg-blue-500/15 text-blue-600 dark:text-blue-300 border-blue-500/25',
  neutral: 'bg-slate-500/15 text-slate-600 dark:text-slate-300 border-slate-500/25',
  gold: 'bg-accent-400/15 text-accent-600 dark:text-accent-300 border-accent-400/25',
  purple: 'bg-purple-500/15 text-purple-600 dark:text-purple-300 border-purple-500/25',
  rented: 'bg-blue-500/15 text-blue-600 dark:text-blue-300 border-blue-500/25',
  maintenance: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/25',
  unpaid: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/25',
  outOfService: 'bg-slate-500/15 text-slate-600 dark:text-slate-300 border-slate-500/25',
};

const dotColors: Record<StatusVariant, string> = {
  success: 'bg-emerald-500',
  available: 'bg-emerald-500',
  paid: 'bg-emerald-500',
  confirmed: 'bg-emerald-500',
  warning: 'bg-amber-500',
  pending: 'bg-amber-500',
  danger: 'bg-rose-500',
  cancelled: 'bg-rose-500',
  overdue: 'bg-rose-500',
  info: 'bg-blue-500',
  blue: 'bg-blue-500',
  neutral: 'bg-slate-500',
  gold: 'bg-accent-400',
  purple: 'bg-purple-500',
  rented: 'bg-blue-500',
  maintenance: 'bg-rose-500',
  unpaid: 'bg-amber-500',
  outOfService: 'bg-slate-500',
};

const sizeStyles = {
  sm: 'px-2 py-0.5 text-[10px] gap-1',
  md: 'px-2.5 py-1 text-[11px] gap-1.5',
  lg: 'px-3 py-1.5 text-xs gap-2',
};

export function StatusBadge({
  variant,
  children,
  icon: Icon,
  className,
  size = 'md',
  dot = false,
}: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-lg border font-semibold uppercase tracking-wider backdrop-blur-sm',
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
    >
      {dot && (
        <span className={cn('w-1.5 h-1.5 rounded-full', dotColors[variant])} />
      )}
      {Icon && <Icon size={size === 'sm' ? 10 : size === 'lg' ? 14 : 12} />}
      {children}
    </span>
  );
}
