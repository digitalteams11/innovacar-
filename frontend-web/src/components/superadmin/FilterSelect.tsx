
import { ChevronDown, X } from 'lucide-react';

interface Option {
  value: string;
  label: string;
}

interface FilterSelectProps {
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

export default function FilterSelect({ options, value, onChange, placeholder = 'All', className = '' }: FilterSelectProps) {
  return (
    <div className={`relative ${className}`}>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="appearance-none w-full bg-white dark:bg-[#1e293b] px-4 py-2.5 pr-10 rounded-xl border border-[#e8e6e1]/80 dark:border-white/5 text-sm text-[#1e293b] dark:text-white cursor-pointer outline-none focus:ring-2 ring-brand-100/50 focus:border-brand-300 transition-all shadow-soft"
      >
        <option value="">{placeholder}</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
      <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none flex items-center gap-1">
        {value && (
          <button
            onClick={(e) => { e.stopPropagation(); onChange(''); }}
            className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors"
          >
            <X size={12} className="text-slate-400" />
          </button>
        )}
        <ChevronDown size={14} className="text-slate-400" />
      </div>
    </div>
  );
}
