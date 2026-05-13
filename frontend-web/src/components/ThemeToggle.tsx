import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, Monitor } from 'lucide-react';

export default function ThemeToggle() {
  const { theme, setTheme } = useTheme();

  const options = [
    { value: 'light' as const, icon: Sun, label: 'Light' },
    { value: 'dark' as const, icon: Moon, label: 'Dark' },
    { value: 'auto' as const, icon: Monitor, label: 'Auto' },
  ];

  return (
    <div className="flex items-center gap-1 bg-white/50 rounded-xl px-1 py-1">
      {options.map((opt) => (
        <button
          key={opt.value}
          onClick={() => setTheme(opt.value)}
          className={`p-1.5 rounded-lg transition-all ${
            theme === opt.value
              ? 'bg-brand-500 text-white shadow-sm'
              : 'text-slate-400 hover:text-slate-600'
          }`}
          title={opt.label}
        >
          <opt.icon size={14} />
        </button>
      ))}
    </div>
  );
}
