import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import Layout from './components/Layout';
import FeatureGate from './components/FeatureGate';
import { FeatureAccessProvider } from './context/FeatureAccessContext';
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

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, loading } = useAuth();
  
  if (loading) return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="w-10 h-10 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin"></div>
    </div>
  );
  
  if (!isAuthenticated) return <Navigate to="/login" replace />;

  return <Layout>{children}</Layout>;
};

function App() {
  const { t } = useTranslation();
  return (
    <AuthProvider>
      <ToastProvider>
        <FeatureAccessProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          
          <Route path="/" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          
          <Route path="/vehicles" element={
            <ProtectedRoute>
              <FeatureGate feature="VEHICLE_MANAGEMENT"><Vehicles /></FeatureGate>
            </ProtectedRoute>
          } />
          
          <Route path="/reservations" element={
            <ProtectedRoute>
              <FeatureGate feature="RESERVATION_MANAGEMENT"><Reservations /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/clients" element={
            <ProtectedRoute>
              <FeatureGate feature="CLIENT_MANAGEMENT"><Clients /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/payments" element={
            <ProtectedRoute>
              <Payments />
            </ProtectedRoute>
          } />

          <Route path="/settings" element={
            <ProtectedRoute>
              <Settings />
            </ProtectedRoute>
          } />

          <Route path="/contracts" element={
            <ProtectedRoute>
              <FeatureGate feature="CONTRACT_MANAGEMENT"><Contracts /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/contracts/:id" element={
            <ProtectedRoute>
              <FeatureGate feature="CONTRACT_MANAGEMENT"><ContractDetails /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/contract-sign/:contractId/:token" element={<PublicContract />} />
          <Route path="/contract-sign/:token" element={<PublicContract />} />

          <Route path="/invoices" element={
            <ProtectedRoute>
              <FeatureGate feature="INVOICE_GENERATION"><Invoices /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/agency" element={
            <ProtectedRoute>
              <Agency />
            </ProtectedRoute>
          } />

          <Route path="/employees" element={
            <ProtectedRoute>
              <FeatureGate feature="MULTI_EMPLOYEE"><Employees /></FeatureGate>
            </ProtectedRoute>
          } />

          <Route path="/reports" element={
            <ProtectedRoute>
              <FeatureGate feature="REPORTS_BASIC"><Reports /></FeatureGate>
            </ProtectedRoute>
          } />

          {/* Fallback for other routes */}
          <Route path="*" element={
            <ProtectedRoute>
              <div className="p-8 text-center bg-white rounded-2xl border border-dashed border-slate-200 animate-fade">
                <h2 className="text-xl font-bold text-slate-900 mb-2">{t('app.underDevelopment')}</h2>
                <p className="text-slate-500">{t('app.underDevelopmentDesc')}</p>
              </div>
            </ProtectedRoute>
          } />
        </Routes>
        </FeatureAccessProvider>
      </ToastProvider>
    </AuthProvider>
  );
}



export default App;
