import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api, { translateApiError } from '../api/axios';
import { Loader2, ArrowLeft, ShieldCheck } from 'lucide-react';
import { ErrorBox } from './ForgotPassword';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PUBLIC_NOINDEX } from '../components/seo/robotsPresets';
import AuthLogo from '../components/auth/AuthLogo';

const RESEND_COOLDOWN = 60;

export default function VerifyResetCode() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const email = searchParams.get('email') || '';

  const [code, setCode]           = useState('');
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [countdown, setCountdown] = useState(RESEND_COOLDOWN);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!email) {
      navigate('/forgot-password', { replace: true });
    }
  }, [email, navigate]);

  useEffect(() => {
    if (countdown > 0) {
      timerRef.current = setInterval(() => setCountdown(c => c - 1), 1000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [countdown]);

  const handleResend = async () => {
    if (countdown > 0) return;
    setError('');
    try {
      await api.post('/auth/forgot-password', { email });
      setCountdown(RESEND_COOLDOWN);
    } catch (err: any) {
      setError(translateApiError(err, t));
    }
  };

  const handleVerifyCode = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const res = await api.post('/auth/verify-reset-code', { email, code: code.trim() });
      const resetSessionToken = res.data?.data?.resetSessionToken;
      if (!resetSessionToken) throw new Error('no token');
      navigate('/reset-password', { state: { email, resetSessionToken }, replace: true });
    } catch (err: any) {
      setError(translateApiError(err, t));
    } finally {
      setLoading(false);
    }
  };

  const btnCls = 'w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70';

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <SeoHead
        title="Verify Reset Code"
        description="Verify the password reset code sent to your email."
        canonical="https://innovacar.app/#/verify-reset-code"
        robots={ROBOTS_PUBLIC_NOINDEX}
      />
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3" />
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3" />

      <div className="w-full max-w-[400px] animate-fade relative z-10">
        <AuthLogo />

        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60">
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
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ms-1">
                {t('forgotPassword.code', '6-digit code')}
              </label>
              <input
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                autoFocus
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
            <Link
              to="/forgot-password"
              className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors"
            >
              <ArrowLeft size={16} />
              {t('forgotPassword.changeEmail', 'Use a different email')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
