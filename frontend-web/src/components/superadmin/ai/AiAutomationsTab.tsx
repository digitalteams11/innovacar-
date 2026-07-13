import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DataTable, Badge, ToggleSwitch } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiAutomationRow } from './types';

export default function AiAutomationsTab() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [automations, setAutomations] = useState<AiAutomationRow[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await aiService.getAutomations();
      setAutomations(data?.data || []);
    } catch {
      showToast(t('superAdmin.ai.automations.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const toggle = async (row: AiAutomationRow) => {
    try {
      if (row.enabled) {
        await aiService.disableAutomation(row.id);
      } else {
        await aiService.enableAutomation(row.id);
      }
      await load();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.automations.updateError'), 'error');
    }
  };

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-amber-200 dark:border-amber-800/40 bg-amber-50 dark:bg-amber-900/10 px-4 py-3 text-xs text-amber-700 dark:text-amber-400">
        {t('superAdmin.ai.automations.wiredNotice')}
      </div>

      <DataTable
        columns={[
          {
            key: 'name', header: t('superAdmin.ai.automations.colName'), render: (row: AiAutomationRow) => (
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-medium text-[#1e293b] dark:text-white">{row.name}</span>
                  {row.wired
                    ? <Badge variant="success">{t('superAdmin.ai.automations.wired')}</Badge>
                    : <Badge variant="default">{t('superAdmin.ai.automations.catalogOnly')}</Badge>}
                </div>
                <p className="text-xs text-slate-400 mt-0.5">{row.code}</p>
              </div>
            ),
          },
          { key: 'featureType', header: t('superAdmin.ai.automations.colCategory'), render: (row: AiAutomationRow) => row.featureType || 'â€”' },
          { key: 'description', header: t('superAdmin.ai.automations.colDescription'), render: (row: AiAutomationRow) => <span className="text-slate-500 dark:text-slate-400 text-sm">{row.description || 'â€”'}</span> },
          {
            key: 'enabled', header: t('superAdmin.ai.automations.colEnabled'), align: 'right', render: (row: AiAutomationRow) => (
              <ToggleSwitch checked={row.enabled} onChange={() => toggle(row)} />
            ),
          },
        ]}
        data={automations}
        loading={loading}
        keyExtractor={(row: AiAutomationRow) => row.id}
        emptyTitle={t('superAdmin.ai.automations.emptyTitle')}
        emptyDescription={t('superAdmin.ai.automations.emptyDesc')}
      />
    </div>
  );
}
