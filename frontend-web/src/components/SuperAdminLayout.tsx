import { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { useAuth } from '../context/AuthContext';
import { resolveMediaUrl } from '../lib/utils';

interface PlatformBranding {
  platformName?: string;
  platformLogoUrl?: string;
}

import { superAdminApi } from '../api/superAdminApi';
import LanguageSwitcher from './LanguageSwitcher';
import ThemeToggle from './ThemeToggle';
import {
  LayoutDashboard, Building2, CreditCard, Satellite,
  Users, Receipt, LifeBuoy, Bell, BarChart3, Settings,
  Shield, LogOut, Search, Menu, X, ChevronLeft, ChevronRight,
  Globe, Zap, Mail, Megaphone, FileText, ClipboardList, KeyRound, DatabaseBackup,
  UserCog, ShieldCheck, XCircle, Sparkles, Database
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface SidebarItemProps {
  to: string;
  icon: LucideIcon;
  label: string;
  active: boolean;
  collapsed: boolean;
}

interface AdminNotification {
  id: number | string;
  type: string;
  message: string;
  timestamp?: string;
}

const SidebarItem = ({ to, icon: Icon, label, active, collapsed }: SidebarItemProps) => (
  <Link
    to={to}
    className={`flex items-center gap-3 px-4 py-3 mx-2 rounded-xl transition-all duration-200 relative group ${
      active
        ? 'text-white bg-white/10'
        : 'text-slate-400 hover:text-white hover:bg-white/5'
    } ${collapsed ? 'justify-center px-2 mx-2' : ''}`}
    title={collapsed ? label : undefined}
  >
    {active && !collapsed && (
      <div className="absolute start-0 top-1/2 -translate-y-1/2 w-[3px] h-6 bg-accent-400 rounded-e-full" />
    )}
    {active && collapsed && (
      <div className="absolute start-0 top-1/2 -translate-y-1/2 w-[3px] h-5 bg-accent-400 rounded-e-full" />
    )}
    <Icon size={18} className={active ? 'text-accent-400' : 'text-slate-400 group-hover:text-white transition-colors'} strokeWidth={active ? 2.5 : 2} />
    {!collapsed && (
      <span className={`text-sm tracking-wide transition-all ${active ? 'font-medium' : 'font-normal'}`}>{label}</span>
    )}
  </Link>
);

export default function SuperAdminLayout({ children }: { children: React.ReactNode }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifications, setNotifications] = useState<AdminNotification[]>([]);
  const [notifLoading, setNotifLoading] = useState(false);
  const [canManageStaff, setCanManageStaff] = useState(false);
  const [branding, setBranding] = useState<PlatformBranding>({});
  const notifRef = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const { logout, user, profile } = useAuth();
  const { t } = useTranslation();

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(event.target as Node)) {
        setNotifOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    // Defense in depth only — the backend enforces STAFF_MANAGE on every
    // staff/role endpoint regardless of what this sidebar shows.
    if (user?.role !== 'SUPER_ADMIN') {
      setCanManageStaff(false);
      return;
    }
    superAdminApi.getMyStaffAccess()
      .then(({ data }) => setCanManageStaff((data?.permissions || []).includes('STAFF_MANAGE')))
      .catch(() => setCanManageStaff(false));
  }, [user?.role]);

  useEffect(() => {
    const loadBranding = () => {
      superAdminApi.getBrandingSettings()
        .then(({ data }) => setBranding(data?.data || {}))
        .catch(() => undefined);
    };
    loadBranding();
    window.addEventListener('platform-branding-updated', loadBranding);
    return () => window.removeEventListener('platform-branding-updated', loadBranding);
  }, []);

  const fetchNotifications = async () => {
    setNotifLoading(true);
    try {
      const res = await superAdminApi.getNotifications();
      setNotifications(res.data.slice(0, 5));
    } catch (err) {
      console.error(err);
    } finally {
      setNotifLoading(false);
    }
  };

  const toggleNotif = () => {
    if (!notifOpen) fetchNotifications();
    setNotifOpen(!notifOpen);
  };

  const menuSections = [
    {
      label: t('superAdmin.nav.platform'),
      items: [
        { to: '/super-admin', icon: LayoutDashboard, label: t('superAdmin.nav.overview') },
        { to: '/super-admin/analytics', icon: BarChart3, label: t('superAdmin.nav.analytics') },
      ],
    },
    {
      label: t('superAdmin.nav.business'),
      items: [
        { to: '/super-admin/agencies', icon: Building2, label: t('superAdmin.nav.agencies') },
        { to: '/super-admin/users', icon: Users, label: t('superAdmin.nav.users') },
        { to: '/super-admin/subscriptions', icon: CreditCard, label: t('superAdmin.nav.subscriptions') },
        { to: '/super-admin/cancellation-requests', icon: XCircle, label: t('superAdmin.nav.cancellationRequests') },
        { to: '/super-admin/features', icon: KeyRound, label: t('superAdmin.nav.features') },
        { to: '/super-admin/payments', icon: Receipt, label: t('superAdmin.nav.payments') },
        { to: '/super-admin/contracts', icon: FileText, label: t('superAdmin.nav.contracts') },
      ],
    },
    {
      label: t('superAdmin.nav.operations'),
      items: [
        { to: '/super-admin/gps', icon: Satellite, label: t('superAdmin.nav.gps') },
        { to: '/super-admin/support', icon: LifeBuoy, label: t('superAdmin.nav.support') },
        { to: '/super-admin/announcements', icon: Megaphone, label: t('superAdmin.nav.announcements') },
        { to: '/super-admin/emails', icon: Mail, label: t('superAdmin.nav.emails') },
        { to: '/super-admin/notifications', icon: Bell, label: t('superAdmin.nav.notifications') },
      ],
    },
    {
      label: t('superAdmin.nav.security'),
      items: [
        { to: '/super-admin/security', icon: Shield, label: t('superAdmin.nav.security') },
      ],
    },
    {
      label: t('superAdmin.nav.system'),
      items: [
        ...(canManageStaff ? [
          { to: '/super-admin/staff', icon: UserCog, label: t('superAdmin.nav.staff') },
          { to: '/super-admin/roles', icon: ShieldCheck, label: t('superAdmin.nav.roles') },
        ] : []),
        { to: '/super-admin/settings', icon: Settings, label: t('superAdmin.nav.settings') },
        { to: '/super-admin/ai-settings', icon: Sparkles, label: 'AI & Automation' },
        { to: '/super-admin/backups', icon: DatabaseBackup, label: t('superAdmin.nav.backups') },
        ...(canManageStaff ? [
          { to: '/super-admin/data-reset', icon: Database, label: 'Data Reset Center' },
        ] : []),
        { to: '/super-admin/reports', icon: ClipboardList, label: t('superAdmin.nav.reports') },
        { to: '/super-admin/marketing', icon: Megaphone, label: t('superAdmin.nav.marketing') },
      ],
    },
  ];

  const isActive = (path: string) => {
    if (path === '/super-admin') return location.pathname === '/super-admin';
    return location.pathname.startsWith(path);
  };

  return (
    <div className="app-shell min-h-screen flex">
      {/* ===== DESKTOP SIDEBAR ===== */}
      <aside
        className={`app-sidebar hidden lg:flex flex-col fixed inset-y-4 start-4 rounded-lg z-50 transition-all duration-300 ${
          sidebarCollapsed ? 'w-[76px]' : 'w-[260px]'
        }`}
      >
        {/* Logo */}
        <div className={`p-5 mb-2 flex items-center ${sidebarCollapsed ? 'justify-center' : 'justify-between'}`}>
          <div className="flex items-center gap-3">
            {branding.platformLogoUrl ? (
              <img
                src={resolveMediaUrl(branding.platformLogoUrl) || ''}
                alt={branding.platformName || 'Logo'}
                className="w-9 h-9 rounded-lg object-contain bg-white shrink-0"
                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
              />
            ) : (
              <div className="w-9 h-9 rounded-lg bg-accent-400 flex items-center justify-center shadow-lg shrink-0">
                <Zap size={18} className="text-[#0a0f2c]" strokeWidth={2.5} />
              </div>
            )}
            {!sidebarCollapsed && (
              <div className="text-white">
                <h1 className="text-base font-bold tracking-tight leading-tight">{branding.platformName || 'Innovax'}</h1>
                <p className="text-[9px] text-slate-400 font-medium uppercase tracking-widest">{t('superAdmin.superAdminBadge', 'Super Admin')}</p>
              </div>
            )}
          </div>
          {!sidebarCollapsed && (
            <button
              onClick={() => setSidebarCollapsed(true)}
              className="text-slate-500 hover:text-white transition-colors p-1"
            >
              <ChevronLeft size={16} />
            </button>
          )}
        </div>

        {/* Collapse toggle when collapsed */}
        {sidebarCollapsed && (
          <button
            onClick={() => setSidebarCollapsed(false)}
            className="mx-auto mb-3 text-slate-500 hover:text-white transition-colors p-1"
          >
            <ChevronRight size={16} />
          </button>
        )}

        <nav className="flex-1 overflow-y-auto no-scrollbar py-2">
          {menuSections.map((section) => (
            <div key={section.label} className="mb-3">
              {!sidebarCollapsed && (
                <p className="px-4 py-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  {section.label}
                </p>
              )}
              <div className="space-y-0.5">
                {section.items.map((item) => (
                  <SidebarItem
                    key={item.to}
                    {...item}
                    active={isActive(item.to)}
                    collapsed={sidebarCollapsed}
                  />
                ))}
              </div>
            </div>
          ))}
        </nav>

        {/* Logout */}
        <div className={`px-3 pb-3 ${sidebarCollapsed ? 'flex justify-center' : ''}`}>
          <button
            onClick={logout}
            className={`flex items-center gap-2 text-rose-400 hover:bg-rose-500/10 hover:text-rose-300 transition-all text-sm font-medium rounded-xl border border-transparent hover:border-rose-500/20 ${sidebarCollapsed ? 'justify-center w-10 h-10 p-0' : 'w-full px-4 py-2.5'}`}
            title={sidebarCollapsed ? t('layout.logout') : undefined}
          >
            <LogOut size={18} />
            {!sidebarCollapsed && <span>{t('layout.logout')}</span>}
          </button>
        </div>

        {/* Bottom actions */}
        {!sidebarCollapsed && (
          <div className="p-4 mx-3 mb-4 bg-[#0f1535] rounded-2xl border border-white/5">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-accent-400/15 flex items-center justify-center shrink-0">
                <Globe size={14} className="text-accent-400" />
              </div>
              <div className="min-w-0">
                <p className="text-white text-xs font-medium truncate">{t('superAdmin.platformActive')}</p>
                <p className="text-slate-500 text-[10px] truncate">{t('superAdmin.allSystemsOperational')}</p>
              </div>
            </div>
          </div>
        )}
      </aside>

      {/* ===== MOBILE SIDEBAR OVERLAY ===== */}
      {mobileMenuOpen && (
        <>
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[60] lg:hidden"
            onClick={() => setMobileMenuOpen(false)}
          />
          <div className="app-sidebar fixed inset-y-0 start-0 w-[260px] z-[70] lg:hidden flex flex-col">
            <div className="p-5 mb-2 flex items-center justify-between">
              <div className="flex items-center gap-3">
                {branding.platformLogoUrl ? (
                  <img
                    src={resolveMediaUrl(branding.platformLogoUrl) || ''}
                    alt={branding.platformName || 'Logo'}
                    className="w-9 h-9 rounded-lg object-contain bg-white"
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                  />
                ) : (
                  <div className="w-9 h-9 rounded-lg bg-accent-400 flex items-center justify-center shadow-lg">
                    <Zap size={18} className="text-[#0a0f2c]" strokeWidth={2.5} />
                  </div>
                )}
                <div className="text-white">
                  <h1 className="text-base font-bold tracking-tight">{branding.platformName || 'Innovax'}</h1>
                  <p className="text-[9px] text-slate-400 uppercase tracking-widest">{t('superAdmin.superAdminBadge', 'Super Admin')}</p>
                </div>
              </div>
              <button onClick={() => setMobileMenuOpen(false)} className="text-slate-400 hover:text-white">
                <X size={20} />
              </button>
            </div>
            <nav className="flex-1 overflow-y-auto py-2">
              {menuSections.map((section) => (
                <div key={section.label} className="mb-3">
                  <p className="px-4 py-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                    {section.label}
                  </p>
                  <div className="space-y-0.5">
                    {section.items.map((item) => (
                      <Link
                        key={item.to}
                        to={item.to}
                        onClick={() => setMobileMenuOpen(false)}
                        className={`flex items-center gap-3 px-4 py-3 mx-2 rounded-xl transition-all ${
                          isActive(item.to)
                            ? 'text-white bg-white/10'
                            : 'text-slate-400 hover:text-white hover:bg-white/5'
                        }`}
                      >
                        <item.icon size={18} className={isActive(item.to) ? 'text-accent-400' : ''} strokeWidth={isActive(item.to) ? 2.5 : 2} />
                        <span className="text-sm">{item.label}</span>
                      </Link>
                    ))}
                  </div>
                </div>
              ))}
            </nav>
            <div className="p-4">
              <button
                onClick={() => { setMobileMenuOpen(false); logout(); }}
                className="flex items-center gap-3 px-4 py-3 w-full rounded-xl text-rose-400 hover:bg-rose-500/10 transition-all"
              >
                <LogOut size={18} />
                <span className="text-sm font-medium">{t('layout.logout')}</span>
              </button>
            </div>
          </div>
        </>
      )}

      {/* ===== MAIN CONTENT AREA ===== */}
      <div className={`flex-1 flex flex-col min-w-0 transition-all duration-300 ${sidebarCollapsed ? 'lg:ms-[96px]' : 'lg:ms-[268px]'}`}>
        {/* Topbar */}
        <header className="app-topbar h-[58px] lg:h-[64px] mx-2 lg:mx-4 px-4 lg:px-5 flex items-center justify-between sticky top-2 lg:top-4 z-40 rounded-lg">
          <div className="relative flex items-center gap-3 w-full">
            <button
              onClick={() => setMobileMenuOpen(true)}
              className="lg:hidden p-2 hover:bg-slate-200/50 rounded-lg transition-colors"
            >
              <Menu size={20} className="text-[#1e293b]" />
            </button>
            <div className="surface-control hidden md:flex items-center gap-3 px-4 py-2.5 w-[360px] group transition-all">
              <Search size={16} className="text-slate-400 group-focus-within:text-brand-500 transition-colors" />
              <input
                type="text"
                placeholder={t('superAdmin.searchPlaceholder')}
                className="bg-transparent border-none outline-none text-sm font-normal w-full text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
              />
            </div>
          </div>

          <div className="relative flex items-center gap-1 lg:gap-2">
            <div className="hidden lg:block">
              <ThemeToggle />
            </div>
            <div className="hidden sm:block">
              <LanguageSwitcher />
            </div>
            <div ref={notifRef} className="relative">
              <button
                onClick={toggleNotif}
                className="relative text-slate-400 hover:text-brand-500 transition-all active:scale-90 p-2 hover:bg-slate-50 rounded-lg"
              >
                <Bell size={18} />
                {notifications.length > 0 && (
                  <span className="absolute top-1.5 end-1.5 w-2 h-2 bg-rose-500 rounded-full border-2 border-white" />
                )}
              </button>
              {notifOpen && (
                <div className="glass-card absolute end-0 top-full mt-2 w-80 z-50 overflow-hidden">
                  <div className="px-4 py-3 border-b border-[#e8e6e1]/60 flex items-center justify-between">
                    <h3 className="text-sm font-bold text-[#1e293b]">{t('layout.notifications')}</h3>
                    <button onClick={() => setNotifOpen(false)} className="text-slate-400 hover:text-[#1e293b] transition-colors">
                      <X size={14} />
                    </button>
                  </div>
                  <div className="max-h-64 overflow-y-auto">
                    {notifLoading ? (
                      <div className="text-center py-4 text-slate-400 text-xs">{t('app.loading')}</div>
                    ) : notifications.length === 0 ? (
                      <div className="text-center py-4 text-slate-400 text-xs">{t('superAdmin.notifications.noNotifications')}</div>
                    ) : (
                      notifications.map((n) => (
                        <div key={n.id} className="px-4 py-3 hover:bg-slate-50 border-b border-[#e8e6e1]/30 last:border-0 cursor-pointer" onClick={() => { setNotifOpen(false); navigate('/super-admin/notifications'); }}>
                          <p className="text-xs font-semibold text-[#1e293b]">{n.type}</p>
                          <p className="text-xs text-slate-500 truncate">{n.message}</p>
                          <p className="text-[10px] text-slate-400 mt-1">{n.timestamp ? new Date(n.timestamp).toLocaleString() : '-'}</p>
                        </div>
                      ))
                    )}
                  </div>
                  <div className="px-4 py-2 border-t border-[#e8e6e1]/60 bg-slate-50/50">
                    <button
                      onClick={() => { setNotifOpen(false); navigate('/super-admin/notifications'); }}
                      className="text-xs font-medium text-brand-600 hover:text-brand-700 w-full text-center transition-colors"
                    >
                      {t('superAdmin.notifications.viewAll')}
                    </button>
                  </div>
                </div>
              )}
            </div>
            <div className="h-5 w-px bg-[#e8e6e1] mx-1 hidden sm:block" />
            <div
              onClick={() => navigate('/super-admin/settings')}
              className="flex items-center gap-2 cursor-pointer group hover:bg-slate-50/80 p-1 pe-2 rounded-xl transition-all"
            >
              <img
                src={resolveMediaUrl(profile?.avatar) || `https://ui-avatars.com/api/?name=${encodeURIComponent(profile?.fullName || user?.email?.split('@')[0] || 'U')}&background=4318ff&color=fff`}
                alt="Profile"
                className="w-8 h-8 rounded-full object-cover border border-white shadow-sm"
              />
              <div className="text-end hidden sm:block">
                <p className="text-xs font-semibold text-[#1e293b] group-hover:text-brand-600 transition-colors truncate max-w-[120px]">{profile?.fullName || user?.email?.split('@')[0] || 'Admin'}</p>
                <p className="text-[9px] font-medium text-slate-400 uppercase tracking-widest">{profile?.jobTitle || t('superAdmin.role.superAdmin')}</p>
              </div>
            </div>
            <div className="h-5 w-px bg-[#e8e6e1] mx-1 hidden sm:block"></div>
            <button
              onClick={logout}
              className="flex items-center gap-1.5 text-slate-400 hover:text-rose-500 transition-all active:scale-90 p-2 hover:bg-rose-50 rounded-lg"
              title={t('layout.logout')}
            >
              <LogOut size={18} />
              <span className="hidden sm:inline text-xs font-medium">{t('layout.logout')}</span>
            </button>
          </div>
        </header>

        {/* Content */}
        <main className="page-canvas p-3 lg:p-6 pt-2 flex-1 w-full">
          {children}
        </main>
      </div>
    </div>
  );
}
