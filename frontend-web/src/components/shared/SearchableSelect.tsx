import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ChevronDown, Search, Loader2, RotateCcw } from 'lucide-react';

export interface SearchableSelectOption {
  value: string;
  label: string;
}

interface SearchableSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SearchableSelectOption[];
  placeholder: string;
  searchPlaceholder: string;
  emptyMessage: string;
  disabled?: boolean;
  loading?: boolean;
  error?: boolean;
  status?: 'idle' | 'loading' | 'error' | 'ready';
  onRetry?: () => void;
  retryLabel?: string;
  id?: string;
  'aria-describedby'?: string;
}

/**
 * A searchable, keyboard-accessible single-select used for the country and
 * city fields (spec sections 3/4). Deliberately not a plain <select> because
 * neither the country list (40 entries) nor a real city list is comfortably
 * browsable without search, and a native <select> can't show loading/empty/
 * retry states inline.
 */
export default function SearchableSelect({
  value, onChange, options, placeholder, searchPlaceholder, emptyMessage,
  disabled, loading, error, status, onRetry, retryLabel, id,
  'aria-describedby': describedBy,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxId = useId();

  const selected = options.find((o) => o.value === value);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  useEffect(() => {
    if (open) {
      setQuery('');
      setActiveIndex(0);
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [open]);

  const commit = (opt: SearchableSelectOption) => {
    onChange(opt.value);
    setOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') { setOpen(false); return; }
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveIndex((i) => Math.min(i + 1, filtered.length - 1)); return; }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActiveIndex((i) => Math.max(i - 1, 0)); return; }
    if (e.key === 'Enter') { e.preventDefault(); const opt = filtered[activeIndex]; if (opt) commit(opt); }
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        id={id}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-invalid={error || undefined}
        aria-describedby={describedBy}
        onClick={() => setOpen((o) => !o)}
        className="form-input flex items-center justify-between gap-2 text-start"
      >
        <span className="truncate" style={{ color: selected ? 'var(--text-primary)' : 'var(--text-faint)' }}>
          {loading ? <Loader2 size={14} className="animate-spin inline" /> : (selected?.label || placeholder)}
        </span>
        <ChevronDown size={16} className="shrink-0" style={{ color: 'var(--text-muted)' }} />
      </button>

      {open && (
        <div
          className="absolute z-30 mt-1.5 w-full rounded-xl overflow-hidden animate-scale-in"
          style={{ background: 'var(--bg-card-solid)', border: '1px solid var(--border-subtle)', boxShadow: 'var(--shadow-elevated)' }}
        >
          <div className="p-2" style={{ borderBottom: '1px solid var(--border-subtle)' }}>
            <div className="relative">
              <Search size={14} className="absolute top-1/2 -translate-y-1/2 start-3" style={{ color: 'var(--text-muted)' }} />
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={(e) => { setQuery(e.target.value); setActiveIndex(0); }}
                onKeyDown={handleKeyDown}
                placeholder={searchPlaceholder}
                className="form-input ps-8"
                style={{ minHeight: 40 }}
              />
            </div>
          </div>

          <ul role="listbox" id={listboxId} className="max-h-56 overflow-y-auto py-1">
            {status === 'loading' && (
              <li className="px-3 py-3 text-sm flex items-center gap-2" style={{ color: 'var(--text-muted)' }}>
                <Loader2 size={14} className="animate-spin" /> {searchPlaceholder}
              </li>
            )}
            {status === 'error' && (
              <li className="px-3 py-3 text-sm flex items-center justify-between gap-2" style={{ color: 'var(--danger)' }}>
                <span>{emptyMessage}</span>
                {onRetry && (
                  <button type="button" onClick={onRetry} className="flex items-center gap-1 text-xs font-semibold shrink-0" style={{ color: 'var(--brand-primary)' }}>
                    <RotateCcw size={12} /> {retryLabel}
                  </button>
                )}
              </li>
            )}
            {status !== 'loading' && status !== 'error' && filtered.length === 0 && (
              <li className="px-3 py-3 text-sm" style={{ color: 'var(--text-muted)' }}>{emptyMessage}</li>
            )}
            {status !== 'loading' && status !== 'error' && filtered.map((opt, i) => (
              <li
                key={opt.value}
                role="option"
                aria-selected={opt.value === value}
                onMouseEnter={() => setActiveIndex(i)}
                onClick={() => commit(opt)}
                className="px-3 py-2 text-sm cursor-pointer truncate"
                style={{
                  background: i === activeIndex ? 'var(--bg-hover)' : 'transparent',
                  color: opt.value === value ? 'var(--brand-primary)' : 'var(--text-primary)',
                  fontWeight: opt.value === value ? 600 : 400,
                }}
              >
                {opt.label}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
