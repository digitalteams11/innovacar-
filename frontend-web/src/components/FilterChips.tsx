
import { motion } from 'framer-motion';
import { cn } from '../lib/utils';
import { X } from 'lucide-react';

interface FilterOption {
  id: string;
  label: string;
  count?: number;
}

interface FilterChipsProps {
  options: FilterOption[];
  activeId: string;
  onChange: (id: string) => void;
  className?: string;
}

export function FilterChips({ options, activeId, onChange, className }: FilterChipsProps) {
  return (
    <div className={cn('inline-flex max-w-full items-center gap-1 flex-wrap p-1 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-card)] backdrop-blur-xl', className)}>
      {options.map((option) => {
        const isActive = activeId === option.id;
        return (
          <motion.button
            key={option.id}
            onClick={() => onChange(option.id)}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className={cn(
              'relative px-3.5 py-2 rounded-md text-xs font-semibold transition-all duration-200',
              isActive
                ? 'shadow-sm'
                : 'hover:bg-[var(--bg-hover)]'
            )}
            aria-pressed={isActive}
            style={{
              backgroundColor: isActive ? 'var(--brand-primary)' : 'var(--bg-card)',
              // Never hardcode this: --brand-primary is a per-tenant white-label/preset
              // color and can be dark (or even near-black) — the foreground must be the
              // computed contrast-safe token ThemeContext derives for it, or the label
              // becomes unreadable (dark text on a dark selected background — this was
              // the "Archived filter" production bug). Falls back to the same #171817
              // default the CSS variable itself defaults to when unset (pre-JS paint).
              color: isActive ? 'var(--brand-primary-foreground, #171817)' : 'var(--text-secondary)',
              border: isActive ? 'none' : '1px solid var(--border-subtle)',
            }}
          >
            {option.label}
            {option.count !== undefined && (
              <span
                className={cn(
                  'ml-1.5 px-1.5 py-0.5 rounded-md text-[10px] font-bold',
                  isActive ? 'bg-white/20' : 'bg-[var(--bg-hover)]'
                )}
              >
                {option.count}
              </span>
            )}
          </motion.button>
        );
      })}
    </div>
  );
}

interface ActiveFilterTagProps {
  label: string;
  onRemove: () => void;
}

export function ActiveFilterTag({ label, onRemove }: ActiveFilterTagProps) {
  return (
    <motion.span
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium"
      style={{
        backgroundColor: 'var(--bg-hover)',
        color: 'var(--text-primary)',
        border: '1px solid var(--border-subtle)',
      }}
    >
      {label}
      <button
        onClick={onRemove}
        className="p-0.5 rounded-md hover:bg-[var(--bg-active)] transition-colors"
      >
        <X size={12} />
      </button>
    </motion.span>
  );
}
