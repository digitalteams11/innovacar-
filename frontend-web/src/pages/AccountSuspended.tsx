import { useNavigate } from 'react-router-dom';
import { ShieldAlert, CreditCard, LifeBuoy, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function AccountSuspended() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const status = user?.agencyStatus || user?.subscriptionStatus || 'SUSPENDED';
  const isBlocked = status === 'BLOCKED';

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="max-w-md w-full bg-white rounded-2xl border border-[#e8e6e1] shadow-soft p-8 text-center">
        <div className="w-16 h-16 rounded-2xl bg-rose-50 flex items-center justify-center mx-auto mb-5">
          <ShieldAlert size={28} className="text-rose-500" />
        </div>
        <h1 className="text-xl font-bold text-[#1e293b] mb-2">Account suspended</h1>
        <p className="text-sm text-slate-500 mb-6">
          Your agency account is currently {isBlocked ? 'blocked' : 'suspended'} by Innovax Technologies.
        </p>

        <div className="bg-slate-50 rounded-xl p-4 mb-6 text-left space-y-1.5">
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Plan</span>
            <span className="font-semibold text-[#1e293b]">{user?.planName || '-'}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Status</span>
            <span className="font-semibold text-rose-600">{status}</span>
          </div>
        </div>

        <div className="space-y-2">
          <button
            onClick={() => navigate('/subscription')}
            className="w-full flex items-center justify-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <CreditCard size={16} />
            Open Billing
          </button>
          <button
            onClick={() => window.dispatchEvent(new CustomEvent('open-help-center'))}
            className="w-full flex items-center justify-center gap-2 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <LifeBuoy size={16} />
            Contact Support
          </button>
          <button
            onClick={logout}
            className="w-full flex items-center justify-center gap-2 text-rose-500 hover:bg-rose-50 py-2.5 rounded-xl text-sm font-semibold transition-colors"
          >
            <LogOut size={16} />
            Logout
          </button>
        </div>
      </div>
    </div>
  );
}
