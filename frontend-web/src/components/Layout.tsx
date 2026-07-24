import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  BarChart3, Bell, Calendar, Car, ChevronDown, CreditCard, FileText, HelpCircle,
  Info, LayoutDashboard, LockKeyhole, MapPin, MoreHorizontal,
  Palette, PanelLeftClose, PanelLeftOpen, Settings, ShieldCheck,
  Sparkles, Users, Wrench,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useFeatureAccess } from '../context/FeatureAccessContext';
import { usePermissions } from '../context/PermissionContext';
import { useTheme } from '../context/ThemeContext';
import api from '../api/axios';
import TrialBanner from './TrialBanner';
import AnnouncementBanner from './AnnouncementBanner';
import NotificationBell from './shared/NotificationBell';
import GuidanceSystem from './GuidanceSystem';
import UserMenu from './UserMenu';
import SubscriptionBadge from './shared/SubscriptionBadge';
import { GlobalSearchBar } from './search/GlobalSearchBar';
import BottomNavigation from './BottomNavigation';
import MobileMoreMenu from './MobileMoreMenu';
import MobileAssistantFab from './shared/MobileAssistantFab';
import { cn } from '../lib/utils';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';

interface NavigationItem {
  to: string;
  icon: LucideIcon;
  label: string;
  feature?: string;
  permission?: string;
  /** When true, item is removed from sidebar entirely if the required feature is missing (not just locked). */
  hideWhenLocked?: boolean;
}

export default function Layout({ children }: { children: React.ReactNode }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const [adminToolsExpanded, setAdminToolsExpanded] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => localStorage.getItem('rentcar_sidebar_collapsed') === 'true',
  );
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { user } = useAuth();
  const isBlocked = user?.accountAccess?.canUsePlatform === false;
  const { hasFeature, planCode } = useFeatureAccess();
  const { hasPermission } = usePermissions();
  const { appearance, updateAppearance, branding } = useTheme();
  const logoSrc = branding?.logoUrl || INNOVACAR_LOGO_URL;
  // A custom logo URL that's unreachable/deleted must never render as a broken
  // image — fall back to the default Innovacar logo instead.
  const onLogoError = (e: React.SyntheticEvent<HTMLImageElement>) => {
    if (e.currentTarget.src !== INNOVACAR_LOGO_URL) e.currentTarget.src = INNOVACAR_LOGO_URL;
  };

  useEffect(() => {
    localStorage.setItem('rentcar_sidebar_collapsed', String(sidebarCollapsed));
  }, [sidebarCollapsed]);

  useEffect(() => {
    api.get('/tenant-settings')
      .then(({ data }) => {
        if (data?.appearance && Object.keys(data.appearance).length > 0) {
          updateAppearance(data.appearance);
        }
      })
      .catch(() => {
        // Operational settings are non-blocking for the application shell.
      });
  }, [updateAppearance]);

  // Only the daily-driver modules live in the primary sidebar. Less-used
  // admin/config pages are kept (not deleted) behind the collapsible
  // "Admin tools" section below, and are also reachable as quick links
  // from the relevant Settings tab.
  const primaryMenuItems = useMemo<NavigationItem[]>(() => [
    { to: '/', icon: LayoutDashboard, label: t('nav.dashboard') },
    { to: '/reservations', icon: Calendar, label: t('nav.reservations'), feature: 'RESERVATION_MANAGEMENT', permission: 'VIEW_RESERVATIONS' },
    { to: '/vehicles', icon: Car, label: t('nav.vehicles'), feature: 'VEHICLE_MANAGEMENT', permission: 'VIEW_VEHICLES' },
    { to: '/clients', icon: Users, label: t('nav.clients'), feature: 'CLIENT_MANAGEMENT', permission: 'VIEW_CLIENTS' },
    { to: '/contracts', icon: FileText, label: t('nav.contracts'), feature: 'CONTRACT_MANAGEMENT', permission: 'VIEW_CONTRACTS' },
    { to: '/payments', icon: CreditCard, label: t('nav.payments'), permission: 'VIEW_PAYMENTS' },
    { to: '/invoices', icon: FileText, label: t('nav.invoices'), feature: 'INVOICE_GENERATION', permission: 'VIEW_INVOICES' },
    { to: '/maintenance', icon: Wrench, label: t('nav.maintenance'), feature: 'VEHICLE_MANAGEMENT', permission: 'VIEW_MAINTENANCE' },
    { to: '/gps-tracking', icon: MapPin, label: t('nav.gps'), feature: 'GPS_TRACKING', permission: 'GPS_ACCESS' },
    { to: '/gps-alerts', icon: Bell, label: t('nav.gpsAlerts', 'GPS Alerts'), feature: 'GPS_TRACKING', permission: 'GPS_ACCESS' },
    { to: '/reports', icon: BarChart3, label: t('nav.reports'), feature: 'REPORTS_BASIC', permission: 'VIEW_REPORTS' },
    { to: '/employees', icon: Users, label: t('nav.employees'), feature: 'MULTI_EMPLOYEE', permission: 'MANAGE_EMPLOYEES' },
    { to: '/settings', icon: Settings, label: t('nav.settings') },
  ], [t]);

  const adminToolItems = useMemo<NavigationItem[]>(() => [
    { to: '/agency', icon: Info, label: t('nav.agency') },
    { to: '/role-permissions', icon: ShieldCheck, label: t('nav.roleAccess'), permission: 'MANAGE_EMPLOYEES' },
    { to: '/white-label', icon: Palette, label: t('nav.whiteLabel'), feature: 'WHITE_LABEL' },
    { to: '/automation-center', icon: Sparkles, label: t('nav.automationCenter', 'AI & Automation'), feature: 'AUTOMATION_CENTER' },
    { to: '/operations-center', icon: HelpCircle, label: t('nav.operationsCenter') },
    { to: '/help', icon: HelpCircle, label: t('nav.help', 'Help & Support') },
  ], [t]);

  const menuItems = useMemo(() => [...primaryMenuItems, ...adminToolItems], [primaryMenuItems, adminToolItems]);

  // A blocked/suspended agency is redirected out of every page except
  // /subscription and /settings (see App.tsx ProtectedRoute) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the sidebar
  // must match that, not dangle links to pages the user will immediately
  // bounce out of.
  const filterVisible = (items: NavigationItem[]) => items
    .filter((item) => !item.permission || hasPermission(item.permission))
    .filter((item) => !(item.hideWhenLocked && item.feature && !hasFeature(item.feature)))
    .filter((item) => !isBlocked || item.to === '/settings');
  const visiblePrimaryItems = filterVisible(primaryMenuItems);
  const visibleAdminToolItems = filterVisible(adminToolItems);
  const visibleItems = filterVisible(menuItems);
  const isActive = (to: string) => to === '/' ? location.pathname === '/' : location.pathname.startsWith(to);
  const isLocked = (item: NavigationItem) => Boolean(
    (item.feature && !hasFeature(item.feature)) ||
    (item.permission && !hasPermission(item.permission)),
  );
  const mobilePrimary = ['/', '/reservations', '/vehicles', '/payments']
    .map((path) => visibleItems.find((item) => item.to === path))
    .filter(Boolean) as NavigationItem[];
  const mobileMore = visibleItems.filter((item) => !mobilePrimary.some((primary) => primary.to === item.to));

  const renderNavLink = (item: NavigationItem) => {
    const active = isActive(item.to);
    const locked = isLocked(item);
    return (
      <Link
        key={item.to}
        to={item.to}
        data-tour={item.to}
        title={appearance.sidebarStyle === 'compact' || sidebarCollapsed ? item.label : undefined}
        className={cn(
          'relative flex items-center gap-3 min-h-10 px-3 rounded-lg text-sm transition-colors',
          (appearance.sidebarStyle === 'compact' || sidebarCollapsed) && 'justify-center px-0',
          active
            ? 'bg-[color-mix(in_srgb,var(--text-sidebar)_10%,transparent)] text-[var(--text-sidebar)]'
            : 'text-[var(--text-sidebar-muted)] hover:text-[var(--text-sidebar)] hover:bg-[color-mix(in_srgb,var(--text-sidebar)_6%,transparent)]',
        )}
      >
        {active && <span className="absolute start-0 h-5 w-0.5 rounded-full bg-[var(--brand-accent)]" />}
        <item.icon size={18} className={active ? 'text-[var(--brand-accent)]' : ''} />
        {appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && <span className="truncate">{item.label}</span>}
        {locked && appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && <LockKeyhole size={12} className="ms-auto text-[var(--text-sidebar-muted)]" />}
      </Link>
    );
  };

  return (
    <div className="app-shell min-h-screen flex">
      <aside className={cn(
        'app-sidebar hidden lg:flex fixed z-50 flex-col transition-all duration-300',
        appearance.sidebarStyle === 'floating' ? 'inset-y-4 start-4 rounded-lg' : 'inset-y-0 start-0',
        appearance.sidebarStyle === 'compact' || sidebarCollapsed ? 'w-[76px]' : 'w-[248px]',
      )}>
        <div className={cn('h-[68px] px-4 flex items-center', appearance.sidebarStyle === 'compact' || sidebarCollapsed ? 'justify-center' : 'justify-between')}>
          <Link to="/" className="flex items-center gap-3 min-w-0">
            <span className="w-10 h-10 rounded-xl border border-[color-mix(in_srgb,var(--text-sidebar)_12%,transparent)] bg-white/95 flex items-center justify-center shrink-0 overflow-hidden shadow-sm">
              <img src={logoSrc} alt="InnovaCar" className="h-full w-full object-contain p-0.5" onError={onLogoError} />
            </span>
            {appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && (
              <span className="min-w-0">
                <strong className="block text-base text-[var(--text-sidebar)] tracking-tight">Innova<span className="text-[var(--brand-accent)]">Car</span></strong>
                <span className="block text-[9px] uppercase tracking-[0.16em] text-[var(--text-sidebar-muted)]">by Innovax Technologies</span>
              </span>
            )}
          </Link>
          {appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && (
            <button onClick={() => setSidebarCollapsed(true)} className="p-2 rounded-lg text-[var(--text-sidebar-muted)] hover:text-[var(--text-sidebar)] hover:bg-[color-mix(in_srgb,var(--text-sidebar)_8%,transparent)]" title={t('layout.collapseSidebar')}>
              <PanelLeftClose size={16} />
            </button>
          )}
        </div>

        {sidebarCollapsed && appearance.sidebarStyle !== 'compact' && (
          <button onClick={() => setSidebarCollapsed(false)} className="mx-auto mb-2 p-2 rounded-lg text-[var(--text-sidebar-muted)] hover:text-[var(--text-sidebar)] hover:bg-[color-mix(in_srgb,var(--text-sidebar)_8%,transparent)]" title={t('layout.expandSidebar')}>
            <PanelLeftOpen size={16} />
          </button>
        )}

        <nav className="flex-1 overflow-y-auto no-scrollbar px-2 py-1 space-y-0.5" aria-label={t('layout.primaryNavigation', 'Primary navigation')}>
          {visiblePrimaryItems.map((item) => renderNavLink(item))}

          {visibleAdminToolItems.length > 0 && (
            <div className="pt-1">
              <button
                onClick={() => setAdminToolsExpanded((v) => !v)}
                title={appearance.sidebarStyle === 'compact' || sidebarCollapsed ? t('layout.adminTools') : undefined}
                className={cn(
                  'w-full flex items-center gap-3 min-h-10 px-3 rounded-lg text-sm text-[var(--text-sidebar-muted)] hover:text-[var(--text-sidebar)] hover:bg-[color-mix(in_srgb,var(--text-sidebar)_6%,transparent)] transition-colors',
                  (appearance.sidebarStyle === 'compact' || sidebarCollapsed) && 'justify-center px-0',
                )}
              >
                <MoreHorizontal size={18} />
                {appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && (
                  <>
                    <span className="truncate">{t('layout.adminTools')}</span>
                    <ChevronDown size={14} className={cn('ms-auto transition-transform', adminToolsExpanded && 'rotate-180')} />
                  </>
                )}
              </button>
              {adminToolsExpanded && (
                <div className="mt-0.5 space-y-0.5">
                  {visibleAdminToolItems.map((item) => renderNavLink(item))}
                </div>
              )}
            </div>
          )}
        </nav>

        {appearance.sidebarStyle !== 'compact' && !sidebarCollapsed && (
          <button
            onClick={() => window.dispatchEvent(new CustomEvent('open-help-center'))}
            className="mx-3 mb-3 p-3 rounded-lg border border-[color-mix(in_srgb,var(--text-sidebar)_8%,transparent)] bg-[color-mix(in_srgb,var(--text-sidebar)_4%,transparent)] text-start group"
          >
            <span className="flex items-center gap-2 text-xs font-semibold text-[var(--text-sidebar)]">
              <HelpCircle size={14} className="text-[var(--brand-accent)]" />
              {t('layout.needHelp')}
            </span>
            <span className="block mt-1 text-[10px] leading-relaxed text-[var(--text-sidebar-muted)]">{t('layout.helpText')}</span>
          </button>
        )}

      </aside>

      <div className={cn(
        'flex-1 min-w-0 flex flex-col transition-[margin] duration-300',
        appearance.sidebarStyle === 'floating'
          ? (sidebarCollapsed ? 'lg:ms-[96px]' : 'lg:ms-[268px]')
          : (appearance.sidebarStyle === 'compact' || sidebarCollapsed ? 'lg:ms-[76px]' : 'lg:ms-[248px]'),
      )}>
        <TrialBanner />
        <header className="app-topbar sticky top-2 lg:top-4 z-40 h-[58px] lg:h-[64px] mx-2 lg:mx-4 px-3 sm:px-4 lg:px-5 flex items-center gap-3 rounded-lg">
          <Link to="/" className="lg:hidden flex items-center gap-2 shrink-0" aria-label={t('layout.innovacarDashboard', 'Innovacar Dashboard')}>
            <span className="w-10 h-10 rounded-xl bg-white flex items-center justify-center overflow-hidden shadow-sm shrink-0">
              <img src={logoSrc} alt="" className="h-full w-full object-contain p-0.5" onError={onLogoError} />
            </span>
            {/* Wordmark only from the sm breakpoint (640px) up — previously
                shown from 375px, which covers the entire 390-430px mobile
                range this was meant to exclude, crowding the header icons. */}
            <strong className="hidden sm:block text-sm">InnovaCar</strong>
          </Link>

          <GlobalSearchBar />

          <div className="ms-auto flex items-center gap-1 sm:gap-2">
            <button
              onClick={() => navigate('/settings?tab=billing')}
              className="hidden md:flex h-9 items-center px-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors"
              title={t('layout.manageSubscription', 'Manage subscription')}
              aria-label={t('layout.manageSubscription', 'Manage subscription')}
            >
              <SubscriptionBadge
                planCode={planCode || user?.planCode}
                status={user?.subscriptionStatus}
                size="sm"
                showLabel
              />
            </button>
            <NotificationBell />
            <button
              onClick={() => window.dispatchEvent(new CustomEvent('open-help-center'))}
              className="h-9 w-9 rounded-lg flex items-center justify-center text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]"
              title={t('guidance.help')}
              aria-label={t('guidance.help')}
            >
              <HelpCircle size={18} />
            </button>
            <UserMenu />
          </div>
        </header>

        <main className="page-canvas flex-1 w-full p-3 sm:p-4 lg:p-6 pb-[calc(var(--mobile-nav-height,66px)+env(safe-area-inset-bottom)+32px)] lg:pb-6">
          <AnnouncementBanner />
          <div key={location.pathname} className="animate-fade">{children}</div>
        </main>
      </div>

      <BottomNavigation
        items={mobilePrimary}
        isActive={isActive}
        moreLabel={t('nav.more')}
        onMoreClick={() => setMoreOpen(true)}
      />

      <MobileMoreMenu
        isOpen={moreOpen}
        onClose={() => setMoreOpen(false)}
        title={t('nav.menu')}
        items={mobileMore}
        isActive={isActive}
        isLocked={isLocked}
      />

      <GuidanceSystem />
      <MobileAssistantFab />
    </div>
  );
}
