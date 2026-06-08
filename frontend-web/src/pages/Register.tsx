import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { UserPlus, Mail, Lock, Car, Loader2, ArrowLeft, Building2, Eye, EyeOff } from 'lucide-react';

export default function Register() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const { register } = useAuth();
  const { showToast } = useToast();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    if (password !== confirmPassword) {
      setError(t('register.passwordMismatch', 'Passwords do not match'));
      setLoading(false);
      return;
    }

    if (password.length < 8) {
      setError(t('register.passwordTooShort', 'Password must be at least 8 characters'));
      setLoading(false);
      return;
    }

    try {
      const tenantIdNum = tenantId ? parseInt(tenantId, 10) : undefined;
      await register({
        email,
        password,
        firstName,
        lastName,
        tenantId: tenantIdNum,
      });
      showToast(t('register.success', 'Account created successfully!'), 'success');
      navigate('/');
    } catch (err: any) {
      const msg = err?.response?.data?.message || t('register.failed', 'Registration failed. Please try again.');
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3"></div>
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3"></div>

      <div className="w-full max-w-[440px] animate-fade relative z-10">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-10">
          <div className="w-10 h-10 rounded-lg bg-brand-500 flex items-center justify-center text-white shadow-lg">
            <Car size={22} strokeWidth={2.5} />
          </div>
          <span className="text-2xl font-bold tracking-tight text-[#1e293b]">Rent<span className="text-brand-500">Car</span></span>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60">
          <div className="text-center mb-8">
            <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">{t('register.title', 'Create Account')}</h1>
            <p className="text-slate-500 font-normal text-xs sm:text-sm">{t('register.subtitle', 'Join your team on RentCar SaaS')}</p>
          </div>

          {error && (
            <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border border-danger-100">
              <div className="w-2 h-2 bg-danger-500 rounded-full"></div>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('register.firstName', 'First Name')}</label>
                <input
                  type="text"
                  value={firstName}
                  onChange={e => setFirstName(e.target.value)}
                  className="block w-full px-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="John"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('register.lastName', 'Last Name')}</label>
                <input
                  type="text"
                  value={lastName}
                  onChange={e => setLastName(e.target.value)}
                  className="block w-full px-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="Doe"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('login.email', 'Email Address')}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Mail size={18} />
                </div>
                <input
                  type="email"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  className="block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder={t('login.emailPlaceholder', 'name@company.com')}
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('register.tenantId', 'Tenant ID')}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Building2 size={18} />
                </div>
                <input
                  type="number"
                  value={tenantId}
                  onChange={e => setTenantId(e.target.value)}
                  className="block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="1"
                />
              </div>
              <p className="text-xs text-slate-400 mt-1 ml-1">{t('register.tenantHint', 'Ask your admin for the tenant ID')}</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('login.password', 'Password')}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Lock size={18} />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="block w-full pl-11 pr-11 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="••••••••"
                  required
                  minLength={8}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('register.confirmPassword', 'Confirm Password')}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Lock size={18} />
                </div>
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  className="block w-full pl-11 pr-11 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="••••••••"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(v => !v)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors"
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70 mt-2"
            >
              {loading ? (
                <Loader2 size={20} className="animate-spin" />
              ) : (
                <>
                  <span>{t('register.createAccount', 'Create Account')}</span>
                  <UserPlus size={16} strokeWidth={2.5} />
                </>
              )}
            </button>
          </form>

          <div className="mt-6 pt-6 border-t border-[#e8e6e1] text-center">
            <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
              <ArrowLeft size={16} />
              {t('register.backToLogin', 'Already have an account? Sign in')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
