import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import api, { translateApiError } from '../api/axios';
import { Loader2, ArrowLeft, CheckCircle, Lock, Eye, EyeOff } from 'lucide-react';
import { ErrorBox } from './ForgotPassword';
import { checkPasswordStrength } from '../lib/passwordPolicy';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';
import AuthLogo from '../components/auth/AuthLogo';

function StrengthItem({ ok, label }: { ok: boolean; label: string }) {
  return (
    <li className={`flex items-center gap-2 text-xs transition-colors ${ok ? 'text-success-600' : 'text-slate-400'}`}>
      <span className={`w-4 h-4 rounded-full flex items-center justify-center text-[10px] font-bold shrink-0 transition-colors ${ok ? 'bg-success-100 text-success-600' : 'bg-slate-100 text-slate-400'}`}>
        {ok ? '✓' : '·'}
      </span>
      {label}
    </li>
  );
}

export default function ResetPassword() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state || {}) as { email?: string; resetSessionToken?: string };

  const [newPassword, setNewPwd]   = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [showPwd, setShowPwd]     = useState(false);
  const [showCfm, setShowCfm]     = useState(false);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [success, setSuccess]     = useState(false);

  const strength = checkPasswordStrength(newPassword);
  const strengthOk = Object.values(strength).every(Boolean);

  useEffect(() => {
    if (!state.resetSessionToken) {
      navigate('/forgot-password', { replace: true });
    }
  }, [state.resetSessionToken, navigate]);

  const handleSetPassword = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    if (newPassword !== confirmPwd) {
      setError(t('register.passwordMismatch', 'Passwords do not match.'));
      return;
    }
    if (!strengthOk) {
      setError(t('forgotPassword.weakPassword', 'Password does not meet the requirements.'));
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.post('/auth/reset-password', {
        resetSessionToken: state.resetSessionToken,
        newPassword,
        confirmPassword: confirmPwd,
      });
      setSuccess(true);
      // Clear the token from history state immediately — it's single-use anyway.
      window.history.replaceState({}, '');
      setTimeout(() => navigate('/login'), 3000);
    } catch (err: any) {
      if (err?.errorCode === 'INVALID_SESSION_TOKEN') {
        navigate('/forgot-password', { replace: true });
      } else {
        setError(translateApiError(err, t));
      }
    } finally {
      setLoading(false);
    }
  };

  const inputCls = 'block w-full ps-11 pe-11 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all';
  const btnCls = 'w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70';

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <SeoHead
        title="Reset Password"
        description="Set a new password for your Innovacar account."
        canonical="https://innovacar.app/#/reset-password"
        robots={ROBOTS_PRIVATE}
      />
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3" />
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3" />

      <div className="w-full max-w-[400px] animate-fade relative z-10">
        <AuthLogo />

        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60">
          {!success ? (
            <>
              <div className="text-center mb-8">
                <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">
                  {t('resetPassword.title', 'Set new password')}
                </h1>
                <p className="text-slate-500 font-normal text-xs sm:text-sm">
                  {t('resetPassword.subtitle', 'Choose a strong password for your account.')}
                </p>
              </div>
              {error && <ErrorBox msg={error} />}
              <form onSubmit={handleSetPassword} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ms-1">
                    {t('resetPassword.newPassword', 'New Password')}
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 start-0 ps-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Lock size={18} />
                    </span>
                    <input
                      type={showPwd ? 'text' : 'password'}
                      value={newPassword}
                      onChange={e => setNewPwd(e.target.value)}
                      className={inputCls}
                      placeholder="••••••••"
                      required
                    />
                    <button type="button" onClick={() => setShowPwd(v => !v)} tabIndex={-1}
                      className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors">
                      {showPwd ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                  {newPassword && (
                    <ul className="mt-3 space-y-1 ps-1">
                      <StrengthItem ok={strength.len}   label={t('forgotPassword.req8chars',   'At least 8 characters')} />
                      <StrengthItem ok={strength.upper} label={t('forgotPassword.reqUpper',    'One uppercase letter')} />
                      <StrengthItem ok={strength.lower} label={t('forgotPassword.reqLower',    'One lowercase letter')} />
                      <StrengthItem ok={strength.digit} label={t('forgotPassword.reqDigit',    'One number')} />
                      <StrengthItem ok={strength.sym}   label={t('forgotPassword.reqSymbol',   'One special character')} />
                    </ul>
                  )}
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ms-1">
                    {t('register.confirmPassword', 'Confirm Password')}
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 start-0 ps-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Lock size={18} />
                    </span>
                    <input
                      type={showCfm ? 'text' : 'password'}
                      value={confirmPwd}
                      onChange={e => setConfirmPwd(e.target.value)}
                      className={inputCls}
                      placeholder="••••••••"
                      required
                    />
                    <button type="button" onClick={() => setShowCfm(v => !v)} tabIndex={-1}
                      className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors">
                      {showCfm ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>
                <button type="submit" disabled={loading || !strengthOk} className={btnCls}>
                  {loading ? <Loader2 size={20} className="animate-spin" /> : t('resetPassword.updatePassword', 'Update Password')}
                </button>
              </form>
            </>
          ) : (
            <div className="text-center py-6">
              <div className="w-14 h-14 bg-success-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <CheckCircle size={28} className="text-success-500" />
              </div>
              <h3 className="text-lg font-semibold text-[#1e293b] mb-2">
                {t('resetPassword.successTitle', 'Password updated!')}
              </h3>
              <p className="text-slate-500 text-sm mb-6">
                {t('resetPassword.successDesc', 'Redirecting you to sign in...')}
              </p>
              <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
                <ArrowLeft size={16} />
                {t('forgotPassword.backToLogin', 'Back to Sign In')}
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
