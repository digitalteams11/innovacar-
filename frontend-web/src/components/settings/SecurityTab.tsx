import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { QRCodeSVG } from 'qrcode.react';
import api, { translateApiError } from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import {
  Lock, Eye, EyeOff, ShieldCheck, Smartphone, Mail, Monitor,
  LogOut, History, AlertTriangle, CheckCircle2, XCircle, Trash2,
  Copy, RefreshCw, Key,
} from 'lucide-react';

interface PasswordData {
  current: string;
  newPassword: string;
  confirm: string;
}

interface TwoFactorStatus {
  enabled: boolean;
  method: string;
  confirmedAt?: string | null;
  hasRecoveryCodes?: boolean;
  emailOtpEnabled?: boolean;
}

interface EmailOtpStatus {
  emailOtpEnabled: boolean;
  smtpConfigured: boolean;
  emailVerified: boolean;
  maskedEmail: string;
}

interface DeviceItem {
  id: number;
  name: string;
  browser: string;
  operatingSystem: string;
  ipAddress: string;
  trusted: boolean;
  blocked: boolean;
  current: boolean;
  createdAt: string;
  lastSeenAt: string;
}

interface LoginHistoryItem {
  id: number;
  email: string;
  ipAddress: string;
  success: boolean;
  suspicious: boolean;
  failureReason: string | null;
  userAgent: string;
  createdAt: string;
}

export default function SecurityTab() {
  const { t } = useTranslation();
  const { user, profile, updateCurrentUser } = useAuth();
  const { showToast } = useToast();

  // ── Password ──────────────────────────────────────────────────────────────
  const [password, setPassword] = useState<PasswordData>({ current: '', newPassword: '', confirm: '' });
  const [passwordError, setPasswordError] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // ── 2FA setup ─────────────────────────────────────────────────────────────
  const [twoFactor, setTwoFactor] = useState<TwoFactorStatus>({ enabled: false, method: 'AUTHENTICATOR' });
  const [setupData, setSetupData] = useState<{ secret: string; provisioningUri: string } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorBusy, setTwoFactorBusy] = useState(false);
  const [setupError, setSetupError] = useState('');

  // ── Recovery codes modal ──────────────────────────────────────────────────
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [showRecoveryModal, setShowRecoveryModal] = useState(false);

  // ── Disable 2FA ───────────────────────────────────────────────────────────
  const [showDisableForm, setShowDisableForm] = useState(false);
  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [showDisablePassword, setShowDisablePassword] = useState(false);

  // ── Regenerate recovery codes ─────────────────────────────────────────────
  const [showRegenForm, setShowRegenForm] = useState(false);
  const [regenPassword, setRegenPassword] = useState('');
  const [regenCode, setRegenCode] = useState('');
  const [regenBusy, setRegenBusy] = useState(false);
  const [showRegenPassword, setShowRegenPassword] = useState(false);

  // ── Email OTP ─────────────────────────────────────────────────────────────
  const [emailOtpStatus, setEmailOtpStatus] = useState<EmailOtpStatus>({
    emailOtpEnabled: false, smtpConfigured: false, emailVerified: false, maskedEmail: '',
  });
  const [emailOtpCodeSent, setEmailOtpCodeSent] = useState(false);
  const [emailOtpCode, setEmailOtpCode] = useState('');
  const [emailOtpBusy, setEmailOtpBusy] = useState(false);
  const [emailOtpError, setEmailOtpError] = useState('');
  const [emailOtpCountdown, setEmailOtpCountdown] = useState(0);
  const [showDisableEmailOtp, setShowDisableEmailOtp] = useState(false);
  const [disableEmailOtpPassword, setDisableEmailOtpPassword] = useState('');
  const [disableEmailOtpBusy, setDisableEmailOtpBusy] = useState(false);

  // ── Verify email address (gate for Email OTP) ────────────────────────────
  const [verifyEmailOpen, setVerifyEmailOpen] = useState(false);
  const [verifyEmailCodeSent, setVerifyEmailCodeSent] = useState(false);
  const [verifyEmailCode, setVerifyEmailCode] = useState('');
  const [verifyEmailBusy, setVerifyEmailBusy] = useState(false);
  const [verifyEmailError, setVerifyEmailError] = useState('');
  const [verifyEmailCountdown, setVerifyEmailCountdown] = useState(0);

  // ── Devices & history ─────────────────────────────────────────────────────
  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [loginHistory, setLoginHistory] = useState<LoginHistoryItem[]>([]);
  const [securityLoading, setSecurityLoading] = useState(true);
  const [loggingOutAll, setLoggingOutAll] = useState(false);
  const [loggingOutOthers, setLoggingOutOthers] = useState(false);

  useEffect(() => { fetchSecurityData(); }, []);

  const fetchSecurityData = async () => {
    setSecurityLoading(true);
    try {
      const [twoFaRes, devicesRes, historyRes, emailOtpRes] = await Promise.all([
        api.get('/security-center/2fa'),
        api.get('/security-center/devices'),
        api.get('/security-center/login-history'),
        api.get('/security/email-otp/status'),
      ]);
      const twoFaData = twoFaRes.data?.data ?? twoFaRes.data;
      setTwoFactor(twoFaData);
      setDevices(devicesRes.data || []);
      setLoginHistory(historyRes.data || []);
      const emailOtpData = emailOtpRes.data?.data ?? emailOtpRes.data;
      if (emailOtpData) setEmailOtpStatus(emailOtpData);
    } catch (err) {
      console.error('Failed to load security data', err);
    } finally {
      setSecurityLoading(false);
    }
  };

  // ── Password ──────────────────────────────────────────────────────────────

  const handlePasswordChange = (field: keyof PasswordData, value: string) => {
    setPassword((prev) => ({ ...prev, [field]: value }));
    setPasswordError('');
  };

  const changePassword = async () => {
    if (!user) return;
    if (password.newPassword !== password.confirm) {
      setPasswordError('New password and confirmation do not match.');
      return;
    }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/.test(password.newPassword)) {
      setPasswordError('Use at least 10 characters with uppercase, lowercase, a number, and a symbol.');
      return;
    }
    setPasswordLoading(true);
    try {
      await api.put(`/users/${user.id}/password`, {
        currentPassword: password.current,
        newPassword: password.newPassword,
      });
      setPassword({ current: '', newPassword: '', confirm: '' });
      showToast('Password changed successfully', 'success');
    } catch (err: any) {
      setPasswordError(err?.userMessage || 'Unable to change password. Please try again later.');
    } finally {
      setPasswordLoading(false);
    }
  };

  // ── 2FA setup ─────────────────────────────────────────────────────────────

  const startAuthenticatorSetup = async () => {
    setTwoFactorBusy(true);
    setSetupError('');
    try {
      const { data } = await api.post('/security-center/2fa/authenticator/setup');
      setSetupData(data);
      setTwoFactorCode('');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to start authenticator setup.', 'error');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  const confirmAuthenticatorEnable = async () => {
    if (!setupData || twoFactorCode.trim().length !== 6) {
      setSetupError('Enter the 6-digit code from your authenticator app.');
      return;
    }
    setTwoFactorBusy(true);
    setSetupError('');
    try {
      const { data } = await api.post('/security-center/2fa/authenticator/enable', {
        secret: setupData.secret,
        code: twoFactorCode.trim(),
      });
      const codes: string[] = data?.data?.recoveryCodes ?? [];
      setRecoveryCodes(codes);
      setShowRecoveryModal(true);
      setSetupData(null);
      setTwoFactorCode('');
      fetchSecurityData();
    } catch (err: any) {
      setSetupError(err?.userMessage || 'The code is invalid or expired. Wait for a new code and try again.');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  // ── Disable ───────────────────────────────────────────────────────────────

  const disableTwoFactor = async () => {
    if (!disablePassword.trim()) {
      showToast('Enter your current password.', 'warning');
      return;
    }
    if (!disableCode.trim()) {
      showToast('Enter the 6-digit code from your authenticator app.', 'warning');
      return;
    }
    setTwoFactorBusy(true);
    try {
      await api.post('/security-center/2fa/disable', {
        password: disablePassword.trim(),
        code: disableCode.trim(),
      });
      showToast('Two-factor authentication disabled.', 'success');
      setShowDisableForm(false);
      setDisablePassword('');
      setDisableCode('');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to disable two-factor authentication. Check your password and code.', 'error');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  // ── Regenerate recovery codes ─────────────────────────────────────────────

  const regenerateRecoveryCodes = async () => {
    if (!regenPassword.trim() || !regenCode.trim()) {
      showToast('Enter your password and current authenticator code.', 'warning');
      return;
    }
    setRegenBusy(true);
    try {
      const { data } = await api.post('/security-center/2fa/recovery-codes/regenerate', {
        password: regenPassword.trim(),
        code: regenCode.trim(),
      });
      const codes: string[] = data?.data?.recoveryCodes ?? [];
      setRecoveryCodes(codes);
      setShowRecoveryModal(true);
      setShowRegenForm(false);
      setRegenPassword('');
      setRegenCode('');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to regenerate recovery codes. Check your password and code.', 'error');
    } finally {
      setRegenBusy(false);
    }
  };

  // ── Devices ───────────────────────────────────────────────────────────────

  const updateDevice = async (device: DeviceItem, action: 'trust' | 'block', value: boolean) => {
    try {
      await api.patch(`/security-center/devices/${device.id}/${action}`, { [action === 'trust' ? 'trusted' : 'blocked']: value });
      showToast('Device updated successfully', 'success');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update device.', 'error');
    }
  };

  const removeDevice = async (deviceId: number) => {
    try {
      await api.delete(`/security-center/devices/${deviceId}`);
      showToast('Device removed successfully', 'success');
      setDevices((prev) => prev.filter((d) => d.id !== deviceId));
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to remove device.', 'error');
    }
  };

  const logoutAllDevices = async () => {
    setLoggingOutAll(true);
    try {
      await api.post('/security-center/logout-all');
      showToast('Logged out from all devices successfully', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to log out from all devices.', 'error');
    } finally {
      setLoggingOutAll(false);
    }
  };

  const logoutOtherDevices = async () => {
    setLoggingOutOthers(true);
    try {
      await api.post('/security-center/logout-others');
      showToast('Signed out from all other devices', 'success');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to sign out other devices.', 'error');
    } finally {
      setLoggingOutOthers(false);
    }
  };

  const copyRecoveryCodes = () => {
    navigator.clipboard.writeText(recoveryCodes.join('\n'));
    showToast('Recovery codes copied to clipboard', 'success');
  };

  // ── Email OTP handlers ────────────────────────────────────────────────────

  const startEmailOtpCountdown = () => {
    setEmailOtpCountdown(60);
    const timer = setInterval(() => {
      setEmailOtpCountdown((prev) => {
        if (prev <= 1) { clearInterval(timer); return 0; }
        return prev - 1;
      });
    }, 1000);
  };

  const sendEmailOtpEnableCode = async () => {
    setEmailOtpBusy(true);
    setEmailOtpError('');
    try {
      await api.post('/security/email-otp/send-enable-code');
      setEmailOtpCodeSent(true);
      startEmailOtpCountdown();
      showToast('Verification code sent to your email.', 'success');
    } catch (err: any) {
      const msg = err?.userMessage || err?.response?.data?.message || 'Failed to send code.';
      setEmailOtpError(msg);
    } finally {
      setEmailOtpBusy(false);
    }
  };

  const enableEmailOtp = async () => {
    if (!emailOtpCode.trim()) { setEmailOtpError('Enter the code from your email.'); return; }
    setEmailOtpBusy(true);
    setEmailOtpError('');
    try {
      await api.post('/security/email-otp/enable', { code: emailOtpCode.trim() });
      setEmailOtpStatus((prev) => ({ ...prev, emailOtpEnabled: true }));
      setEmailOtpCodeSent(false);
      setEmailOtpCode('');
      showToast('Email OTP enabled successfully.', 'success');
    } catch (err: any) {
      setEmailOtpError(err?.userMessage || err?.response?.data?.message || 'Invalid code.');
    } finally {
      setEmailOtpBusy(false);
    }
  };

  const disableEmailOtp = async () => {
    if (!disableEmailOtpPassword.trim()) { return; }
    setDisableEmailOtpBusy(true);
    try {
      await api.post('/security/email-otp/disable', { password: disableEmailOtpPassword });
      setEmailOtpStatus((prev) => ({ ...prev, emailOtpEnabled: false }));
      setShowDisableEmailOtp(false);
      setDisableEmailOtpPassword('');
      showToast('Email OTP disabled.', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || err?.response?.data?.message || 'Incorrect password.', 'error');
    } finally {
      setDisableEmailOtpBusy(false);
    }
  };

  // ── Verify email address handlers ────────────────────────────────────────

  const startVerifyEmailCountdown = () => {
    setVerifyEmailCountdown(60);
    const timer = setInterval(() => {
      setVerifyEmailCountdown((prev) => {
        if (prev <= 1) { clearInterval(timer); return 0; }
        return prev - 1;
      });
    }, 1000);
  };

  const openVerifyEmail = () => {
    setVerifyEmailOpen(true);
    setVerifyEmailCodeSent(false);
    setVerifyEmailCode('');
    setVerifyEmailError('');
    sendVerifyEmailCode();
  };

  const sendVerifyEmailCode = async () => {
    setVerifyEmailBusy(true);
    setVerifyEmailError('');
    try {
      await api.post('/auth/send-email-verification-code');
      setVerifyEmailCodeSent(true);
      startVerifyEmailCountdown();
    } catch (err: any) {
      setVerifyEmailError(translateApiError(err, t));
    } finally {
      setVerifyEmailBusy(false);
    }
  };

  const submitVerifyEmailCode = async () => {
    if (verifyEmailCode.trim().length < 6) return;
    setVerifyEmailBusy(true);
    setVerifyEmailError('');
    try {
      await api.post('/auth/verify-email-code', { code: verifyEmailCode.trim() });
      setVerifyEmailOpen(false);
      setVerifyEmailCodeSent(false);
      setVerifyEmailCode('');
      updateCurrentUser({ emailVerified: true });
      setEmailOtpStatus((prev) => ({ ...prev, emailVerified: true }));
      showToast(t('settings.securityTab.emailVerifiedSuccess', 'Email verified successfully.'), 'success');
      fetchSecurityData();
    } catch (err: any) {
      setVerifyEmailError(translateApiError(err, t));
    } finally {
      setVerifyEmailBusy(false);
    }
  };

  const deviceDisplayName = (device: DeviceItem) =>
    [device.operatingSystem, device.browser].filter(Boolean).join(' · ') || device.name || 'Unknown device';

  const recommendations = [
    { label: 'Enable two-factor authentication', done: twoFactor.enabled },
    { label: 'Verify your email address', done: Boolean(user?.emailVerified) },
    { label: 'Keep your phone number up to date', done: Boolean(profile?.phone) },
  ];

  const inputCls = 'w-full px-3 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:border-brand-300';

  return (
    <div className="space-y-6">

      {/* ── Recovery codes modal ──────────────────────────────────────────── */}
      {showRecoveryModal && recoveryCodes.length > 0 && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center flex-shrink-0">
                <Key size={20} className="text-amber-500" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.saveRecoveryCodes')}</h3>
                <p className="text-xs text-slate-400">{t('settings.securityTab.recoveryCodesWontShowAgain')}</p>
              </div>
            </div>

            <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 space-y-3">
              <p className="text-xs text-amber-700 font-medium">
                {t('settings.securityTab.recoveryCodesWarning')}
              </p>
              <div className="grid grid-cols-2 gap-2">
                {recoveryCodes.map((code, i) => (
                  <span
                    key={i}
                    className="text-sm font-mono font-semibold bg-white border border-amber-200 rounded-lg px-3 py-1.5 text-center text-[#1e293b] tracking-widest"
                  >
                    {code}
                  </span>
                ))}
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={copyRecoveryCodes}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 border border-[#e8e6e1] rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-all"
              >
                <Copy size={14} /> {t('settings.securityTab.copyCodes')}
              </button>
              <button
                onClick={() => setShowRecoveryModal(false)}
                className="flex-1 py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium hover:bg-brand-600 transition-all"
              >
                {t('settings.securityTab.savedThem')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Change Password ───────────────────────────────────────────────── */}
      <div className="card-premium space-y-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Lock size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.changePassword')}</h3>
            <p className="text-sm text-slate-400 font-normal">{t('settings.securityTab.changePasswordDesc')}</p>
          </div>
        </div>

        {passwordError && (
          <div className="bg-danger-50/80 text-danger-500 p-4 rounded-xl text-sm font-medium flex items-center gap-3 border border-danger-100">
            <div className="w-2 h-2 bg-danger-500 rounded-full" />
            {passwordError}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
          <div className="sm:col-span-2">
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.securityTab.currentPassword')}</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type={showCurrentPassword ? 'text' : 'password'} value={password.current}
                onChange={(e) => handlePasswordChange('current', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              <button type="button" onClick={() => setShowCurrentPassword(v => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showCurrentPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.securityTab.newPassword')}</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type={showNewPassword ? 'text' : 'password'} value={password.newPassword}
                onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              <button type="button" onClick={() => setShowNewPassword(v => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showNewPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">{t('settings.securityTab.confirmPassword')}</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type={showConfirmPassword ? 'text' : 'password'} value={password.confirm}
                onChange={(e) => handlePasswordChange('confirm', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all" />
              <button type="button" onClick={() => setShowConfirmPassword(v => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
        </div>

        <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
          <button onClick={changePassword} disabled={passwordLoading}
            className="flex items-center gap-2 px-3 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all disabled:opacity-70 w-full sm:w-auto">
            {passwordLoading ? <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Lock size={16} />}
            {t('settings.securityTab.updatePassword')}
          </button>
        </div>
      </div>

      {/* ── Two-Factor Authentication ─────────────────────────────────────── */}
      <div className="card-premium space-y-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <ShieldCheck size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.twoFactorAuth')}</h3>
            <p className="text-sm text-slate-400 font-normal">{t('settings.securityTab.twoFactorAuthDesc')}</p>
          </div>
        </div>

        {/* Status row */}
        <div className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
          <div className="flex items-center gap-3">
            <Smartphone size={18} className="text-slate-400" />
            <div>
              <p className="text-sm font-semibold text-[#1e293b]">{t('settings.securityTab.authenticatorApp')}</p>
              <p className="text-xs text-slate-400">
                {twoFactor.enabled
                  ? `${t('settings.securityTab.enabledSince')}${twoFactor.confirmedAt ? ' · ' + new Date(twoFactor.confirmedAt).toLocaleDateString() : ''}`
                  : t('settings.securityTab.notEnabled')}
              </p>
            </div>
          </div>
          {twoFactor.enabled ? (
            <button onClick={() => { setShowDisableForm(v => !v); setShowRegenForm(false); }}
              className="text-xs font-medium text-rose-600 hover:text-rose-700">
              {t('settings.securityTab.disable')}
            </button>
          ) : (
            <button onClick={startAuthenticatorSetup} disabled={twoFactorBusy}
              className="text-xs font-medium text-brand-600 hover:text-brand-700 disabled:opacity-50">
              {t('settings.securityTab.setUp')}
            </button>
          )}
        </div>

        {/* Setup panel — only shown when not yet enabled */}
        {setupData && !twoFactor.enabled && (
          <div className="p-4 bg-brand-50/40 border border-brand-100 rounded-xl space-y-4">
            <p className="text-sm font-medium text-[#1e293b]">
              {t('settings.securityTab.scanQrHint')}
            </p>

            <div className="flex justify-center p-4 bg-white border border-[#e8e6e1] rounded-xl">
              <QRCodeSVG value={setupData.provisioningUri} size={180} level="M" />
            </div>

            <div>
              <p className="text-xs text-slate-400 mb-1">{t('settings.securityTab.cantScanHint')}</p>
              <div className="flex items-center gap-2">
                <span className="flex-1 text-xs font-mono break-all bg-white border border-[#e8e6e1] rounded-lg p-2 text-[#1e293b]">
                  {setupData.secret}
                </span>
                <button onClick={() => { navigator.clipboard.writeText(setupData.secret); showToast(t('settings.securityTab.secretCopied'), 'success'); }}
                  className="p-2 text-slate-400 hover:text-brand-500" title={t('settings.securityTab.copySecret')}>
                  <Copy size={14} />
                </button>
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">
                {t('settings.securityTab.enterAuthenticatorCodeHint')}
              </label>
              <input
                type="text"
                inputMode="numeric"
                value={twoFactorCode}
                onChange={(e) => { setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6)); setSetupError(''); }}
                placeholder="000000"
                maxLength={6}
                className={inputCls}
              />
              {setupError && (
                <p className="mt-1.5 text-xs text-rose-500">{setupError}</p>
              )}
            </div>

            <button
              onClick={confirmAuthenticatorEnable}
              disabled={twoFactorBusy || twoFactorCode.length !== 6}
              className="w-full py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium hover:bg-brand-600 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {twoFactorBusy ? t('settings.securityTab.verifying') : t('settings.securityTab.confirmEnable')}
            </button>
          </div>
        )}

        {/* Recovery codes section — only when enabled */}
        {twoFactor.enabled && (
          <div className="p-3 border border-[#e8e6e1] rounded-xl space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Key size={15} className="text-slate-400" />
                <div>
                  <p className="text-sm font-semibold text-[#1e293b]">{t('settings.securityTab.recoveryCodes')}</p>
                  <p className="text-xs text-slate-400">
                    {twoFactor.hasRecoveryCodes ? t('settings.securityTab.activeUseIfLost') : t('settings.securityTab.noCodesRemaining')}
                  </p>
                </div>
              </div>
              <button onClick={() => { setShowRegenForm(v => !v); setShowDisableForm(false); }}
                className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700">
                <RefreshCw size={12} /> {t('settings.securityTab.regenerate')}
              </button>
            </div>

            {showRegenForm && (
              <div className="pt-3 border-t border-[#e8e6e1] space-y-3">
                <p className="text-xs text-slate-500">{t('settings.securityTab.verifyIdentityRegen')}</p>
                <div className="relative">
                  <Lock size={14} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type={showRegenPassword ? 'text' : 'password'}
                    value={regenPassword}
                    onChange={(e) => setRegenPassword(e.target.value)}
                    placeholder={t('settings.securityTab.currentPasswordPlaceholder')}
                    className="w-full ps-9 pe-9 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100"
                  />
                  <button type="button" onClick={() => setShowRegenPassword(v => !v)} className="absolute inset-y-0 end-0 pe-3 flex items-center text-slate-400" tabIndex={-1}>
                    {showRegenPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
                <input
                  type="text"
                  inputMode="numeric"
                  value={regenCode}
                  onChange={(e) => setRegenCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder={t('settings.securityTab.sixDigitAuthenticatorCode')}
                  maxLength={6}
                  className={inputCls}
                />
                <button onClick={regenerateRecoveryCodes} disabled={regenBusy || !regenPassword || regenCode.length !== 6}
                  className="w-full py-2.5 bg-brand-500 text-white rounded-xl text-sm font-medium hover:bg-brand-600 transition-all disabled:opacity-50">
                  {regenBusy ? t('settings.securityTab.regenerating') : t('settings.securityTab.generateNewCodes')}
                </button>
              </div>
            )}
          </div>
        )}

        {/* Disable form */}
        {showDisableForm && twoFactor.enabled && (
          <div className="p-4 bg-rose-50/40 border border-rose-100 rounded-xl space-y-3">
            <p className="text-sm font-medium text-[#1e293b]">
              {t('settings.securityTab.disableTwoFactorHint')}
            </p>
            <div className="relative">
              <Lock size={14} className="absolute start-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type={showDisablePassword ? 'text' : 'password'}
                value={disablePassword}
                onChange={(e) => setDisablePassword(e.target.value)}
                placeholder={t('settings.securityTab.currentPasswordPlaceholder')}
                className="w-full ps-9 pe-9 py-2.5 bg-white border border-rose-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-rose-100"
              />
              <button type="button" onClick={() => setShowDisablePassword(v => !v)} className="absolute inset-y-0 end-0 pe-3 flex items-center text-slate-400" tabIndex={-1}>
                {showDisablePassword ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
            <input
              type="text"
              inputMode="numeric"
              value={disableCode}
              onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder={t('settings.securityTab.sixDigitAuthenticatorCode')}
              maxLength={6}
              className="w-full px-3 py-2.5 bg-white border border-rose-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-rose-100"
            />
            <button onClick={disableTwoFactor} disabled={twoFactorBusy || !disablePassword || disableCode.length !== 6}
              className="w-full py-2.5 bg-rose-500 text-white rounded-xl text-sm font-medium hover:bg-rose-600 transition-all disabled:opacity-50">
              {twoFactorBusy ? t('settings.securityTab.disabling') : t('settings.securityTab.confirmDisable')}
            </button>
          </div>
        )}

        {/* Email OTP */}
        <div className="p-3 border border-[#e8e6e1] rounded-xl space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Mail size={18} className={emailOtpStatus.emailOtpEnabled ? 'text-green-500' : 'text-slate-400'} />
              <div>
                <p className="text-sm font-semibold text-[#1e293b]">{t('settings.securityTab.emailOtp')}</p>
                <p className="text-xs text-slate-400">
                  {emailOtpStatus.emailOtpEnabled
                    ? t('settings.securityTab.activeCodesSentTo', { email: emailOtpStatus.maskedEmail })
                    : t('settings.securityTab.receiveOtpHint')}
                </p>
              </div>
            </div>
            {emailOtpStatus.emailOtpEnabled ? (
              <span className="text-xs font-medium text-green-600 bg-green-50 px-2 py-0.5 rounded-full">{t('settings.securityTab.enabled')}</span>
            ) : !emailOtpStatus.smtpConfigured ? (
              <span className="text-xs font-medium text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">{t('settings.securityTab.smtpNotConfigured')}</span>
            ) : !emailOtpStatus.emailVerified ? (
              <span className="text-xs font-medium text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">{t('settings.securityTab.emailUnverified')}</span>
            ) : null}
          </div>

          {/* SMTP not configured */}
          {!emailOtpStatus.smtpConfigured && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              {t('settings.securityTab.smtpNotConfiguredHint')}
            </p>
          )}

          {/* Email unverified — actionable verify flow */}
          {emailOtpStatus.smtpConfigured && !emailOtpStatus.emailVerified && (
            <div className="space-y-2">
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                {t('settings.securityTab.verifyEmailHint')}
              </p>
              {!verifyEmailOpen ? (
                <button
                  onClick={openVerifyEmail}
                  disabled={verifyEmailBusy}
                  className="text-xs px-3 py-1.5 bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50"
                >
                  {verifyEmailBusy ? t('settings.securityTab.sending') : t('settings.securityTab.verifyEmailAddress')}
                </button>
              ) : (
                <div className="space-y-2">
                  {verifyEmailCodeSent && (
                    <p className="text-xs text-slate-600">
                      {t('settings.securityTab.enterCodeSentToColon', { email: emailOtpStatus.maskedEmail })}
                    </p>
                  )}
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={verifyEmailCode}
                    onChange={(e) => { setVerifyEmailCode(e.target.value.replace(/\D/g, '')); setVerifyEmailError(''); }}
                    placeholder="000000"
                    className={`${inputCls} tracking-widest text-center text-lg font-mono`}
                  />
                  {verifyEmailError && <p className="text-xs text-red-600">{verifyEmailError}</p>}
                  <div className="flex items-center gap-3">
                    <button
                      onClick={submitVerifyEmailCode}
                      disabled={verifyEmailBusy || verifyEmailCode.length < 6}
                      className="px-3 py-1.5 text-xs bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50"
                    >
                      {verifyEmailBusy ? t('settings.securityTab.verifying') : t('settings.securityTab.verify')}
                    </button>
                    <button
                      onClick={sendVerifyEmailCode}
                      disabled={verifyEmailBusy || verifyEmailCountdown > 0}
                      className="text-xs text-brand-600 hover:underline disabled:opacity-40"
                    >
                      {verifyEmailCountdown > 0 ? t('settings.securityTab.resendIn', { seconds: verifyEmailCountdown }) : t('settings.securityTab.resendCode')}
                    </button>
                    <button
                      onClick={() => { setVerifyEmailOpen(false); setVerifyEmailCode(''); setVerifyEmailError(''); }}
                      className="text-xs text-slate-500 hover:underline"
                    >
                      {t('settings.securityTab.cancel')}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Enabled — show disable option */}
          {emailOtpStatus.emailOtpEnabled && (
            <>
              {!showDisableEmailOtp ? (
                <button
                  onClick={() => setShowDisableEmailOtp(true)}
                  className="text-xs text-red-600 hover:underline"
                >
                  {t('settings.securityTab.disableEmailOtp')}
                </button>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs text-slate-500">{t('settings.securityTab.enterPasswordDisableOtp')}</p>
                  <input
                    type="password"
                    value={disableEmailOtpPassword}
                    onChange={(e) => setDisableEmailOtpPassword(e.target.value)}
                    placeholder={t('settings.securityTab.currentPasswordPlaceholder')}
                    className={inputCls}
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={disableEmailOtp}
                      disabled={disableEmailOtpBusy || !disableEmailOtpPassword.trim()}
                      className="px-3 py-1.5 text-xs bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
                    >
                      {disableEmailOtpBusy ? t('settings.securityTab.disabling') : t('settings.securityTab.confirmDisable')}
                    </button>
                    <button
                      onClick={() => { setShowDisableEmailOtp(false); setDisableEmailOtpPassword(''); }}
                      className="px-3 py-1.5 text-xs border border-[#e8e6e1] rounded-lg hover:bg-slate-50"
                    >
                      {t('settings.securityTab.cancel')}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}

          {/* Not enabled, SMTP ok, email verified — show enable flow */}
          {!emailOtpStatus.emailOtpEnabled && emailOtpStatus.smtpConfigured && emailOtpStatus.emailVerified && (
            <>
              {!emailOtpCodeSent ? (
                <button
                  onClick={sendEmailOtpEnableCode}
                  disabled={emailOtpBusy}
                  className="text-xs px-3 py-1.5 bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50"
                >
                  {emailOtpBusy ? t('settings.securityTab.sending') : t('settings.securityTab.sendVerificationCode')}
                </button>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs text-slate-600">
                    {t('settings.securityTab.enterCodeSentToColon', { email: emailOtpStatus.maskedEmail })}
                  </p>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={emailOtpCode}
                    onChange={(e) => { setEmailOtpCode(e.target.value.replace(/\D/g, '')); setEmailOtpError(''); }}
                    placeholder="000000"
                    className={`${inputCls} tracking-widest text-center text-lg font-mono`}
                  />
                  {emailOtpError && <p className="text-xs text-red-600">{emailOtpError}</p>}
                  <div className="flex items-center gap-3">
                    <button
                      onClick={enableEmailOtp}
                      disabled={emailOtpBusy || emailOtpCode.length < 6}
                      className="px-3 py-1.5 text-xs bg-brand-600 text-white rounded-lg hover:bg-brand-700 disabled:opacity-50"
                    >
                      {emailOtpBusy ? t('settings.securityTab.verifying') : t('settings.securityTab.verifyAndEnable')}
                    </button>
                    <button
                      onClick={sendEmailOtpEnableCode}
                      disabled={emailOtpBusy || emailOtpCountdown > 0}
                      className="text-xs text-brand-600 hover:underline disabled:opacity-40"
                    >
                      {emailOtpCountdown > 0 ? t('settings.securityTab.resendIn', { seconds: emailOtpCountdown }) : t('settings.securityTab.resendCode')}
                    </button>
                  </div>
                </div>
              )}
              {emailOtpError && !emailOtpCodeSent && <p className="text-xs text-red-600">{emailOtpError}</p>}
            </>
          )}
        </div>
      </div>

      {/* ── Active Devices ────────────────────────────────────────────────── */}
      <div className="card-premium space-y-5">
        <div className="flex items-center justify-between pb-5 border-b border-[#e8e6e1]/60">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Monitor size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.activeDevices')}</h3>
              <p className="text-sm text-slate-400 font-normal">{t('settings.securityTab.activeDevicesDesc')}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={logoutOtherDevices} disabled={loggingOutOthers}
              className="flex items-center gap-2 px-3 py-2 text-slate-600 bg-slate-50 rounded-xl text-xs font-medium hover:bg-slate-100 transition-all disabled:opacity-50">
              <LogOut size={14} />
              {loggingOutOthers ? t('settings.securityTab.signingOut') : t('settings.securityTab.signOutOthers')}
            </button>
            <button onClick={logoutAllDevices} disabled={loggingOutAll}
              className="flex items-center gap-2 px-3 py-2 text-rose-600 bg-rose-50 rounded-xl text-xs font-medium hover:bg-rose-100 transition-all disabled:opacity-50">
              <LogOut size={14} />
              {loggingOutAll ? t('settings.securityTab.signingOut') : t('settings.securityTab.signOutAll')}
            </button>
          </div>
        </div>

        {securityLoading ? (
          <div className="text-center py-6 text-sm text-slate-400">{t('settings.securityTab.loading')}</div>
        ) : devices.length === 0 ? (
          <div className="text-center py-6 text-sm text-slate-400">{t('settings.securityTab.noActiveDevices')}</div>
        ) : (
          <div className="space-y-2">
            {devices.map((device) => (
              <div key={device.id} className={`flex items-center justify-between p-3 border rounded-xl ${device.current ? 'border-brand-200 bg-brand-50/30' : 'border-[#e8e6e1]'}`}>
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold text-[#1e293b]">{deviceDisplayName(device)}</p>
                    {device.current && (
                      <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-md bg-brand-100 text-brand-600">{t('settings.securityTab.thisDevice')}</span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400">{device.ipAddress} · last seen {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : '—'}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={() => updateDevice(device, 'trust', !device.trusted)}
                    className={`text-xs font-medium px-2.5 py-1 rounded-lg ${device.trusted ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-50 text-slate-500'}`}>
                    {device.trusted ? t('settings.securityTab.trusted') : t('settings.securityTab.trust')}
                  </button>
                  {!device.current && (
                    <button onClick={() => removeDevice(device.id)} className="text-slate-400 hover:text-rose-500 p-1.5" title={t('settings.securityTab.removeDevice')}>
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Login History ─────────────────────────────────────────────────── */}
      <div className="card-premium space-y-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <History size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.loginHistory')}</h3>
            <p className="text-sm text-slate-400 font-normal">{t('settings.securityTab.loginHistoryDesc')}</p>
          </div>
        </div>

        {securityLoading ? (
          <div className="text-center py-6 text-sm text-slate-400">{t('settings.securityTab.loading')}</div>
        ) : loginHistory.length === 0 ? (
          <div className="text-center py-6 text-sm text-slate-400">{t('settings.securityTab.noLoginHistory')}</div>
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {loginHistory.slice(0, 20).map((item) => (
              <div key={item.id} className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
                <div className="flex items-center gap-2.5">
                  {item.success ? <CheckCircle2 size={16} className="text-emerald-500" /> : <XCircle size={16} className="text-rose-500" />}
                  <div>
                    <p className="text-sm font-medium text-[#1e293b]">{item.ipAddress || t('settings.securityTab.unknownIp')}</p>
                    <p className="text-xs text-slate-400">{item.createdAt ? new Date(item.createdAt).toLocaleString() : '—'}</p>
                  </div>
                </div>
                {item.suspicious && (
                  <span className="text-xs font-medium text-amber-600 flex items-center gap-1">
                    <AlertTriangle size={12} /> {t('settings.securityTab.suspicious')}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Security Recommendations ──────────────────────────────────────── */}
      <div className="card-premium space-y-3">
        <h3 className="text-base font-bold text-[#1e293b]">{t('settings.securityTab.securityRecommendations')}</h3>
        <div className="space-y-2">
          {recommendations.map((rec) => (
            <div key={rec.label} className="flex items-center gap-3 p-3 border border-[#e8e6e1] rounded-xl">
              {rec.done ? <CheckCircle2 size={16} className="text-emerald-500" /> : <AlertTriangle size={16} className="text-amber-500" />}
              <span className="text-sm text-[#1e293b]">{rec.label}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
