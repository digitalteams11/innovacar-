import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import UserMenu from '../UserMenu';

vi.mock('../../api/axios', () => ({
  default: { put: vi.fn(() => Promise.resolve({ data: {} })) },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    user: { email: 'jane@example.com', role: 'ADMIN', emailVerified: true, twoFactorEnabled: false },
    profile: { fullName: 'Jane Doe', email: 'jane@example.com', avatar: null },
    logout: vi.fn(),
    updateCurrentUser: vi.fn(),
  }),
}));

vi.mock('../../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'light', setTheme: vi.fn() }),
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock('../../hooks/useSubscription', () => ({
  useSubscription: () => ({ status: null }),
}));

vi.mock('../Modal', () => ({ default: () => null }));

function setViewportWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: width });
  window.dispatchEvent(new Event('resize'));
}

function renderUserMenu() {
  return render(
    <MemoryRouter>
      <UserMenu />
    </MemoryRouter>,
  );
}

describe('UserMenu — opaque surface, layering, accessibility', () => {
  beforeEach(() => {
    setViewportWidth(1280);
  });

  it('opens on trigger click and renders a solid, non-transparent panel', () => {
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));

    const panel = screen.getByRole('menu', { name: 'User menu' });
    expect(panel).toBeInTheDocument();
    // Never the translucent "glass" token — a critical account menu must be opaque.
    expect(panel.className).toContain('bg-[var(--bg-card-solid)]');
    expect(panel.className).not.toContain('bg-[var(--bg-card)]');
    expect(panel.className).not.toMatch(/backdrop-blur|bg-opacity|opacity-\d/);
  });

  it('uses the documented z-index popover token, not a random literal', () => {
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));
    const panel = screen.getByRole('menu', { name: 'User menu' });
    expect(panel.className).toContain('z-[var(--z-popover)]');
  });

  it('renders through a portal on document.body, not clipped inside the trigger', () => {
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));
    const panel = screen.getByRole('menu', { name: 'User menu' });
    expect(panel.closest('body')).toBe(document.body);
    // Not nested inside the trigger button's own relatively-positioned wrapper.
    expect(screen.getByLabelText('Account menu').closest('div')?.contains(panel)).toBe(false);
  });

  it('closes on outside click', async () => {
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));
    expect(screen.getByRole('menu', { name: 'User menu' })).toBeInTheDocument();

    fireEvent.mouseDown(document.body);
    await waitFor(() => expect(screen.queryByRole('menu', { name: 'User menu' })).not.toBeInTheDocument());
  });

  it('closes on Escape and restores focus to the trigger', async () => {
    renderUserMenu();
    const trigger = screen.getByLabelText('Account menu');
    fireEvent.click(trigger);
    expect(screen.getByRole('menu', { name: 'User menu' })).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('menu', { name: 'User menu' })).not.toBeInTheDocument());
    await waitFor(() => expect(document.activeElement).toBe(trigger));
  });

  it('mobile: renders a real dimming overlay behind an opaque bottom-sheet panel', () => {
    setViewportWidth(375);
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));

    const panel = screen.getByRole('menu', { name: 'User menu' });
    expect(panel.className).toContain('bg-[var(--bg-card-solid)]');

    // The overlay is a sibling of the panel, not a class on the panel itself —
    // the panel must never inherit the overlay's transparency.
    const overlay = panel.parentElement?.querySelector('[aria-hidden="true"]') as HTMLElement | null;
    expect(overlay).toBeTruthy();
    expect(overlay?.style.background).toContain('rgba(15, 23, 42, 0.45)');
  });

  it('mobile: clicking the overlay closes the menu', async () => {
    setViewportWidth(375);
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));
    const panel = screen.getByRole('menu', { name: 'User menu' });
    const overlay = panel.parentElement?.querySelector('[aria-hidden="true"]') as HTMLElement;

    fireEvent.click(overlay);
    await waitFor(() => expect(screen.queryByRole('menu', { name: 'User menu' })).not.toBeInTheDocument());
  });

  it('theme segmented control never uses the translucent glass token for the active state', () => {
    renderUserMenu();
    fireEvent.click(screen.getByLabelText('Account menu'));
    const lightButton = screen.getByRole('menuitemradio', { name: 'Light' });
    expect(lightButton.className).toContain('bg-[var(--bg-card-solid)]');
  });
});
