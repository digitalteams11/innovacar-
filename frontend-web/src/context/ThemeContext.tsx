import { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';

type Theme = 'light' | 'dark' | 'auto';

interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  resolvedTheme: 'light' | 'dark';
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

function getResolvedTheme(theme: Theme): 'light' | 'dark' {
  if (theme === 'auto') {
    const hour = new Date().getHours();
    // Light: 6:00 - 17:59 (6am to 6pm)
    // Dark: 18:00 - 5:59 (6pm to 6am)
    return hour >= 6 && hour < 18 ? 'light' : 'dark';
  }
  return theme;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    const stored = localStorage.getItem('theme') as Theme | null;
    return stored || 'auto';
  });

  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(getResolvedTheme(theme));

  useEffect(() => {
    const resolved = getResolvedTheme(theme);
    setResolvedTheme(resolved);
    const root = document.documentElement;
    if (resolved === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    localStorage.setItem('theme', theme);
  }, [theme]);

  // Re-check auto theme every minute
  useEffect(() => {
    if (theme !== 'auto') return;
    const interval = setInterval(() => {
      const resolved = getResolvedTheme('auto');
      setResolvedTheme(prev => {
        if (prev !== resolved) {
          const root = document.documentElement;
          if (resolved === 'dark') {
            root.classList.add('dark');
          } else {
            root.classList.remove('dark');
          }
        }
        return resolved;
      });
    }, 60000); // check every minute
    return () => clearInterval(interval);
  }, [theme]);

  const setTheme = (newTheme: Theme) => {
    setThemeState(newTheme);
  };

  return (
    <ThemeContext.Provider value={{ theme, setTheme, resolvedTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}
