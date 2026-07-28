import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, waitFor, act } from '@testing-library/react';
import { useState as reactUseState, useEffect as reactUseEffect } from 'react';
import api from '../../api/axios';
import { ThemeProvider, useTheme } from '../ThemeContext';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock('../ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

// A genuinely reactive mock of useAuth: setMockAuth() below pushes a new
// value to every mounted consumer (wrapped in act()), the same way a real
// AuthContext re-render would when isAuthenticated flips after login/logout —
// a plain module-level variable wouldn't trigger React to re-render
// ThemeProvider's effects, which is exactly the transition these tests need.
interface MockAuthState { isAuthenticated: boolean; user: { id: number; themeMode?: string } | null }
let currentAuth: MockAuthState = { isAuthenticated: false, user: null };
const authListeners = new Set<() => void>();
function setMockAuth(next: MockAuthState) {
  currentAuth = next;
  authListeners.forEach((l) => l());
}
vi.mock('../AuthContext', () => ({
  useAuth: () => {
    const [, forceRender] = reactUseState(0);
    reactUseEffect(() => {
      const listener = () => forceRender((n) => n + 1);
      authListeners.add(listener);
      return () => { authListeners.delete(listener); };
    }, []);
    return { ...currentAuth, updateCurrentUser: vi.fn() };
  },
}));

const mockedApi = vi.mocked(api, true);
const CANONICAL_KEY = 'innovacar-theme';

function TestProbe() {
  const { theme, resolvedTheme } = useTheme();
  return <div data-testid="probe" data-mode={theme} data-resolved={resolvedTheme} />;
}

function mockMatchMedia(prefersDark: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('dark') ? prefersDark : false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

function isDark(): boolean {
  return document.documentElement.classList.contains('dark');
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove('dark');
  currentAuth = { isAuthenticated: false, user: null };
  authListeners.clear();
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/tenant-settings') return Promise.resolve({ data: { data: {} } });
    if (url === '/white-label') return Promise.resolve({ data: {} });
    return Promise.reject(new Error(`unmocked: ${url}`));
  });
  mockedApi.put.mockResolvedValue({ data: { data: {} } });
  mockMatchMedia(false);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('ThemeProvider — persistence across "refresh" (fresh mount)', () => {
  it('light mode survives refresh', async () => {
    localStorage.setItem(CANONICAL_KEY, 'light');
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(false));
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('light');
  });

  it('dark mode survives refresh', async () => {
    localStorage.setItem(CANONICAL_KEY, 'dark');
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(true));
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('dark');
  });

  it('system mode follows the OS preference (dark OS -> dark)', async () => {
    localStorage.setItem(CANONICAL_KEY, 'system');
    mockMatchMedia(true);
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(true));
  });

  it('system mode follows the OS preference (light OS -> light)', async () => {
    localStorage.setItem(CANONICAL_KEY, 'system');
    mockMatchMedia(false);
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(false));
  });
});

describe('ThemeProvider — explicit choice overrides OS preference', () => {
  it('explicit light overrides an OS set to dark', async () => {
    localStorage.setItem(CANONICAL_KEY, 'light');
    mockMatchMedia(true);
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(false));
  });

  it('explicit dark overrides an OS set to light', async () => {
    localStorage.setItem(CANONICAL_KEY, 'dark');
    mockMatchMedia(false);
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(true));
  });
});

describe('ThemeProvider — login/logout must not silently change the theme', () => {
  it('login does not force dark mode when the local preference is light, even if the backend profile says dark', async () => {
    localStorage.setItem(CANONICAL_KEY, 'light');
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(false));

    // Simulate session restoration / login completing — backend reports a
    // stale/default themeMode of 'dark' for this user.
    await act(async () => {
      setMockAuth({ isAuthenticated: true, user: { id: 42, themeMode: 'dark' } });
    });

    // Give any async sync effect a chance to run — it must NOT apply.
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(isDark()).toBe(false);
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('light');
  });

  it('a genuinely new device (no local preference at all) still adopts the backend theme on first login', async () => {
    // No CANONICAL_KEY set — nothing chosen on this device yet.
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(false)); // safe default: light

    await act(async () => {
      setMockAuth({ isAuthenticated: true, user: { id: 7, themeMode: 'dark' } });
    });

    await waitFor(() => expect(isDark()).toBe(true));
  });

  it('logout does not unexpectedly change the theme', async () => {
    localStorage.setItem(CANONICAL_KEY, 'dark');
    currentAuth = { isAuthenticated: true, user: { id: 1, themeMode: 'dark' } };
    render(<ThemeProvider><TestProbe /></ThemeProvider>);
    await waitFor(() => expect(isDark()).toBe(true));

    await act(async () => {
      setMockAuth({ isAuthenticated: false, user: null });
    });
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(isDark()).toBe(true);
    expect(localStorage.getItem(CANONICAL_KEY)).toBe('dark');
  });
});
