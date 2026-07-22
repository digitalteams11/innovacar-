import React, { useEffect, useRef, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { RefreshCw, ServerOff } from 'lucide-react';

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

// â”€â”€ Backend reachability gate â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// This is the ONLY thing allowed to block the whole app behind a full-screen
// message. It only fires when /api/health itself can't be reached at all
// (server down / connection refused) â€” any other endpoint failing is a
// per-component concern (empty state, inline error), never a global one.

function MaintenanceScreen({ onRetryNow }: { onRetryNow: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f7f7f4] px-4 dark:bg-[#101418]">
      <div className="w-full max-w-md rounded-2xl border border-rose-100 bg-white p-8 text-center shadow-soft dark:border-white/10 dark:bg-[#1a2332]">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-600 dark:bg-rose-500/10">
          <ServerOff size={22} />
        </div>
        <h1 className="text-xl font-bold text-[#1e293b] dark:text-white">{t('app.serviceUnavailable', 'Service temporarily unavailable')}</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-300">
          {t('app.serviceUnavailableDesc', "We can't reach the RentCar server right now. We'll keep retrying automatically every 10 seconds.")}
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

function BackendHealthGate({ children }: { children: React.ReactNode }) {
  const [backendUp, setBackendUp] = useState(true);
  const [checked, setChecked] = useState(false);
  const intervalRef = useRef<number | null>(null);

  const runCheck = async () => {
    const up = await checkHealth();
    setBackendUp(up);
    setChecked(true);
  };

  useEffect(() => {
    runCheck();
    intervalRef.current = window.setInterval(runCheck, 10000);
    return () => {
      if (intervalRef.current) window.clearInterval(intervalRef.current);
    };
  }, []);

  // Avoid flashing the maintenance screen during the very first check.
  if (!checked) return <>{children}</>;
  if (!backendUp) return <MaintenanceScreen onRetryNow={runCheck} />;
  return <>{children}</>;
}

function App() {
  const [showSplash, setShowSplash] = useState(true);
  useEffect(() => {
    // Hard cap on the splash screen itself — independent of auth/branding/theme
    // startup, none of which this timer waits on. See AuthContext's own bootstrap
    // watchdog for why "loading" can never hang the login page behind this either.
    const timer = window.setTimeout(() => setShowSplash(false), 1400);
    return () => window.clearTimeout(timer);
  }, []);

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
            {showSplash && <SplashScreen />}
            {/* ThemeProvider wraps ALL routes (public + protected) — Login's
                Google-button theming (useTheme()) needs it too, and it only
                talks to the backend when isAuthenticated, so it's safe/inert
                on public routes. */}
            <ThemeProvider>
              <BackendHealthGate>
                <AppRoutes />
              </BackendHealthGate>
              <CookieConsentBanner />
            </ThemeProvider>
          </ToastProvider>
        </NotificationProvider>
      </NotificationSoundProvider>
    </ErrorBoundary>
  );
}

export default App;
