import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useToast } from '../context/ToastContext';
import { 
  LayoutDashboard, Car, Calendar, Users, CreditCard, 
  FileText, BarChart3, Settings, LogOut, Search, Bell, 
  Menu, X, Info, Mail
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import LanguageSwitcher from './LanguageSwitcher';
import ThemeToggle from './ThemeToggle';

const SidebarItem = ({ to, icon: Icon, label, active }: any) => (
  <Link 
    to={to} 
    className={`flex items-center gap-3 px-4 py-3 mx-3 rounded-xl transition-all duration-200 relative group ${
      active 
        ? 'text-white bg-white/8' 
        : 'text-slate-400 hover:text-white hover:bg-white/5'
    }`}
  >
    {active && (
      <div className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-6 bg-accent-400 rounded-r-full"></div>
    )}
    <Icon size={18} className={active ? 'text-accent-400' : 'text-slate-400 group-hover:text-white transition-colors'} strokeWidth={active ? 2.5 : 2} />
    <span className={`text-sm tracking-wide transition-all ${active ? 'font-medium' : 'font-normal'}`}>{label}</span>
  </Link>
);

export default function Layout({ children }: { children: React.ReactNode }) {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const location = useLocation();
  const { logout } = useAuth();
  const { showToast } = useToast();
  const { t } = useTranslation();

  const handleAction = (label: string) => {
    showToast(t('toast.success', { action: label }));
  };

  const menuItems = [
    { to: '/', icon: LayoutDashboard, label: t('nav.dashboard') },
    { to: '/reservations', icon: Calendar, label: t('nav.reservations') },
    { to: '/vehicles', icon: Car, label: t('nav.vehicles') },
    { to: '/clients', icon: Users, label: t('nav.clients') },
    { to: '/contracts', icon: FileText, label: t('nav.contracts') },
    { to: '/payments', icon: CreditCard, label: t('nav.payments') },
    { to: '/invoices', icon: FileText, label: t('nav.invoices') },
    { to: '/agency', icon: Info, label: t('nav.agency') },
    { to: '/employees', icon: Users, label: t('nav.employees') },
    { to: '/reports', icon: BarChart3, label: t('nav.reports') },
    { to: '/settings', icon: Settings, label: t('nav.settings') },
  ];

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex">
      {/* Sidebar Desktop */}
      <aside className="hidden lg:flex flex-col w-[260px] bg-[#0b1437] fixed inset-y-0 z-50 shadow-2xl">
        <div className="p-6 mb-2">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-accent-400 flex items-center justify-center shadow-lg">
              <Car size={20} className="text-[#0b1437]" strokeWidth={2.5} />
            </div>
            <div className="text-white">
              <h1 className="text-lg font-bold tracking-tight leading-tight">RentCar</h1>
              <p className="text-[9px] text-slate-400 font-medium uppercase tracking-widest">{t('layout.brandSubtitle')}</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto no-scrollbar py-2 space-y-0.5">
          {menuItems.map((item) => (
            <SidebarItem 
              key={item.to} 
              {...item} 
              active={location.pathname === item.to} 
            />
          ))}
        </nav>

        {/* Support Widget */}
        <div 
          onClick={() => handleAction(t('layout.needHelp'))}
          className="p-5 mb-5 mx-4 bg-[#0f1b3d] rounded-2xl relative overflow-hidden group cursor-pointer border border-white/5"
        >
          <div className="relative z-10 text-center">
            <div className="w-10 h-10 bg-accent-400/15 rounded-xl flex items-center justify-center mx-auto mb-3 group-hover:bg-accent-400/25 transition-colors">
              <Mail size={18} className="text-accent-400" />
            </div>
            <p className="text-white text-xs font-medium mb-1">{t('layout.needHelp')}</p>
            <p className="text-slate-500 text-[10px] font-normal mb-4 px-2 leading-relaxed">{t('layout.helpText')}</p>
            <button className="w-full bg-accent-400 hover:bg-accent-300 text-[#0b1437] py-2 rounded-xl text-[10px] font-bold uppercase tracking-wider transition-colors">
              {t('layout.contactUs')}
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 lg:ml-[260px] flex flex-col min-w-0">
        {/* Topbar */}
        <header className="h-[76px] px-6 flex items-center justify-between sticky top-0 z-40">
          <div className="absolute inset-0 bg-[#f5f5f0]/70 backdrop-blur-2xl border-b border-[#e8e6e1]/60"></div>
          <div className="relative flex items-center gap-4 w-full">
            <button 
              className="lg:hidden p-2.5 text-[#1e293b] bg-white rounded-xl shadow-soft border border-[#e8e6e1]/80" 
              onClick={() => setIsMobileMenuOpen(true)}
            >
              <Menu size={18} />
            </button>
            <div className="hidden md:flex items-center gap-3 bg-white px-4 py-2.5 rounded-xl shadow-soft border border-[#e8e6e1]/80 w-[360px] group focus-within:ring-2 ring-brand-100/50 transition-all">
              <Search size={16} className="text-slate-400 group-focus-within:text-brand-500 transition-colors" />
              <input 
                type="text" 
                placeholder={t('layout.searchPlaceholder')}
                className="bg-transparent border-none outline-none text-sm font-normal w-full text-[#1e293b] placeholder:text-slate-400"
              />
            </div>
          </div>

          <div className="relative flex items-center gap-3 bg-white p-1.5 px-3 rounded-xl shadow-soft border border-[#e8e6e1]/80">
            <ThemeToggle />
            <LanguageSwitcher />
            <button 
              onClick={() => handleAction(t('layout.notifications'))}
              className="relative text-slate-400 hover:text-brand-500 transition-all active:scale-90 p-2 hover:bg-slate-50 rounded-lg"
            >
              <Bell size={18} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-danger-500 rounded-full border-2 border-white"></span>
            </button>
            <button 
              onClick={() => handleAction(t('layout.messages'))}
              className="text-slate-400 hover:text-brand-500 transition-all active:scale-90 p-2 hover:bg-slate-50 rounded-lg"
            >
              <Mail size={18} />
            </button>
            <div className="h-5 w-px bg-[#e8e6e1] mx-1"></div>
            <div 
              onClick={() => handleAction(t('layout.profileMenu'))}
              className="flex items-center gap-2.5 cursor-pointer group hover:bg-slate-50/80 p-1.5 pr-2.5 rounded-xl transition-all"
            >
              <img 
                src="https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=100" 
                alt="Profile" 
                className="w-8 h-8 rounded-full object-cover border border-white shadow-sm"
              />
              <div className="text-right hidden sm:block">
                <p className="text-xs font-semibold text-[#1e293b] group-hover:text-brand-600 transition-colors">Yassine Admin</p>
                <p className="text-[9px] font-medium text-slate-400 uppercase tracking-widest">{t('layout.admin')}</p>
              </div>
            </div>
          </div>
        </header>

        {/* Content */}
        <main className="p-6 pt-2 flex-1">
          {children}
        </main>
      </div>

      {/* Mobile Sidebar Overlay */}
      {isMobileMenuOpen && (
        <>
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[60] lg:hidden animate-in fade-in duration-300" onClick={() => setIsMobileMenuOpen(false)}></div>
          <aside className="fixed inset-y-0 left-0 w-72 bg-[#0b1437] z-[70] lg:hidden flex flex-col shadow-2xl animate-in slide-in-from-left duration-300">
             <div className="p-6 flex justify-between items-center text-white border-b border-white/5">
                <div className="flex items-center gap-2">
                   <div className="w-9 h-9 rounded-lg bg-accent-400 flex items-center justify-center">
                     <Car size={18} className="text-[#0b1437]" />
                   </div>
                   <span className="text-lg font-bold tracking-tight">RentCar</span>
                </div>
                <button onClick={() => setIsMobileMenuOpen(false)} className="p-2 hover:bg-white/10 rounded-xl transition-colors"><X size={22} /></button>
             </div>
             <nav className="flex-1 py-4 space-y-0.5 overflow-y-auto no-scrollbar">
                {menuItems.map((item) => (
                  <SidebarItem key={item.to} {...item} active={location.pathname === item.to} />
                ))}
              </nav>
              <div className="p-6 mt-auto">
                 <button 
                  onClick={() => logout()}
                  className="w-full flex items-center justify-center gap-2 py-3 bg-white/5 text-white rounded-xl font-semibold text-sm hover:bg-danger-500/90 transition-all duration-300"
                 >
                    <LogOut size={18} />
                    {t('layout.logout')}
                 </button>
              </div>
          </aside>
        </>
      )}
    </div>
  );
}
