import { useEffect, useState } from 'react';
import api from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import {
  Lock, Eye, EyeOff, ShieldCheck, Smartphone, Mail, Monitor,
  LogOut, History, AlertTriangle, CheckCircle2, XCircle, Trash2,
} from 'lucide-react';

interface PasswordData {
  current: string;
  newPassword: string;
  confirm: string;
}

interface TwoFactorStatus {
  enabled: boolean;
  method: string;
}

interface DeviceItem {
  id: number;
  name: string;
  browser: string;
  operatingSystem: string;
  ipAddress: string;
  trusted: boolean;
  blocked: boolean;
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
  const { user, profile } = useAuth();
  const { showToast } = useToast();

  const [password, setPassword] = useState<PasswordData>({ current: '', newPassword: '', confirm: '' });
  const [passwordError, setPasswordError] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [twoFactor, setTwoFactor] = useState<TwoFactorStatus>({ enabled: false, method: 'AUTHENTICATOR' });
  const [setupData, setSetupData] = useState<{ secret: string; provisioningUri: string } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [twoFactorBusy, setTwoFactorBusy] = useState(false);
  const [disableCode, setDisableCode] = useState('');
  const [showDisableForm, setShowDisableForm] = useState(false);

  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [loginHistory, setLoginHistory] = useState<LoginHistoryItem[]>([]);
  const [securityLoading, setSecurityLoading] = useState(true);
  const [loggingOutAll, setLoggingOutAll] = useState(false);

  useEffect(() => {
    fetchSecurityData();
  }, []);

  const fetchSecurityData = async () => {
    setSecurityLoading(true);
    try {
      const [twoFaRes, devicesRes, historyRes] = await Promise.all([
        api.get('/security-center/2fa'),
        api.get('/security-center/devices'),
        api.get('/security-center/login-history'),
      ]);
      setTwoFactor(twoFaRes.data);
      setDevices(devicesRes.data || []);
      setLoginHistory(historyRes.data || []);
    } catch (err) {
      console.error('Failed to load security data', err);
    } finally {
      setSecurityLoading(false);
    }
  };

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

  const startAuthenticatorSetup = async () => {
    setTwoFactorBusy(true);
    try {
      const { data } = await api.post('/security-center/2fa/authenticator/setup');
      setSetupData(data);
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to start authenticator setup. Please try again later.', 'error');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  const confirmAuthenticatorEnable = async () => {
    if (!setupData || !twoFactorCode.trim()) {
      showToast('Enter the 6-digit code from your authenticator app.', 'warning');
      return;
    }
    setTwoFactorBusy(true);
    try {
      await api.post('/security-center/2fa/authenticator/enable', {
        secret: setupData.secret,
        code: twoFactorCode.trim(),
      });
      showToast('Two-factor authentication enabled.', 'success');
      setSetupData(null);
      setTwoFactorCode('');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Invalid code. Please try again.', 'error');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  const disableTwoFactor = async () => {
    if (!disableCode.trim()) {
      showToast('Enter your current 6-digit code to disable 2FA.', 'warning');
      return;
    }
    setTwoFactorBusy(true);
    try {
      await api.post('/security-center/2fa/disable', { code: disableCode.trim() });
      showToast('Two-factor authentication disabled.', 'success');
      setShowDisableForm(false);
      setDisableCode('');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to disable two-factor authentication. Please try again.', 'error');
    } finally {
      setTwoFactorBusy(false);
    }
  };

  const updateDevice = async (device: DeviceItem, action: 'trust' | 'block', value: boolean) => {
    try {
      await api.patch(`/security-center/devices/${device.id}/${action}`, { [action === 'trust' ? 'trusted' : 'blocked']: value });
      showToast('Device updated successfully', 'success');
      fetchSecurityData();
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to update device. Please try again later.', 'error');
    }
  };

  const removeDevice = async (deviceId: number) => {
    try {
      await api.delete(`/security-center/devices/${deviceId}`);
      showToast('Device removed successfully', 'success');
      setDevices((prev) => prev.filter((d) => d.id !== deviceId));
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to remove device. Please try again later.', 'error');
    }
  };

  const logoutAllDevices = async () => {
    setLoggingOutAll(true);
    try {
      await api.post('/security-center/logout-all');
      showToast('Logged out from all devices successfully', 'success');
    } catch (err: any) {
      showToast(err?.userMessage || 'Unable to log out from all devices. Please try again later.', 'error');
    } finally {
      setLoggingOutAll(false);
    }
  };

  const recommendations = [
    { label: 'Enable two-factor authentication', done: twoFactor.enabled },
    { label: 'Verify your email address', done: Boolean(user?.emailVerified) },
    { label: 'Keep your phone number up to date', done: Boolean(profile?.phone) },
  ];

  return (
    <div className="space-y-6">
      {/* Password */}
      <div className="card-premium space-y-6">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
            <Lock size={20} className="text-brand-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Change Password</h3>
            <p className="text-sm text-slate-400 font-normal">Use a strong, unique password</p>
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
            <label className="block text-sm font-medium text-[#1e293b] mb-2">Current Password</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type={showCurrentPassword ? 'text' : 'password'}
                value={password.current}
                onChange={(e) => handlePasswordChange('current', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
              <button type="button" onClick={() => setShowCurrentPassword((v) => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showCurrentPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">New Password</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type={showNewPassword ? 'text' : 'password'}
                value={password.newPassword}
                onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
              <button type="button" onClick={() => setShowNewPassword((v) => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showNewPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-[#1e293b] mb-2">Confirm Password</label>
            <div className="relative">
              <Lock size={16} className="absolute start-4 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                value={password.confirm}
                onChange={(e) => handlePasswordChange('confirm', e.target.value)}
                placeholder="••••••••"
                className="w-full ps-11 pe-11 py-2.5 bg-[#f5f5f0] border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 focus:bg-white focus:border-brand-300 transition-all"
              />
              <button type="button" onClick={() => setShowConfirmPassword((v) => !v)} className="absolute inset-y-0 end-0 pe-4 flex items-center text-slate-400 hover:text-brand-500" tabIndex={-1}>
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>
        </div>

        <div className="pt-4 border-t border-[#e8e6e1]/60 flex justify-end">
          <button
            onClick={changePassword}
            disabled={passwordLoading}
            className="flex items-center gap-2 px-3 sm:px-6 py-2 sm:py-2.5 bg-brand-500 text-white rounded-xl font-medium text-sm hover:bg-brand-600 transition-all disabled:opacity-70 w-full sm:w-auto"
          >
            {passwordLoading ? <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Lock size={16} />}
            Update Password
          </button>
        </div>
      </div>

      {/* Two-Factor Authentication */}
      <div className="card-premium space-y-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <ShieldCheck size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Two-Factor Authentication</h3>
            <p className="text-sm text-slate-400 font-normal">Add an extra layer of security to your account</p>
          </div>
        </div>

        <div className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
          <div className="flex items-center gap-3">
            <Smartphone size={18} className="text-slate-400" />
            <div>
              <p className="text-sm font-semibold text-[#1e293b]">Authenticator App</p>
              <p className="text-xs text-slate-400">
                {twoFactor.enabled && twoFactor.method === 'AUTHENTICATOR' ? 'Enabled' : 'Not enabled'}
              </p>
            </div>
          </div>
          {twoFactor.enabled ? (
            <button onClick={() => setShowDisableForm((v) => !v)} className="text-xs font-medium text-rose-600 hover:text-rose-700">
              Disable
            </button>
          ) : (
            <button onClick={startAuthenticatorSetup} disabled={twoFactorBusy} className="text-xs font-medium text-brand-600 hover:text-brand-700 disabled:opacity-50">
              Set up
            </button>
          )}
        </div>

        {setupData && (
          <div className="p-4 bg-brand-50/40 border border-brand-100 rounded-xl space-y-3">
            <p className="text-sm text-[#1e293b]">Scan this with your authenticator app, or enter the secret manually:</p>
            <p className="text-xs font-mono break-all bg-white border border-[#e8e6e1] rounded-lg p-2">{setupData.secret}</p>
            <input
              type="text"
              value={twoFactorCode}
              onChange={(e) => setTwoFactorCode(e.target.value)}
              placeholder="Enter 6-digit code"
              className="w-full px-3 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100"
            />
            <button onClick={confirmAuthenticatorEnable} disabled={twoFactorBusy} className="px-4 py-2 bg-brand-500 text-white rounded-xl text-sm font-medium disabled:opacity-50">
              Confirm & Enable
            </button>
          </div>
        )}

        {showDisableForm && (
          <div className="p-4 bg-rose-50/40 border border-rose-100 rounded-xl space-y-3">
            <p className="text-sm text-[#1e293b]">Enter your current 6-digit code to disable two-factor authentication.</p>
            <input
              type="text"
              value={disableCode}
              onChange={(e) => setDisableCode(e.target.value)}
              placeholder="Enter 6-digit code"
              className="w-full px-3 py-2.5 bg-white border border-[#e8e6e1] rounded-xl text-sm focus:outline-none focus:ring-2 ring-rose-100"
            />
            <button onClick={disableTwoFactor} disabled={twoFactorBusy} className="px-4 py-2 bg-rose-500 text-white rounded-xl text-sm font-medium disabled:opacity-50">
              Confirm & Disable
            </button>
          </div>
        )}

        <div className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl opacity-60">
          <div className="flex items-center gap-3">
            <Mail size={18} className="text-slate-400" />
            <div>
              <p className="text-sm font-semibold text-[#1e293b]">Email OTP</p>
              <p className="text-xs text-slate-400">Coming soon</p>
            </div>
          </div>
          <span className="text-xs font-medium text-slate-400">Unavailable</span>
        </div>
      </div>

      {/* Active Sessions / Devices */}
      <div className="card-premium space-y-5">
        <div className="flex items-center justify-between pb-5 border-b border-[#e8e6e1]/60">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-brand-50 flex items-center justify-center">
              <Monitor size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1e293b]">Active Devices</h3>
              <p className="text-sm text-slate-400 font-normal">Devices that have signed in to your account</p>
            </div>
          </div>
          <button
            onClick={logoutAllDevices}
            disabled={loggingOutAll}
            className="flex items-center gap-2 px-3 py-2 text-rose-600 bg-rose-50 rounded-xl text-xs font-medium hover:bg-rose-100 transition-all disabled:opacity-50"
          >
            <LogOut size={14} />
            {loggingOutAll ? 'Logging out...' : 'Logout all devices'}
          </button>
        </div>

        {securityLoading ? (
          <div className="text-center py-6 text-sm text-slate-400">Loading...</div>
        ) : devices.length === 0 ? (
          <div className="text-center py-6 text-sm text-slate-400">No known devices recorded yet.</div>
        ) : (
          <div className="space-y-2">
            {devices.map((device) => (
              <div key={device.id} className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
                <div>
                  <p className="text-sm font-semibold text-[#1e293b]">{device.name || `${device.browser} on ${device.operatingSystem}`}</p>
                  <p className="text-xs text-slate-400">{device.ipAddress} · last seen {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : '—'}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => updateDevice(device, 'trust', !device.trusted)}
                    className={`text-xs font-medium px-2.5 py-1 rounded-lg ${device.trusted ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-50 text-slate-500'}`}
                  >
                    {device.trusted ? 'Trusted' : 'Trust'}
                  </button>
                  <button onClick={() => removeDevice(device.id)} className="text-slate-400 hover:text-rose-500 p-1.5" title="Remove device">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Login History */}
      <div className="card-premium space-y-5">
        <div className="flex items-center gap-3 pb-5 border-b border-[#e8e6e1]/60">
          <div className="w-10 h-10 rounded-xl bg-accent-50 flex items-center justify-center">
            <History size={20} className="text-accent-500" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#1e293b]">Login History</h3>
            <p className="text-sm text-slate-400 font-normal">Recent sign-in attempts on your account</p>
          </div>
        </div>

        {securityLoading ? (
          <div className="text-center py-6 text-sm text-slate-400">Loading...</div>
        ) : loginHistory.length === 0 ? (
          <div className="text-center py-6 text-sm text-slate-400">No login history recorded yet.</div>
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {loginHistory.slice(0, 20).map((item) => (
              <div key={item.id} className="flex items-center justify-between p-3 border border-[#e8e6e1] rounded-xl">
                <div className="flex items-center gap-2.5">
                  {item.success ? <CheckCircle2 size={16} className="text-emerald-500" /> : <XCircle size={16} className="text-rose-500" />}
                  <div>
                    <p className="text-sm font-medium text-[#1e293b]">{item.ipAddress || 'Unknown IP'}</p>
                    <p className="text-xs text-slate-400">{item.createdAt ? new Date(item.createdAt).toLocaleString() : '—'}</p>
                  </div>
                </div>
                {item.suspicious && (
                  <span className="text-xs font-medium text-amber-600 flex items-center gap-1">
                    <AlertTriangle size={12} /> Suspicious
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Recommendations */}
      <div className="card-premium space-y-3">
        <h3 className="text-base font-bold text-[#1e293b]">Security Recommendations</h3>
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
