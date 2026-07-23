import { Moon, Monitor, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../context/ThemeContext';

export default function ThemeToggle() {
  const { theme, resolvedTheme, setTheme } = useTheme();
  const { t } = useTranslation();
  const isDark = resolvedTheme === 'dark';
  const isAuto = theme === 'system';

  const toggleTheme = () => {
    setTheme(isDark ? 'light' : 'dark');
  };

  return (
    <div className="flex items-center gap-2" aria-label={t('common.themeControls', 'Theme controls')}>
      <button
        type="button"
        onClick={toggleTheme}
        aria-label={isDark ? t('common.switchToLightMode', 'Switch to light mode') : t('common.switchToDarkMode', 'Switch to dark mode')}
        aria-pressed={isDark}
        className={`relative flex h-9 w-[74px] items-center rounded-full border p-1 shadow-inner transition-all duration-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400/70 ${
          isDark
            ? 'border-white/15 bg-slate-950/70 shadow-cyan-500/10'
            : 'border-slate-900/10 bg-white/75 shadow-slate-900/10'
        }`}
        title={isDark ? t('common.darkModeActive', 'Dark mode active') : t('common.lightModeActive', 'Light mode active')}
      >
        <span className="absolute left-2 text-amber-400 transition-opacity duration-300" aria-hidden="true">
          <Sun size={15} className={isDark ? 'opacity-45' : 'opacity-100'} />
        </span>
        <span className="absolute right-2 text-cyan-300 transition-opacity duration-300" aria-hidden="true">
          <Moon size={15} className={isDark ? 'opacity-100' : 'opacity-45'} />
        </span>
        <span
          className={`relative z-10 flex h-7 w-7 items-center justify-center rounded-full bg-white text-slate-900 shadow-lg shadow-slate-900/20 transition-transform duration-300 ${
            isDark ? 'translate-x-[36px]' : 'translate-x-0'
          }`}
          aria-hidden="true"
        >
          {isDark ? <Moon size={14} /> : <Sun size={14} />}
        </span>
      </button>

      <button
        type="button"
        onClick={() => setTheme('system')}
        aria-pressed={isAuto}
        className={`hidden items-center gap-1.5 rounded-full border px-3 py-2 text-[11px] font-bold uppercase tracking-[0.18em] transition-all sm:inline-flex ${
          isAuto
            ? 'border-emerald-400/40 bg-emerald-400/15 text-emerald-600 dark:text-emerald-200'
            : 'border-[var(--border-subtle)] bg-[var(--bg-card)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
        }`}
        title={t('common.useSystemTheme', 'Use system theme')}
      >
        <Monitor size={13} />
        {t('common.autoMode', 'Auto')}
      </button>
    </div>
  );
}
