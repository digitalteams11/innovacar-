import React, { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';

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
const AccountSuspended = React.lazy(() => import('./pages/AccountSuspended'));
const Layout = React.lazy(() => import('./components/Layout'));
const SuperAdminLayout = React.lazy(() => import('./components/SuperAdminLayout'));
const Dashboard = React.lazy(() => import('./pages/Dashboard'));
const Vehicles = React.lazy(() => import('./pages/Vehicles'));
const Reservations = React.lazy(() => import('./pages/Reservations'));
const Clients = React.lazy(() => import('./pages/Clients'));
const Payments = React.lazy(() => import('./pages/Payments'));
const Login = React.lazy(() => import('./pages/Login'));
const Settings = React.lazy(() => import('./pages/Settings'));
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
const WhiteLabel = React.lazy(() => import('./pages/WhiteLabel'));
const Maintenance = React.lazy(() => import('./pages/Maintenance'));
const RolePermissions = React.lazy(() => import('./pages/RolePermissions'));
const OperationsCenter = React.lazy(() => import('./pages/OperationsCenter'));
const Branches = React.lazy(() => import('./pages/Branches'));
const Register = React.lazy(() => import('./pages/Register'));
const ForgotPassword = React.lazy(() => import('./pages/ForgotPassword'));
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
const SuperAdminAnnouncements = React.lazy(() => import('./pages/superadmin/SuperAdminAnnouncements'));
const SuperAdminStaff = React.lazy(() => import('./pages/superadmin/SuperAdminStaff'));
const SuperAdminRoles = React.lazy(() => import('./pages/superadmin/SuperAdminRoles'));

// ── Route Guards ───────────────────────────────────────────────────────────

const AuthenticatedAppProviders = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>
    <NotificationProvider>
      <FeatureAccessProvider>
        <PermissionProvider>
          <OnboardingProvider>
            {children}
          </OnboardingProvider>
        </PermissionProvider>
      </FeatureAccessProvider>
    </NotificationProvider>
  </ThemeProvider>
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

// Authenticated-only, no Layout/sidebar — used for the full-screen lock state itself.
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

// ── App Routes ─────────────────────────────────────────────────────────────

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
      <Route path="/reset-password" element={<PublicRoute><ResetPassword /></PublicRoute>} />
      <Route path="/verify-email" element={<PublicRoute><VerifyEmail /></PublicRoute>} />

      <Route path="/admin" element={<Navigate to="/" replace />} />
      <Route path="/superadmin" element={<Navigate to="/super-admin" replace />} />
      {/* Public Contract Signing — isolated from all auth guards */}
      <Route path="/contract-sign/:contractId/:token" element={<PublicContract />} />
      <Route path="/contract-sign/:token" element={<PublicContract />} />
      <Route path="/inspection/:token" element={<InspectionCapture />} />

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
      <Route path="/super-admin/announcements" element={<SuperAdminRoute><SuperAdminAnnouncements /></SuperAdminRoute>} />
      <Route path="/super-admin/staff" element={<SuperAdminRoute><SuperAdminStaff /></SuperAdminRoute>} />
      <Route path="/super-admin/roles" element={<SuperAdminRoute><SuperAdminRoles /></SuperAdminRoute>} />

      {/* Account lock screen — intentionally outside ProtectedRoute's Layout wrap (no sidebar) */}
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
      <Route path="/subscription" element={<Navigate to="/settings?tab=billing" replace />} />
      <Route path="/white-label" element={<ProtectedRoute><FeatureGate feature="WHITE_LABEL"><WhiteLabel /></FeatureGate></ProtectedRoute>} />
      <Route path="/maintenance" element={<ProtectedRoute><PermissionGate permission="VIEW_MAINTENANCE"><FeatureGate feature="VEHICLE_MANAGEMENT"><Maintenance /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/role-permissions" element={<ProtectedRoute><PermissionGate permission="MANAGE_EMPLOYEES"><RolePermissions /></PermissionGate></ProtectedRoute>} />
      <Route path="/operations-center" element={<ProtectedRoute><OperationsCenter /></ProtectedRoute>} />
      <Route path="/branches" element={<ProtectedRoute><FeatureGate feature="MULTI_BRANCH"><Branches /></FeatureGate></ProtectedRoute>} />

      {/* Fallback */}
      <Route path="*" element={<Navigate to={isSuperAdmin ? '/super-admin' : '/'} replace />} />
    </Routes>
    </React.Suspense>
    </ErrorBoundary>
  );
}

function App() {
  const [showSplash, setShowSplash] = useState(true);
  useEffect(() => {
    const timer = window.setTimeout(() => setShowSplash(false), 1400);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <ErrorBoundary>
      <NotificationSoundProvider>
        <ToastProvider>
          {showSplash && <SplashScreen />}
          <AppRoutes />
        </ToastProvider>
      </NotificationSoundProvider>
    </ErrorBoundary>
  );
}

export default App;
