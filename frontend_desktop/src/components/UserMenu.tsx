import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  BadgeCheck, ChevronDown, CreditCard, HelpCircle, LogOut, Monitor, Moon,
  ShieldAlert, ShieldCheck, Sun, User as UserIcon, Bell,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import type { ThemeMode } from '../context/ThemeContext';
import { useToast } from '../context/ToastContext';
import api from '../api/axios';
import Modal from './Modal';
import { resolveMediaUrl } from '../lib/utils';

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  AGENCY_OWNER: 'Agency Owner',
  ADMIN: 'Administrator',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
  ACCOUNTANT: 'Accountant',
  FLEET_MANAGER: 'Fleet Manager',
  CUSTOM: 'Custom Role',
  RECEPTIONIST: 'Receptionist',
  VIEWER: 'Viewer',
  AGENT: 'Agent',
  CLIENT: 'Client',
};

const LANGUAGES: { code: string; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'fr', label: 'Français' },
  { code: 'ar', label: 'العربية' },
];

const THEME_MODES: { value: ThemeMode; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'auto', label: 'System', icon: Monitor },
];

export default function UserMenu() {
  const [open, setOpen] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
  const { user, profile, logout, updateCurrentUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const { showToast } = useToast();

  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [open]);

  const goTo = (path: string) => {
    setOpen(false);
    navigate(path);
  };

  const handleLogout = async () => {
    setConfirmLogout(false);
    setOpen(false);
    await logout();
  };

  const selectLanguage = (code: string) => {
    setOpen(false);
    if (i18n.language !== code) i18n.changeLanguage(code);
    api.put('/users/me/preferences', { language: code })
      .then(({ data }) => {
        const saved = data?.data;
        if (saved?.language) updateCurrentUser({ language: saved.language });
      })
      .catch((err: any) => {
        showToast(err?.userMessage || 'Unable to save preferences. Changes may not persist after refresh.', 'error');
      });
  };

  const selectTheme = (mode: ThemeMode) => {
    setOpen(false);
    setTheme(mode);
  };

  const avatarSrc = resolveMediaUrl(profile?.avatar)
    || `https://ui-avatars.com/api/?name=${encodeURIComponent(profile?.fullName || user?.email?.split('@')[0] || 'U')}&background=272725&color=d8c39b`;

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="ms-1 flex items-center gap-2 p-1 rounded-lg hover:bg-[var(--bg-hover)]"
      >
        <img src={avatarSrc} alt="" className="w-8 h-8 rounded-full object-cover border border-[var(--border-medium)]" />
        <span className="hidden xl:block text-start max-w-[130px]">
          <strong className="block text-xs truncate">{profile?.fullName || user?.email?.split('@')[0] || 'Admin'}</strong>
          <span className="block text-[9px] uppercase tracking-wider text-[var(--text-muted)]">{profile?.jobTitle || t('layout.admin')}</span>
        </span>
        <ChevronDown size={14} className="hidden sm:block text-[var(--text-muted)]" />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute end-0 top-[calc(100%+8px)] z-[100] w-80 max-h-[calc(100vh-5rem)] overflow-y-auto rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card)] shadow-2xl animate-scale-in"
        >
          <div className="p-4 flex items-center gap-3 border-b border-[var(--border-subtle)]">
            <img src={avatarSrc} alt="" className="w-11 h-11 rounded-full object-cover border border-[var(--border-medium)]" />
            <div className="min-w-0">
              <strong className="block text-sm truncate text-[var(--text-primary)]">{profile?.fullName || 'User'}</strong>
              <span className="block text-xs truncate text-[var(--text-muted)]">{profile?.email}</span>
              <span className="inline-block mt-1 text-[10px] font-semibold uppercase tracking-wide text-[var(--brand-primary)]">
                {ROLE_LABELS[user?.role || ''] || user?.role}
              </span>
            </div>
          </div>

          <div className="px-4 py-3 flex flex-wrap gap-1.5 border-b border-[var(--border-subtle)]">
            <Badge ok={!!user?.emailVerified} okLabel="Email verified" koLabel="Email not verified" />
            <Badge ok={!!user?.twoFactorEnabled} okLabel="2FA enabled" koLabel="2FA disabled" />
          </div>

          <div className="py-1.5 border-b border-[var(--border-subtle)]">
            <SectionLabel>Account</SectionLabel>
            <MenuItem icon={UserIcon} label="My Profile" onClick={() => goTo('/settings?tab=profile')} />
            <MenuItem icon={ShieldCheck} label="Account Security" onClick={() => goTo('/settings?tab=security')} />
            <MenuItem icon={Bell} label="Notification Settings" onClick={() => goTo('/settings?tab=notifications')} />
            <MenuItem icon={CreditCard} label="Billing" onClick={() => goTo('/settings?tab=billing')} />
          </div>

          <div className="py-3 border-b border-[var(--border-subtle)] space-y-3">
            <SectionLabel>Preferences</SectionLabel>

            <div className="px-4 space-y-1.5">
              <span className="block text-[11px] font-medium text-[var(--text-muted)]">Language</span>
              <div className="flex items-center gap-1 rounded-lg bg-[var(--bg-hover)] p-1">
                {LANGUAGES.map((lang) => (
                  <button
                    key={lang.code}
                    role="menuitemradio"
                    aria-checked={i18n.language === lang.code}
                    onClick={() => selectLanguage(lang.code)}
                    className={`flex-1 px-2 py-1.5 rounded-md text-xs font-semibold transition-all ${
                      i18n.language === lang.code
                        ? 'bg-[var(--brand-primary)] text-white shadow-sm'
                        : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
                    }`}
                  >
                    {lang.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="px-4 space-y-1.5">
              <span className="block text-[11px] font-medium text-[var(--text-muted)]">Theme</span>
              <div className="flex items-center gap-1 rounded-lg bg-[var(--bg-hover)] p-1">
                {THEME_MODES.map((mode) => (
                  <button
                    key={mode.value}
                    role="menuitemradio"
                    aria-checked={theme === mode.value}
                    onClick={() => selectTheme(mode.value)}
                    className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs font-semibold transition-all ${
                      theme === mode.value
                        ? 'bg-[var(--brand-primary)] text-white shadow-sm'
                        : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
                    }`}
                  >
                    <mode.icon size={13} />
                    {mode.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="py-1.5">
            <SectionLabel>Support</SectionLabel>
            <MenuItem icon={HelpCircle} label="Help Center" onClick={() => { setOpen(false); window.dispatchEvent(new CustomEvent('open-help-center')); }} />
          </div>

          <div className="py-1.5 border-t border-[var(--border-subtle)]">
            <button
              role="menuitem"
              onClick={() => { setOpen(false); setConfirmLogout(true); }}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-500/85 hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
            >
              <LogOut size={16} />
              Logout
            </button>
          </div>
        </div>
      )}

      <Modal isOpen={confirmLogout} onClose={() => setConfirmLogout(false)} title="Sign out" maxWidth="max-w-md">
        <p className="text-sm text-[var(--text-secondary)] mb-5">Are you sure you want to sign out of your account?</p>
        <div className="flex justify-end gap-2">
          <button
            onClick={() => setConfirmLogout(false)}
            className="px-4 py-2 rounded-lg text-sm font-medium border border-[var(--border-subtle)] text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]"
          >
            Cancel
          </button>
          <button
            onClick={handleLogout}
            className="px-4 py-2 rounded-lg text-sm font-medium bg-rose-500 text-white hover:bg-rose-600"
          >
            Sign out
          </button>
        </div>
      </Modal>
    </div>
  );
}

function Badge({ ok, okLabel, koLabel }: { ok: boolean; okLabel: string; koLabel: string }) {
  return (
    <span className={`inline-flex items-center gap-1 text-[10px] font-medium px-2 py-1 rounded-full ${
      ok ? 'bg-emerald-500/10 text-emerald-500' : 'bg-amber-500/10 text-amber-500'
    }`}>
      {ok ? <BadgeCheck size={11} /> : <ShieldAlert size={11} />}
      {ok ? okLabel : koLabel}
    </span>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <span className="block px-4 pb-1.5 text-[10px] font-semibold uppercase tracking-wide text-[var(--text-muted)]">
      {children}
    </span>
  );
}

function MenuItem({ icon: Icon, label, onClick }: { icon: typeof UserIcon; label: string; onClick: () => void }) {
  return (
    <button
      role="menuitem"
      onClick={onClick}
      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)] transition-colors"
    >
      <Icon size={16} />
      {label}
    </button>
  );
}
