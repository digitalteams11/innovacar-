import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { motion, AnimatePresence } from 'framer-motion';
import { LogIn, Lock, Mail, Loader2, ShieldCheck, Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';
import { usePublicBranding } from '../hooks/usePublicBranding';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PUBLIC_NOINDEX } from '../components/seo/robotsPresets';

const INNOVACAR_LOGO_URL = '/brand/innovacar-logo.png';

declare global {
  interface Window {
    google?: any;
  }
}

/* ============================================
   ANIMATED BACKGROUND BLOBS
   ============================================ */
function AnimatedBackground() {
  return (
    <div className="fixed inset-0 overflow-hidden pointer-events-none">
      <motion.div
        animate={{ x: [0, 30, 0], y: [0, -20, 0], scale: [1, 1.1, 1] }}
        transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute -top-1/4 -right-1/4 w-[800px] h-[800px] rounded-full opacity-30"
        style={{ background: 'radial-gradient(circle, rgba(30,58,95,0.15) 0%, transparent 70%)' }}
      />
      <motion.div
        animate={{ x: [0, -20, 0], y: [0, 30, 0], scale: [1, 1.15, 1] }}
        transition={{ duration: 25, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute -bottom-1/4 -left-1/4 w-[600px] h-[600px] rounded-full opacity-20"
        style={{ background: 'radial-gradient(circle, rgba(212,168,83,0.12) 0%, transparent 70%)' }}
      />
      <motion.div
        animate={{ x: [0, 40, 0], y: [0, 20, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute top-1/3 left-1/3 w-[400px] h-[400px] rounded-full opacity-20"
        style={{ background: 'radial-gradient(circle, rgba(59,130,246,0.1) 0%, transparent 70%)' }}
      />
    </div>
  );
}

/* ============================================
   GLASS INPUT COMPONENT
   ============================================ */
function GlassInput({
  icon: Icon,
  type = 'text',
  value,
  onChange,
  placeholder,
  required,
  rightElement,
  className,
}: {
  icon?: React.ElementType;
  type?: string;
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  required?: boolean;
  rightElement?: React.ReactNode;
  className?: string;
}) {
  const [focused, setFocused] = useState(false);

  return (
    <div
      className={`relative flex items-center transition-all duration-300 rounded-xl ${className || ''}`}
      style={{
        backgroundColor: 'var(--bg-input)',
        border: focused ? '1px solid var(--brand-gold)' : '1px solid var(--border-subtle)',
        boxShadow: focused ? '0 0 0 3px rgba(212, 168, 83, 0.1)' : 'none',
      }}
    >
      {Icon && (
        <div className="pl-4 pr-3">
          <Icon size={18} className={focused ? 'text-brand-500' : 'text-[var(--text-muted)]'} />
        </div>
      )}
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
        className="flex-1 bg-transparent border-none outline-none py-3.5 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
      {rightElement && <div className="pr-3">{rightElement}</div>}
    </div>
  );
}

/* ============================================
   MAIN LOGIN COMPONENT
   ============================================ */
export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorRequired, setTwoFactorRequired] = useState(false);
  const [challengeToken, setChallengeToken] = useState('');
  const [useRecoveryCode, setUseRecoveryCode] = useState(false);
  const [recoveryCode, setRecoveryCode] = useState('');
  const [trustDevice, setTrustDevice] = useState(false);
  const [availableMethods, setAvailableMethods] = useState<string[]>([]);
  const [activeMethod, setActiveMethod] = useState<'AUTHENTICATOR' | 'EMAIL'>('AUTHENTICATOR');
  const [emailOtpSent, setEmailOtpSent] = useState(false);
  const [emailOtpLoginCode, setEmailOtpLoginCode] = useState('');
  const [emailOtpMasked, setEmailOtpMasked] = useState('');
  const [emailOtpLoginCountdown, setEmailOtpLoginCountdown] = useState(0);
  const [emailOtpLoginBusy, setEmailOtpLoginBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [googleLoading, setGoogleLoading] = useState(false);
  const { login, verify2FA, verifyEmailOtp2FA, googleLogin } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const googleBtnRef = useRef<HTMLDivElement>(null);
  const publicBranding = usePublicBranding();
  const brandedLogoUrl = publicBranding?.found && publicBranding.logoUrl ? publicBranding.logoUrl : INNOVACAR_LOGO_URL;
  const brandedName = publicBranding?.found && publicBranding.tenantName ? publicBranding.tenantName : null;

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
    if (!clientId) return;
    let attempts = 0;
    const maxAttempts = 50;
    const initGoogle = () => {
      if (!window.google) {
        attempts++;
        if (attempts < maxAttempts) setTimeout(initGoogle, 100);
        return;
      }
      try {
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: handleGoogleCredentialResponse,
          auto_select: false,
        });
        if (googleBtnRef.current) {
          window.google.accounts.id.renderButton(googleBtnRef.current, {
            theme: 'outline', size: 'large', width: '100%',
            text: 'signin_with', shape: 'rectangular',
          });
        }
      } catch (e) {
        console.warn('Google Sign-In initialization failed:', e);
      }
    };
    initGoogle();
  }, []);

  const handleGoogleCredentialResponse = async (response: any) => {
    setGoogleLoading(true);
    setError('');
    try {
      const userData = await googleLogin(response.credential);
      // 2FA required after Google auth Ã¢â‚¬â€ reuse the same 2FA challenge flow
      if (userData?.twoFactorRequired) {
        const methods: string[] = userData.availableTwoFactorMethods ?? ['AUTHENTICATOR'];
        setChallengeToken(userData.challengeToken || '');
        setTwoFactorRequired(true);
        setAvailableMethods(methods);
        setActiveMethod(methods.includes('AUTHENTICATOR') ? 'AUTHENTICATOR' : 'EMAIL');
        setTwoFactorCode('');
        setRecoveryCode('');
        setUseRecoveryCode(false);
        setEmailOtpSent(false);
        setEmailOtpLoginCode('');
        setEmailOtpLoginCountdown(0);
        setError('');
        return;
      }
      if (!userData.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      navigateByRole(userData.role);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Google login failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const navigateByRole = (role: string) => {
    if (role === 'SUPER_ADMIN') navigate('/super-admin/dashboard');
    else if (role === 'EMPLOYEE') navigate('/employee/dashboard');
    else if (role === 'ACCOUNTANT') navigate('/payments');
    else navigate('/dashboard');
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');

    try {
      // Ã¢â€â‚¬Ã¢â€â‚¬ 2FA second-leg: challenge token already issued Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
      if (twoFactorRequired && challengeToken) {
        const userData = await verify2FA(
          challengeToken,
          useRecoveryCode ? '' : twoFactorCode,
          useRecoveryCode ? recoveryCode : undefined,
          trustDevice,
        );
        if (!userData?.role) {
          setError('Your account has no role assigned. Please contact support.');
          return;
        }
        navigateByRole(userData.role);
        return;
      }

      // Ã¢â€â‚¬Ã¢â€â‚¬ First-leg: email + password Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
      await api.get('/health', { timeout: 5000 });
      const userData = await login({ email, password });

      // Backend issued a 2FA challenge (200 response with twoFactorRequired)
      if (userData?.twoFactorRequired) {
        const methods: string[] = userData.availableTwoFactorMethods ?? ['AUTHENTICATOR'];
        setChallengeToken(userData.challengeToken || '');
        setTwoFactorRequired(true);
        setAvailableMethods(methods);
        setActiveMethod(methods.includes('AUTHENTICATOR') ? 'AUTHENTICATOR' : 'EMAIL');
        setTwoFactorCode('');
        setRecoveryCode('');
        setUseRecoveryCode(false);
        setEmailOtpSent(false);
        setEmailOtpLoginCode('');
        setEmailOtpLoginCountdown(0);
        setError('');
        return;
      }

      if (!userData?.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      navigateByRole(userData.role);
    } catch (err: any) {
      // When already in 2FA step, show the code-specific error from backend
      if (twoFactorRequired) {
        setError(err.response?.data?.message || 'The verification code is invalid or expired. Please try again.');
      } else if (err.response?.status === 428 && err.response?.data?.details?.twoFactorRequired) {
        // Backward-compat: some older paths may still return 428
        setTwoFactorRequired(true);
        setError(err.response.data.message || 'Enter your authenticator code.');
      } else if (err.code === 'ERR_NETWORK' || err.code === 'ECONNABORTED' || !err.response) {
        setError('Unable to connect to backend. Check API URL, network, or CORS configuration.');
      } else if (err.response?.status === 401) {
        setError('Invalid email or password.');
      } else if (err.response?.status === 500) {
        setError('Server error. Please check backend logs.');
      } else if (err.response?.status === 403) {
        setError(t('login.invalidCredentials'));
      } else if ((err as any).userMessage) {
        setError((err as any).userMessage);
      } else {
        setError(err.response?.data?.message || 'Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const startEmailOtpLoginCountdown = () => {
    setEmailOtpLoginCountdown(60);
    const timer = setInterval(() => {
      setEmailOtpLoginCountdown((prev) => {
        if (prev <= 1) { clearInterval(timer); return 0; }
        return prev - 1;
      });
    }, 1000);
  };

  const sendEmailOtpLogin = async () => {
    setEmailOtpLoginBusy(true);
    setError('');
    try {
      const res = await api.post('/auth/2fa/email/send', { challengeToken });
      const data = res.data?.data ?? {};
      setEmailOtpMasked(data.maskedEmail || '');
      setEmailOtpSent(true);
      startEmailOtpLoginCountdown();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to send code. Please try again.');
    } finally {
      setEmailOtpLoginBusy(false);
    }
  };

  const verifyEmailOtpLogin = async () => {
    if (emailOtpLoginCode.length < 6) return;
    setEmailOtpLoginBusy(true);
    setError('');
    try {
      const userData = await verifyEmailOtp2FA(challengeToken, emailOtpLoginCode, trustDevice);
      if (!userData?.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      navigateByRole(userData.role);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Invalid or expired code. Please try again.');
    } finally {
      setEmailOtpLoginBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden" style={{ backgroundColor: 'var(--bg-page)' }}>
      <SeoHead
        title="Log In"
        description="Log in to your Innovacar account to manage vehicles, reservations, contracts and clients."
        canonical="https://innovacar.app/#/login"
        robots={ROBOTS_PUBLIC_NOINDEX}
      />
      <AnimatedBackground />

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-[420px] relative z-10"
      >
        {/* Logo */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2, duration: 0.5 }}
          className="flex items-center justify-center gap-3 mb-10"
        >
          <div className="h-16 w-16 rounded-2xl bg-white flex items-center justify-center shadow-lg shadow-accent-400/20 overflow-hidden">
            <img src={brandedLogoUrl} alt={brandedName || 'InnovaCar'} className="h-full w-full object-contain p-1" />
          </div>
          <span className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            {brandedName ?? <>Innova<span className="text-accent-500">Car</span></>}
          </span>
        </motion.div>

        {/* Glass Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, duration: 0.5 }}
          className="rounded-3xl p-8 sm:p-10 relative overflow-hidden"
          style={{
            backgroundColor: 'var(--glass-bg)',
            backdropFilter: 'blur(24px) saturate(180%)',
            WebkitBackdropFilter: 'blur(24px) saturate(180%)',
            border: '1px solid var(--glass-border)',
            boxShadow: 'var(--shadow-glass)',
          }}
        >
          {/* Subtle gradient overlay */}
          <div className="absolute inset-0 bg-gradient-to-br from-accent-400/5 via-transparent to-brand-500/5 pointer-events-none" />

          <div className="relative z-10">
            <div className="text-center mb-8">
              <h1 className="text-xl font-bold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('login.welcomeBack')}</h1>
              <p className="text-sm font-normal" style={{ color: 'var(--text-muted)' }}>{t('login.enterDetails')}</p>
            </div>

            {/* Error */}
            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border overflow-hidden"
                  style={{
                    backgroundColor: 'rgba(239, 68, 68, 0.08)',
                    color: '#ef4444',
                    borderColor: 'rgba(239, 68, 68, 0.15)',
                    padding: '14px 16px',
                  }}
                >
                  <div className="w-2 h-2 bg-rose-500 rounded-full shrink-0" />
                  {error}
                </motion.div>
              )}
            </AnimatePresence>

            <AnimatePresence mode="wait">
              <motion.form
                key="email"
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20 }}
                transition={{ duration: 0.3 }}
                onSubmit={handleEmailSubmit}
                className="space-y-4"
              >
                  {/* Ã¢â€â‚¬Ã¢â€â‚¬ 2FA challenge step Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬ */}
                  {twoFactorRequired ? (
                    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
                      <div className="text-center space-y-2">
                        <div className="flex justify-center">
                          <div className="w-12 h-12 rounded-xl bg-brand-50 flex items-center justify-center">
                            <ShieldCheck size={24} className="text-brand-500" />
                          </div>
                        </div>
                        <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{t('login.twoFactorTitle')}</p>
                      </div>

                      {/* Method tabs Ã¢â‚¬â€ shown only when both methods are available */}
                      {availableMethods.length > 1 && (
                        <div className="flex rounded-xl overflow-hidden border border-[var(--border-subtle)]">
                          {availableMethods.includes('AUTHENTICATOR') && (
                            <button
                              type="button"
                              onClick={() => { setActiveMethod('AUTHENTICATOR'); setError(''); }}
                              className={`flex-1 py-2 text-xs font-medium transition-colors ${activeMethod === 'AUTHENTICATOR' ? 'bg-brand-600 text-white' : 'bg-transparent text-[var(--text-muted)] hover:bg-[var(--bg-subtle)]'}`}
                            >
                              {t('login.authenticatorTab')}
                            </button>
                          )}
                          {availableMethods.includes('EMAIL') && (
                            <button
                              type="button"
                              onClick={() => { setActiveMethod('EMAIL'); setError(''); setEmailOtpSent(false); setEmailOtpLoginCode(''); }}
                              className={`flex-1 py-2 text-xs font-medium transition-colors ${activeMethod === 'EMAIL' ? 'bg-brand-600 text-white' : 'bg-transparent text-[var(--text-muted)] hover:bg-[var(--bg-subtle)]'}`}
                            >
                              {t('login.emailOtpTab')}
                            </button>
                          )}
                        </div>
                      )}

                      {/* Authenticator App tab */}
                      {activeMethod === 'AUTHENTICATOR' && (
                        <>
                          <p className="text-xs text-center" style={{ color: 'var(--text-muted)' }}>
                            {useRecoveryCode
                              ? t('login.recoveryCodeHint')
                              : t('login.authenticatorHint')}
                          </p>
                          {useRecoveryCode ? (
                            <GlassInput
                              icon={ShieldCheck}
                              value={recoveryCode}
                              onChange={(v) => setRecoveryCode(v.toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 11))}
                              placeholder="XXXXX-XXXXX"
                            />
                          ) : (
                            <GlassInput
                              icon={ShieldCheck}
                              value={twoFactorCode}
                              onChange={(v) => setTwoFactorCode(v.replace(/\D/g, '').slice(0, 6))}
                              placeholder="000000"
                              type="text"
                            />
                          )}
                          <button
                            type="button"
                            onClick={() => { setUseRecoveryCode(v => !v); setTwoFactorCode(''); setRecoveryCode(''); setError(''); }}
                            className="text-xs text-center w-full text-brand-500 hover:text-brand-600"
                          >
                            {useRecoveryCode ? t('login.useAuthenticatorInstead') : t('login.useRecoveryCode')}
                          </button>
                        </>
                      )}

                      {/* Email OTP tab */}
                      {activeMethod === 'EMAIL' && (
                        <>
                          {!emailOtpSent ? (
                            <>
                              <p className="text-xs text-center" style={{ color: 'var(--text-muted)' }}>
                                {t('login.emailOtpSendHint')}
                              </p>
                              <button
                                type="button"
                                onClick={sendEmailOtpLogin}
                                disabled={emailOtpLoginBusy}
                                className="w-full py-2.5 rounded-xl text-sm font-medium border border-brand-300 text-brand-600 hover:bg-brand-50 disabled:opacity-50 flex items-center justify-center gap-2"
                              >
                                {emailOtpLoginBusy ? <Loader2 size={16} className="animate-spin" /> : <Mail size={16} />}
                                {emailOtpLoginBusy ? t('login.sending') : t('login.sendVerificationCode')}
                              </button>
                            </>
                          ) : (
                            <>
                              <p className="text-xs text-center" style={{ color: 'var(--text-muted)' }}>
                                {t('login.enterCodeSentTo', { email: emailOtpMasked })}
                              </p>
                              <GlassInput
                                icon={Mail}
                                value={emailOtpLoginCode}
                                onChange={(v) => { setEmailOtpLoginCode(v.replace(/\D/g, '').slice(0, 6)); setError(''); }}
                                placeholder="000000"
                                type="text"
                              />
                              <div className="flex justify-between items-center">
                                <button
                                  type="button"
                                  onClick={verifyEmailOtpLogin}
                                  disabled={emailOtpLoginBusy || emailOtpLoginCode.length < 6}
                                  className="px-4 py-2 rounded-xl text-sm font-semibold text-white disabled:opacity-50 flex items-center gap-2"
                                  style={{ background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)' }}
                                >
                                  {emailOtpLoginBusy ? <Loader2 size={16} className="animate-spin" /> : <ShieldCheck size={16} />}
                                  {emailOtpLoginBusy ? t('login.verifying') : t('login.verify')}
                                </button>
                                <button
                                  type="button"
                                  onClick={sendEmailOtpLogin}
                                  disabled={emailOtpLoginBusy || emailOtpLoginCountdown > 0}
                                  className="text-xs text-brand-500 hover:text-brand-600 disabled:opacity-40"
                                >
                                  {emailOtpLoginCountdown > 0 ? t('login.resendIn', { seconds: emailOtpLoginCountdown }) : t('login.resendCode')}
                                </button>
                              </div>
                            </>
                          )}
                        </>
                      )}

                      {/* Trust device Ã¢â‚¬â€ shared */}
                      <label className="flex items-start gap-3 cursor-pointer select-none pt-1">
                        <input
                          type="checkbox"
                          checked={trustDevice}
                          onChange={e => setTrustDevice(e.target.checked)}
                          className="mt-0.5 w-4 h-4 rounded border-[var(--border-subtle)] text-brand-500 focus:ring-brand-500 shrink-0"
                        />
                        <span className="text-xs leading-snug" style={{ color: 'var(--text-muted)' }}>
                          <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{t('login.trustDevice')}</span>
                          <br />{t('login.trustDeviceHint')}
                        </span>
                      </label>

                      {/* Submit button Ã¢â‚¬â€ only for AUTHENTICATOR tab (EMAIL uses its own button) */}
                      {activeMethod === 'AUTHENTICATOR' && (
                        <motion.button
                          type="submit"
                          disabled={loading || (!useRecoveryCode && twoFactorCode.length !== 6) || (useRecoveryCode && recoveryCode.length < 11)}
                          whileHover={{ scale: 1.01 }}
                          whileTap={{ scale: 0.98 }}
                          className="w-full py-3.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                          style={{ background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)', color: 'white', boxShadow: '0 4px 16px -4px rgba(30,58,95,0.3)' }}
                        >
                          {loading ? <Loader2 size={20} className="animate-spin" /> : <><ShieldCheck size={16} /><span>{t('login.verify')}</span></>}
                        </motion.button>
                      )}

                      <button
                        type="button"
                        onClick={() => { setTwoFactorRequired(false); setChallengeToken(''); setTwoFactorCode(''); setRecoveryCode(''); setUseRecoveryCode(false); setTrustDevice(false); setEmailOtpSent(false); setEmailOtpLoginCode(''); setError(''); }}
                        className="text-xs text-center w-full mt-1"
                        style={{ color: 'var(--text-muted)' }}
                      >
                        {t('login.backToLogin')}
                      </button>
                    </motion.div>
                  ) : (
                    /* Ã¢â€â‚¬Ã¢â€â‚¬ Email + password step Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬ */
                    <>
                      <div>
                        <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('login.email')}</label>
                        <GlassInput icon={Mail} type="email" value={email} onChange={setEmail} placeholder={t('login.emailPlaceholder')} required />
                      </div>

                      <div>
                        <div className="flex items-center justify-between mb-2 ml-1">
                          <label className="block text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{t('login.password')}</label>
                          <Link to="/forgot-password" className="text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
                            {t('login.forgot')}
                          </Link>
                        </div>
                        <GlassInput
                          icon={Lock}
                          type={showPassword ? 'text' : 'password'}
                          value={password}
                          onChange={setPassword}
                          placeholder="********"
                          required
                          rightElement={
                            <button type="button" onClick={() => setShowPassword(v => !v)} className="text-[var(--text-muted)] hover:text-brand-500 transition-colors" tabIndex={-1}>
                              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                            </button>
                          }
                        />
                      </div>

                      <div className="flex items-center gap-3 pt-1">
                        <input type="checkbox" id="remember" className="w-4 h-4 rounded border-[var(--border-subtle)] text-brand-500 focus:ring-brand-500" />
                        <label htmlFor="remember" className="text-sm font-normal cursor-pointer" style={{ color: 'var(--text-muted)' }}>{t('login.rememberMe')}</label>
                      </div>

                      <motion.button
                        type="submit"
                        disabled={loading}
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.98 }}
                        className="w-full py-3.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-3 disabled:opacity-70 mt-2"
                        style={{ background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)', color: 'white', boxShadow: '0 4px 16px -4px rgba(30,58,95,0.3)' }}
                      >
                        {loading ? <Loader2 size={20} className="animate-spin" /> : <><span>{t('login.signIn')}</span><LogIn size={16} strokeWidth={2.5} /></>}
                      </motion.button>
                    </>
                  )}
                </motion.form>
            </AnimatePresence>

            {/* Divider */}
            <div className="relative my-6">
              <div className="absolute inset-0 flex items-center"><div className="w-full" style={{ borderTop: '1px solid var(--border-subtle)' }} /></div>
              <div className="relative flex justify-center text-xs"><span className="px-3 font-medium" style={{ backgroundColor: 'var(--glass-bg)', color: 'var(--text-muted)' }}>{t('login.or')}</span></div>
            </div>

            {/* Google Sign-In */}
            {googleLoading ? (
              <div className="w-full py-3 rounded-xl flex items-center justify-center gap-2 text-sm" style={{ border: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                <Loader2 size={18} className="animate-spin" />
                <span className="font-medium">{t('login.signingInWithGoogle')}</span>
              </div>
            ) : (
              <div ref={googleBtnRef} className="w-full flex justify-center" />
            )}
            {!window.google && (
              <button
                onClick={() => setError(t('login.googleNotConfigured'))}
                className="w-full py-3 rounded-xl flex items-center justify-center gap-2 text-sm font-medium transition-all hover:bg-[var(--bg-hover)]"
                style={{ border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                {t('login.signInWithGoogle')}
              </button>
            )}

            {/* Register link */}
            <div className="mt-6 pt-6 text-center" style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                {t('login.dontHaveAccount')}{' '}
                <Link to="/register" className="font-medium text-brand-500 hover:text-brand-600 transition-colors">{t('login.signUp')}</Link>
              </p>
            </div>
          </div>
        </motion.div>

        {/* Footer */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.6 }}
          className="mt-8 text-center flex items-center justify-center gap-2"
          style={{ color: 'var(--text-muted)' }}
        >
          <ShieldCheck size={15} />
          <p className="text-xs font-normal">{t('login.secureAccess')}</p>
        </motion.div>
      </motion.div>
    </div>
  );
}
