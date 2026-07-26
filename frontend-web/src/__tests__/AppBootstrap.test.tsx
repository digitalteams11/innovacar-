import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { HashRouter } from 'react-router-dom';
import '../i18n';
import App from '../App';

// jsdom doesn't implement matchMedia — ThemeContext/theme-bootstrap-equivalent
// logic calls it to resolve the "system" preference.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

// Regression coverage for the refresh-flash bug: the app used to show its
// splash screen behind a flat setTimeout(1400ms) regardless of whether the
// session check had actually finished, which could let route content
// (login/landing) render for a frame before auth resolved, or leave the
// splash up/down independent of real readiness. It's now gated on
// AuthContext's own `loading` flag — see App.tsx's AppShell component.
const authState = { loading: true, isAuthenticated: false, isSuperAdmin: false, user: null as any };
vi.mock('../context/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../context/AuthContext')>();
  return {
    ...actual,
    useAuth: () => ({
      user: authState.user,
      profile: { fullName: '', email: '', phone: '', jobTitle: '', avatar: '' },
      tenant: null,
      login: vi.fn(),
      verify2FA: vi.fn(),
      verifyEmailOtp2FA: vi.fn(),
      logout: vi.fn(),
      register: vi.fn(),
      exchangeOAuth2Code: vi.fn(),
      refreshAccessToken: vi.fn(),
      updateProfile: vi.fn(),
      updateCurrentUser: vi.fn(),
      getProfile: () => ({ fullName: '', email: '', phone: '', jobTitle: '', avatar: '' }),
      isAuthenticated: authState.isAuthenticated,
      isSuperAdmin: authState.isSuperAdmin,
      loading: authState.loading,
      sessionExpired: false,
      signInAgain: vi.fn(),
    }),
  };
});

vi.mock('../api/axios', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: {} })), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  translateApiError: () => 'error',
}));

vi.mock('../lib/api', () => ({
  checkHealth: vi.fn(() => Promise.resolve(true)),
  API_BASE_URL: 'http://localhost:8082/api',
  API_ORIGIN: 'http://localhost:8082',
}));

function renderApp() {
  return render(
    <HashRouter>
      <App />
    </HashRouter>,
  );
}

describe('App bootstrap gate (refresh-flash regression)', () => {
  beforeEach(() => {
    authState.loading = true;
    authState.isAuthenticated = false;
    authState.isSuperAdmin = false;
    authState.user = null;
  });

  it('shows only the bootstrap splash while the session check is in progress, no route content underneath', () => {
    renderApp();
    expect(screen.getByRole('status')).toBeInTheDocument();
    // The login page's password field is a stable marker of route content
    // having mounted — it must not exist while still bootstrapping.
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(document.querySelector('input[type="email"]')).not.toBeInTheDocument();
  });

  it('renders route content and hides the splash once the session check resolves', () => {
    authState.loading = false;
    renderApp();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    // Unauthenticated + resolved -> PublicRoute renders Login, which has an
    // email input as a stable marker.
    expect(document.querySelector('input[type="email"]')).toBeInTheDocument();
  });
});
