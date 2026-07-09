import { useEffect, useState, useRef } from 'react';
import { motion } from 'framer-motion';
import { cn } from '../lib/utils';
import { type LucideIcon, TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: LucideIcon;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
  trendLabel?: string;
  iconBg?: string;
  iconColor?: string;
  glow?: 'none' | 'gold' | 'blue' | 'success' | 'danger';
  onClick?: () => void;
  delay?: number;
  formatter?: (val: number) => string;
  numericValue?: number;
  loading?: boolean;
}

const glowMap = {
  none: '',
  gold: 'hover:shadow-[0_0_30px_-4px_rgba(212,168,83,0.25)]',
  blue: 'hover:shadow-[0_0_30px_-4px_rgba(59,130,246,0.25)]',
  success: 'hover:shadow-[0_0_30px_-4px_rgba(16,185,129,0.25)]',
  danger: 'hover:shadow-[0_0_30px_-4px_rgba(239,68,68,0.25)]',
};

export function StatCard({
  title,
  value,
  icon: Icon,
  trend = 'neutral',
  trendValue,
  trendLabel = 'vs last month',
  iconBg = 'bg-brand-500',
  iconColor = 'text-white',
  glow = 'none',
  onClick,
  delay = 0,
  numericValue,
  loading = false,
}: StatCardProps) {
  const [displayValue, setDisplayValue] = useState(0);
  const hasAnimated = useRef(false);

  useEffect(() => {
    if (numericValue === undefined || hasAnimated.current) return;
    const timer = setTimeout(() => {
      hasAnimated.current = true;
      const duration = 1200;
      const startTime = performance.now();
      const startValue = 0;

      const animate = (currentTime: number) => {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        setDisplayValue(startValue + (numericValue - startValue) * eased);
        if (progress < 1) requestAnimationFrame(animate);
      };
      requestAnimationFrame(animate);
    }, delay + 200);
    return () => clearTimeout(timer);
  }, [numericValue, delay]);

  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : Minus;
  const trendColor = trend === 'up' ? 'text-emerald-500' : trend === 'down' ? 'text-rose-500' : 'text-slate-400';
  const trendBg = trend === 'up' ? 'bg-emerald-500/10' : trend === 'down' ? 'bg-rose-500/10' : 'bg-slate-500/10';

  const formattedValue = numericValue !== undefined
    ? displayValue.toLocaleString('en-US', { maximumFractionDigits: 0 })
    : value;

  if (loading) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: delay / 1000, ease: [0.16, 1, 0.3, 1] }}
        className="metric-surface flex items-start justify-between gap-4"
      >
        <div className="flex-1 min-w-0 space-y-2">
          <div className="shimmer h-3 w-24 rounded-md" />
          <div className="shimmer h-7 w-16 rounded-md" />
          <div className="shimmer h-3 w-20 rounded-md" />
        </div>
        <div className="shimmer w-9 h-9 rounded-lg shrink-0" />
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: delay / 1000, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -3, transition: { duration: 0.2 } }}
      onClick={onClick}
      className={cn(
        'metric-surface flex items-start justify-between gap-4 group',
        glow !== 'none' && glowMap[glow],
        onClick && 'cursor-pointer'
      )}
    >
      <div className="flex-1 min-w-0">
        <p className="text-[10px] uppercase font-semibold mb-2 tracking-[0.12em]" style={{ color: 'var(--text-muted)' }}>{title}</p>
        <h3 className="text-2xl font-semibold tracking-tight" style={{ color: 'var(--text-primary)' }}>
          {formattedValue}
        </h3>
        {(trendValue || trend !== 'neutral') && (
          <div className="flex items-center gap-1.5 mt-1">
            <span className={cn('flex items-center gap-0.5 text-[10px] font-bold px-1.5 py-0.5 rounded-md', trendBg, trendColor)}>
              <TrendIcon size={10} />
              {trendValue}%
            </span>
            <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>{trendLabel}</span>
          </div>
        )}
      </div>
      <div className={cn('w-9 h-9 rounded-lg flex items-center justify-center shrink-0 shadow-sm transition-transform duration-300 group-hover:scale-105', iconBg)}>
        <Icon size={18} className={iconColor} />
      </div>
    </motion.div>
  );
}
