import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import SeoHead from '../components/seo/SeoHead';
import { ROBOTS_PUBLIC_NOINDEX } from '../components/seo/robotsPresets';
import { motion } from 'framer-motion';
import { UserPlus, Mail, Lock, Loader2, ArrowLeft, Eye, EyeOff, Check, X } from 'lucide-react';
import { checkPasswordStrength, isPasswordStrong } from '../lib/passwordPolicy';
import AuthLogo from '../components/auth/AuthLogo';

declare global {
  interface Window { google?: any; }
}

/* ============================================
   ANIMATED BACKGROUND
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
    </div>
  );
}

/* ============================================
   GLASS INPUT
   ============================================ */
function GlassInput({
  icon: Icon, type = 'text', value, onChange, placeholder, required, rightElement, className, minLength,
}: {
  icon?: React.ElementType; type?: string; value: string; onChange: (v: string) => void;
  placeholder: string; required?: boolean; rightElement?: React.ReactNode; className?: string; minLength?: number;
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
        type={type} value={value} onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder} required={required} minLength={minLength}
        className="flex-1 bg-transparent border-none outline-none py-3.5 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)]"
        onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
      />
      {rightElement && <div className="pr-3">{rightElement}</div>}
    </div>
  );
}

/* ============================================
   PASSWORD STRENGTH INDICATOR
   ============================================ */
function PasswordStrength({ password }: { password: string }) {
  const { t } = useTranslation();
  const strength = checkPasswordStrength(password);
  const checks = [
    { label: t('forgotPassword.req8chars', 'At least 8 characters'), pass: strength.len },
    { label: t('forgotPassword.reqUpper', 'One uppercase letter'), pass: strength.upper },
    { label: t('forgotPassword.reqLower', 'One lowercase letter'), pass: strength.lower },
    { label: t('forgotPassword.reqDigit', 'One number'), pass: strength.digit },
    { label: t('forgotPassword.reqSymbol', 'One special character'), pass: strength.sym },
  ];
  const score = checks.filter(c => c.pass).length;

  return (
    <div className="space-y-1.5 mt-2">
      <div className="flex gap-1">
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            className="h-1 flex-1 rounded-full transition-all duration-300"
            style={{
              backgroundColor: i <= score
                ? score <= 2 ? '#f59e0b' : score === 3 ? '#3b82f6' : '#10b981'
                : 'var(--border-subtle)',
            }}
          />
        ))}
      </div>
      <div className="flex flex-wrap gap-x-3 gap-y-1">
        {checks.map((check) => (
          <span key={check.label} className={`flex items-center gap-1 text-[10px] font-medium ${check.pass ? 'text-emerald-500' : 'text-[var(--text-muted)]'}`}>
            {check.pass ? <Check size={10} /> : <X size={10} />}
            {check.label}
          </span>
        ))}
      </div>
    </div>
  );
}

/* ============================================
   MAIN REGISTER COMPONENT
   ============================================ */
export default function Register() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const { register, googleLogin } = useAuth();
  const { showToast } = useToast();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const googleBtnRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
    if (!clientId) return;
    let attempts = 0;
    const initGoogle = () => {
      if (!window.google) {
        if (attempts++ < 50) setTimeout(initGoogle, 100);
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
            text: 'signup_with', shape: 'rectangular',
          });
        }
      } catch (e) {
        console.warn('Google Sign-In init failed:', e);
      }
    };
    initGoogle();
  }, []);

  const handleGoogleCredentialResponse = async (response: any) => {
    setGoogleLoading(true);
    setError('');
    try {
      const userData = await googleLogin(response.credential);
      if (userData.role === 'SUPER_ADMIN') navigate('/super-admin/dashboard');
      else if (userData.role === 'EMPLOYEE') navigate('/employee/dashboard');
      else if (userData.role === 'ACCOUNTANT') navigate('/payments');
      else navigate('/dashboard');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Google sign-in failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    if (password !== confirmPassword) {
      setError(t('register.passwordMismatch', 'Passwords do not match'));
      setLoading(false);
      return;
    }
    if (!isPasswordStrong(password)) {
      setError(t('errors.strongPassword', 'Use at least 8 characters with uppercase, lowercase, a number, and a symbol.'));
      setLoading(false);
      return;
    }

    try {
      await register({ email, password, firstName, lastName });
      showToast(t('register.success', 'Account created successfully!'), 'success');
      navigate('/');
    } catch (err: any) {
      const msg = err?.response?.data?.message || t('register.failed', 'Registration failed. Please try again.');
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 relative overflow-hidden" style={{ backgroundColor: 'var(--bg-page)' }}>
      <SeoHead
        title="Create Account"
        description="Create your Innovacar account to start managing your car rental agency's fleet, reservations and contracts."
        canonical="https://innovacar.app/#/register"
        robots={ROBOTS_PUBLIC_NOINDEX}
      />
      <AnimatedBackground />

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
        className="w-full max-w-[460px] relative z-10"
      >
        {/* Logo */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2, duration: 0.5 }}
        >
          <AuthLogo />
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
          <div className="absolute inset-0 bg-gradient-to-br from-accent-400/5 via-transparent to-brand-500/5 pointer-events-none" />

          <div className="relative z-10">
            <div className="text-center mb-8">
              <h1 className="text-xl font-bold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('register.title', 'Create Account')}</h1>
              <p className="text-sm font-normal" style={{ color: 'var(--text-muted)' }}>{t('register.subtitle', 'Join your team on RentCar SaaS')}</p>
            </div>

            {/* Error */}
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                className="rounded-xl mb-6 text-sm font-medium flex items-center gap-3 border overflow-hidden"
                style={{ backgroundColor: 'rgba(239, 68, 68, 0.08)', color: '#ef4444', borderColor: 'rgba(239, 68, 68, 0.15)', padding: '14px 16px' }}
              >
                <div className="w-2 h-2 bg-rose-500 rounded-full shrink-0" />
                {error}
              </motion.div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('register.firstName', 'First Name')}</label>
                  <GlassInput value={firstName} onChange={setFirstName} placeholder="John" required />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('register.lastName', 'Last Name')}</label>
                  <GlassInput value={lastName} onChange={setLastName} placeholder="Doe" required />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('login.email', 'Email Address')}</label>
                <GlassInput icon={Mail} type="email" value={email} onChange={setEmail} placeholder={t('login.emailPlaceholder', 'name@company.com')} required />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('login.password', 'Password')}</label>
                <GlassInput
                  icon={Lock}
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={setPassword}
                  placeholder="••••••••"
                  required
                  minLength={8}
                  rightElement={
                    <button type="button" onClick={() => setShowPassword(v => !v)} className="text-[var(--text-muted)] hover:text-brand-500 transition-colors" tabIndex={-1}>
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  }
                />
                {password && <PasswordStrength password={password} />}
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 ml-1" style={{ color: 'var(--text-primary)' }}>{t('register.confirmPassword', 'Confirm Password')}</label>
                <GlassInput
                  icon={Lock}
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={setConfirmPassword}
                  placeholder="••••••••"
                  required
                  rightElement={
                    <button type="button" onClick={() => setShowConfirmPassword(v => !v)} className="text-[var(--text-muted)] hover:text-brand-500 transition-colors" tabIndex={-1}>
                      {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  }
                />
              </div>

              <motion.button
                type="submit"
                disabled={loading}
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.98 }}
                className="w-full py-3.5 rounded-xl font-semibold text-sm transition-all flex items-center justify-center gap-3 disabled:opacity-70 mt-2"
                style={{
                  background: 'linear-gradient(135deg, var(--brand-primary), #2a4a73)',
                  color: 'var(--brand-primary-foreground, #fff)',
                  boxShadow: '0 4px 16px -4px rgba(30, 58, 95, 0.3)',
                }}
              >
                {loading ? <Loader2 size={20} className="animate-spin" /> : <><span>{t('register.createAccount', 'Create Account')}</span><UserPlus size={16} strokeWidth={2.5} /></>}
              </motion.button>
            </form>

            {/* ── Google Sign-In ─────────────────────────────────── */}
            {import.meta.env.VITE_GOOGLE_CLIENT_ID && (
              <div className="mt-5">
                <div className="flex items-center gap-3 mb-4">
                  <div className="flex-1 h-px" style={{ backgroundColor: 'var(--border-subtle)' }} />
                  <span className="text-xs font-medium" style={{ color: 'var(--text-muted)' }}>{t('login.or')}</span>
                  <div className="flex-1 h-px" style={{ backgroundColor: 'var(--border-subtle)' }} />
                </div>
                {googleLoading ? (
                  <div className="flex items-center justify-center gap-2 py-3 text-sm" style={{ color: 'var(--text-muted)' }}>
                    <Loader2 size={18} className="animate-spin" />
                    <span className="font-medium">{t('login.signingInWithGoogle')}</span>
                  </div>
                ) : (
                  <div ref={googleBtnRef} className="w-full flex justify-center" />
                )}
              </div>
            )}

            <div className="mt-6 pt-6 text-center" style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-brand-500 hover:text-brand-600 transition-colors">
                <ArrowLeft size={16} />
                {t('register.backToLogin', 'Already have an account? Sign in')}
              </Link>
            </div>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}
