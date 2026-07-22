import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import api, { translateApiError } from '../api/axios';
import { Mail, Loader2, ArrowLeft } from 'lucide-react';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PUBLIC_NOINDEX } from '../components/seo/robotsPresets';
import AuthLogo from '../components/auth/AuthLogo';

export default function ForgotPassword() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [email, setEmail]     = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const handleSendCode = async (e: { preventDefault(): void }) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await api.post('/auth/forgot-password', { email });
      navigate(`/verify-reset-code?email=${encodeURIComponent(email)}`);
    } catch (err: any) {
      setError(translateApiError(err, t));
    } finally {
      setLoading(false);
    }
  };

  const inputCls = 'block w-full ps-11 pe-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all';
  const btnCls = 'w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70';

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <SeoHead
        title="Forgot Password"
        description="Reset your Innovacar account password."
        canonical="https://innovacar.app/#/forgot-password"
        robots={ROBOTS_PUBLIC_NOINDEX}
      />
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3" />
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3" />

      <div className="w-full max-w-[400px] animate-fade relative z-10">
        <AuthLogo />

        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60">
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
              <label className="block text-sm font-medium text-[#1e293b] mb-2 ms-1">
                {t('login.email', 'Email Address')}
              </label>
              <div className="relative group">
                <span className="absolute inset-y-0 start-0 ps-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
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
        </div>
      </div>
    </div>
  );
}

export function ErrorBox({ msg }: { msg: string }) {
  return (
    <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-5 text-sm font-medium flex items-center gap-3 border border-danger-100">
      <div className="w-2 h-2 bg-danger-500 rounded-full shrink-0" />
      {msg}
    </div>
  );
}
