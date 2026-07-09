import React from 'react';
import { cn } from '../lib/utils';

interface BadgeProps {
  children: React.ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'gold';
  size?: 'sm' | 'md';
  glow?: boolean;
  className?: string;
}

const variantStyles = {
  default: 'bg-[var(--bg-hover)] text-[var(--text-secondary)]',
  success: 'bg-success-50/80 text-success-600 dark:bg-success-500/15 dark:text-success-400',
  warning: 'bg-warning-50/80 text-warning-600 dark:bg-warning-500/15 dark:text-warning-400',
  danger: 'bg-danger-50/80 text-danger-600 dark:bg-danger-500/15 dark:text-danger-400',
  info: 'bg-electric-50/80 text-electric-600 dark:bg-electric-500/15 dark:text-electric-400',
  gold: 'bg-accent-50/80 text-accent-600 dark:bg-accent-500/15 dark:text-accent-400',
};

const glowStyles = {
  default: '',
  success: 'glow-success',
  warning: 'glow-warning',
  danger: 'glow-danger',
  info: 'shadow-[0_0_16px_-4px_rgba(59,130,246,0.4)]',
  gold: 'glow-gold',
};

export function Badge({ 
  children, 
  variant = 'default', 
  size = 'sm', 
  glow = false,
  className 
}: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 font-semibold uppercase tracking-wider rounded-full',
        size === 'sm' ? 'px-2.5 py-1 text-[10px]' : 'px-3 py-1.5 text-xs',
        variantStyles[variant],
        glow && glowStyles[variant],
        className
      )}
    >
      {glow && (
        <span className={cn(
          'w-1.5 h-1.5 rounded-full',
          variant === 'success' && 'bg-success-500',
          variant === 'warning' && 'bg-warning-500',
          variant === 'danger' && 'bg-danger-500',
          variant === 'info' && 'bg-electric-500',
          variant === 'gold' && 'bg-accent-400',
          variant === 'default' && 'bg-[var(--text-muted)]',
        )} />
      )}
      {children}
    </span>
  );
}
