import React, { useEffect, useRef, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { RefreshCw, ServerOff, WifiOff } from 'lucide-react';

import { checkHealth } from './lib/api';
import { useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { NotificationProvider } from './context/NotificationContext';
import { NotificationSoundProvider } from './context/NotificationSoundContext';
import { FeatureAccessProvider } from './context/FeatureAccessContext';
import FeatureGate from './components/FeatureGate';
import PermissionGate from './components/PermissionGate';
import { PermissionProvider } from './context/PermissionContext';
import { OnboardingProvider } from './context/OnboardingContext';
import { ThemeProvider } from './context/ThemeContext';
import PremiumLoader from './components/PremiumLoader';
import SplashScreen from './components/SplashScreen';
import ErrorBoundary from './components/ErrorBoundary';
import CookieConsentBanner from './components/CookieConsentBanner';
import SessionExpiredModal from './components/SessionExpiredModal';
import { CHUNK_RELOAD_MARKER } from './lazyLoadRecovery';
import Login from './pages/Login';
const AccountSuspended = React.lazy(() => import('./pages/AccountSuspended'));
const Layout = React.lazy(() => import('./components/Layout'));
const SuperAdminLayout = React.lazy(() => import('./components/SuperAdminLayout'));
const Dashboard = React.lazy(() => import('./pages/Dashboard'));
const Vehicles = React.lazy(() => import('./pages/Vehicles'));
const Reservations = React.lazy(() => import('./pages/Reservations'));
const Clients = React.lazy(() => import('./pages/Clients'));
const Payments = React.lazy(() => import('./pages/Payments'));
const Settings = React.lazy(() => import('./pages/Settings'));
const CheckoutTrial = React.lazy(() => import('./pages/CheckoutTrial'));
const Contracts = React.lazy(() => import('./pages/Contracts'));
const ContractDetails = React.lazy(() => import('./pages/ContractDetails'));
const PublicContract = React.lazy(() => import('./pages/PublicContract'));
const PublicClientInformation = React.lazy(() => import('./pages/PublicClientInformation'));
const ClientInformationRequests = React.lazy(() => import('./pages/ClientInformationRequests'));
const InspectionCapture = React.lazy(() => import('./pages/InspectionCapture'));
const Invoices = React.lazy(() => import('./pages/Invoices'));
const Agency = React.lazy(() => import('./pages/Agency'));
const Employees = React.lazy(() => import('./pages/Employees'));
const Reports = React.lazy(() => import('./pages/Reports'));
const GpsSettingsPage = React.lazy(() => import('./pages/GpsSettings'));
const GpsDashboard = React.lazy(() => import('./pages/GpsDashboard'));
const GpsAlerts = React.lazy(() => import('./pages/GpsAlerts'));
const WhiteLabel = React.lazy(() => import('./pages/WhiteLabel'));
const AutomationCenter = React.lazy(() => import('./pages/AutomationCenter'));
const Maintenance = React.lazy(() => import('./pages/Maintenance'));
const RolePermissions = React.lazy(() => import('./pages/RolePermissions'));
const OperationsCenter = React.lazy(() => import('./pages/OperationsCenter'));
const HelpCenter = React.lazy(() => import('./pages/HelpCenter'));
const TicketDetail = React.lazy(() => import('./pages/TicketDetail'));
const PublicContact = React.lazy(() => import('./pages/PublicContact'));
const Register = React.lazy(() => import('./pages/Register'));
const ForgotPassword = React.lazy(() => import('./pages/ForgotPassword'));
const VerifyResetCode = React.lazy(() => import('./pages/VerifyResetCode'));
const ResetPassword = React.lazy(() => import('./pages/ResetPassword'));
const VerifyEmail = React.lazy(() => import('./pages/VerifyEmail'));
const SuperAdminDashboard = React.lazy(() => import('./pages/superadmin/SuperAdminDashboard'));
const SuperAdminAgencies = React.lazy(() => import('./pages/superadmin/SuperAdminAgencies'));
const SuperAdminAgencyDetail = React.lazy(() => import('./pages/superadmin/SuperAdminAgencyDetail'));
const SuperAdminSubscriptions = React.lazy(() => import('./pages/superadmin/SuperAdminSubscriptions'));
const SuperAdminGps = React.lazy(() => import('./pages/superadmin/SuperAdminGps'));
const SuperAdminUsers = React.lazy(() => import('./pages/superadmin/SuperAdminUsers'));
const SuperAdminPayments = React.lazy(() => import('./pages/superadmin/SuperAdminPayments'));
const SuperAdminSupport = React.lazy(() => import('./pages/superadmin/SuperAdminSupport'));
const SuperAdminContactRequests = React.lazy(() => import('./pages/superadmin/SuperAdminContactRequests'));
const SuperAdminHelpArticles = React.lazy(() => import('./pages/superadmin/SuperAdminHelpArticles'));
const SuperAdminTicketDetail = React.lazy(() => import('./pages/superadmin/SuperAdminTicketDetail'));
const SuperAdminNotifications = React.lazy(() => import('./pages/superadmin/SuperAdminNotifications'));
const SuperAdminAnalytics = React.lazy(() => import('./pages/superadmin/SuperAdminAnalytics'));
const SuperAdminSettings = React.lazy(() => import('./pages/superadmin/SuperAdminSettings'));
const SuperAdminSecurity = React.lazy(() => import('./pages/superadmin/SuperAdminSecurity'));
const SuperAdminEmailCenter = React.lazy(() => import('./pages/superadmin/SuperAdminEmailCenter'));
const SuperAdminMarketing = React.lazy(() => import('./pages/superadmin/SuperAdminMarketing'));
const SuperAdminContracts = React.lazy(() => import('./pages/superadmin/SuperAdminContracts'));
const SuperAdminReports = React.lazy(() => import('./pages/superadmin/SuperAdminReports'));
const SuperAdminFeatures = React.lazy(() => import('./pages/superadmin/SuperAdminFeatures'));
const SuperAdminBackups = React.lazy(() => import('./pages/superadmin/SuperAdminBackups'));
const SuperAdminDataReset = React.lazy(() => import('./pages/superadmin/SuperAdminDataReset'));
const SuperAdminAnnouncements = React.lazy(() => import('./pages/superadmin/SuperAdminAnnouncements'));
const SuperAdminStaff = React.lazy(() => import('./pages/superadmin/SuperAdminStaff'));
const SuperAdminRoles = React.lazy(() => import('./pages/superadmin/SuperAdminRoles'));
const SuperAdminCancellationRequests = React.lazy(() => import('./pages/superadmin/SuperAdminCancellationRequests'));
const SuperAdminAiSettings = React.lazy(() => import('./pages/superadmin/SuperAdminAiSettings'));

// â”€â”€ Route Guards â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

// ThemeProvider is NOT included here — it now wraps the whole route tree in
// App() below, because public routes (Login's Google-button theming) need
// useTheme() too. Only nest the providers that are genuinely authenticated-only.
const AuthenticatedAppProviders = ({ children }: { children: React.ReactNode }) => (
  <FeatureAccessProvider>
    <PermissionProvider>
      <OnboardingProvider>
        {children}
      </OnboardingProvider>
    </PermissionProvider>
  </FeatureAccessProvider>
);

const PublicRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, isAuthenticated, loading } = useAuth();
  if (loading) return (
    <PremiumLoader fullScreen />
  );
  if (isAuthenticated) {
    if (user?.role === 'SUPER_ADMIN') return <Navigate to="/super-admin/dashboard" replace />;
    if (user?.role === 'EMPLOYEE') return <Navigate to="/employee/dashboard" replace />;
    if (user?.role === 'ACCOUNTANT') return <Navigate to="/payments" replace />;
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
};

// Pages a blocked/suspended agency must still be able to reach: billing
// (to fix the subscription), settings (profile), and the lock screen itself.
// /checkout doesn't need to be listed here — it uses AuthOnlyRoute (below),
// not ProtectedRoute, so this blocked-agency gate never applies to it at all.
const ALWAYS_ALLOWED_PATHS_WHEN_BLOCKED = ['/subscription', '/settings', '/account-suspended'];

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, isAuthenticated, isSuperAdmin, loading } = useAuth();
  const location = useLocation();

  if (loading) return (
    <PremiumLoader fullScreen />
  );

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (isSuperAdmin) return <Navigate to="/super-admin" replace />;

  const blocked = user?.accountAccess?.canUsePlatform === false;
  if (blocked && !ALWAYS_ALLOWED_PATHS_WHEN_BLOCKED.includes(location.pathname)) {
    return <Navigate to="/account-suspended" replace />;
  }

  return (
    <AuthenticatedAppProviders>
      <Layout>{children}</Layout>
    </AuthenticatedAppProviders>
  );
};

// Authenticated-only, no Layout/sidebar â€” used for the full-screen lock state itself.
const AuthOnlyRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, isSuperAdmin, loading } = useAuth();
  if (loading) return <PremiumLoader fullScreen />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (isSuperAdmin) return <Navigate to="/super-admin" replace />;
  return <>{children}</>;
};

const SuperAdminRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, isSuperAdmin, loading } = useAuth();

  if (loading) return (
    <PremiumLoader fullScreen />
  );

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isSuperAdmin) return <Navigate to="/" replace />;

  return (
    <AuthenticatedAppProviders>
      <SuperAdminLayout>{children}</SuperAdminLayout>
    </AuthenticatedAppProviders>
  );
};

// â”€â”€ App Routes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function AppRoutes() {
  const { isSuperAdmin } = useAuth();
  return (
    <ErrorBoundary>
    <React.Suspense fallback={<PremiumLoader fullScreen />}>
    <Routes>
      {/* Public Auth Routes */}
      <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
      <Route path="/register" element={<PublicRoute><Register /></PublicRoute>} />
      <Route path="/forgot-password" element={<PublicRoute><ForgotPassword /></PublicRoute>} />
      <Route path="/verify-reset-code" element={<PublicRoute><VerifyResetCode /></PublicRoute>} />
      <Route path="/reset-password" element={<PublicRoute><ResetPassword /></PublicRoute>} />
      <Route path="/verify-email" element={<PublicRoute><VerifyEmail /></PublicRoute>} />

      <Route path="/admin" element={<Navigate to="/" replace />} />
      <Route path="/superadmin" element={<Navigate to="/super-admin" replace />} />
      {/* Public Contract Signing â€” isolated from all auth guards */}
      <Route path="/contract-sign/:contractId/:token" element={<PublicContract />} />
      <Route path="/contract-sign/:token" element={<PublicContract />} />
      <Route path="/client-info/:token" element={<PublicClientInformation />} />
      <Route path="/inspection/:token" element={<InspectionCapture />} />
      {/* Public Contact â€” no login required */}
      <Route path="/contact" element={<PublicContact />} />

      {/* Super Admin Routes */}
      <Route path="/super-admin" element={<SuperAdminRoute><SuperAdminDashboard /></SuperAdminRoute>} />
      <Route path="/super-admin/dashboard" element={<SuperAdminRoute><SuperAdminDashboard /></SuperAdminRoute>} />
      <Route path="/super-admin/agencies" element={<SuperAdminRoute><SuperAdminAgencies /></SuperAdminRoute>} />
      <Route path="/super-admin/agencies/:id" element={<SuperAdminRoute><SuperAdminAgencyDetail /></SuperAdminRoute>} />
      <Route path="/super-admin/subscriptions" element={<SuperAdminRoute><SuperAdminSubscriptions /></SuperAdminRoute>} />
      <Route path="/super-admin/gps" element={<SuperAdminRoute><SuperAdminGps /></SuperAdminRoute>} />
      <Route path="/super-admin/users" element={<SuperAdminRoute><SuperAdminUsers /></SuperAdminRoute>} />
      <Route path="/super-admin/payments" element={<SuperAdminRoute><SuperAdminPayments /></SuperAdminRoute>} />
      <Route path="/super-admin/support" element={<SuperAdminRoute><SuperAdminSupport /></SuperAdminRoute>} />
      <Route path="/super-admin/support/:id" element={<SuperAdminRoute><SuperAdminTicketDetail /></SuperAdminRoute>} />
      <Route path="/super-admin/support/settings" element={<SuperAdminRoute><SuperAdminEmailCenter /></SuperAdminRoute>} />
      <Route path="/super-admin/contact-requests" element={<SuperAdminRoute><SuperAdminContactRequests /></SuperAdminRoute>} />
      <Route path="/super-admin/help/articles" element={<SuperAdminRoute><SuperAdminHelpArticles /></SuperAdminRoute>} />
      <Route path="/super-admin/notifications" element={<SuperAdminRoute><SuperAdminNotifications /></SuperAdminRoute>} />
      <Route path="/super-admin/analytics" element={<SuperAdminRoute><SuperAdminAnalytics /></SuperAdminRoute>} />
      <Route path="/super-admin/settings" element={<SuperAdminRoute><SuperAdminSettings /></SuperAdminRoute>} />
      <Route path="/super-admin/security" element={<SuperAdminRoute><SuperAdminSecurity /></SuperAdminRoute>} />
      <Route path="/super-admin/emails" element={<SuperAdminRoute><SuperAdminEmailCenter /></SuperAdminRoute>} />
      <Route path="/super-admin/marketing" element={<SuperAdminRoute><SuperAdminMarketing /></SuperAdminRoute>} />
      <Route path="/super-admin/contracts" element={<SuperAdminRoute><SuperAdminContracts /></SuperAdminRoute>} />
      <Route path="/super-admin/reports" element={<SuperAdminRoute><SuperAdminReports /></SuperAdminRoute>} />
      <Route path="/super-admin/features" element={<SuperAdminRoute><SuperAdminFeatures /></SuperAdminRoute>} />
      <Route path="/super-admin/backups" element={<SuperAdminRoute><SuperAdminBackups /></SuperAdminRoute>} />
      <Route path="/super-admin/data-reset" element={<SuperAdminRoute><SuperAdminDataReset /></SuperAdminRoute>} />
      <Route path="/super-admin/announcements" element={<SuperAdminRoute><SuperAdminAnnouncements /></SuperAdminRoute>} />
      <Route path="/super-admin/staff" element={<SuperAdminRoute><SuperAdminStaff /></SuperAdminRoute>} />
      <Route path="/super-admin/roles" element={<SuperAdminRoute><SuperAdminRoles /></SuperAdminRoute>} />
      <Route path="/super-admin/cancellation-requests" element={<SuperAdminRoute><SuperAdminCancellationRequests /></SuperAdminRoute>} />
      <Route path="/super-admin/ai-settings" element={<SuperAdminRoute><SuperAdminAiSettings /></SuperAdminRoute>} />

      {/* Account lock screen â€” intentionally outside ProtectedRoute's Layout wrap (no sidebar) */}
      <Route path="/account-suspended" element={
        <AuthOnlyRoute><AccountSuspended /></AuthOnlyRoute>
      } />

      {/* Regular Admin Routes */}
      <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/employee/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/vehicles" element={<ProtectedRoute><PermissionGate permission="VIEW_VEHICLES"><FeatureGate feature="VEHICLE_MANAGEMENT"><Vehicles /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/reservations" element={<ProtectedRoute><PermissionGate permission="VIEW_RESERVATIONS"><FeatureGate feature="RESERVATION_MANAGEMENT"><Reservations /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/clients" element={<ProtectedRoute><PermissionGate permission="VIEW_CLIENTS"><FeatureGate feature="CLIENT_MANAGEMENT"><Clients /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/payments" element={<ProtectedRoute><PermissionGate permission="VIEW_PAYMENTS"><Payments /></PermissionGate></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
      <Route path="/contracts" element={<ProtectedRoute><PermissionGate permission="VIEW_CONTRACTS"><FeatureGate feature="CONTRACT_MANAGEMENT"><Contracts /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/contracts/:id" element={<ProtectedRoute><PermissionGate permission="VIEW_CONTRACTS"><FeatureGate feature="CONTRACT_MANAGEMENT"><ContractDetails /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/client-information-requests" element={<ProtectedRoute><PermissionGate permission="VIEW_CONTRACTS"><FeatureGate feature="CONTRACT_MANAGEMENT"><ClientInformationRequests /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/invoices" element={<ProtectedRoute><PermissionGate permission="VIEW_INVOICES"><FeatureGate feature="INVOICE_GENERATION"><Invoices /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/agency" element={<ProtectedRoute><Agency /></ProtectedRoute>} />
      <Route path="/employees" element={<ProtectedRoute><PermissionGate permission="MANAGE_EMPLOYEES"><FeatureGate feature="MULTI_EMPLOYEE"><Employees /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute><PermissionGate permission="VIEW_REPORTS"><FeatureGate feature="REPORTS_BASIC"><Reports /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/gps-settings" element={<ProtectedRoute><PermissionGate permission="GPS_ACCESS"><FeatureGate feature="GPS_TRACKING"><GpsSettingsPage /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/gps-tracking" element={<ProtectedRoute><PermissionGate permission="GPS_ACCESS"><FeatureGate feature="GPS_TRACKING"><GpsDashboard /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/gps-alerts" element={<ProtectedRoute><PermissionGate permission="GPS_ACCESS"><FeatureGate feature="GPS_TRACKING"><GpsAlerts /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/subscription" element={<Navigate to="/settings?tab=billing" replace />} />
      {/* Full-bleed like the account-lock screen — a premium checkout page
          shouldn't be wrapped in the regular dashboard sidebar/topbar. */}
      <Route path="/checkout" element={<AuthOnlyRoute><CheckoutTrial /></AuthOnlyRoute>} />
      <Route path="/white-label" element={<ProtectedRoute><FeatureGate feature="WHITE_LABEL"><WhiteLabel /></FeatureGate></ProtectedRoute>} />
      <Route path="/automation-center" element={<ProtectedRoute><FeatureGate feature="AUTOMATION_CENTER"><AutomationCenter /></FeatureGate></ProtectedRoute>} />
      <Route path="/maintenance" element={<ProtectedRoute><PermissionGate permission="VIEW_MAINTENANCE"><FeatureGate feature="VEHICLE_MANAGEMENT"><Maintenance /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/role-permissions" element={<ProtectedRoute><PermissionGate permission="MANAGE_EMPLOYEES"><RolePermissions /></PermissionGate></ProtectedRoute>} />
      <Route path="/operations-center" element={<ProtectedRoute><OperationsCenter /></ProtectedRoute>} />
      <Route path="/help" element={<ProtectedRoute><HelpCenter /></ProtectedRoute>} />
      <Route path="/support" element={<ProtectedRoute><HelpCenter /></ProtectedRoute>} />
      <Route path="/tickets" element={<ProtectedRoute><HelpCenter /></ProtectedRoute>} />
      <Route path="/tickets/:id" element={<ProtectedRoute><TicketDetail /></ProtectedRoute>} />

      {/* Fallback */}
      <Route path="*" element={<Navigate to={isSuperAdmin ? '/super-admin' : '/'} replace />} />
    </Routes>
    </React.Suspense>
    </ErrorBoundary>
  );
}

// Backend reachability gate
// This is the ONLY thing allowed to block the whole app behind a full-screen
// message, and only after several consecutive failures of the dedicated core
// health endpoint (/api/health) -- a single transient blip (e.g.
// ERR_NETWORK_CHANGED during a wifi/cell handoff) must never blank the app.
// Any other endpoint failing (notifications, SSE, charts, email, dashboard
// widgets) is a per-component concern (empty state, inline error), never a
// global one -- see api/axios.ts, which deliberately never touches this gate.
//
// Failure policy: 1st failure is silent (still retries after a short delay);
// 2nd shows a small non-blocking "Reconnecting..." banner; only the 3rd+
// consecutive failure shows the full overlay. Retries back off exponentially
// (capped) so a genuinely-down backend isn't hammered forever. The app tree
// stays mounted underneath the overlay the whole time, so any dashboard/
// vehicle data already loaded is never discarded -- it's simply hidden behind
// the overlay until the connection recovers, then revealed as-is.

const HEALTH_RETRY_BASE_DELAY_MS = 2000;
const HEALTH_RETRY_MAX_DELAY_MS = 30000;
const HEALTH_STEADY_POLL_MS = 10000;
const HEALTH_DOWN_THRESHOLD = 3;

type HealthStatus = 'ok' | 'reconnecting' | 'down';

function MaintenanceScreen({ onRetryNow }: { onRetryNow: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[10000] flex min-h-screen items-center justify-center bg-[#f7f7f4]/95 px-4 backdrop-blur-sm dark:bg-[#101418]/95">
      <div className="w-full max-w-md rounded-2xl border border-rose-100 bg-white p-8 text-center shadow-soft dark:border-white/10 dark:bg-[#1a2332]">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600 dark:bg-rose-500/10">
          <ServerOff size={22} />
        </div>
        <h1 className="text-xl font-bold text-[#1e293b] dark:text-white">{t('app.serviceUnavailable', 'Service temporarily unavailable')}</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-300">
          {t('app.serviceUnavailableDesc', "We can't reach the RentCar server right now. We'll keep retrying automatically.")}
        </p>
        <button
          type="button"
          onClick={onRetryNow}
          className="mt-6 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700"
        >
          <RefreshCw size={15} /> {t('app.retryNow', 'Retry now')}
        </button>
      </div>
    </div>
  );
}

function ReconnectingBanner() {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-x-0 top-0 z-[10000] flex items-center justify-center gap-2 bg-amber-500 py-1.5 text-xs font-semibold text-white shadow-md">
      <RefreshCw size={13} className="animate-spin" /> {t('app.reconnecting', 'Reconnecting…')}
    </div>
  );
}

function OfflineBanner() {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-x-0 top-0 z-[10000] flex items-center justify-center gap-2 bg-slate-700 py-1.5 text-xs font-semibold text-white shadow-md">
      <WifiOff size={13} /> {t('app.offline', "You're offline")}
    </div>
  );
}

export function BackendHealthGate({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<HealthStatus>('ok');
  const [browserOffline, setBrowserOffline] = useState(
    typeof navigator !== 'undefined' ? !navigator.onLine : false
  );
  const consecutiveFailuresRef = useRef(0);
  const timeoutRef = useRef<number | null>(null);
  const checkInFlightRef = useRef(false);

  const scheduleNext = (delay: number) => {
    if (timeoutRef.current) window.clearTimeout(timeoutRef.current);
    timeoutRef.current = window.setTimeout(() => { void runCheck(); }, delay);
  };

  const runCheck = async () => {
    // Guards against overlapping checks: the "Retry now" button and the
    // scheduled timer could otherwise both be in flight at once.
    if (checkInFlightRef.current) return;
    checkInFlightRef.current = true;
    const up = await checkHealth();
    checkInFlightRef.current = false;

    if (up) {
      consecutiveFailuresRef.current = 0;
      setStatus('ok');
      scheduleNext(HEALTH_STEADY_POLL_MS);
      return;
    }

    consecutiveFailuresRef.current += 1;
    const failures = consecutiveFailuresRef.current;
    setStatus(failures >= HEALTH_DOWN_THRESHOLD ? 'down' : failures >= 2 ? 'reconnecting' : 'ok');
    const delay = Math.min(HEALTH_RETRY_BASE_DELAY_MS * 2 ** (failures - 1), HEALTH_RETRY_MAX_DELAY_MS);
    scheduleNext(delay);
  };

  const retryNow = () => {
    if (timeoutRef.current) window.clearTimeout(timeoutRef.current);
    void runCheck();
  };

  useEffect(() => {
    void runCheck();
    return () => {
      if (timeoutRef.current) window.clearTimeout(timeoutRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // A network transition (e.g. ERR_NETWORK_CHANGED, wifi/cell handoff) or the
  // browser regaining connectivity should trigger an immediate recheck rather
  // than waiting out whatever backoff delay is currently scheduled.
  useEffect(() => {
    const handleOnline = () => { setBrowserOffline(false); retryNow(); };
    const handleOffline = () => setBrowserOffline(true);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      {browserOffline ? <OfflineBanner /> : status === 'reconnecting' ? <ReconnectingBanner /> : null}
      {status === 'down' && !browserOffline && <MaintenanceScreen onRetryNow={retryNow} />}
      {children}
    </>
  );
}

// ── App bootstrap gate ───────────────────────────────────────────────────────
// The splash screen used to hide behind a flat setTimeout(1400ms) regardless
// of whether the session check had actually finished — a fixed delay that
// both outlived a fast bootstrap (needless wait) and could resolve before a
// slow one (AuthContext's own 6s watchdog caps the worst case), briefly
// exposing whatever the route guards render next. This gates on the real
// signal instead: AuthContext.loading, which is true for exactly as long as
// session restoration (`GET /me`) is actually in flight, and never again
// after that for the rest of the tab session. While it's true, AppRoutes is
// not mounted at all — not hidden behind an overlay — so there is nothing
// for a route guard or lazy chunk to race and expose for one frame.
function AppShell() {
  const { loading: authLoading } = useAuth();

  return (
    <>
      {authLoading && <SplashScreen />}
      {/* ThemeProvider wraps ALL routes (public + protected) — public pages
          (Login, Register, landing) need the resolved theme/CSS vars too,
          and it only talks to the backend when isAuthenticated, so it's
          safe/inert on public routes. Always mounted (even during the splash) so the resolved
          theme/CSS vars are ready the instant route content does mount —
          see ThemeContext's synchronous initial state + useLayoutEffect. */}
      <ThemeProvider>
        {!authLoading && (
          <BackendHealthGate>
            <AppRoutes />
          </BackendHealthGate>
        )}
        <CookieConsentBanner />
        <SessionExpiredModal />
      </ThemeProvider>
    </>
  );
}

function App() {
  // Re-arms the one-shot auto-reload guards so a later, unrelated stale-module/
  // chunk error still gets one reload attempt instead of being permanently
  // disabled for the rest of the tab session. Deliberately delayed (not cleared
  // immediately on mount): a genuine, deterministic render crash (e.g. a
  // component throwing "X must be used within <Y>Provider" on every render,
  // not just a one-off stale-HMR artifact) reloads once, then throws again on
  // the very next mount — if this effect cleared the guard immediately, that
  // second throw would see the guard already gone and trigger *another*
  // reload, forever, which is exactly the infinite-splash/infinite-reload bug
  // reported in production. Waiting a few seconds gives the just-reloaded page
  // a chance to actually stay up before we consider the reload "successful"
  // and re-arm the guard for the future.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      sessionStorage.removeItem('rentcar_error_boundary_reload_once');
      sessionStorage.removeItem(CHUNK_RELOAD_MARKER);
    }, 4000);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <ErrorBoundary>
      <NotificationSoundProvider>
        <NotificationProvider>
          <ToastProvider>
            <AppShell />
          </ToastProvider>
        </NotificationProvider>
      </NotificationSoundProvider>
    </ErrorBoundary>
  );
}

export default App;
