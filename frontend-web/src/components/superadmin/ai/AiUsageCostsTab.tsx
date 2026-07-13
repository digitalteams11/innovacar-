import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DollarSign, Activity, TrendingUp, Clock } from 'lucide-react';
import { StatCard, DataTable, Badge } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiUsageSummary, AiUsageLogRow } from './types';

export default function AiUsageCostsTab() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [summary, setSummary] = useState<AiUsageSummary | null>(null);
  const [logs, setLogs] = useState<AiUsageLogRow[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const pageSize = 20;

  useEffect(() => {
    (async () => {
      try {
        const { data } = await aiService.getUsageSummary();
        setSummary(data?.data || null);
      } catch {
        setSummary(null);
      }
    })();
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const { data } = await aiService.getUsageLogs(page - 1, pageSize);
        setLogs(data?.data?.items || []);
        setTotal(data?.data?.totalElements || 0);
      } catch {
        showToast(t('superAdmin.ai.usage.loadError'), 'error');
      } finally {
        setLoading(false);
      }
    })();
  }, [page]);

  const totalCost = logs.reduce((sum, l) => sum + (l.estimatedCost || 0), 0);
  const successRate = summary && summary.requestsToday > 0
    ? Math.round((summary.successfulToday / summary.requestsToday) * 100)
    : null;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard title={t('superAdmin.ai.usage.requestsToday')} value={summary?.requestsToday ?? 0} icon={Activity} tone="blue" />
        <StatCard title={t('superAdmin.ai.usage.successRate')} value={successRate !== null ? `${successRate}%` : 'â€”'} icon={TrendingUp} tone="emerald" />
        <StatCard title={t('superAdmin.ai.usage.avgLatency')} value={summary?.averageLatencyMs ? `${Math.round(summary.averageLatencyMs)}ms` : 'â€”'} icon={Clock} tone="neutral" />
        <StatCard title={t('superAdmin.ai.usage.estimatedCostPage')} value={`$${totalCost.toFixed(4)}`} icon={DollarSign} tone="violet" />
      </div>

      <div>
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white mb-3">{t('superAdmin.ai.usage.recentRequests')}</h3>
        <DataTable
          columns={[
            { key: 'createdAt', header: t('superAdmin.ai.usage.colWhen'), render: (row: AiUsageLogRow) => <span className="text-xs text-slate-400">{new Date(row.createdAt).toLocaleString()}</span> },
            { key: 'automationCode', header: t('superAdmin.ai.usage.colAutomation'), render: (row: AiUsageLogRow) => row.automationCode || 'â€”' },
            { key: 'status', header: t('superAdmin.ai.usage.colStatus'), render: (row: AiUsageLogRow) => <Badge variant={row.status === 'SUCCESS' ? 'success' : 'danger'}>{row.status}</Badge> },
            { key: 'totalTokens', header: t('superAdmin.ai.usage.colTokens'), render: (row: AiUsageLogRow) => row.totalTokens ?? 'â€”' },
            { key: 'latencyMs', header: t('superAdmin.ai.usage.colLatency'), render: (row: AiUsageLogRow) => row.latencyMs ? `${row.latencyMs}ms` : 'â€”' },
            { key: 'estimatedCost', header: t('superAdmin.ai.usage.colCost'), render: (row: AiUsageLogRow) => row.estimatedCost ? `$${row.estimatedCost.toFixed(5)}` : 'â€”' },
            { key: 'agencyId', header: t('superAdmin.ai.usage.colAgency'), render: (row: AiUsageLogRow) => row.agencyId ?? t('superAdmin.ai.usage.platform') },
          ]}
          data={logs}
          loading={loading}
          keyExtractor={(row: AiUsageLogRow) => row.id}
          emptyTitle={t('superAdmin.ai.usage.emptyTitle')}
          emptyDescription={t('superAdmin.ai.usage.emptyDesc')}
          pagination={{ page, pageSize, total, onPageChange: setPage }}
        />
      </div>
    </div>
  );
}
