import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle, ArrowRight, CheckCircle2, ClipboardCopy, Database, KeyRound, Mail, ShieldAlert, ShieldCheck, XCircle,
} from 'lucide-react';
import { superAdminApi } from '../../api/superAdminApi';
import { useToast } from '../../context/ToastContext';

type Scope = 'AGENCY' | 'CLIENT' | 'PLATFORM';
type Action =
  | 'DELETE_CLIENT'
  | 'RESET_AGENCY_TEST_DATA'
  | 'RESET_OPERATIONAL_DATA'
  | 'RESET_GPS_DATA'
  | 'RESET_AI_DATA'
  | 'FULL_AGENCY_RESET'
  | 'FULL_PLATFORM_RESET';

const ACTIONS_BY_SCOPE: Record<Scope, { value: Action; label: string }[]> = {
  AGENCY: [
    { value: 'RESET_OPERATIONAL_DATA', label: 'Reset operational data (clients, contracts, reservations, payments, invoices)' },
    { value: 'RESET_GPS_DATA', label: 'Reset GPS data' },
    { value: 'RESET_AI_DATA', label: 'Reset AI data' },
    { value: 'RESET_AGENCY_TEST_DATA', label: 'Reset agency test data' },
    { value: 'FULL_AGENCY_RESET', label: 'Full agency reset' },
  ],
  CLIENT: [{ value: 'DELETE_CLIENT', label: 'Delete client' }],
  PLATFORM: [{ value: 'FULL_PLATFORM_RESET', label: 'Full platform reset (dev only)' }],
};

type StatusInfo = {
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  environment: 'development' | 'production';
  backupAvailable: boolean;
  isSuperOwner: boolean;
  lastResetAction: { action: string; tenantName: string; status: string; createdAt: string } | null;
};

type AuditRow = {
  id: number;
  scope: string;
  action: string;
  tenantName: string;
  status: string;
  performedByEmail: string;
  createdAt: string;
  errorMessage?: string;
};

export default function SuperAdminDataReset() {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [status, setStatus] = useState<StatusInfo | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditRow[]>([]);
  const [agencies, setAgencies] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const [scope, setScope] = useState<Scope>('AGENCY');
  const [action, setAction] = useState<Action>('RESET_OPERATIONAL_DATA');
  const [agencyId, setAgencyId] = useState<string>('');
  const [clientId, setClientId] = useState<string>('');

  const [preview, setPreview] = useState<any>(null);
  const [previewing, setPreviewing] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [force, setForce] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [confirmationCode, setConfirmationCode] = useState('');
  const [result, setResult] = useState<any>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [statusRes, agenciesRes, auditRes] = await Promise.allSettled([
        superAdminApi.getDataResetStatus(),
        superAdminApi.getAgencies({}),
        superAdminApi.getDataResetAuditLogs(20),
      ]);
      if (statusRes.status === 'fulfilled') setStatus(statusRes.value.data?.data ?? null);
      if (agenciesRes.status === 'fulfilled') {
        const list = agenciesRes.value.data;
        setAgencies(Array.isArray(list) ? list : list?.content || []);
      }
      if (auditRes.status === 'fulfilled') setAuditLogs(auditRes.value.data?.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    setAction(ACTIONS_BY_SCOPE[scope][0].value);
    setPreview(null);
    setConfirmationCode('');
    setResult(null);
  }, [scope]);

  // Clear stale preview and confirmation when the target agency changes
  useEffect(() => {
    setPreview(null);
    setConfirmationCode('');
    setResult(null);
  }, [agencyId]);

  // Clear stale preview and confirmation when the reset action changes
  useEffect(() => {
    setPreview(null);
    setConfirmationCode('');
    setResult(null);
  }, [action]);

  const runPreview = async () => {
    setPreviewing(true);
    setPreview(null);
    setResult(null);
    try {
      const body: Record<string, unknown> = { scope, action, force };
      if (scope === 'AGENCY' || scope === 'CLIENT') body.agencyId = Number(agencyId) || undefined;
      if (scope === 'CLIENT') body.clientId = Number(clientId) || undefined;
      const res = await superAdminApi.previewDataReset(body);
      const previewData = res.data?.data ?? res.data;
      console.log('[DATA_RESET_CONFIRM_DEBUG] previewLoaded=true scope=%s agencyId=%s action=%s expectedConfirmationCode=%s',
        scope, agencyId, action, previewData?.confirmationCode);
      setPreview(previewData);
    } catch (err) {
      showToast((err as any).userMessage || 'Unable to generate reset preview.', 'error');
    } finally {
      setPreviewing(false);
    }
  };

  const runExecute = async () => {
    if (!preview) return;
    setExecuting(true);
    console.log('[DATA_RESET_CONFIRM_DEBUG] executeAllowed=%s scope=%s agencyId=%s action=%s expectedConfirmationCode=%s providedConfirmationCodeMatches=%s twoFactorValid=%s previewLoaded=true',
      canExecute, scope, agencyId, action, preview?.confirmationCode,
      confirmationCode === preview?.confirmationCode, twoFactorCode.length === 6);
    try {
      const body: Record<string, unknown> = {
        scope, action, force, currentPassword, twoFactorCode, confirmationCode,
      };
      if (scope === 'AGENCY' || scope === 'CLIENT') body.agencyId = Number(agencyId) || undefined;
      if (scope === 'CLIENT') body.clientId = Number(clientId) || undefined;
      const res = await superAdminApi.executeDataReset(body);
      setResult(res.data?.data ?? res.data);
      showToast('Data reset completed successfully.', 'success');
      setPreview(null);
      setCurrentPassword('');
      setTwoFactorCode('');
      setConfirmationCode('');
      await load();
    } catch (err) {
      showToast((err as any).userMessage || 'Data reset failed.', 'error');
    } finally {
      setExecuting(false);
    }
  };

  const blocked = !status?.isSuperOwner || !status?.emailVerified || !status?.twoFactorEnabled;
  const canExecute = Boolean(preview) && currentPassword.length > 0 && twoFactorCode.length === 6
    && confirmationCode === preview?.confirmationCode;

  return (
    <div className="space-y-6">
      <header className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-300"><Database size={22} /></div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#1e293b] dark:text-white">Data Reset Center</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Destructive agency/client/platform resets — gated by email verification, 2FA, password, and an exact confirmation code.</p>
        </div>
      </header>

      {/* Safety status checklist */}
      <section className="grid gap-4 grid-cols-2 sm:grid-cols-3 lg:grid-cols-5">
        {[
          { label: 'Super Owner', ok: status?.isSuperOwner, icon: ShieldAlert },
          { label: 'Email verified', ok: status?.emailVerified, icon: Mail },
          { label: '2FA enabled', ok: status?.twoFactorEnabled, icon: KeyRound },
          { label: 'Environment', ok: status?.environment === 'development', icon: ShieldCheck, customLabel: status?.environment },
          { label: 'Backup available', ok: status?.backupAvailable, icon: Database },
        ].map((item) => (
          <article key={item.label} className="rounded-2xl border border-[#e8e6e1]/80 bg-white p-4 dark:border-white/5 dark:bg-[#1a2332]/70">
            <div className="flex items-center justify-between">
              <item.icon size={18} className="text-slate-400" />
              {item.label === 'Environment' ? (
                <span className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${item.ok ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'}`}>{item.customLabel || '...'}</span>
              ) : item.ok ? <CheckCircle2 size={16} className="text-emerald-500" /> : <XCircle size={16} className="text-rose-500" />}
            </div>
            <p className="mt-3 text-xs font-semibold text-slate-500 dark:text-slate-400">{item.label}</p>
          </article>
        ))}
      </section>

      {/* Super Owner access warning — shown as inline card, not a page-replacing block */}
      {!loading && status && !status.isSuperOwner && (
        <div className="flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
          <ShieldAlert size={19} className="mt-0.5 shrink-0" />
          <div>
            <p className="font-semibold text-sm">Super Owner access required</p>
            <p className="mt-0.5 text-sm opacity-80">Data Reset Center operations require a Super Owner account. Contact your platform administrator to request Super Owner access.</p>
          </div>
        </div>
      )}

      {blocked && !loading && status?.isSuperOwner && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200">
          <AlertTriangle size={19} className="mt-0.5 shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-semibold">Security requirements not met</p>
            <p className="mt-0.5 text-sm opacity-80">
              {!status.twoFactorEnabled && !status.emailVerified
                ? 'Enable two-factor authentication and verify your email before you can preview or run a reset.'
                : !status.twoFactorEnabled
                ? 'Enable two-factor authentication in Security Settings before you can preview or run a reset.'
                : 'Verify your email before you can preview or run a reset.'}
            </p>
            {!status.twoFactorEnabled && (
              <button
                onClick={() => navigate('/super-admin/security')}
                className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-amber-800/10 px-3 py-1.5 text-xs font-semibold text-amber-900 hover:bg-amber-800/20 dark:bg-amber-200/10 dark:text-amber-200 dark:hover:bg-amber-200/20 transition-colors"
              >
                Go to Security Settings <ArrowRight size={13} />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Scope + action selection */}
      <section className="rounded-2xl border border-[#e8e6e1]/80 bg-white p-5 dark:border-white/5 dark:bg-[#1a2332]/70">
        <h2 className="text-sm font-bold text-[#1e293b] dark:text-white">1. Select scope &amp; action</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">Scope</span>
            <select value={scope} onChange={(e) => setScope(e.target.value as Scope)} className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-[#f8f8f5] px-3 py-2.5 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white">
              <option value="AGENCY">Agency</option>
              <option value="CLIENT">Client</option>
              <option value="PLATFORM">Platform (dev only)</option>
            </select>
          </label>
          {(scope === 'AGENCY' || scope === 'CLIENT') && (
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">Agency</span>
              <select value={agencyId} onChange={(e) => setAgencyId(e.target.value)} className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-[#f8f8f5] px-3 py-2.5 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white">
                <option value="">Select agency...</option>
                {agencies.map((a) => <option key={a.id} value={a.id}>{a.name || a.businessName || `Agency #${a.id}`}</option>)}
              </select>
            </label>
          )}
          {scope === 'CLIENT' && (
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">Client ID</span>
              <input value={clientId} onChange={(e) => setClientId(e.target.value)} placeholder="e.g. 42" className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-[#f8f8f5] px-3 py-2.5 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white" />
            </label>
          )}
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">Action</span>
            <select value={action} onChange={(e) => setAction(e.target.value as Action)} className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-[#f8f8f5] px-3 py-2.5 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white">
              {ACTIONS_BY_SCOPE[scope].map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
            </select>
          </label>
        </div>
        {action === 'DELETE_CLIENT' && (
          <label className="mt-4 flex items-center gap-2 text-xs text-slate-500">
            <input type="checkbox" checked={force} onChange={(e) => setForce(e.target.checked)} /> Force delete even if the client has an active/draft contract
          </label>
        )}
        <button
          onClick={runPreview}
          disabled={blocked || previewing || ((scope === 'AGENCY' || scope === 'CLIENT') && !agencyId) || (scope === 'CLIENT' && !clientId)}
          className="mt-5 rounded-xl bg-[#0a0f2c] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-40 dark:bg-accent-400 dark:text-[#0a0f2c]"
        >
          {previewing ? 'Generating preview...' : '2. Preview impact'}
        </button>
      </section>

      {/* Preview result */}
      {preview && (
        <section className="rounded-2xl border border-amber-200 bg-amber-50/60 p-5 dark:border-amber-500/20 dark:bg-amber-500/5">
          <h2 className="text-sm font-bold text-[#1e293b] dark:text-white">3. Preview result — {preview.agencyName || preview.clientName}</h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-rose-600">Will delete</p>
              <ul className="mt-2 space-y-1 text-sm text-slate-600 dark:text-slate-300">
                {Object.entries(preview.willDelete || {}).map(([key, value]) => (
                  <li key={key} className="flex justify-between"><span className="capitalize">{key}</span><strong>{String(value)}</strong></li>
                ))}
              </ul>
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-wide text-emerald-600">Will keep</p>
              <ul className="mt-2 space-y-1 text-sm text-slate-600 dark:text-slate-300">
                {Object.entries(preview.willKeep || {}).map(([key, value]) => (
                  <li key={key} className="flex justify-between"><span className="capitalize">{key}</span><strong>{value ? 'Yes' : 'No'}</strong></li>
                ))}
              </ul>
            </div>
          </div>
          {preview.productionBlocked && (
            <p className="mt-3 text-xs font-semibold text-rose-600">Full platform reset is disabled in production — execute will fail until the server runs in a dev/local profile.</p>
          )}

          <h3 className="mt-5 text-sm font-bold text-[#1e293b] dark:text-white">4. Confirm &amp; execute</h3>
          <div className="mt-3 grid gap-4 sm:grid-cols-3">
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">Current password</span>
              <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2.5 text-sm dark:border-white/10 dark:bg-white/5 dark:text-white" />
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">2FA code</span>
              <input value={twoFactorCode} onChange={(e) => setTwoFactorCode(e.target.value)} maxLength={6} placeholder="123456" className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2.5 text-sm font-mono dark:border-white/10 dark:bg-white/5 dark:text-white" />
            </label>
            <div className="block">
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-slate-500">
                  Type <code className="text-rose-600">{preview.confirmationCode}</code> exactly
                </span>
                <button
                  type="button"
                  onClick={() => navigator.clipboard.writeText(preview.confirmationCode)}
                  title="Copy confirmation code"
                  className="flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-semibold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors"
                >
                  <ClipboardCopy size={11} /> Copy
                </button>
              </div>
              <input
                value={confirmationCode}
                onChange={(e) => setConfirmationCode(e.target.value)}
                className="mt-1.5 w-full rounded-xl border border-[#e8e6e1] bg-white px-3 py-2.5 text-sm font-mono dark:border-white/10 dark:bg-white/5 dark:text-white"
              />
              {confirmationCode.length > 0 && (
                <p className={`mt-1 text-[11px] font-semibold ${confirmationCode === preview.confirmationCode ? 'text-emerald-600' : 'text-rose-500'}`}>
                  {confirmationCode === preview.confirmationCode ? 'Confirmation code matches' : 'Confirmation code does not match'}
                </p>
              )}
            </div>
          </div>
          <button
            onClick={runExecute}
            disabled={!canExecute || executing}
            className="mt-5 rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-40"
          >
            {executing ? 'Executing reset...' : 'Execute reset'}
          </button>
        </section>
      )}

      {/* Final result */}
      {result && (
        <section className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5 dark:border-emerald-500/20 dark:bg-emerald-500/5">
          <h2 className="flex items-center gap-2 text-sm font-bold text-emerald-700 dark:text-emerald-300"><CheckCircle2 size={16} /> Reset completed</h2>
          <ul className="mt-3 space-y-1 text-sm text-slate-600 dark:text-slate-300">
            {Object.entries(result).map(([key, value]) => (
              <li key={key} className="flex justify-between"><span className="capitalize">{key}</span><strong>{String(value)}</strong></li>
            ))}
          </ul>
        </section>
      )}

      {/* Audit log */}
      <section className="overflow-hidden rounded-2xl border border-[#e8e6e1]/80 bg-white shadow-soft dark:border-white/5 dark:bg-[#1a2332]/70">
        <header className="border-b border-[#e8e6e1]/70 px-5 py-4 dark:border-white/5">
          <h2 className="text-sm font-bold text-[#1e293b] dark:text-white">Recent destructive actions</h2>
        </header>
        {auditLogs.length === 0 ? (
          <p className="px-5 py-6 text-sm text-slate-400">No data reset actions have been performed yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] text-left">
              <thead className="bg-[#f8f8f5] text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:bg-white/[0.025]">
                <tr><th className="px-5 py-3">Action</th><th className="px-4 py-3">Agency</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">By</th><th className="px-5 py-3">When</th></tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/60 dark:divide-white/5">
                {auditLogs.map((row) => (
                  <tr key={row.id}>
                    <td className="px-5 py-3 text-sm font-semibold text-[#1e293b] dark:text-white">{row.action}</td>
                    <td className="px-4 py-3 text-sm text-slate-500">{row.tenantName}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-md px-2 py-1 text-[10px] font-bold ${row.status === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700' : row.status === 'FAILED' ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-700'}`}>{row.status}</span>
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-500">{row.performedByEmail}</td>
                    <td className="px-5 py-3 text-xs text-slate-400">{new Date(row.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
