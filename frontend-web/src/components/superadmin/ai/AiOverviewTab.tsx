import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Activity, CheckCircle2, XCircle, Clock, Zap, AlertTriangle } from 'lucide-react';
import { StatCard, Badge, EmptyState } from '..';
import { aiService } from '../../../api/aiService';
import type { AiUsageSummary, AiUsageLogRow } from './types';

export default function AiOverviewTab() {
  const { t } = useTranslation();
  const [summary, setSummary] = useState<AiUsageSummary | null>(null);
  const [recentErrors, setRecentErrors] = useState<AiUsageLogRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [summaryRes, errorsRes] = await Promise.all([
          aiService.getUsageSummary(),
          aiService.getUsageErrors(0, 5),
        ]);
        if (cancelled) return;
        setSummary(summaryRes.data?.data || null);
        setRecentErrors(errorsRes.data?.data?.items || []);
      } catch {
        if (!cancelled) {
          setSummary(null);
          setRecentErrors([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const connected = summary?.connectionStatus === 'CONNECTED';

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <StatCard
          title={t('superAdmin.ai.overview.globalStatus')}
          value={summary?.globalEnabled ? t('superAdmin.ai.common.enabled') : t('superAdmin.ai.common.disabled')}
          icon={summary?.globalEnabled ? CheckCircle2 : XCircle}
          tone={summary?.globalEnabled ? 'emerald' : 'rose'}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.activeProvider')}
          value={summary?.activeProviderName || t('superAdmin.ai.overview.none')}
          icon={Zap}
          tone="blue"
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.connection')}
          value={connected ? t('superAdmin.ai.status.connected') : t('superAdmin.ai.status.notConnected')}
          icon={connected ? CheckCircle2 : AlertTriangle}
          tone={connected ? 'emerald' : 'amber'}
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.requestsToday')}
          value={summary?.requestsToday ?? 0}
          icon={Activity}
          tone="violet"
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.successfulToday')}
          value={summary?.successfulToday ?? 0}
          icon={CheckCircle2}
          tone="emerald"
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.failedToday')}
          value={summary?.failedToday ?? 0}
          icon={XCircle}
          tone="rose"
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.avgLatency')}
          value={summary?.averageLatencyMs ? `${Math.round(summary.averageLatencyMs)}ms` : 'â€”'}
          icon={Clock}
          tone="neutral"
          loading={loading}
        />
        <StatCard
          title={t('superAdmin.ai.overview.activeModel')}
          value={summary?.activeModel || t('superAdmin.ai.overview.none')}
          icon={Zap}
          tone="blue"
          loading={loading}
        />
      </div>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6 space-y-4">
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{t('superAdmin.ai.overview.recentErrors')}</h3>
        {recentErrors.length === 0 ? (
          <EmptyState title={t('superAdmin.ai.overview.noErrors')} description={t('superAdmin.ai.overview.noErrorsDesc')} />
        ) : (
          <div className="space-y-2">
            {recentErrors.map((row) => (
              <div key={row.id} className="flex items-center justify-between gap-3 text-sm rounded-xl border border-[#e8e6e1]/60 dark:border-white/5 px-4 py-2.5">
                <div className="flex items-center gap-3 min-w-0">
                  <Badge variant="danger">{row.errorCode || 'ERROR'}</Badge>
                  <span className="text-slate-500 dark:text-slate-400 truncate">{row.automationCode || 'â€”'}</span>
                </div>
                <span className="text-xs text-slate-400 shrink-0">{new Date(row.createdAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
