import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import AnnouncementBanner from '../AnnouncementBanner';

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ isAuthenticated: true }),
}));

const mockGet = vi.fn();
const mockPost = vi.fn().mockResolvedValue({ data: { success: true } });
vi.mock('../../api/axios', () => ({
  default: { get: (...args: unknown[]) => mockGet(...args), post: (...args: unknown[]) => mockPost(...args) },
}));

function desktopAnnouncement(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1,
    title: 'Innovacar is now available for Windows',
    message: 'Work faster with native notifications.',
    priority: 'NORMAL',
    type: 'DESKTOP_AVAILABLE',
    platform: 'WINDOWS',
    dismissible: true,
    actionUrl: '/desktop-app',
    ...overrides,
  };
}

describe('AnnouncementBanner', () => {
  let userAgentSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    // Simulate an established user (past the "first session" grace period).
    localStorage.setItem('innovacar_session_count', '5');
  });

  afterEach(() => {
    userAgentSpy?.mockRestore();
  });

  function mockWindowsUserAgent() {
    userAgentSpy = vi.spyOn(window.navigator, 'userAgent', 'get')
      .mockReturnValue('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36');
  }

  function mockMobileUserAgent() {
    userAgentSpy = vi.spyOn(window.navigator, 'userAgent', 'get')
      .mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)');
  }

  it('shows a DESKTOP_AVAILABLE announcement to an eligible Windows user', async () => {
    mockWindowsUserAgent();
    mockGet.mockResolvedValue({ data: { data: [desktopAnnouncement()] } });

    render(<MemoryRouter><AnnouncementBanner /></MemoryRouter>);

    expect(await screen.findByText(/now available for Windows/i)).toBeInTheDocument();
    expect(mockGet).toHaveBeenCalledWith('/announcements/active', { params: { platform: 'WINDOWS' } });
  });

  it('hides the desktop announcement entirely on a mobile user agent', async () => {
    mockMobileUserAgent();
    mockGet.mockResolvedValue({ data: { data: [desktopAnnouncement()] } });

    render(<MemoryRouter><AnnouncementBanner /></MemoryRouter>);

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(screen.queryByText(/now available for Windows/i)).not.toBeInTheDocument();
  });

  it('does not show a desktop promotion before the user has had a few sessions', async () => {
    mockWindowsUserAgent();
    localStorage.setItem('innovacar_session_count', '1');
    mockGet.mockResolvedValue({ data: { data: [desktopAnnouncement()] } });

    render(<MemoryRouter><AnnouncementBanner /></MemoryRouter>);

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(screen.queryByText(/now available for Windows/i)).not.toBeInTheDocument();
  });

  it('dismissing calls the backend dismiss endpoint and hides the banner immediately', async () => {
    mockWindowsUserAgent();
    mockGet.mockResolvedValue({ data: { data: [desktopAnnouncement()] } });
    const user = userEvent.setup();

    render(<MemoryRouter><AnnouncementBanner /></MemoryRouter>);
    await screen.findByText(/now available for Windows/i);

    await user.click(screen.getByRole('button', { name: /not now/i }));

    expect(mockPost).toHaveBeenCalledWith('/announcements/1/dismiss');
    expect(screen.queryByText(/now available for Windows/i)).not.toBeInTheDocument();
  });

  it('a GENERIC announcement is not platform-restricted and shows regardless of session count', async () => {
    localStorage.setItem('innovacar_session_count', '0');
    mockGet.mockResolvedValue({
      data: { data: [desktopAnnouncement({ id: 2, type: 'GENERIC', platform: null, actionUrl: null })] },
    });

    render(<MemoryRouter><AnnouncementBanner /></MemoryRouter>);

    expect(await screen.findByText(/now available for Windows/i)).toBeInTheDocument();
  });
});
