import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, X } from 'lucide-react';

interface SearchBarProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  className?: string;
}

export default function SearchBar({ placeholder, value, onChange, className = '' }: SearchBarProps) {
  const { t } = useTranslation();
  const resolvedPlaceholder = placeholder ?? t('common.search');
  const [localValue, setLocalValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      onChange(localValue);
    }, 300);
    return () => clearTimeout(timer);
  }, [localValue]);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  return (
    <div className={`relative flex items-center ${className}`}>
      <Search size={16} className="absolute left-3.5 text-slate-400 pointer-events-none" />
      <input
        type="text"
        placeholder={resolvedPlaceholder}
        value={localValue}
        onChange={(e) => setLocalValue(e.target.value)}
        className="w-full pl-10 pr-9 py-2 sm:py-2.5 rounded-xl border border-[#e8e6e1]/80 dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white placeholder:text-slate-400 outline-none focus:ring-2 ring-brand-100/50 focus:border-brand-300 transition-all shadow-soft"
      />
      {localValue && (
        <button
          onClick={() => { setLocalValue(''); onChange(''); }}
          className="absolute right-3 p-0.5 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors"
        >
          <X size={14} className="text-slate-400" />
        </button>
      )}
    </div>
  );
}
