import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { motion, AnimatePresence } from 'framer-motion';
import { LogIn, Lock, Mail, Car, Loader2, ShieldCheck, Smartphone, Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';

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
  const [mode, setMode] = useState<'email' | 'phone'>('email');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorRequired, setTwoFactorRequired] = useState(false);
  const [phoneStep, setPhoneStep] = useState<'input' | 'otp'>('input');
  const [phoneLoading, setPhoneLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [googleLoading, setGoogleLoading] = useState(false);
  const { login, googleLogin } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const googleBtnRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown(c => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

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
      if (!userData.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      if (userData.role === 'SUPER_ADMIN') navigate('/super-admin/dashboard');
      else if (userData.role === 'EMPLOYEE') navigate('/employee/dashboard');
      else if (userData.role === 'ACCOUNTANT') navigate('/payments');
      else navigate('/dashboard');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Google login failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      await api.get('/health', { timeout: 5000 });
      const userData = await login({
        email,
        password,
        otpCode: twoFactorRequired ? twoFactorCode : undefined,
      });
      if (!userData.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      if (userData.role === 'SUPER_ADMIN') navigate('/super-admin/dashboard');
      else if (userData.role === 'EMPLOYEE') navigate('/employee/dashboard');
      else if (userData.role === 'ACCOUNTANT') navigate('/payments');
      else navigate('/dashboard');
    } catch (err: any) {
      if (err.response?.status === 428
          && err.response?.data?.details?.twoFactorRequired) {
        setTwoFactorRequired(true);
        setError(err.response.data.message || 'Enter your authenticator code.');
      } else if (err.code === 'ERR_NETWORK' || err.code === 'ECONNABORTED' || !err.response) {
        setError('Unable to connect to backend. Check API URL, network, or CORS configuration.');
      } else if (err.response.status === 401) {
        setError('Invalid email or password.');
      } else if (err.response.status === 500) {
        setError('Server error. Please check backend logs.');
      } else if (err.response.status === 403) {
        setError(t('login.invalidCredentials'));
      } else if ((err as any).userMessage) {
        setError((err as any).userMessage);
      } else {
        setError('Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!phone.trim()) return;
    setPhoneLoading(true);
    setError('');
    try {
      await api.post('/auth/phone/send-otp', { phoneNumber: phone.trim() });
      setPhoneStep('otp');
      setCountdown(60);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to send OTP. Please try again.');
    } finally {
      setPhoneLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otp.trim()) return;
    setPhoneLoading(true);
    setError('');
    try {
      const { data } = await api.post('/auth/phone/verify-otp', {
        phoneNumber: phone.trim(), otpCode: otp.trim(),
      });
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.setItem('user', JSON.stringify(data));
      localStorage.removeItem('tokenExpiry');
      if (!data.role) {
        setError('Your account has no role assigned. Please contact support.');
        return;
      }
      if (data.role === 'SUPER_ADMIN') window.location.href = '/#/super-admin/dashboard';
      else if (data.role === 'EMPLOYEE') window.location.href = '/#/employee/dashboard';
      else if (data.role === 'ACCOUNTANT') window.location.href = '/#/payments';
      else window.location.href = '/#/';
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Invalid OTP. Please try again.');
    } finally {
      setPhoneLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (countdown > 0) return;
    setError('');
    try {
      await api.post('/auth/phone/send-otp', { phoneNumber: phone.trim() });
      setCountdown(60);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to resend OTP.');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden" style={{ backgroundColor: 'var(--bg-page)' }}>
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
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-accent-300 to-accent-500 flex items-center justify-center shadow-lg shadow-accent-400/20">
            <Car size={24} className="text-brand-900" strokeWidth={2.5} />
          </div>
          <span className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Rent<span className="text-accent-500">Car</span>
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

            {/* Mode Toggle */}
            <div
              className="flex p-1 rounded-xl mb-6"
              style={{ backgroundColor: 'var(--bg-hover)' }}
            >
              {(['email', 'phone'] as const).map((m) => (
                <button
                  key={m}
                  onClick={() => { setMode(m); setError(''); }}
                  className={`flex-1 py-2.5 text-sm font-medium rounded-lg transition-all flex items-center justify-center gap-2 ${
                    mode === m
                      ? 'shadow-sm'
                      : 'hover:text-[var(--text-primary)]'
                  }`}
                  style={{
                    backgroundColor: mode === m ? 'var(--bg-card-solid)' : 'transparent',
                    color: mode === m ? 'var(--brand-primary)' : 'var(--text-muted)',
                  }}
                >
                  {m === 'email' ? <Mail size={16} /> : <Smartphone size={16} />}
                  {m === 'email' ? 'Email' : 'Phone'}
                </button>
              ))}
            </div>

            <AnimatePresence mode="wait">
              {mode === 'email' ? (
                <motion.form
                  key="email"
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 20 }}
                  transition={{ duration: 0.3 }}
                  onSubmit={handleEmailSubmit}
                  className="space-y-4"
                >
                  <div>
                    <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('login.email')}</label>
                    <GlassInput
                      icon={Mail}
                      type="email"
                      value={email}
                      onChange={setEmail}
                      placeholder={t('login.emailPlaceholder')}
                      required
                    />
                  </div>

                  {twoFactorRequired && (
                    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                      <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>
                        Authenticator code
                      </label>
                      <GlassInput
                        icon={ShieldCheck}
                        value={twoFactorCode}
                        onChange={(value) => setTwoFactorCode(value.replace(/\D/g, '').slice(0, 6))}
                        placeholder="000000"
                        required
                      />
                      <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                        Enter the 6-digit code from Google Authenticator or Microsoft Authenticator.
                      </p>
                    </motion.div>
                  )}

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
                      placeholder="••••••••"
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
                    style={{
                      background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)',
                      color: 'white',
                      boxShadow: '0 4px 16px -4px rgba(30, 58, 95, 0.3)',
                    }}
                  >
                    {loading ? <Loader2 size={20} className="animate-spin" /> : <><span>{t('login.signIn')}</span><LogIn size={16} strokeWidth={2.5} /></>}
                  </motion.button>
                </motion.form>
              ) : (
                <motion.div
                  key="phone"
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.3 }}
                >
                  {phoneStep === 'input' ? (
                    <form onSubmit={handleSendOtp} className="space-y-4">
                      <div>
                        <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>Phone Number</label>
                        <GlassInput
                          icon={Smartphone}
                          type="tel"
                          value={phone}
                          onChange={setPhone}
                          placeholder="+212612345678"
                          required
                        />
                        <p className="text-xs mt-1.5 ml-1" style={{ color: 'var(--text-muted)' }}>Enter with country code (e.g. +212...)</p>
                      </div>
                      <motion.button
                        type="submit"
                        disabled={phoneLoading}
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.98 }}
                        className="w-full py-3.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                        style={{
                          background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)',
                          color: 'white',
                          boxShadow: '0 4px 16px -4px rgba(30, 58, 95, 0.3)',
                        }}
                      >
                        {phoneLoading ? <Loader2 size={20} className="animate-spin" /> : <span>Send OTP</span>}
                      </motion.button>
                    </form>
                  ) : (
                    <form onSubmit={handleVerifyOtp} className="space-y-4">
                      <div className="text-center mb-2">
                        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Code sent to <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{phone}</span></p>
                      </div>
                      <div>
                        <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>Verification Code</label>
                        <input
                          type="text"
                          value={otp}
                          onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                          className="w-full px-4 py-3.5 rounded-xl text-center text-lg tracking-[0.5em] font-mono transition-all"
                          style={{
                            backgroundColor: 'var(--bg-input)',
                            border: '1px solid var(--border-subtle)',
                            color: 'var(--text-primary)',
                          }}
                          placeholder="000000"
                          required
                          maxLength={6}
                          inputMode="numeric"
                          autoFocus
                        />
                      </div>
                      <motion.button
                        type="submit"
                        disabled={phoneLoading}
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.98 }}
                        className="w-full py-3.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                        style={{
                          background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)',
                          color: 'white',
                          boxShadow: '0 4px 16px -4px rgba(30, 58, 95, 0.3)',
                        }}
                      >
                        {phoneLoading ? <Loader2 size={20} className="animate-spin" /> : <span>Verify & Sign In</span>}
                      </motion.button>
                      <div className="flex items-center justify-between text-sm">
                        <button type="button" onClick={() => { setPhoneStep('input'); setOtp(''); setError(''); }} className="transition-colors" style={{ color: 'var(--text-muted)' }}>Change number</button>
                        {countdown > 0 ? (
                          <span style={{ color: 'var(--text-muted)' }}>Resend in {countdown}s</span>
                        ) : (
                          <button type="button" onClick={handleResendOtp} className="text-brand-500 hover:text-brand-600 font-medium transition-colors">Resend OTP</button>
                        )}
                      </div>
                    </form>
                  )}
                </motion.div>
              )}
            </AnimatePresence>

            {/* Divider */}
            <div className="relative my-6">
              <div className="absolute inset-0 flex items-center"><div className="w-full" style={{ borderTop: '1px solid var(--border-subtle)' }} /></div>
              <div className="relative flex justify-center text-xs"><span className="px-3 font-medium" style={{ backgroundColor: 'var(--glass-bg)', color: 'var(--text-muted)' }}>or</span></div>
            </div>

            {/* Google Sign-In */}
            {googleLoading ? (
              <div className="w-full py-3 rounded-xl flex items-center justify-center gap-2 text-sm" style={{ border: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                <Loader2 size={18} className="animate-spin" />
                <span className="font-medium">Signing in with Google...</span>
              </div>
            ) : (
              <div ref={googleBtnRef} className="w-full flex justify-center" />
            )}
            {!window.google && (
              <button
                onClick={() => setError('Google Sign-In is not configured. Please set VITE_GOOGLE_CLIENT_ID in your environment.')}
                className="w-full py-3 rounded-xl flex items-center justify-center gap-2 text-sm font-medium transition-all hover:bg-[var(--bg-hover)]"
                style={{ border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                Sign in with Google
              </button>
            )}

            {/* Register link */}
            <div className="mt-6 pt-6 text-center" style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                Don't have an account?{' '}
                <Link to="/register" className="font-medium text-brand-500 hover:text-brand-600 transition-colors">Sign up</Link>
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
