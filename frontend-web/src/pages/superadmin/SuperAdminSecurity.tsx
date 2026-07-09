import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { QRCodeSVG } from 'qrcode.react';
import { superAdminApi } from '../../api/superAdminApi';
import {
  AlertTriangle, CheckCircle, CheckCircle2, Copy, Download, Eye, EyeOff,
  KeyRound, Loader2, ShieldCheck, ShieldOff, X, XCircle,
} from 'lucide-react';
import { PageHeader, StatCard, DataTable, TabGroup, Badge } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

// ── Types ─────────────────────────────────────────────────────────────────────

type TwoFAStep =
  | 'idle'
  | 'loading-setup'
  | 'show-qr'
  | 'confirming'
  | 'show-codes'
  | 'disabling'
  | 'regenerating';

// Matches GET /api/auth/security-status response shape
type SecurityStatus = {
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  twoFactorConfirmedAt: string | null;
  role: string;
  email: string;
};

// Only display data — the raw TOTP secret is stored server-side and never sent to the client
type SetupData = {
  provisioningUri: string;
  manualSecret: string; // formatted with spaces e.g. "ABCD EFGH IJKL MNOP"
};

// ── Recovery codes panel ───────────────────────────────────────────────────────

function RecoveryCodesPanel({
  codes,
  title = 'Save your recovery codes',
  onDone,
}: { codes: string[]; title?: string; onDone: () => void }) {
  const { showToast } = useToast();
  const [copied, setCopied] = useState(false);

  const copyAll = async () => {
    await navigator.clipboard.writeText(codes.join('\n'));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    showToast('Codes copied to clipboard', 'success');
  };

  const download = () => {
    const blob = new Blob(
      [`RentCar — 2FA Recovery Codes\nGenerated: ${new Date().toISOString()}\n\n${codes.join('\n')}\n\nEach code can only be used once.`],
      { type: 'text/plain' }
    );
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'rentcar-recovery-codes.txt';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 dark:border-amber-500/20 dark:bg-amber-500/10">
        <div className="flex items-start gap-3">
          <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />
          <div>
            <p className="text-sm font-semibold text-amber-800 dark:text-amber-200">{title}</p>
            <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
              These recovery codes will only be shown once. Store them in a safe place. Each code can be used once to regain access if you lose your authenticator.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-4 font-mono text-sm dark:bg-white/5">
        {codes.map((c) => (
          <span
            key={c}
            className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-center text-[13px] font-semibold tracking-wider text-[#1e293b] dark:border-white/10 dark:bg-white/10 dark:text-white"
          >
            {c}
          </span>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={copyAll}
          className="flex items-center gap-2 rounded-xl border border-[#e8e6e1] bg-white px-4 py-2 text-sm font-semibold text-[#1e293b] transition-colors hover:bg-slate-50 dark:border-white/10 dark:bg-white/5 dark:text-white dark:hover:bg-white/10"
        >
          {copied ? <CheckCircle2 size={15} className="text-emerald-500" /> : <Copy size={15} />}
          {copied ? 'Copied!' : 'Copy all'}
        </button>
        <button
          onClick={download}
          className="flex items-center gap-2 rounded-xl border border-[#e8e6e1] bg-white px-4 py-2 text-sm font-semibold text-[#1e293b] transition-colors hover:bg-slate-50 dark:border-white/10 dark:bg-white/5 dark:text-white dark:hover:bg-white/10"
        >
          <Download size={15} /> Download .txt
        </button>
        <button
          onClick={onDone}
          className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          I've saved my codes →
        </button>
      </div>
    </div>
  );
}

// ── 2FA Setup Tab ──────────────────────────────────────────────────────────────

function TwoFactorTab() {
  const { showToast } = useToast();

  const [secStatus, setSecStatus] = useState<SecurityStatus | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);

  const [step, setStep] = useState<TwoFAStep>('idle');
  const [setupData, setSetupData] = useState<SetupData | null>(null);
  const [confirmCode, setConfirmCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);

  const [showManualSecret, setShowManualSecret] = useState(false);

  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [disableLoading, setDisableLoading] = useState(false);

  const [regenPassword, setRegenPassword] = useState('');
  const [regenCode, setRegenCode] = useState('');
  const [regenLoading, setRegenLoading] = useState(false);
  const [regenCodes, setRegenCodes] = useState<string[]>([]);

  const confirmInputRef = useRef<HTMLInputElement>(null);

  const loadStatus = async () => {
    setStatusLoading(true);
    try {
      const res = await superAdminApi.getMySecurityStatus();
      const d = res.data?.data ?? res.data ?? null;
      setSecStatus(d);
    } catch {
      // Silent fail — show default not-enabled state
      setSecStatus(null);
    } finally {
      setStatusLoading(false);
    }
  };

  useEffect(() => { loadStatus(); }, []);

  const handleSetup = async () => {
    setStep('loading-setup');
    setSetupData(null);
    try {
      const res = await superAdminApi.setup2fa();
      const d = res.data?.data ?? res.data;
      // Raw secret is stored server-side; we only receive provisioningUri + formatted display secret
      setSetupData({
        provisioningUri: d.provisioningUri,
        manualSecret: d.manualSecret,
      });
      setConfirmCode('');
      setStep('show-qr');
      setTimeout(() => confirmInputRef.current?.focus(), 300);
    } catch (err: any) {
      showToast(err?.userMessage || 'Failed to initiate 2FA setup. Please try again.', 'error');
      setStep('idle');
    }
  };

  const handleConfirm = async () => {
    const normalized = confirmCode.replace(/\s/g, '').trim();
    if (normalized.length !== 6) return;
    setStep('confirming');
    try {
      // Only sends { code } — server reads the pending secret from DB
      const res = await superAdminApi.confirm2fa(normalized);
      const d = res.data?.data ?? res.data;
      setRecoveryCodes(d.recoveryCodes ?? []);
      setStep('show-codes');
      await loadStatus();
      showToast('Two-factor authentication enabled!', 'success');
    } catch (err: any) {
      showToast(
        err?.userMessage || 'Invalid authenticator code. Try the newest code from your app.',
        'error'
      );
      setStep('show-qr');
      setConfirmCode('');
      setTimeout(() => confirmInputRef.current?.focus(), 100);
    }
  };

  const handleDisable = async () => {
    if (!disablePassword || disableCode.length !== 6) return;
    setDisableLoading(true);
    try {
      await superAdminApi.disable2fa(disablePassword, disableCode);
      showToast('Two-factor authentication disabled.', 'success');
      setDisablePassword('');
      setDisableCode('');
      setStep('idle');
      await loadStatus();
    } catch (err: any) {
      showToast(err?.userMessage || 'Failed to disable 2FA. Check your password and code.', 'error');
    } finally {
      setDisableLoading(false);
    }
  };

  const handleRegen = async () => {
    if (!regenPassword || regenCode.length !== 6) return;
    setRegenLoading(true);
    try {
      const res = await superAdminApi.regenerate2faCodes(regenPassword, regenCode);
      const d = res.data?.data ?? res.data;
      setRegenCodes(d.recoveryCodes ?? []);
      showToast('Recovery codes regenerated!', 'success');
      setRegenPassword('');
      setRegenCode('');
      setStep('idle');
    } catch (err: any) {
      showToast(err?.userMessage || 'Failed to regenerate codes. Check your password and code.', 'error');
    } finally {
      setRegenLoading(false);
    }
  };

  if (statusLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 size={28} className="animate-spin text-slate-400" />
      </div>
    );
  }

  const enabled = secStatus?.twoFactorEnabled ?? false;

  return (
    <div className="max-w-xl space-y-6">
      {/* Status card */}
      <div
        className={`flex items-start gap-4 rounded-2xl border p-5 ${
          enabled
            ? 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-500/20 dark:bg-emerald-500/5'
            : 'border-[#e8e6e1] bg-slate-50 dark:border-white/5 dark:bg-white/[0.025]'
        }`}
      >
        <div
          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
            enabled ? 'bg-emerald-100 dark:bg-emerald-500/20' : 'bg-slate-100 dark:bg-white/10'
          }`}
        >
          {enabled
            ? <ShieldCheck size={22} className="text-emerald-600 dark:text-emerald-400" />
            : <KeyRound size={22} className="text-slate-400" />}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-bold text-[#1e293b] dark:text-white">Two-Factor Authentication</h3>
            {enabled
              ? <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300">Enabled</span>
              : <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-slate-500 dark:bg-white/10 dark:text-slate-400">Not enabled</span>}
          </div>
          {enabled && secStatus?.twoFactorConfirmedAt && (
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              Enabled on {new Date(secStatus.twoFactorConfirmedAt).toLocaleDateString()}
            </p>
          )}
          {!enabled && (
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Protect destructive actions and Super Admin access with an authenticator app (Google Authenticator, Authy, 1Password, etc.).
            </p>
          )}
        </div>
      </div>

      {/* Recovery codes shown immediately after enabling */}
      {step === 'show-codes' && recoveryCodes.length > 0 && (
        <RecoveryCodesPanel
          codes={recoveryCodes}
          title="2FA enabled — save your recovery codes"
          onDone={() => { setStep('idle'); setRecoveryCodes([]); }}
        />
      )}

      {/* Regenerated codes result */}
      {regenCodes.length > 0 && step === 'idle' && (
        <RecoveryCodesPanel
          codes={regenCodes}
          title="New recovery codes — save them now"
          onDone={() => setRegenCodes([])}
        />
      )}

      {/* NOT ENABLED — setup flow */}
      {!enabled && step !== 'show-codes' && (
        <div className="space-y-4">
          {step === 'idle' && (
            <button
              onClick={handleSetup}
              className="rounded-xl bg-[#0a0f2c] px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#151c3d] dark:bg-accent-400 dark:text-[#0a0f2c] dark:hover:bg-accent-300"
            >
              Set up authenticator app
            </button>
          )}

          {step === 'loading-setup' && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 size={16} className="animate-spin" /> Generating setup code…
            </div>
          )}

          {(step === 'show-qr' || step === 'confirming') && setupData && (
            <div className="space-y-5">
              {/* Step 1 — QR */}
              <div>
                <p className="mb-3 text-sm font-semibold text-[#1e293b] dark:text-white">
                  1. Scan this QR code with your authenticator app
                </p>
                <div className="inline-block rounded-2xl border border-[#e8e6e1] bg-white p-4 dark:border-white/10 dark:bg-white/[0.05]">
                  <QRCodeSVG value={setupData.provisioningUri} size={180} level="M" marginSize={0} />
                </div>
                <p className="mt-2 text-xs text-slate-400">
                  Works with Google Authenticator, Authy, Microsoft Authenticator, and 1Password.
                </p>
              </div>

              {/* Manual secret */}
              <div>
                <p className="mb-1.5 text-xs font-semibold text-slate-500 dark:text-slate-400">
                  Or enter the secret key manually
                </p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 select-all rounded-xl border border-[#e8e6e1] bg-slate-50 px-3 py-2 font-mono text-sm tracking-widest text-[#1e293b] dark:border-white/10 dark:bg-white/5 dark:text-white">
                    {showManualSecret
                      ? setupData.manualSecret
                      : '•'.repeat(setupData.manualSecret.replace(/ /g, '').length)}
                  </code>
                  <button
                    onClick={() => setShowManualSecret((v) => !v)}
                    className="rounded-lg border border-[#e8e6e1] p-2 text-slate-400 hover:text-slate-600 dark:border-white/10 dark:hover:text-slate-200"
                    title={showManualSecret ? 'Hide secret' : 'Show secret'}
                  >
                    {showManualSecret ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {/* Step 2 — enter code */}
              <div>
                <p className="mb-1.5 text-sm font-semibold text-[#1e293b] dark:text-white">
                  2. Enter the 6-digit code from your app
                </p>
                <div className="flex items-center gap-3">
                  <input
                    ref={confirmInputRef}
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={confirmCode}
                    onChange={(e) => setConfirmCode(e.target.value.replace(/\D/g, ''))}
                    onKeyDown={(e) => { if (e.key === 'Enter') handleConfirm(); }}
                    placeholder="123456"
                    className="w-40 rounded-xl border border-[#e8e6e1] bg-white px-4 py-2.5 text-center font-mono text-xl tracking-widest text-[#1e293b] focus:outline-none focus:ring-2 focus:ring-[#0a0f2c]/20 dark:border-white/10 dark:bg-white/5 dark:text-white dark:focus:ring-accent-400/30"
                  />
                  <button
                    onClick={handleConfirm}
                    disabled={confirmCode.length !== 6 || step === 'confirming'}
                    className="flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:opacity-40"
                  >
                    {step === 'confirming'
                      ? <><Loader2 size={15} className="animate-spin" /> Verifying…</>
                      : <><CheckCircle size={15} /> Confirm &amp; Enable</>}
                  </button>
                </div>
                <button
                  onClick={() => {
                    setStep('idle');
                    setSetupData(null);
                    setConfirmCode('');
                    setShowManualSecret(false);
                  }}
                  className="mt-3 text-xs text-slate-400 transition-colors hover:text-slate-600 dark:hover:text-slate-300"
                >
                  ← Cancel setup
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ENABLED — management options */}
      {enabled && step !== 'show-codes' && (
        <div className="space-y-4">
          {step === 'idle' && (
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => setStep('regenerating')}
                className="rounded-xl border border-[#e8e6e1] bg-white px-4 py-2 text-sm font-semibold text-[#1e293b] transition-colors hover:bg-slate-50 dark:border-white/10 dark:bg-white/5 dark:text-white dark:hover:bg-white/10"
              >
                Regenerate recovery codes
              </button>
              <button
                onClick={() => setStep('disabling')}
                className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 transition-colors hover:bg-rose-100 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300 dark:hover:bg-rose-500/20"
              >
                <ShieldOff size={15} /> Disable 2FA
              </button>
            </div>
          )}

          {step === 'disabling' && (
            <div className="space-y-4 rounded-xl border border-rose-200 bg-rose-50/60 p-5 dark:border-rose-500/20 dark:bg-rose-500/5">
              <div className="flex items-center justify-between">
                <p className="text-sm font-bold text-rose-700 dark:text-rose-300">Disable Two-Factor Authentication</p>
                <button
                  onClick={() => { setStep('idle'); setDisablePassword(''); setDisableCode(''); }}
                  className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                >
                  <X size={16} />
                </button>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block">
                  <span className="text-xs font-semibold text-slate-500">Current password</span>
                  <input
                    type="password"
                    value={disablePassword}
                    onChange={(e) => setDisablePassword(e.target.value)}
                    className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-semibold text-slate-500">Authenticator code</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={disableCode}
                    onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, ''))}
                    placeholder="123456"
                    className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2 font-mono text-sm dark:border-white/10 dark:bg-white/5 dark:text-white"
                  />
                </label>
              </div>
              <button
                onClick={handleDisable}
                disabled={!disablePassword || disableCode.length !== 6 || disableLoading}
                className="flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-rose-700 disabled:opacity-40"
              >
                {disableLoading ? <><Loader2 size={14} className="animate-spin" /> Disabling…</> : 'Confirm disable'}
              </button>
            </div>
          )}

          {step === 'regenerating' && (
            <div className="space-y-4 rounded-xl border border-amber-200 bg-amber-50/60 p-5 dark:border-amber-500/20 dark:bg-amber-500/5">
              <div className="flex items-center justify-between">
                <p className="text-sm font-bold text-amber-800 dark:text-amber-200">Regenerate Recovery Codes</p>
                <button
                  onClick={() => { setStep('idle'); setRegenPassword(''); setRegenCode(''); }}
                  className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                >
                  <X size={16} />
                </button>
              </div>
              <p className="text-xs text-amber-700 dark:text-amber-300">
                Old recovery codes will be immediately invalidated and replaced with new ones.
              </p>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block">
                  <span className="text-xs font-semibold text-slate-500">Current password</span>
                  <input
                    type="password"
                    value={regenPassword}
                    onChange={(e) => setRegenPassword(e.target.value)}
                    className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-semibold text-slate-500">Authenticator code</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={regenCode}
                    onChange={(e) => setRegenCode(e.target.value.replace(/\D/g, ''))}
                    placeholder="123456"
                    className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2 font-mono text-sm dark:border-white/10 dark:bg-white/5 dark:text-white"
                  />
                </label>
              </div>
              <button
                onClick={handleRegen}
                disabled={!regenPassword || regenCode.length !== 6 || regenLoading}
                className="flex items-center gap-2 rounded-xl bg-amber-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-amber-700 disabled:opacity-40"
              >
                {regenLoading ? <><Loader2 size={14} className="animate-spin" /> Regenerating…</> : 'Regenerate codes'}
              </button>
            </div>
          )}
        </div>
      )}

      {!enabled && step === 'idle' && (
        <p className="text-xs text-slate-400 dark:text-slate-500">
          Works with Google Authenticator, Microsoft Authenticator, Authy, and 1Password.
        </p>
      )}
    </div>
  );
}

// ── Main Component ─────────────────────────────────────────────────────────────

export default function SuperAdminSecurity() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [summary, setSummary] = useState<any>(null);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [loginHistory, setLoginHistory] = useState<any[]>([]);
  const [sessions, setSessions] = useState<any[]>([]);
  const [failedLogins, setFailedLogins] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('2fa');

  useEffect(() => { fetchData(); }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [summaryRes, logsRes, loginRes, sessionsRes, failedRes] = await Promise.all([
        superAdminApi.getSecuritySummary(),
        superAdminApi.getAuditLogs(),
        superAdminApi.getLoginHistory().catch(() => ({ data: [] })),
        superAdminApi.getSessions().catch(() => ({ data: [] })),
        superAdminApi.getFailedLogins().catch(() => ({ data: [] })),
      ]);
      setSummary(summaryRes.data);
      setAuditLogs(logsRes.data);
      setLoginHistory(loginRes.data);
      setSessions(sessionsRes.data);
      setFailedLogins(failedRes.data);
    } catch (err) {
      console.error(err);
      showToast('Unable to load security data. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleRevokeSession = async (sessionId: number) => {
    try {
      await superAdminApi.revokeSession(sessionId);
      showToast('Session revoked successfully', 'success');
      await fetchData();
    } catch (err) {
      console.error(err);
      showToast('Unable to revoke session. Please try again later.', 'error');
    }
  };

  const tabs = [
    { id: '2fa',      label: 'Two-Factor Auth' },
    { id: 'overview', label: 'Overview' },
    { id: 'audit',    label: 'Audit Logs' },
    { id: 'logins',   label: 'Login History' },
    { id: 'sessions', label: 'Active Sessions' },
    { id: 'failed',   label: 'Failed Logins' },
  ];

  return (
    <div className="animate-fade space-y-6">
      <PageHeader title={t('superAdmin.security.title')} subtitle={t('superAdmin.security.subtitle')} />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 lg:grid-cols-4">
        <StatCard title={t('superAdmin.security.events24h')}     value={summary?.totalEvents24h || 0}  icon={ShieldCheck}   loading={loading} />
        <StatCard title={t('superAdmin.security.failedLogins')}  value={summary?.failedLogins24h || 0} icon={AlertTriangle}  loading={loading} />
        <StatCard title={t('superAdmin.security.suspicious')}    value={summary?.suspiciousEvents || 0} icon={XCircle}       loading={loading} />
        <StatCard title={t('superAdmin.security.activeSessions')} value={summary?.activeSessions || 0}  icon={CheckCircle}   loading={loading} />
      </div>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="rounded-2xl border border-[#e8e6e1]/80 bg-white p-4 shadow-soft dark:border-white/5 dark:bg-[#1a2332]/70 sm:p-6">
        {activeTab === '2fa' && <TwoFactorTab />}

        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              <div className="rounded-xl bg-slate-50 p-4 dark:bg-white/5">
                <h3 className="mb-3 text-sm font-bold text-[#1e293b] dark:text-white">Recent Suspicious Activity</h3>
                {auditLogs.filter((l: any) => !l.isSuccess).slice(0, 5).length === 0 ? (
                  <p className="text-sm text-slate-400">No suspicious events detected.</p>
                ) : (
                  <div className="space-y-2">
                    {auditLogs.filter((l: any) => !l.isSuccess).slice(0, 5).map((log: any) => (
                      <div key={log.id} className="flex items-center gap-3 rounded-lg border border-rose-200 bg-white p-2 dark:border-rose-500/20 dark:bg-[#1a2332]/50">
                        <AlertTriangle size={14} className="shrink-0 text-rose-500" />
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-medium text-[#1e293b] dark:text-white">{log.action}</p>
                          <p className="truncate text-xs text-slate-500">{log.description}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div className="rounded-xl bg-slate-50 p-4 dark:bg-white/5">
                <h3 className="mb-3 text-sm font-bold text-[#1e293b] dark:text-white">Security Recommendations</h3>
                <div className="space-y-2">
                  {[
                    { text: 'Enable 2FA for all admin accounts', done: false },
                    { text: 'Review API key permissions',        done: true  },
                    { text: 'Update password policy',            done: false },
                    { text: 'Enable IP whitelisting',            done: false },
                  ].map((rec, idx) => (
                    <div key={idx} className="flex items-center gap-2 text-sm">
                      {rec.done
                        ? <CheckCircle2 size={14} className="text-emerald-500" />
                        : <div className="h-3.5 w-3.5 rounded-full border-2 border-slate-300" />}
                      <span className={rec.done ? 'text-slate-400 line-through' : 'text-[#1e293b] dark:text-white'}>
                        {rec.text}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'audit' && (
          <DataTable
            columns={[
              { key: 'action',      header: t('superAdmin.security.action') },
              { key: 'description', header: t('superAdmin.security.description') },
              { key: 'user',   header: t('superAdmin.security.user'),   render: (row: any) => <span className="text-sm text-slate-500">{row.performedBy || '-'}</span> },
              { key: 'status', header: t('superAdmin.security.status'), render: (row: any) => <Badge variant={row.isSuccess ? 'success' : 'danger'}>{row.isSuccess ? 'Success' : 'Failed'}</Badge> },
              { key: 'time',   header: t('superAdmin.security.time'),   render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
            ]}
            data={auditLogs}
            loading={loading}
            keyExtractor={(row) => row.id}
            emptyTitle={t('superAdmin.security.noLogs')}
          />
        )}

        {activeTab === 'logins' && (
          <DataTable
            columns={[
              { key: 'user',   header: 'User' },
              { key: 'ip',     header: 'IP Address', render: (row: any) => <span className="font-mono text-sm text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'result', header: 'Result',     render: (row: any) => <Badge variant={row.success ? 'success' : 'danger'}>{row.success ? 'Success' : 'Failed'}</Badge> },
              { key: 'time',   header: 'Time',       render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
            ]}
            data={loginHistory}
            loading={loading}
            keyExtractor={(row) => row.id}
            emptyTitle="No login history found"
          />
        )}

        {activeTab === 'sessions' && (
          <DataTable
            columns={[
              { key: 'user',    header: 'User',       render: (row: any) => <span className="text-sm text-[#1e293b] dark:text-white">{row.userId || '-'}</span> },
              { key: 'ip',      header: 'IP Address', render: (row: any) => <span className="font-mono text-sm text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'created', header: 'Created',    render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
              { key: 'expires', header: 'Expires',    render: (row: any) => <span className="text-xs text-slate-500">{row.expiresAt ? new Date(row.expiresAt).toLocaleString() : '-'}</span> },
              {
                key: 'actions', header: '', align: 'right' as const,
                render: (row: any) => (
                  <button
                    onClick={() => handleRevokeSession(row.id)}
                    className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-500/10"
                    title="Revoke session"
                  >
                    <X size={16} />
                  </button>
                ),
              },
            ]}
            data={sessions}
            loading={loading}
            keyExtractor={(row) => row.id}
            emptyTitle="No active sessions"
          />
        )}

        {activeTab === 'failed' && (
          <DataTable
            columns={[
              { key: 'user',   header: 'User',       render: (row: any) => <span className="text-sm text-[#1e293b] dark:text-white">{row.performedBy || '-'}</span> },
              { key: 'ip',     header: 'IP Address', render: (row: any) => <span className="font-mono text-sm text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'reason', header: 'Reason',     render: (row: any) => <span className="text-sm text-slate-500">{row.reason || 'Invalid credentials'}</span> },
              { key: 'time',   header: 'Time',       render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
            ]}
            data={failedLogins}
            loading={loading}
            keyExtractor={(row) => row.id}
            emptyTitle="No failed login attempts"
          />
        )}
      </div>
    </div>
  );
}
