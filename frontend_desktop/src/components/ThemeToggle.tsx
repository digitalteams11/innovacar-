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
    <div className="flex items-center gap-0.5 rounded-lg p-0.5 bg-[var(--bg-card)] border border-[var(--border-subtle)]">
      {options.map((opt) => (
        <button
          key={opt.value}
          onClick={() => setTheme(opt.value)}
          className={`p-1.5 rounded-md transition-all ${
            theme === opt.value
              ? 'bg-[var(--bg-active)] text-[#477d91]'
              : 'text-[var(--text-muted)] hover:text-[var(--text-primary)]'
          }`}
          title={opt.label}
        >
          <opt.icon size={14} />
        </button>
      ))}
    </div>
  );
}
