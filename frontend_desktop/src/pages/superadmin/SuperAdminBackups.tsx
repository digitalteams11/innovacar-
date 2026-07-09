import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle, CheckCircle2, Clock3, DatabaseBackup, Download, HardDrive,
  RefreshCw, RotateCcw, ShieldCheck, Trash2, XCircle,
} from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import ApiErrorState from '../../components/ApiErrorState';
import EmptyState from '../../components/EmptyState';
import { ShimmerTable } from '../../components/ShimmerSkeleton';

type BackupStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'RESTORING' | 'RESTORED';
type BackupType = 'MANUAL' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'PRE_RESTORE';

type BackupRecord = {
  id: number;
  type: BackupType;
  status: BackupStatus;
  fileName?: string;
  sizeBytes?: number;
  sha256?: string;
  createdBy?: string;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
  restoredAt?: string;
};

type BackupConfiguration = {
  restoreEnabled: boolean;
  daily: { cron: string; retentionDays: number };
  weekly: { cron: string; retentionDays: number };
  monthly: { cron: string; retentionDays: number };
};

const formatBytes = (bytes?: number) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** index)).toFixed(index >= 2 ? 1 : 0)} ${units[index]}`;
};

const statusClass = (status: BackupStatus) => {
  if (status === 'COMPLETED' || status === 'RESTORED') return 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300';
  if (status === 'FAILED') return 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300';
  return 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300';
};

export default function SuperAdminBackups() {
  const { showToast } = useToast();
  const [backups, setBackups] = useState<BackupRecord[]>([]);
  const [configuration, setConfiguration] = useState<BackupConfiguration | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<BackupRecord | null>(null);
  const [confirmation, setConfirmation] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const [recordsResponse, configurationResponse] = await Promise.all([
        api.get<BackupRecord[]>('/super-admin/backups'),
        api.get<BackupConfiguration>('/super-admin/backups/configuration'),
      ]);
      setBackups(recordsResponse.data);
      setConfiguration(configurationResponse.data);
    } catch (requestError) {
      console.error(requestError);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const metrics = useMemo(() => ({
    completed: backups.filter((backup) => backup.status === 'COMPLETED' || backup.status === 'RESTORED').length,
    failed: backups.filter((backup) => backup.status === 'FAILED').length,
    bytes: backups.reduce((sum, backup) => sum + (backup.sizeBytes || 0), 0),
  }), [backups]);

  const create = async () => {
    setCreating(true);
    try {
      await api.post('/super-admin/backups');
      showToast('Database backup completed successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast((requestError as any).userMessage || 'Database backup failed.', 'error');
      await load();
    } finally {
      setCreating(false);
    }
  };

  const download = async (backup: BackupRecord) => {
    setBusyId(backup.id);
    try {
      const response = await api.get(`/super-admin/backups/${backup.id}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = backup.fileName || `rentcar-backup-${backup.id}.dump`;
      anchor.click();
      URL.revokeObjectURL(url);
      showToast('Backup downloaded successfully', 'success');
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to download this backup.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const remove = async (backup: BackupRecord) => {
    if (!window.confirm(`Delete ${backup.fileName || `backup #${backup.id}`} permanently?`)) return;
    setBusyId(backup.id);
    try {
      await api.delete(`/super-admin/backups/${backup.id}`);
      showToast('Backup deleted successfully', 'success');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast('Unable to delete this backup.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const restore = async () => {
    if (!restoreTarget) return;
    setBusyId(restoreTarget.id);
    try {
      await api.post(`/super-admin/backups/${restoreTarget.id}/restore`, { confirmation });
      showToast('Database restore completed successfully', 'success');
      setRestoreTarget(null);
      setConfirmation('');
      await load();
    } catch (requestError) {
      console.error(requestError);
      showToast((requestError as any).userMessage || 'Database restore failed.', 'error');
      await load();
    } finally {
      setBusyId(null);
    }
  };

  if (error && backups.length === 0) {
    return <ApiErrorState message="Backup and recovery records could not be loaded." onRetry={load} />;
  }

  const restoreEnabled = configuration?.restoreEnabled === true;

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300"><DatabaseBackup size={22} /></div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-[#1e293b] dark:text-white">Backup & Recovery</h1>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Native PostgreSQL backups with checksum verification and guarded disaster recovery.</p>
            </div>
          </div>
        </div>
        <div className="flex gap-2">
          <button onClick={load} disabled={loading} className="inline-flex items-center gap-2 rounded-xl border border-[#e8e6e1] bg-white px-4 py-2.5 text-sm font-semibold text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
          <button onClick={create} disabled={creating} className="inline-flex items-center gap-2 rounded-xl bg-[#0a0f2c] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50 dark:bg-accent-400 dark:text-[#0a0f2c]">
            <DatabaseBackup size={16} /> {creating ? 'Creating backup...' : 'Create backup'}
          </button>
        </div>
      </header>

      <section className="grid gap-4 sm:grid-cols-3">
        {[
          { label: 'Recoverable backups', value: metrics.completed, icon: CheckCircle2, tone: 'text-emerald-500' },
          { label: 'Storage used', value: formatBytes(metrics.bytes), icon: HardDrive, tone: 'text-blue-500' },
          { label: 'Failed attempts', value: metrics.failed, icon: XCircle, tone: 'text-rose-500' },
        ].map((metric) => (
          <article key={metric.label} className="rounded-2xl border border-[#e8e6e1]/80 bg-white p-5 shadow-soft dark:border-white/5 dark:bg-[#1a2332]/70">
            <metric.icon size={20} className={metric.tone} />
            <strong className="mt-4 block text-2xl text-[#1e293b] dark:text-white">{metric.value}</strong>
            <span className="text-xs text-slate-500 dark:text-slate-400">{metric.label}</span>
          </article>
        ))}
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {[
          { title: 'Daily', policy: configuration?.daily },
          { title: 'Weekly', policy: configuration?.weekly },
          { title: 'Monthly', policy: configuration?.monthly },
        ].map((policy) => (
          <article key={policy.title} className="rounded-2xl border border-[#e8e6e1]/80 bg-white p-5 dark:border-white/5 dark:bg-[#1a2332]/70">
            <div className="flex items-center gap-2 text-sm font-bold text-[#1e293b] dark:text-white"><Clock3 size={16} className="text-brand-500" /> {policy.title} policy</div>
            <p className="mt-3 text-xs text-slate-500">Cron: <code>{policy.policy?.cron || 'Loading...'}</code></p>
            <p className="mt-1 text-xs text-slate-500">Retention: {policy.policy ? `${policy.policy.retentionDays} days` : 'Loading...'}</p>
          </article>
        ))}
      </section>

      {!restoreEnabled && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200">
          <ShieldCheck size={19} className="mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-semibold">Restore protection is enabled</p>
            <p className="mt-1 text-xs leading-5">Restores are disabled by default. Set <code>BACKUP_RESTORE_ENABLED=true</code> only during a controlled maintenance window.</p>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-2xl border border-[#e8e6e1]/80 bg-white shadow-soft dark:border-white/5 dark:bg-[#1a2332]/70">
        <header className="border-b border-[#e8e6e1]/70 px-5 py-4 dark:border-white/5">
          <h2 className="text-sm font-bold text-[#1e293b] dark:text-white">Backup history</h2>
          <p className="mt-1 text-xs text-slate-500">Each completed file is verified with SHA-256 before download or restore.</p>
        </header>
        {loading && backups.length === 0 ? (
          <div className="p-5"><ShimmerTable rows={5} cols={5} /></div>
        ) : backups.length === 0 ? (
          <EmptyState icon={DatabaseBackup} title="No backups yet" description="Create the first recoverable database snapshot." action={{ label: 'Create backup', onClick: create }} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-left">
              <thead className="bg-[#f8f8f5] text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:bg-white/[0.025]">
                <tr><th className="px-5 py-3">Backup</th><th className="px-4 py-3">Policy</th><th className="px-4 py-3">Size</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Checksum</th><th className="px-5 py-3 text-right">Actions</th></tr>
              </thead>
              <tbody className="divide-y divide-[#e8e6e1]/60 dark:divide-white/5">
                {backups.map((backup) => (
                  <tr key={backup.id}>
                    <td className="px-5 py-4">
                      <p className="max-w-[260px] truncate text-sm font-semibold text-[#1e293b] dark:text-white">{backup.fileName || `Backup #${backup.id}`}</p>
                      <p className="mt-1 text-xs text-slate-400">{new Date(backup.createdAt).toLocaleString()} by {backup.createdBy || 'system'}</p>
                      {backup.errorMessage && <p className="mt-1 max-w-md truncate text-xs text-rose-500" title={backup.errorMessage}>{backup.errorMessage}</p>}
                    </td>
                    <td className="px-4 py-4 text-xs font-semibold text-slate-600 dark:text-slate-300">{backup.type.replace('_', ' ')}</td>
                    <td className="px-4 py-4 text-xs text-slate-500">{formatBytes(backup.sizeBytes)}</td>
                    <td className="px-4 py-4"><span className={`rounded-md px-2 py-1 text-[10px] font-bold ${statusClass(backup.status)}`}>{backup.status}</span></td>
                    <td className="px-4 py-4 font-mono text-[10px] text-slate-400" title={backup.sha256}>{backup.sha256 ? `${backup.sha256.slice(0, 12)}...` : '-'}</td>
                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-1.5">
                        {(backup.status === 'COMPLETED' || backup.status === 'RESTORED') && (
                          <>
                            <button onClick={() => download(backup)} disabled={busyId === backup.id} title="Download backup" className="rounded-lg border border-[#e8e6e1] p-2 text-slate-500 hover:text-brand-600 disabled:opacity-40 dark:border-white/10"><Download size={15} /></button>
                            <button onClick={() => { setRestoreTarget(backup); setConfirmation(''); }} disabled={!restoreEnabled || busyId === backup.id} title={restoreEnabled ? 'Restore database' : 'Restore disabled'} className="rounded-lg border border-amber-200 p-2 text-amber-600 disabled:cursor-not-allowed disabled:opacity-30 dark:border-amber-500/20"><RotateCcw size={15} /></button>
                          </>
                        )}
                        <button onClick={() => remove(backup)} disabled={busyId === backup.id || backup.status === 'RUNNING' || backup.status === 'RESTORING'} title="Delete backup" className="rounded-lg border border-rose-200 p-2 text-rose-600 disabled:opacity-30 dark:border-rose-500/20"><Trash2 size={15} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {restoreTarget && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
          <section className="w-full max-w-lg rounded-2xl border border-rose-200 bg-white p-6 shadow-2xl dark:border-rose-500/20 dark:bg-[#17202e]">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-600 dark:bg-rose-500/10"><AlertTriangle size={20} /></div>
              <div>
                <h2 className="text-lg font-bold text-[#1e293b] dark:text-white">Restore the production database?</h2>
                <p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">This creates a safety backup, validates the selected checksum, then runs PostgreSQL restore with clean mode. Active requests must be stopped during this maintenance operation.</p>
              </div>
            </div>
            <label className="mt-5 block">
              <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">Type <code className="text-rose-600">RESTORE {restoreTarget.id}</code> to confirm</span>
              <input value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="mt-2 w-full rounded-xl border border-[#e8e6e1] bg-[#f8f8f5] px-3 py-3 font-mono text-sm outline-none focus:border-rose-400 dark:border-white/10 dark:bg-white/5 dark:text-white" />
            </label>
            <div className="mt-6 flex justify-end gap-2">
              <button onClick={() => { setRestoreTarget(null); setConfirmation(''); }} className="rounded-xl border border-[#e8e6e1] px-4 py-2.5 text-sm font-semibold text-slate-600 dark:border-white/10 dark:text-slate-300">Cancel</button>
              <button onClick={restore} disabled={confirmation !== `RESTORE ${restoreTarget.id}` || busyId === restoreTarget.id} className="rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-40">{busyId === restoreTarget.id ? 'Restoring...' : 'Restore database'}</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
