import { forwardRef } from 'react';
import { motion } from 'framer-motion';
import { cn } from '../lib/utils';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  hover?: boolean;
  glow?: 'none' | 'gold' | 'blue' | 'success' | 'warning' | 'danger';
  padding?: 'none' | 'sm' | 'md' | 'lg';
  onClick?: () => void;
  delay?: number;
}

const glowMap = {
  none: '',
  gold: 'hover:shadow-[0_0_30px_-4px_rgba(212,168,83,0.25)]',
  blue: 'hover:shadow-[0_0_30px_-4px_rgba(59,130,246,0.25)]',
  success: 'hover:shadow-[0_0_30px_-4px_rgba(16,185,129,0.25)]',
  warning: 'hover:shadow-[0_0_30px_-4px_rgba(245,158,11,0.25)]',
  danger: 'hover:shadow-[0_0_30px_-4px_rgba(239,68,68,0.25)]',
};

const paddingMap = {
  none: '',
  sm: 'p-[calc(1rem*var(--density-scale))]',
  md: 'p-[calc(1.25rem*var(--density-scale))] sm:p-[calc(1.5rem*var(--density-scale))]',
  lg: 'p-[calc(1.5rem*var(--density-scale))] sm:p-[calc(2rem*var(--density-scale))]',
};

export const GlassCard = forwardRef<HTMLDivElement, GlassCardProps>(
  ({ children, className, hover = true, glow = 'none', padding = 'md', onClick, delay = 0 }, ref) => {
    return (
      <motion.div
        ref={ref}
        onClick={onClick}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: delay / 1000, ease: [0.16, 1, 0.3, 1] }}
        whileHover={hover ? { y: -2, transition: { duration: 0.2 } } : undefined}
        className={cn(
          'glass-card',
          paddingMap[padding],
          hover && 'hover:shadow-elevated',
          glow !== 'none' && glowMap[glow],
          onClick && 'cursor-pointer',
          className
        )}
      >
        {children}
      </motion.div>
    );
  }
);

GlassCard.displayName = 'GlassCard';
