import { useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/utils';
import { Search, X } from 'lucide-react';

interface SearchInputProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  className?: string;
  autoFocus?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export function SearchInput({
  placeholder,
  value,
  onChange,
  className,
  autoFocus,
  size = 'md',
}: SearchInputProps) {
  const { t } = useTranslation();
  const [focused, setFocused] = useState(false);
  const resolvedPlaceholder = placeholder ?? t('common.search');

  const sizeClasses = {
    sm: 'px-3 py-2 text-xs',
    md: 'px-4 py-2.5 text-sm',
    lg: 'px-5 py-3 text-base',
  };

  return (
    <div
      className={cn(
        'relative flex items-center transition-all duration-300',
        sizeClasses[size],
        'rounded-lg backdrop-blur-xl',
        focused && 'ring-2',
        className
      )}
      style={{
        backgroundColor: 'var(--bg-input)',
        border: focused ? '1px solid var(--brand-gold)' : '1px solid var(--border-subtle)',
        boxShadow: focused ? '0 0 0 3px rgba(71, 125, 145, 0.1)' : 'var(--shadow-card)',
      }}
    >
      <Search
        size={size === 'sm' ? 14 : size === 'lg' ? 18 : 16}
        className={cn(
          'shrink-0 mr-3 transition-colors duration-300',
          focused ? 'text-brand-500' : 'text-[var(--text-muted)]'
        )}
      />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={resolvedPlaceholder}
        autoFocus={autoFocus}
        className="bg-transparent border-none outline-none w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
      {value && (
        <motion.button
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.8 }}
          onClick={() => onChange('')}
          className="p-1 rounded-md hover:bg-[var(--bg-hover)] transition-colors ml-2"
        >
          <X size={14} className="text-[var(--text-muted)]" />
        </motion.button>
      )}
    </div>
  );
}
