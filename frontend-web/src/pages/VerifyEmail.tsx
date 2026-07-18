import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/axios';
import { Loader2, CheckCircle, XCircle } from 'lucide-react';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PRIVATE } from '../components/seo/robotsPresets';
import AuthLogo from '../components/auth/AuthLogo';

export default function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [verified, setVerified] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (!token) {
      setError(t('verifyEmail.invalidToken', 'Invalid or missing verification token.'));
      setLoading(false);
      return;
    }

    const verify = async () => {
      try {
        await api.post('/auth/verify-email', { token });
        setVerified(true);
      } catch (err: any) {
        const msg = err?.response?.data?.message || t('verifyEmail.failed', 'Failed to verify email. The link may have expired.');
        setError(msg);
      } finally {
        setLoading(false);
      }
    };

    verify();
  }, [token, t]);

  return (
    <div className="min-h-screen bg-[#f5f5f0] flex items-center justify-center p-6 relative overflow-hidden">
      <SeoHead
        title="Verify Email"
        description="Verify your email address for your Innovacar account."
        canonical="https://innovacar.app/#/verify-email"
        robots={ROBOTS_PRIVATE}
      />
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-brand-500/3 rounded-full blur-3xl translate-x-1/3 -translate-y-1/3"></div>
      <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-accent-400/5 rounded-full blur-3xl -translate-x-1/3 translate-y-1/3"></div>

      <div className="w-full max-w-[400px] animate-fade relative z-10">
        <AuthLogo />

        {/* Card */}
        <div className="bg-white rounded-2xl p-8 md:p-10 shadow-elevated border border-[#e8e6e1]/60 text-center">
          {loading ? (
            <div className="py-8">
              <Loader2 size={40} className="animate-spin text-brand-500 mx-auto mb-4" />
              <p className="text-slate-500 text-sm">{t('verifyEmail.verifying', 'Verifying your email...')}</p>
            </div>
          ) : verified ? (
            <div className="py-4">
              <div className="w-14 h-14 bg-success-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <CheckCircle size={28} className="text-success-500" />
              </div>
              <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-2">{t('verifyEmail.successTitle', 'Email Verified!')}</h1>
              <p className="text-slate-500 text-sm mb-6">{t('verifyEmail.successDesc', 'Your email has been successfully verified. You can now access all features.')}</p>
              <Link to="/login" className="inline-flex items-center justify-center w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all">
                {t('verifyEmail.goToLogin', 'Go to Sign In')}
              </Link>
            </div>
          ) : (
            <div className="py-4">
              <div className="w-14 h-14 bg-danger-50 rounded-full flex items-center justify-center mx-auto mb-4">
                <XCircle size={28} className="text-danger-500" />
              </div>
              <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-2">{t('verifyEmail.errorTitle', 'Verification Failed')}</h1>
              <p className="text-danger-500 text-sm mb-2">{error}</p>
              <p className="text-slate-500 text-sm mb-6">{t('verifyEmail.resendHint', 'Please request a new verification email or contact support.')}</p>
              <Link to="/login" className="inline-flex items-center justify-center w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all">
                {t('verifyEmail.goToLogin', 'Go to Sign In')}
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
