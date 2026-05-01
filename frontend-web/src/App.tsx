import React from 'react';
import { Routes, Route, Link, useLocation, Navigate } from 'react-router-dom';
import { LayoutDashboard, Car, CalendarRange, LogOut } from 'lucide-react';
import { AuthProvider, useAuth } from './context/AuthContext';
import Dashboard from './pages/Dashboard';
import Vehicles from './pages/Vehicles';
import Reservations from './pages/Reservations';
import Login from './pages/Login';

const NavItem = ({ to, icon: Icon, label }: { to: string, icon: any, label: string }) => {
  const location = useLocation();
  const isActive = location.pathname === to;
  
  return (
    <Link to={to} className={`nav-item ${isActive ? 'active' : ''}`}>
      <div className="nav-icon-container">
        <Icon size={24} />
      </div>
      <span>{label}</span>
    </Link>
  );
};

const ProtectedLayout = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, loading, logout } = useAuth();
  
  if (loading) return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;

  return (
    <>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1 className="header-title">LocCar <span>Mobile</span></h1>
        <button onClick={logout} style={{ background: 'none', border: 'none', color: 'var(--danger)', padding: '0.5rem' }}>
          <LogOut size={20} />
        </button>
      </header>

      <main className="content-section">
        {children}
      </main>

      <nav className="bottom-nav">
        <NavItem to="/" icon={LayoutDashboard} label="Home" />
        <NavItem to="/vehicles" icon={Car} label="Fleet" />
        <NavItem to="/reservations" icon={CalendarRange} label="Bookings" />
      </nav>
    </>
  );
};

function App() {
  return (
    <AuthProvider>
      <div className="app-container">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/*" element={
            <ProtectedLayout>
              <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/vehicles" element={<Vehicles />} />
                <Route path="/reservations" element={<Reservations />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </ProtectedLayout>
          } />
        </Routes>
      </div>
    </AuthProvider>
  );
}

export default App;
