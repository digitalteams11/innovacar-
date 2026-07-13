import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader, TabGroup } from '../../components/superadmin';
import AiOverviewTab from '../../components/superadmin/ai/AiOverviewTab';
import AiProvidersTab from '../../components/superadmin/ai/AiProvidersTab';
import AiModelsTab from '../../components/superadmin/ai/AiModelsTab';
import AiAutomationsTab from '../../components/superadmin/ai/AiAutomationsTab';
import AiPromptsTab from '../../components/superadmin/ai/AiPromptsTab';
import AiUsageCostsTab from '../../components/superadmin/ai/AiUsageCostsTab';
import AiLogsErrorsTab from '../../components/superadmin/ai/AiLogsErrorsTab';
import AiSettingsTab from '../../components/superadmin/ai/AiSettingsTab';

const TAB_IDS = ['overview', 'providers', 'models', 'automations', 'prompts', 'usage', 'logs', 'settings'] as const;
type TabId = typeof TAB_IDS[number];

export default function SuperAdminAiSettings() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [refreshKey, setRefreshKey] = useState(0);

  const tabs = TAB_IDS.map((id) => ({ id, label: t(`superAdmin.ai.tabs.${id}`) }));

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title={t('superAdmin.ai.pageTitle')} subtitle={t('superAdmin.ai.pageSubtitle')} />

      <TabGroup tabs={tabs} activeTab={activeTab} onChange={(id) => setActiveTab(id as TabId)} />

      <div key={`${activeTab}-${refreshKey}`}>
        {activeTab === 'overview' && <AiOverviewTab />}
        {activeTab === 'providers' && <AiProvidersTab onChanged={() => setRefreshKey((k) => k + 1)} />}
        {activeTab === 'models' && <AiModelsTab />}
        {activeTab === 'automations' && <AiAutomationsTab />}
        {activeTab === 'prompts' && <AiPromptsTab />}
        {activeTab === 'usage' && <AiUsageCostsTab />}
        {activeTab === 'logs' && <AiLogsErrorsTab />}
        {activeTab === 'settings' && <AiSettingsTab onChanged={() => setRefreshKey((k) => k + 1)} />}
      </div>
    </div>
  );
}
