import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  BadgeCheck, Bell, ChevronRight, CreditCard, HelpCircle, LogOut,
  Monitor, Moon, ShieldAlert, ShieldCheck, Sun, User as UserIcon,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import type { ThemeMode } from '../context/ThemeContext';
import { useToast } from '../context/ToastContext';
import { useSubscription } from '../hooks/useSubscription';
import api from '../api/axios';
import Modal from './Modal';
import { resolveMediaUrl } from '../lib/utils';
import SubscriptionBadge from './shared/SubscriptionBadge';

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

const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'fr', label: 'Français' },
  { code: 'ar', label: 'العربية' },
];

const THEME_MODES: { value: ThemeMode; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark',  label: 'Dark',  icon: Moon },
  { value: 'auto',  label: 'Auto',  icon: Monitor },
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
  const { status: subscription } = useSubscription();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const goTo = (path: string) => { setOpen(false); navigate(path); };

  const handleLogout = async () => {
    setConfirmLogout(false);
    setOpen(false);
    await logout();
  };

  const selectLanguage = (code: string) => {
    if (i18n.language === code) return;
    i18n.changeLanguage(code);
    api.put('/users/me/preferences', { language: code })
      .then(({ data }) => {
        const saved = data?.data;
        if (saved?.language) updateCurrentUser({ language: saved.language });
      })
      .catch((err: any) => {
        showToast(err?.userMessage || 'Unable to save language preference.', 'error');
      });
  };

  const avatarSrc =
    resolveMediaUrl(profile?.avatar) ||
    `https://ui-avatars.com/api/?name=${encodeURIComponent(
      profile?.fullName || user?.email?.split('@')[0] || 'U',
    )}&background=272725&color=d8c39b`;

  const displayName = profile?.fullName || user?.email?.split('@')[0] || 'Admin';
  const roleLabel   = ROLE_LABELS[user?.role || ''] || user?.role || '';
  const emailLabel  = profile?.email || user?.email || '';

  // Subscription expiry hint — only show when genuinely useful/actionable
  let subscriptionHint: ReactNode = null;
  if (subscription?.isTrial && (subscription.remainingTrialDays ?? 0) > 0) {
    subscriptionHint = (
      <span className="text-[10px] text-[var(--text-muted)]">
        {subscription.remainingTrialDays}d left in trial
      </span>
    );
  } else if (subscription?.subscriptionEndDate && subscription.status === 'CANCEL_SCHEDULED') {
    const d = new Date(subscription.subscriptionEndDate);
    subscriptionHint = (
      <span className="text-[10px] text-[var(--text-muted)]">
        Ends {d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}
      </span>
    );
  }

  return (
    <div className="relative" ref={containerRef}>

      {/* ── Trigger ─────────────────────────────────────────────────────── */}
      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="flex items-center gap-2 p-1 rounded-xl hover:bg-[var(--bg-hover)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand-primary)]/40"
      >
        <img
          src={avatarSrc}
          alt={displayName}
          className="w-8 h-8 rounded-full object-cover border-2 border-[var(--border-medium)]"
        />
        <span className="hidden xl:block text-start max-w-[120px]">
          <strong className="block text-xs leading-tight truncate text-[var(--text-primary)]">
            {displayName}
          </strong>
          <span className="block text-[9px] uppercase tracking-wider text-[var(--text-muted)]">
            {roleLabel}
          </span>
        </span>
      </button>

      {/* ── Dropdown ────────────────────────────────────────────────────── */}
      {open && (
        <div
          role="menu"
          aria-label="User menu"
          className="absolute end-0 top-[calc(100%+10px)] z-[100] w-[296px] max-h-[calc(100vh-5rem)] overflow-y-auto rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card)] shadow-[0_12px_48px_-8px_rgba(0,0,0,0.22),0_0_0_1px_rgba(0,0,0,0.04)] animate-scale-in overflow-hidden"
        >

          {/* Identity block */}
          <div className="px-4 pt-4 pb-3 bg-[var(--bg-hover)]">
            <div className="flex items-start gap-3">
              <div className="relative shrink-0">
                <img
                  src={avatarSrc}
                  alt={displayName}
                  className="w-11 h-11 rounded-full object-cover border-2 border-[var(--border-medium)]"
                />
                <span
                  className="absolute -bottom-0.5 -end-0.5 w-3 h-3 rounded-full bg-emerald-400 border-2 border-[var(--bg-hover)]"
                  aria-hidden="true"
                />
              </div>
              <div className="min-w-0 flex-1">
                <strong className="block text-sm font-semibold text-[var(--text-primary)] truncate leading-snug">
                  {displayName}
                </strong>
                <span className="block text-[11px] text-[var(--text-muted)] truncate mt-0.5">
                  {emailLabel}
                </span>
                <span className="inline-block mt-1.5 text-[9px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full"
                  style={{
                    color: 'var(--brand-primary)',
                    background: 'color-mix(in srgb, var(--brand-primary) 12%, transparent)',
                  }}
                >
                  {roleLabel}
                </span>
                {/* Subscription badge row */}
                <div className="mt-2 flex items-center gap-2 flex-wrap">
                  <SubscriptionBadge
                    planCode={subscription?.planCode || user?.planCode}
                    status={subscription?.status || user?.subscriptionStatus}
                    size="sm"
                    showLabel
                    showTooltip={false}
                  />
                  {subscriptionHint}
                </div>
              </div>
            </div>

            {/* Status badges */}
            <div className="flex flex-wrap gap-1.5 mt-3">
              <StatusBadge ok={!!user?.emailVerified} okLabel="Email verified" koLabel="Email not verified" />
              <StatusBadge ok={!!user?.twoFactorEnabled} okLabel="2FA enabled" koLabel="2FA off" />
            </div>
          </div>

          <div className="h-px bg-[var(--border-subtle)]" />

          {/* Account */}
          <div className="py-1.5 px-1.5">
            <MenuSectionLabel>{t('userMenu.account', 'Account')}</MenuSectionLabel>
            <NavItem icon={UserIcon}    label={t('userMenu.myProfile', 'My Profile')}             onClick={() => goTo('/settings?tab=profile')} />
            <NavItem icon={ShieldCheck} label={t('userMenu.security', 'Account Security')}        onClick={() => goTo('/settings?tab=security')} />
            <NavItem icon={Bell}        label={t('userMenu.notifications', 'Notifications')}      onClick={() => goTo('/settings?tab=notifications')} />
            <NavItem icon={CreditCard}  label={t('userMenu.billing', 'Billing')}                 onClick={() => goTo('/settings?tab=billing')} />
          </div>

          <div className="h-px bg-[var(--border-subtle)]" />

          {/* Preferences */}
          <div className="px-3.5 py-3 space-y-3.5">
            <MenuSectionLabel>{t('userMenu.preferences', 'Preferences')}</MenuSectionLabel>

            {/* Language */}
            <PreferenceRow label={t('userMenu.language', 'Language')}>
              <SegmentedControl>
                {LANGUAGES.map((lang) => {
                  const active = i18n.language === lang.code || i18n.language.startsWith(lang.code + '-');
                  return (
                    <SegmentedButton
                      key={lang.code}
                      active={active}
                      onClick={() => selectLanguage(lang.code)}
                      aria-label={lang.label}
                    >
                      {lang.label}
                    </SegmentedButton>
                  );
                })}
              </SegmentedControl>
            </PreferenceRow>

            {/* Theme */}
            <PreferenceRow label={t('userMenu.theme', 'Theme')}>
              <SegmentedControl>
                {THEME_MODES.map((mode) => {
                  const active = theme === mode.value;
                  return (
                    <SegmentedButton
                      key={mode.value}
                      active={active}
                      onClick={() => setTheme(mode.value)}
                      aria-label={mode.label}
                    >
                      <mode.icon size={12} aria-hidden="true" />
                      <span className="hidden sm:inline">{mode.label}</span>
                      <span className="sm:hidden">{mode.label.slice(0, 2)}</span>
                    </SegmentedButton>
                  );
                })}
              </SegmentedControl>
            </PreferenceRow>
          </div>

          <div className="h-px bg-[var(--border-subtle)]" />

          {/* Support */}
          <div className="py-1.5 px-1.5">
            <MenuSectionLabel>{t('userMenu.support', 'Support')}</MenuSectionLabel>
            <NavItem
              icon={HelpCircle}
              label={t('userMenu.helpCenter', 'Help Center')}
              onClick={() => { setOpen(false); window.dispatchEvent(new CustomEvent('open-help-center')); }}
            />
          </div>

          <div className="h-px bg-[var(--border-subtle)]" />

          {/* Logout */}
          <div className="p-1.5">
            <button
              role="menuitem"
              onClick={() => { setOpen(false); setConfirmLogout(true); }}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-rose-500/80 hover:bg-rose-500/8 hover:text-rose-500 transition-colors group"
            >
              <LogOut size={15} className="shrink-0 transition-transform group-hover:-translate-x-0.5" />
              {t('userMenu.signOut', 'Sign out')}
            </button>
          </div>
        </div>
      )}

      {/* Logout confirmation */}
      <Modal isOpen={confirmLogout} onClose={() => setConfirmLogout(false)} title={t('userMenu.signOut', 'Sign out')} maxWidth="max-w-md">
        <p className="text-sm text-[var(--text-secondary)] mb-5">
          {t('userMenu.signOutConfirm', 'Are you sure you want to sign out of your account?')}
        </p>
        <div className="flex justify-end gap-2">
          <button
            onClick={() => setConfirmLogout(false)}
            className="px-4 py-2 rounded-lg text-sm font-medium border border-[var(--border-subtle)] text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] transition-colors"
          >
            {t('common.cancel', 'Cancel')}
          </button>
          <button
            onClick={handleLogout}
            className="px-4 py-2 rounded-lg text-sm font-medium bg-rose-500 text-white hover:bg-rose-600 transition-colors"
          >
            {t('userMenu.signOut', 'Sign out')}
          </button>
        </div>
      </Modal>
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function StatusBadge({ ok, okLabel, koLabel }: { ok: boolean; okLabel: string; koLabel: string }) {
  return (
    <span className={`inline-flex items-center gap-1 text-[10px] font-medium px-2 py-0.5 rounded-full ${
      ok
        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
        : 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
    }`}>
      {ok ? <BadgeCheck size={10} aria-hidden="true" /> : <ShieldAlert size={10} aria-hidden="true" />}
      {ok ? okLabel : koLabel}
    </span>
  );
}

function MenuSectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p className="px-2.5 pb-1.5 pt-1 text-[9px] font-bold uppercase tracking-[0.12em] text-[var(--text-muted)]">
      {children}
    </p>
  );
}

function NavItem({ icon: Icon, label, onClick }: { icon: typeof UserIcon; label: string; onClick: () => void }) {
  return (
    <button
      role="menuitem"
      onClick={onClick}
      className="w-full group flex items-center gap-3 px-2.5 py-2 rounded-lg text-sm text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)] transition-colors"
    >
      <Icon
        size={15}
        className="shrink-0 text-[var(--text-muted)] group-hover:text-[var(--text-secondary)] transition-colors"
        aria-hidden="true"
      />
      <span className="flex-1 text-start">{label}</span>
      <ChevronRight
        size={12}
        className="text-[var(--text-muted)] opacity-0 group-hover:opacity-60 -translate-x-1 group-hover:translate-x-0 transition-all"
        aria-hidden="true"
      />
    </button>
  );
}

function PreferenceRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <span className="block text-[11px] font-medium text-[var(--text-muted)]">{label}</span>
      {children}
    </div>
  );
}

function SegmentedControl({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex gap-0.5 p-0.5 rounded-lg bg-[var(--bg-hover)] border border-[var(--border-subtle)]">
      {children}
    </div>
  );
}

function SegmentedButton({
  active, onClick, children, 'aria-label': ariaLabel,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
  'aria-label'?: string;
}) {
  return (
    <button
      role="menuitemradio"
      aria-checked={active}
      aria-label={ariaLabel}
      onClick={onClick}
      className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-[11px] font-semibold transition-all ${
        active
          ? 'bg-[var(--bg-card)] text-[var(--text-primary)] shadow-sm border border-[var(--border-subtle)]'
          : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
      }`}
    >
      {children}
    </button>
  );
}

