import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import {
  Mail, Car, Loader2, ArrowLeft, CheckCircle,
  Lock, Eye, EyeOff, ShieldCheck,
} from 'lucide-react';

type Step = 'email' | 'code' | 'password' | 'success';

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

function checkStrength(p: string) {
  return {
    len:   p.length >= 8,
    upper: /[A-Z]/.test(p),
    lower: /[a-z]/.test(p),
    digit: /\d/.test(p),
    sym:   /[^A-Za-z0-9]/.test(p),
  };
}

const RESEND_COOLDOWN = 60;

export default function ForgotPassword() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [step, setStep]           = useState<Step>('email');
  const [email, setEmail]         = useState('');
  const [code, setCode]           = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPwd]  = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [showPwd, setShowPwd]     = useState(false);
  const [showCfm, setShowCfm]     = useState(false);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [countdown, setCountdown] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const strength = checkStrength(newPassword);
  const strengthOk = Object.values(strength).every(Boolean);

  useEffect(() => {
    if (countdown > 0) {
      timerRef.current = setInterval(() => setCountdown(c => c - 1), 1000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [countdown]);

  const startCountdown = () => setCountdown(RESEND_COOLDOWN);

  const handleSendCode = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await api.post('/auth/forgot-password', { email });
      setStep('code');
      startCountdown();
    } catch {
      setError(t('forgotPassword.failed', 'Failed to send code. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (countdown > 0) return;
    setError('');
    try {
      await api.post('/auth/forgot-password', { email });
      startCountdown();
    } catch {
      setError(t('forgotPassword.failed', 'Failed to resend code.'));
    }
  };

  const handleVerifyCode = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await api.post('/auth/verify-reset-code', { email, code: code.trim() });
      const token = res.data?.data?.resetSessionToken;
      if (!token) throw new Error('no token');
      setResetToken(token);
      setStep('password');
    } catch (err: any) {
      const msg = err?.response?.data?.message
        || t('forgotPassword.invalidCode', 'Invalid or expired code. Please try again.');
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

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
        resetSessionToken: resetToken,
        newPassword,
        confirmPassword: confirmPwd,
      });
      setStep('success');
      setTimeout(() => navigate('/login'), 3000);
    } catch (err: any) {
      const msg = err?.response?.data?.message
        || t('forgotPassword.resetFailed', 'Failed to reset password. Please start over.');
      if (msg.toLowerCase().includes('session') || msg.toLowerCase().includes('expired')) {
        setStep('email');
        setError(t('forgotPassword.sessionExpired', 'Your reset session expired. Please start over.'));
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  const inputCls = 'block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all';
  const btnCls = 'w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70';

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3" />
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3" />

      <div className="w-full max-w-[400px] animate-fade relative z-10">
        <div className="flex items-center justify-center gap-3 mb-10">
          <div className="w-10 h-10 rounded-lg bg-brand-500 flex items-center justify-center text-white shadow-lg">
            <Car size={22} strokeWidth={2.5} />
          </div>
          <span className="text-2xl font-bold tracking-tight text-[#1e293b]">Rent<span className="text-brand-500">Car</span></span>
        </div>

        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60">

          {/* ── Step 1: Email ── */}
          {step === 'email' && (
            <>
              <div className="text-center mb-8">
                <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">
                  {t('forgotPassword.title', 'Reset Password')}
                </h1>
                <p className="text-slate-500 font-normal text-xs sm:text-sm">
                  {t('forgotPassword.subtitle', "Enter your email and we'll send you a 6-digit code.")}
                </p>
              </div>
              {error && <ErrorBox msg={error} />}
              <form onSubmit={handleSendCode} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">
                    {t('login.email', 'Email Address')}
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Mail size={18} />
                    </span>
                    <input
                      type="email"
                      value={email}
                      onChange={e => setEmail(e.target.value)}
                      className={inputCls}
                      placeholder={t('login.emailPlaceholder', 'name@company.com')}
                      required
                    />
                  </div>
                </div>
                <button type="submit" disabled={loading} className={btnCls}>
                  {loading ? <Loader2 size={20} className="animate-spin" /> : t('forgotPassword.sendCode', 'Send Code')}
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

          {/* ── Step 2: 6-digit code ── */}
          {step === 'code' && (
            <>
              <div className="text-center mb-8">
                <div className="w-14 h-14 bg-brand-50 rounded-full flex items-center justify-center mx-auto mb-4">
                  <ShieldCheck size={28} className="text-brand-500" />
                </div>
                <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">
                  {t('forgotPassword.enterCode', 'Enter verification code')}
                </h1>
                <p className="text-slate-500 font-normal text-xs sm:text-sm">
                  {t('forgotPassword.codeSentTo', 'We sent a 6-digit code to')}{' '}
                  <span className="font-semibold text-[#1e293b]">{email}</span>
                </p>
              </div>
              {error && <ErrorBox msg={error} />}
              <form onSubmit={handleVerifyCode} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">
                    {t('forgotPassword.code', '6-digit code')}
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    pattern="\d{6}"
                    maxLength={6}
                    value={code}
                    onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    className="block w-full text-center py-4 text-3xl font-mono tracking-[0.5em] bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                    placeholder="——————"
                    required
                  />
                </div>
                <button type="submit" disabled={loading || code.length < 6} className={btnCls}>
                  {loading ? <Loader2 size={20} className="animate-spin" /> : t('forgotPassword.verifyCode', 'Verify Code')}
                </button>
              </form>
              <div className="mt-5 text-center text-sm text-slate-500">
                {t('forgotPassword.didntReceive', "Didn't receive it?")}{' '}
                {countdown > 0 ? (
                  <span className="text-slate-400">
                    {t('forgotPassword.resendIn', 'Resend in')} {countdown}s
                  </span>
                ) : (
                  <button
                    onClick={handleResend}
                    className="text-brand-500 font-medium hover:text-brand-600 transition-colors"
                  >
                    {t('forgotPassword.resend', 'Resend')}
                  </button>
                )}
              </div>
              <div className="mt-6 pt-6 border-t border-[#e8e6e1] text-center">
                <button
                  onClick={() => { setStep('email'); setError(''); setCode(''); }}
                  className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors"
                >
                  <ArrowLeft size={16} />
                  {t('forgotPassword.changeEmail', 'Use a different email')}
                </button>
              </div>
            </>
          )}

          {/* ── Step 3: New password ── */}
          {step === 'password' && (
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
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">
                    {t('resetPassword.newPassword', 'New Password')}
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Lock size={18} />
                    </span>
                    <input
                      type={showPwd ? 'text' : 'password'}
                      value={newPassword}
                      onChange={e => setNewPwd(e.target.value)}
                      className={inputCls.replace('pr-4', 'pr-11')}
                      placeholder="••••••••"
                      required
                    />
                    <button type="button" onClick={() => setShowPwd(v => !v)} tabIndex={-1}
                      className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors">
                      {showPwd ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                  {newPassword && (
                    <ul className="mt-3 space-y-1 pl-1">
                      <StrengthItem ok={strength.len}   label={t('forgotPassword.req8chars',   'At least 8 characters')} />
                      <StrengthItem ok={strength.upper} label={t('forgotPassword.reqUpper',    'One uppercase letter')} />
                      <StrengthItem ok={strength.lower} label={t('forgotPassword.reqLower',    'One lowercase letter')} />
                      <StrengthItem ok={strength.digit} label={t('forgotPassword.reqDigit',    'One number')} />
                      <StrengthItem ok={strength.sym}   label={t('forgotPassword.reqSymbol',   'One special character')} />
                    </ul>
                  )}
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">
                    {t('register.confirmPassword', 'Confirm Password')}
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                      <Lock size={18} />
                    </span>
                    <input
                      type={showCfm ? 'text' : 'password'}
                      value={confirmPwd}
                      onChange={e => setConfirmPwd(e.target.value)}
                      className={inputCls.replace('pr-4', 'pr-11')}
                      placeholder="••••••••"
                      required
                    />
                    <button type="button" onClick={() => setShowCfm(v => !v)} tabIndex={-1}
                      className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400 hover:text-brand-500 transition-colors">
                      {showCfm ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>
                <button type="submit" disabled={loading || !strengthOk} className={btnCls}>
                  {loading ? <Loader2 size={20} className="animate-spin" /> : t('resetPassword.updatePassword', 'Update Password')}
                </button>
              </form>
            </>
          )}

          {/* ── Step 4: Success ── */}
          {step === 'success' && (
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

function ErrorBox({ msg }: { msg: string }) {
  return (
    <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-5 text-sm font-medium flex items-center gap-3 border border-danger-100">
      <div className="w-2 h-2 bg-danger-500 rounded-full shrink-0" />
      {msg}
    </div>
  );
}
