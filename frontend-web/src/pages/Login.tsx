import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogIn, Lock, Mail, Car, Loader2, ShieldCheck, Smartphone, Eye, EyeOff } from 'lucide-react';
import api from '../api/axios';

declare global {
  interface Window {
    google?: any;
  }
}

export default function Login() {
  const [mode, setMode] = useState<'email' | 'phone'>('email');

  // Email login state
  const [email, setEmail] = useState('admin@test.com');
  const [password, setPassword] = useState('admin123');
  const [loading, setLoading] = useState(false);

  // Phone login state
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [phoneStep, setPhoneStep] = useState<'input' | 'otp'>('input');
  const [phoneLoading, setPhoneLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [showPassword, setShowPassword] = useState(false);

  // Shared
  const [error, setError] = useState('');
  const [googleLoading, setGoogleLoading] = useState(false);
  const { login, googleLogin } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const googleBtnRef = useRef<HTMLDivElement>(null);

  // Countdown timer for OTP resend
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown(c => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  // Load Google Identity Services script (polls until ready)
  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
    if (!clientId) return;

    let attempts = 0;
    const maxAttempts = 50; // ~5 seconds

    const initGoogle = () => {
      if (!window.google) {
        attempts++;
        if (attempts < maxAttempts) {
          setTimeout(initGoogle, 100);
        }
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
            theme: 'outline',
            size: 'large',
            width: '100%',
            text: 'signin_with',
            shape: 'rectangular',
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
      navigate(userData.role === 'SUPER_ADMIN' ? '/super-admin' : '/');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Google login failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const userData = await login({ email, password });
      navigate(userData.role === 'SUPER_ADMIN' ? '/super-admin' : '/');
    } catch (err: any) {
      if (!err.response) {
        setError('Server is not running. Please start the backend (mvn spring-boot:run in server folder).');
      } else if (err.response.status === 401 || err.response.status === 403) {
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
        phoneNumber: phone.trim(),
        otpCode: otp.trim(),
      });
      // Store auth data manually since phone login bypasses AuthContext.login
      localStorage.setItem('token', data.accessToken);
      if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
      localStorage.setItem('user', JSON.stringify(data));
      if (data.expiresIn) {
        localStorage.setItem('tokenExpiry', (Date.now() + data.expiresIn * 1000).toString());
      }
      window.location.href = data.role === 'SUPER_ADMIN' ? '/#/super-admin' : '/#/'; // Force reload to refresh auth context
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
            <h1 className="text-lg sm:text-xl font-bold text-[#1e293b] mb-1.5">{t('login.welcomeBack')}</h1>
            <p className="text-slate-500 font-normal text-xs sm:text-sm">{t('login.enterDetails')}</p>
          </div>

          {error && (
            <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border border-danger-100">
              <div className="w-2 h-2 bg-danger-500 rounded-full"></div>
              {error}
            </div>
          )}

          {/* Mode Toggle */}
          <div className="flex p-1 bg-[#f5f5f0] rounded-xl mb-6">
            <button
              onClick={() => { setMode('email'); setError(''); }}
              className={`flex-1 py-2.5 text-sm font-medium rounded-lg transition-all flex items-center justify-center gap-2 ${
                mode === 'email' ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-[#1e293b]'
              }`}
            >
              <Mail size={16} />
              Email
            </button>
            <button
              onClick={() => { setMode('phone'); setError(''); }}
              className={`flex-1 py-2.5 text-sm font-medium rounded-lg transition-all flex items-center justify-center gap-2 ${
                mode === 'phone' ? 'bg-white text-brand-500 shadow-sm' : 'text-slate-500 hover:text-[#1e293b]'
              }`}
            >
              <Smartphone size={16} />
              Phone
            </button>
          </div>

          {mode === 'email' ? (
            <form onSubmit={handleEmailSubmit} className="space-y-5">
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
                  <Link to="/forgot-password" className="text-sm font-medium text-brand-500 hover:text-brand-600">{t('login.forgot')}</Link>
                </div>
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
          ) : (
            <>
              {phoneStep === 'input' ? (
                <form onSubmit={handleSendOtp} className="space-y-5">
                  <div>
                    <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">Phone Number</label>
                    <div className="relative group">
                      <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-brand-500 transition-colors">
                        <Smartphone size={18} />
                      </div>
                      <input
                        type="tel"
                        value={phone}
                        onChange={e => setPhone(e.target.value)}
                        className="block w-full pl-11 pr-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
                        placeholder="+212612345678"
                        required
                      />
                    </div>
                    <p className="text-xs text-slate-400 mt-1.5 ml-1">Enter with country code (e.g. +212...)</p>
                  </div>

                  <button
                    type="submit"
                    disabled={phoneLoading}
                    className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                  >
                    {phoneLoading ? (
                      <Loader2 size={20} className="animate-spin" />
                    ) : (
                      <span>Send OTP</span>
                    )}
                  </button>
                </form>
              ) : (
                <form onSubmit={handleVerifyOtp} className="space-y-5">
                  <div className="text-center mb-2">
                    <p className="text-sm text-slate-500">Code sent to <span className="font-medium text-[#1e293b]">{phone}</span></p>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-[#1e293b] mb-2 ml-1">Verification Code</label>
                    <input
                      type="text"
                      value={otp}
                      onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      className="block w-full px-4 py-3 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-[#1e293b] font-normal placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-100 focus:bg-white focus:border-brand-300 transition-all text-center text-lg tracking-[0.5em]"
                      placeholder="000000"
                      required
                      maxLength={6}
                      inputMode="numeric"
                      autoFocus
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={phoneLoading}
                    className="w-full py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 hover:shadow-lg hover:shadow-brand-500/15 active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-70"
                  >
                    {phoneLoading ? (
                      <Loader2 size={20} className="animate-spin" />
                    ) : (
                      <span>Verify & Sign In</span>
                    )}
                  </button>

                  <div className="flex items-center justify-between text-sm">
                    <button
                      type="button"
                      onClick={() => { setPhoneStep('input'); setOtp(''); setError(''); }}
                      className="text-slate-500 hover:text-[#1e293b] transition-colors"
                    >
                      Change number
                    </button>
                    {countdown > 0 ? (
                      <span className="text-slate-400">Resend in {countdown}s</span>
                    ) : (
                      <button
                        type="button"
                        onClick={handleResendOtp}
                        className="text-brand-500 hover:text-brand-600 font-medium transition-colors"
                      >
                        Resend OTP
                      </button>
                    )}
                  </div>
                </form>
              )}
            </>
          )}

          {/* Divider */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-[#e8e6e1]"></div>
            </div>
            <div className="relative flex justify-center text-xs">
              <span className="px-3 bg-white text-slate-400 font-medium">or</span>
            </div>
          </div>

          {/* Google Sign-In */}
          {googleLoading ? (
            <div className="w-full py-3 border border-[#e8e6e1] rounded-xl flex items-center justify-center gap-2 text-slate-500">
              <Loader2 size={18} className="animate-spin" />
              <span className="text-sm font-medium">Signing in with Google...</span>
            </div>
          ) : (
            <div ref={googleBtnRef} className="w-full flex justify-center"></div>
          )}

          {/* Fallback Google button if script not loaded */}
          {!window.google && (
            <button
              onClick={() => setError('Google Sign-In is not configured. Please set VITE_GOOGLE_CLIENT_ID in your environment.')}
              className="w-full py-3 border border-[#e8e6e1] rounded-xl flex items-center justify-center gap-2 text-[#1e293b] hover:bg-[#f5f5f0] transition-all"
            >
              <svg width="18" height="18" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
              <span className="text-sm font-medium">Sign in with Google</span>
            </button>
          )}

          {/* Register link */}
          <div className="mt-6 pt-6 border-t border-[#e8e6e1] text-center">
            <p className="text-sm text-slate-500">
              Don't have an account?{' '}
              <Link to="/register" className="font-medium text-brand-500 hover:text-brand-600 transition-colors">
                Sign up
              </Link>
            </p>
          </div>
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
