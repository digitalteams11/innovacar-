import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogIn, Lock, Mail, Car, Loader2, ShieldCheck } from 'lucide-react';

export default function Login() {
  const [email, setEmail] = useState('admin@test.com');
  const [password, setPassword] = useState('admin123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const { login } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login({ email, password });
      navigate('/');
    } catch (err: any) {
      setError(t('login.invalidCredentials'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      {/* Background decoration */}
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3"></div>
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3"></div>
      
      <div className="w-full max-w-[400px] animate-fade relative z-10">
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
            <h1 className="text-xl font-bold text-[#1e293b] mb-1.5">{t('login.welcomeBack')}</h1>
            <p className="text-slate-500 font-normal text-sm">{t('login.enterDetails')}</p>
          </div>

          {error && (
            <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border border-danger-100">
              <div className="w-2 h-2 bg-danger-500 rounded-full"></div>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('login.email')}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Mail size={18} />
                </div>
                <input 
                  type="email" 
                  value={email} 
                  onChange={e => setEmail(e.target.value)} 
                  className="block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder={t('login.emailPlaceholder')}
                  required
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-2 ml-1">
                <label className="block text-sm font-medium text-[#1e293b]">{t('login.password')}</label>
                <a href="#" className="text-sm font-medium text-brand-500 hover:text-brand-600">{t('login.forgot')}</a>
              </div>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                  <Lock size={18} />
                </div>
                <input 
                  type="password" 
                  value={password} 
                  onChange={e => setPassword(e.target.value)} 
                  className="block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <div className="flex items-center gap-3 pt-1">
              <input type="checkbox" id="remember" className="w-4 h-4 rounded border-[#e8e6e1] text-brand-500 focus:ring-brand-500" />
              <label htmlFor="remember" className="text-sm font-normal text-slate-500 cursor-pointer">{t('login.rememberMe')}</label>
            </div>

            <button 
              type="submit" 
              disabled={loading} 
              className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70"
            >
              {loading ? (
                <Loader2 size={20} className="animate-spin" />
              ) : (
                <>
                  <span>{t('login.signIn')}</span>
                  <LogIn size={16} strokeWidth={2.5} />
                </>
              )}
            </button>
          </form>
        </div>

        {/* Footer */}
        <div className="mt-8 text-center flex items-center justify-center gap-2 text-slate-400">
          <ShieldCheck size={15} />
          <p className="text-xs font-normal">{t('login.secureAccess')}</p>
        </div>
      </div>
    </div>
  );
}
