import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';

import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { NotificationProvider } from './context/NotificationContext';
import { FeatureAccessProvider } from './context/FeatureAccessContext';
import FeatureGate from './components/FeatureGate';
import PermissionGate from './components/PermissionGate';
import { PermissionProvider } from './context/PermissionContext';
import Layout from './components/Layout';
import SuperAdminLayout from './components/SuperAdminLayout';

// Regular pages
import Dashboard from './pages/Dashboard';
import Vehicles from './pages/Vehicles';
import Reservations from './pages/Reservations';
import Clients from './pages/Clients';
import Payments from './pages/Payments';
import Login from './pages/Login';
import Settings from './pages/Settings';
import Contracts from './pages/Contracts';
import ContractDetails from './pages/ContractDetails';
import PublicContract from './pages/PublicContract';
import Invoices from './pages/Invoices';
import Agency from './pages/Agency';
import Employees from './pages/Employees';
import Reports from './pages/Reports';
import GpsSettingsPage from './pages/GpsSettings';
import GpsDashboard from './pages/GpsDashboard';
import Subscription from './pages/Subscription';
import WhiteLabel from './pages/WhiteLabel';
import Maintenance from './pages/Maintenance';
import RolePermissions from './pages/RolePermissions';

// Auth pages
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import VerifyEmail from './pages/VerifyEmail';

// Super Admin pages
import SuperAdminDashboard from './pages/superadmin/SuperAdminDashboard';
import SuperAdminAgencies from './pages/superadmin/SuperAdminAgencies';
import SuperAdminAgencyDetail from './pages/superadmin/SuperAdminAgencyDetail';
import SuperAdminSubscriptions from './pages/superadmin/SuperAdminSubscriptions';
import SuperAdminGps from './pages/superadmin/SuperAdminGps';
import SuperAdminUsers from './pages/superadmin/SuperAdminUsers';
import SuperAdminPayments from './pages/superadmin/SuperAdminPayments';
import SuperAdminSupport from './pages/superadmin/SuperAdminSupport';
import SuperAdminTicketDetail from './pages/superadmin/SuperAdminTicketDetail';
import SuperAdminNotifications from './pages/superadmin/SuperAdminNotifications';
import SuperAdminAnalytics from './pages/superadmin/SuperAdminAnalytics';
import SuperAdminSettings from './pages/superadmin/SuperAdminSettings';
import SuperAdminSecurity from './pages/superadmin/SuperAdminSecurity';
import SuperAdminEmailCenter from './pages/superadmin/SuperAdminEmailCenter';
import SuperAdminMarketing from './pages/superadmin/SuperAdminMarketing';
import SuperAdminContracts from './pages/superadmin/SuperAdminContracts';
import SuperAdminReports from './pages/superadmin/SuperAdminReports';
import SuperAdminFeatures from './pages/superadmin/SuperAdminFeatures';

// ── Route Guards ───────────────────────────────────────────────────────────

const PublicRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin"></div>
    </div>
  );
  if (isAuthenticated) return <Navigate to="/" replace />;
  return <>{children}</>;
};

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, isSuperAdmin, loading } = useAuth();

  if (loading) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin"></div>
    </div>
  );

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (isSuperAdmin) return <Navigate to="/super-admin" replace />;

  return <Layout>{children}</Layout>;
};

const SuperAdminRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, isSuperAdmin, loading } = useAuth();

  if (loading) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin"></div>
    </div>
  );

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isSuperAdmin) return <Navigate to="/" replace />;

  return <SuperAdminLayout>{children}</SuperAdminLayout>;
};

// ── App Routes ─────────────────────────────────────────────────────────────

function AppRoutes() {
  const { isSuperAdmin } = useAuth();
  return (
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

      {/* Super Admin Routes */}
      <Route path="/super-admin" element={<SuperAdminRoute><SuperAdminDashboard /></SuperAdminRoute>} />
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

      {/* Regular Admin Routes */}
      <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
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
      <Route path="/subscription" element={<ProtectedRoute><Subscription /></ProtectedRoute>} />
      <Route path="/white-label" element={<ProtectedRoute><FeatureGate feature="WHITE_LABEL"><WhiteLabel /></FeatureGate></ProtectedRoute>} />
      <Route path="/maintenance" element={<ProtectedRoute><PermissionGate permission="VIEW_MAINTENANCE"><FeatureGate feature="VEHICLE_MANAGEMENT"><Maintenance /></FeatureGate></PermissionGate></ProtectedRoute>} />
      <Route path="/role-permissions" element={<ProtectedRoute><PermissionGate permission="MANAGE_EMPLOYEES"><RolePermissions /></PermissionGate></ProtectedRoute>} />

      {/* Fallback */}
      <Route path="*" element={<Navigate to={isSuperAdmin ? '/super-admin' : '/'} replace />} />
    </Routes>
  );
}

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <NotificationProvider>
          <FeatureAccessProvider>
            <PermissionProvider>
              <AppRoutes />
            </PermissionProvider>
          </FeatureAccessProvider>
        </NotificationProvider>
      </ToastProvider>
    </AuthProvider>
  );
}

export default App;
