import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { RefreshCw, Plus, Star } from 'lucide-react';
import { DataTable, Badge, FilterSelect, Modal, FormField, TextInput, ActionMenu } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiModelRow, AiProviderRow } from './types';

export default function AiModelsTab() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [providers, setProviders] = useState<AiProviderRow[]>([]);
  const [providerId, setProviderId] = useState<string>('');
  const [models, setModels] = useState<AiModelRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ modelId: '', displayName: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await aiService.getProviders();
        const list: AiProviderRow[] = data?.data || [];
        setProviders(list);
        if (list.length > 0) setProviderId(String(list[0].id));
      } catch {
        setProviders([]);
      }
    })();
  }, []);

  const loadModels = async (id: string) => {
    if (!id) { setModels([]); return; }
    setLoading(true);
    try {
      const { data } = await aiService.getModels(Number(id));
      setModels(data?.data || []);
    } catch {
      showToast(t('superAdmin.ai.models.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadModels(providerId); }, [providerId]);

  const sync = async () => {
    if (!providerId) return;
    setSyncing(true);
    try {
      const { data } = await aiService.syncModels(Number(providerId));
      setModels(data?.data || []);
      showToast(t('superAdmin.ai.models.syncSuccess'), 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.models.syncError'), 'error');
    } finally {
      setSyncing(false);
    }
  };

  const addModel = async () => {
    if (!providerId || !form.modelId.trim()) return;
    setSaving(true);
    try {
      await aiService.addModel(Number(providerId), { modelId: form.modelId.trim(), displayName: form.displayName || undefined });
      showToast(t('superAdmin.ai.models.addSuccess'), 'success');
      setModalOpen(false);
      setForm({ modelId: '', displayName: '' });
      await loadModels(providerId);
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.models.addError'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (row: AiModelRow) => {
    try {
      await aiService.setModelEnabled(row.id, !row.enabled);
      await loadModels(providerId);
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.models.updateError'), 'error');
    }
  };

  const setDefault = async (row: AiModelRow) => {
    try {
      await aiService.setDefaultModel(row.id);
      showToast(t('superAdmin.ai.models.defaultSuccess'), 'success');
      await loadModels(providerId);
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.models.updateError'), 'error');
    }
  };

  const remove = async (row: AiModelRow) => {
    if (!confirm(t('superAdmin.ai.models.confirmDelete'))) return;
    try {
      await aiService.deleteModel(row.id);
      await loadModels(providerId);
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.models.deleteError'), 'error');
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <FilterSelect
          options={providers.map((p) => ({ value: String(p.id), label: `${p.name} (${p.providerType})` }))}
          value={providerId}
          onChange={setProviderId}
          placeholder={t('superAdmin.ai.models.selectProvider')}
          className="w-64"
        />
        <div className="flex items-center gap-2">
          <button
            onClick={sync}
            disabled={!providerId || syncing}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 text-sm font-semibold text-[#1e293b] dark:text-white hover:bg-slate-50 dark:hover:bg-white/5 disabled:opacity-50"
          >
            <RefreshCw size={15} className={syncing ? 'animate-spin' : ''} />
            {syncing ? t('superAdmin.ai.models.syncing') : t('superAdmin.ai.models.sync')}
          </button>
          <button
            onClick={() => setModalOpen(true)}
            disabled={!providerId}
            className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50"
          >
            <Plus size={16} />
            {t('superAdmin.ai.models.addManually')}
          </button>
        </div>
      </div>

      <DataTable
        columns={[
          {
            key: 'modelId', header: t('superAdmin.ai.models.colModel'), render: (row: AiModelRow) => (
              <div className="flex items-center gap-2">
                <span className="font-medium text-[#1e293b] dark:text-white">{row.displayName || row.modelId}</span>
                {row.defaultModel && <Badge variant="primary"><Star size={10} className="inline mr-1" />{t('superAdmin.ai.models.default')}</Badge>}
              </div>
            ),
          },
          { key: 'source', header: t('superAdmin.ai.models.colSource'), render: (row: AiModelRow) => <Badge variant={row.source === 'SYNCED' ? 'info' : 'default'}>{row.source}</Badge> },
          { key: 'contextWindow', header: t('superAdmin.ai.models.colContext'), render: (row: AiModelRow) => row.contextWindow ?? 'â€”' },
          {
            key: 'capabilities', header: t('superAdmin.ai.models.colCapabilities'), render: (row: AiModelRow) => (
              <div className="flex gap-1">
                {row.supportsStreaming && <Badge variant="info">{t('superAdmin.ai.models.streaming')}</Badge>}
                {row.supportsJsonMode && <Badge variant="info">{t('superAdmin.ai.models.json')}</Badge>}
                {row.supportsToolCalling && <Badge variant="info">{t('superAdmin.ai.models.tools')}</Badge>}
              </div>
            ),
          },
          { key: 'enabled', header: t('superAdmin.ai.models.colStatus'), render: (row: AiModelRow) => <Badge variant={row.enabled ? 'success' : 'default'}>{row.enabled ? t('superAdmin.ai.common.enabled') : t('superAdmin.ai.common.disabled')}</Badge> },
          {
            key: 'actions', header: '', align: 'right', render: (row: AiModelRow) => (
              <ActionMenu
                items={[
                  { label: row.enabled ? t('superAdmin.ai.models.disable') : t('superAdmin.ai.models.enable'), onClick: () => toggleEnabled(row) },
                  { label: t('superAdmin.ai.models.setDefault'), onClick: () => setDefault(row), disabled: row.defaultModel },
                  { label: t('superAdmin.ai.models.remove'), onClick: () => remove(row), danger: true },
                ]}
              />
            ),
          },
        ]}
        data={models}
        loading={loading}
        keyExtractor={(row: AiModelRow) => row.id}
        emptyTitle={t('superAdmin.ai.models.emptyTitle')}
        emptyDescription={t('superAdmin.ai.models.emptyDesc')}
      />

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={t('superAdmin.ai.models.addTitle')}
        footer={
          <div className="flex justify-end gap-2">
            <button onClick={() => setModalOpen(false)} className="px-4 py-2.5 rounded-xl text-sm font-semibold text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5">
              {t('superAdmin.ai.common.cancel')}
            </button>
            <button onClick={addModel} disabled={saving || !form.modelId.trim()} className="px-4 py-2.5 rounded-xl text-sm font-semibold bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90 disabled:opacity-50">
              {saving ? t('superAdmin.ai.common.saving') : t('superAdmin.ai.common.save')}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label={t('superAdmin.ai.models.fieldModelId')} required>
            <TextInput value={form.modelId} onChange={(v) => setForm((p) => ({ ...p, modelId: v }))} placeholder="llama-3.3-70b-versatile" />
          </FormField>
          <FormField label={t('superAdmin.ai.models.fieldDisplayName')}>
            <TextInput value={form.displayName} onChange={(v) => setForm((p) => ({ ...p, displayName: v }))} />
          </FormField>
        </div>
      </Modal>
    </div>
  );
}
