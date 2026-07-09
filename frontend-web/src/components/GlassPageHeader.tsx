import { useNavigate } from 'react-router-dom';
import { cn } from '../lib/utils';
import { ChevronLeft, type LucideIcon } from 'lucide-react';
import { motion } from 'framer-motion';

interface GlassPageHeaderProps {
  title: string;
  subtitle?: string;
  icon?: LucideIcon;
  backTo?: string;
  actions?: React.ReactNode;
  className?: string;
  breadcrumbs?: Array<{ label: string; to?: string }>;
}

export function GlassPageHeader({
  title,
  subtitle,
  icon: Icon,
  backTo,
  actions,
  className,
  breadcrumbs,
}: GlassPageHeaderProps) {
  const navigate = useNavigate();

  return (
    <motion.div
      initial={{ opacity: 0, y: -12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      className={cn('page-header-orbit mb-5', className)}
    >
      {/* Breadcrumbs */}
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav className="flex items-center gap-2 text-xs mb-3">
          {breadcrumbs.map((crumb, i) => (
            <span key={i} className="flex items-center gap-2">
              {i > 0 && <span className="text-[var(--text-muted)]">/</span>}
              {crumb.to ? (
                <button
                  onClick={() => navigate(crumb.to!)}
                  className="text-[var(--text-muted)] hover:text-[var(--brand-gold)] transition-colors"
                >
                  {crumb.label}
                </button>
              ) : (
                <span className="text-[var(--text-primary)] font-medium">{crumb.label}</span>
              )}
            </span>
          ))}
        </nav>
      )}

      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-5">
        <div className="flex items-center gap-3">
          {backTo && (
            <button
              onClick={() => navigate(backTo)}
              className="p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors"
            >
              <ChevronLeft size={20} style={{ color: 'var(--text-secondary)' }} />
            </button>
          )}
          {Icon && (
            <div className="w-10 h-10 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] flex items-center justify-center shadow-sm">
              <Icon size={19} className="text-[var(--brand-primary)]" />
            </div>
          )}
          <div>
            <h1 className="text-xl sm:text-[28px] font-semibold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              {title}
            </h1>
            {subtitle && (
              <p className="text-xs sm:text-sm mt-1 max-w-2xl" style={{ color: 'var(--text-muted)' }}>{subtitle}</p>
            )}
          </div>
        </div>
        {actions && (
          <div className="flex items-center gap-2">
            {actions}
          </div>
        )}
      </div>
    </motion.div>
  );
}
