import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Trash2 } from 'lucide-react';
import { DataTable, Badge } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiUsageLogRow } from './types';

export default function AiLogsErrorsTab() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [errors, setErrors] = useState<AiUsageLogRow[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [clearing, setClearing] = useState(false);
  const pageSize = 20;

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await aiService.getUsageErrors(page - 1, pageSize);
      setErrors(data?.data?.items || []);
      setTotal(data?.data?.totalElements || 0);
    } catch {
      showToast(t('superAdmin.ai.logs.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page]);

  const deleteLog = async (id: number) => {
    try {
      await aiService.deleteUsageLog(id);
      setErrors((prev) => prev.filter((r) => r.id !== id));
      showToast(t('superAdmin.ai.logs.deleteSuccess'), 'success');
    } catch {
      showToast(t('superAdmin.ai.logs.deleteError'), 'error');
    }
  };

  const clearAll = async () => {
    if (!confirm(t('superAdmin.ai.logs.confirmClear'))) return;
    setClearing(true);
    try {
      await aiService.clearUsageLogs();
      setErrors([]);
      setTotal(0);
      showToast(t('superAdmin.ai.logs.clearSuccess'), 'success');
    } catch {
      showToast(t('superAdmin.ai.logs.clearError'), 'error');
    } finally {
      setClearing(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        {errors.length > 0 && (
          <button
            onClick={clearAll}
            disabled={clearing}
            className="flex items-center gap-2 text-sm text-rose-600 hover:text-rose-700 dark:text-rose-400 disabled:opacity-50"
          >
            <Trash2 size={14} />
            {clearing ? t('superAdmin.ai.logs.clearing') : t('superAdmin.ai.logs.clearAll')}
          </button>
        )}
      </div>

      <DataTable
        columns={[
          { key: 'createdAt', header: t('superAdmin.ai.logs.colWhen'), render: (row: AiUsageLogRow) => <span className="text-xs text-slate-400">{new Date(row.createdAt).toLocaleString()}</span> },
          { key: 'errorCode', header: t('superAdmin.ai.logs.colError'), render: (row: AiUsageLogRow) => <Badge variant="danger">{row.errorCode || 'UNKNOWN'}</Badge> },
          { key: 'automationCode', header: t('superAdmin.ai.logs.colAutomation'), render: (row: AiUsageLogRow) => row.automationCode || 'â€”' },
          { key: 'agencyId', header: t('superAdmin.ai.logs.colAgency'), render: (row: AiUsageLogRow) => row.agencyId ?? t('superAdmin.ai.usage.platform') },
          {
            key: 'actions', header: '', align: 'right', render: (row: AiUsageLogRow) => (
              <button onClick={() => deleteLog(row.id)} className="p-1.5 text-slate-400 hover:text-rose-500 dark:hover:text-rose-400" title={t('superAdmin.ai.logs.delete')}>
                <Trash2 size={14} />
              </button>
            ),
          },
        ]}
        data={errors}
        loading={loading}
        keyExtractor={(row: AiUsageLogRow) => row.id}
        emptyTitle={t('superAdmin.ai.logs.emptyTitle')}
        emptyDescription={t('superAdmin.ai.logs.emptyDesc')}
        pagination={{ page, pageSize, total, onPageChange: setPage }}
      />
    </div>
  );
}
