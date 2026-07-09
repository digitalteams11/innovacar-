import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Lock, Car, Loader2, ArrowLeft, CheckCircle, Eye, EyeOff } from 'lucide-react';

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const { t } = useTranslation();
  const navigate = useNavigate();

  useEffect(() => {
    if (!token) {
      setError(t('resetPassword.invalidToken', 'Invalid or missing reset token.'));
    }
  }, [token, t]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    if (newPassword !== confirmPassword) {
      setError(t('register.passwordMismatch', 'Passwords do not match'));
      setLoading(false);
      return;
    }

    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(newPassword)) {
      setError('Use at least 10 characters with uppercase, lowercase, a number, and a symbol.');
      setLoading(false);
      return;
    }

    try {
      await api.post('/auth/reset-password', { token, newPassword });
      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err: any) {
      const msg = err?.response?.data?.message || t('resetPassword.failed', 'Failed to reset password. Please try again.');
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
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
            <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">{t('resetPassword.title', 'New Password')}</h1>
            <p className="text-slate-500 font-normal text-xs sm:text-sm">{t('resetPassword.subtitle', 'Enter your new password below.')}</p>
          </div>

          {success ? (
            <div className="text-center py-6">
              <div className="w-14 h-14 bg-success-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <CheckCircle size={28} className="text-success-500" />
              </div>
              <h3 className="text-lg font-semibold text-[#1e293b] mb-2">{t('resetPassword.successTitle', 'Password updated!')}</h3>
              <p className="text-slate-500 text-sm mb-6">{t('resetPassword.successDesc', 'Redirecting you to sign in...')}</p>
              <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
                <ArrowLeft size={16} />
                {t('forgotPassword.backToLogin', 'Back to Sign In')}
              </Link>
            </div>
          ) : (
            <>
              {error && (
                <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border border-danger-100">
                  <div className="w-2 h-2 bg-danger-500 rounded-full"></div>
                  {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">{t('resetPassword.newPassword', 'New Password')}</label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Lock size={18} />
                    </div>
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={newPassword}
                      onChange={e => setNewPassword(e.target.value)}
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
                  disabled={loading || !token}
                  className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                >
                  {loading ? (
                    <Loader2 size={20} className="animate-spin" />
                  ) : (
                    <span>{t('resetPassword.updatePassword', 'Update Password')}</span>
                  )}
                </button>
              </form>

              <div className="mt-6 pt-6 border-t border-[#e8e6e1] text-center">
                <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
                  <ArrowLeft size={16} />
                  {t('forgotPassword.backToLogin', 'Back to Sign In')}
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
