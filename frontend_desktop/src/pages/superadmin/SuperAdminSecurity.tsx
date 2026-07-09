import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { superAdminApi } from '../../api/superAdminApi';
import { Shield, AlertTriangle, CheckCircle, XCircle, X } from 'lucide-react';
import { PageHeader, StatCard, DataTable, TabGroup, Badge } from '../../components/superadmin';
import { useToast } from '../../context/ToastContext';

export default function SuperAdminSecurity() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [summary, setSummary] = useState<any>(null);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [loginHistory, setLoginHistory] = useState<any[]>([]);
  const [sessions, setSessions] = useState<any[]>([]);
  const [failedLogins, setFailedLogins] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    fetchData();
  }, []);

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
    { id: 'overview', label: 'Overview' },
    { id: 'audit', label: 'Audit Logs' },
    { id: 'logins', label: 'Login History' },
    { id: 'sessions', label: 'Active Sessions' },
    { id: 'failed', label: 'Failed Logins' },
  ];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title={t('superAdmin.security.title')} subtitle={t('superAdmin.security.subtitle')} />

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <StatCard title={t('superAdmin.security.events24h')} value={summary?.totalEvents24h || 0} icon={Shield} loading={loading} />
        <StatCard title={t('superAdmin.security.failedLogins')} value={summary?.failedLogins24h || 0} icon={AlertTriangle} loading={loading} />
        <StatCard title={t('superAdmin.security.suspicious')} value={summary?.suspiciousEvents || 0} icon={XCircle} loading={loading} />
        <StatCard title={t('superAdmin.security.activeSessions')} value={summary?.activeSessions || 0} icon={CheckCircle} loading={loading} />
      </div>

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-3">Recent Suspicious Activity</h3>
                {auditLogs.filter((l: any) => !l.isSuccess).slice(0, 5).length === 0 ? (
                  <p className="text-sm text-slate-400">No suspicious events detected.</p>
                ) : (
                  <div className="space-y-2">
                    {auditLogs.filter((l: any) => !l.isSuccess).slice(0, 5).map((log: any) => (
                      <div key={log.id} className="flex items-center gap-3 p-2 rounded-lg bg-white dark:bg-[#1a2332]/50 border border-rose-200 dark:border-rose-500/20">
                        <AlertTriangle size={14} className="text-rose-500 shrink-0" />
                        <div className="flex-1 min-w-0">
                          <p className="text-xs font-medium text-[#1e293b] dark:text-white">{log.action}</p>
                          <p className="text-xs text-slate-500 truncate">{log.description}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div className="p-4 bg-slate-50 dark:bg-white/5 rounded-xl">
                <h3 className="text-sm font-bold text-[#1e293b] dark:text-white mb-3">Security Recommendations</h3>
                <div className="space-y-2">
                  {[
                    { text: 'Enable 2FA for all admin accounts', status: 'pending' },
                    { text: 'Review API key permissions', status: 'done' },
                    { text: 'Update password policy', status: 'pending' },
                    { text: 'Enable IP whitelisting', status: 'pending' },
                  ].map((rec, idx) => (
                    <div key={idx} className="flex items-center gap-2 text-sm">
                      {rec.status === 'done' ? <CheckCircle size={14} className="text-emerald-500" /> : <div className="w-3.5 h-3.5 rounded-full border-2 border-slate-300" />}
                      <span className={rec.status === 'done' ? 'text-slate-400 line-through' : 'text-[#1e293b] dark:text-white'}>{rec.text}</span>
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
              { key: 'action', header: t('superAdmin.security.action') },
              { key: 'description', header: t('superAdmin.security.description') },
              { key: 'user', header: t('superAdmin.security.user'), render: (row: any) => <span className="text-sm text-slate-500">{row.performedBy || '-'}</span> },
              { key: 'status', header: t('superAdmin.security.status'), render: (row: any) => <Badge variant={row.isSuccess ? 'success' : 'danger'}>{row.isSuccess ? 'Success' : 'Failed'}</Badge> },
              { key: 'time', header: t('superAdmin.security.time'), render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
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
              { key: 'user', header: 'User' },
              { key: 'ip', header: 'IP Address', render: (row: any) => <span className="text-sm font-mono text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'result', header: 'Result', render: (row: any) => <Badge variant={row.success ? 'success' : 'danger'}>{row.success ? 'Success' : 'Failed'}</Badge> },
              { key: 'time', header: 'Time', render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
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
              { key: 'user', header: 'User', render: (row: any) => <span className="text-sm text-[#1e293b] dark:text-white">{row.userId || 'User ' + row.userId}</span> },
              { key: 'ip', header: 'IP Address', render: (row: any) => <span className="text-sm font-mono text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'created', header: 'Created', render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
              { key: 'expires', header: 'Expires', render: (row: any) => <span className="text-xs text-slate-500">{row.expiresAt ? new Date(row.expiresAt).toLocaleString() : '-'}</span> },
              { key: 'actions', header: '', align: 'right' as const, render: (row: any) => (
                <button onClick={() => handleRevokeSession(row.id)} className="p-2 hover:bg-rose-50 dark:hover:bg-rose-500/10 rounded-lg text-slate-400 hover:text-rose-600 transition-colors" title="Revoke">
                  <X size={16} />
                </button>
              )},
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
              { key: 'user', header: 'User', render: (row: any) => <span className="text-sm text-[#1e293b] dark:text-white">{row.performedBy || '-'}</span> },
              { key: 'ip', header: 'IP Address', render: (row: any) => <span className="text-sm font-mono text-slate-500">{row.ipAddress || '-'}</span> },
              { key: 'reason', header: 'Reason', render: (row: any) => <span className="text-sm text-slate-500">{row.reason || 'Invalid credentials'}</span> },
              { key: 'time', header: 'Time', render: (row: any) => <span className="text-xs text-slate-500">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</span> },
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
