import { motion } from 'framer-motion';
import { cn } from '../lib/utils';
import { Package, type LucideIcon } from 'lucide-react';

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: LucideIcon;
  action?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

export default function EmptyState({
  title,
  description,
  icon: Icon = Package,
  action,
  className,
  size = 'md',
}: EmptyStateProps) {
  const sizeMap = {
    sm: { icon: 20, container: 'w-10 h-10', padding: 'py-8', title: 'text-sm', desc: 'text-xs' },
    md: { icon: 28, container: 'w-14 h-14', padding: 'py-12', title: 'text-base', desc: 'text-sm' },
    lg: { icon: 36, container: 'w-18 h-18', padding: 'py-16', title: 'text-lg', desc: 'text-base' },
  };

  const s = sizeMap[size];

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      className={cn('flex flex-col items-center justify-center text-center', s.padding, className)}
    >
      <div
        className={cn(
          'rounded-lg flex items-center justify-center mb-4',
          s.container
        )}
        style={{
          background: 'linear-gradient(135deg, var(--bg-hover), transparent)',
          border: '1px solid var(--border-subtle)',
        }}
      >
        <Icon size={s.icon} style={{ color: 'var(--text-muted)' }} />
      </div>
      <h3 className={cn('font-bold mb-1', s.title)} style={{ color: 'var(--text-primary)' }}>
        {title}
      </h3>
      {description && (
        <p className={cn('max-w-sm mb-4', s.desc)} style={{ color: 'var(--text-muted)' }}>
          {description}
        </p>
      )}
      {action && (
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={action.onClick}
          className="premium-action px-5 py-2.5 text-sm font-semibold transition-all"
          style={{
            boxShadow: 'var(--shadow-card)',
          }}
        >
          {action.label}
        </motion.button>
      )}
    </motion.div>
  );
}
