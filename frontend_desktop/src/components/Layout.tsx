import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutDashboard, 
  Car, 
  CalendarRange, 
  LogOut,
  CarFront
} from 'lucide-react';
import { cn } from '../lib/utils';

const SidebarItem = ({ to, icon: Icon, label, active }: { to: string, icon: any, label: string, active: boolean }) => (
  <Link
    to={to}
    className={cn(
      "flex items-center gap-3 px-4 py-3 rounded-lg transition-colors",
      active 
        ? "bg-blue-600 text-white shadow-md" 
        : "text-gray-600 hover:bg-gray-100"
    )}
  >
    <Icon size={20} />
    <span className="font-medium">{label}</span>
  </Link>
);

export default function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const location = useLocation();

  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
        <div className="p-6 flex items-center gap-3 text-blue-600">
          <CarFront size={32} strokeWidth={2.5} />
          <h1 className="text-xl font-bold tracking-tight text-gray-900">LocCar <span className="text-blue-600">Pro</span></h1>
        </div>

        <nav className="flex-1 px-4 py-4 space-y-2">
          <SidebarItem 
            to="/" 
            icon={LayoutDashboard} 
            label="Dashboard" 
            active={location.pathname === '/'} 
          />
          <SidebarItem 
            to="/vehicles" 
            icon={Car} 
            label="Vehicles" 
            active={location.pathname === '/vehicles'} 
          />
          <SidebarItem 
            to="/reservations" 
            icon={CalendarRange} 
            label="Reservations" 
            active={location.pathname === '/reservations'} 
          />
        </nav>

        <div className="p-4 border-t border-gray-200">
          <div className="flex items-center gap-3 px-2 py-3 mb-4">
            <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-bold">
              {user?.email[0].toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 truncate">{user?.tenantName}</p>
              <p className="text-xs text-gray-500 truncate">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={logout}
            className="flex items-center gap-3 w-full px-4 py-2 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors font-medium"
          >
            <LogOut size={18} />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 overflow-auto relative">
        <div className="p-8 max-w-7xl mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
}
