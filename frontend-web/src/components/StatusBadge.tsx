import { cn } from '../lib/utils';
import { type LucideIcon } from 'lucide-react';

export type StatusVariant = 
  | 'success' | 'warning' | 'danger' | 'info' 
  | 'neutral' | 'gold' | 'blue' | 'purple'
  | 'available' | 'rented' | 'maintenance' | 'pending' | 'confirmed' | 'cancelled'
  | 'paid' | 'unpaid' | 'overdue';

interface StatusBadgeProps {
  variant: StatusVariant;
  children: React.ReactNode;
  icon?: LucideIcon;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  dot?: boolean;
}

const variantStyles: Record<StatusVariant, string> = {
  success: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  available: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  paid: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  confirmed: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20',
  warning: 'bg-amber-500/10 text-amber-500 border-amber-500/20',
  pending: 'bg-amber-500/10 text-amber-500 border-amber-500/20',
  danger: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  cancelled: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  overdue: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  info: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  blue: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  neutral: 'bg-slate-500/10 text-slate-500 border-slate-500/20',
  gold: 'bg-accent-400/10 text-accent-500 border-accent-400/20',
  purple: 'bg-purple-500/10 text-purple-500 border-purple-500/20',
  rented: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
  maintenance: 'bg-rose-500/10 text-rose-500 border-rose-500/20',
  unpaid: 'bg-amber-500/10 text-amber-500 border-amber-500/20',
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
