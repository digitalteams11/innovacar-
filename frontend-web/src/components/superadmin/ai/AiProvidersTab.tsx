import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, PlugZap, Power, PowerOff, Trash2, Pencil } from 'lucide-react';
import { DataTable, Modal, Badge, ActionMenu, FormField, TextInput, SelectInput, ToggleSwitch } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import { PROVIDER_TYPES, PROVIDER_DEFAULT_BASE_URL } from './types';
import type { AiProviderRow } from './types';

const emptyForm = {
  id: 0,
  name: '',
  providerType: 'GROQ',
  baseUrl: '',
  apiKey: '',
  organizationId: '',
  enabled: true,
};

export default function AiProvidersTab({ onChanged }: { onChanged?: () => void }) {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [providers, setProviders] = useState<AiProviderRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await aiService.getProviders();
      setProviders(data?.data || []);
    } catch {
      showToast(t('superAdmin.ai.providers.loadError'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (row: AiProviderRow) => {
    setEditingId(row.id);
    setForm({
      id: row.id,
      name: row.name,
      providerType: row.providerType,
      baseUrl: row.baseUrl || '',
      apiKey: '',
      organizationId: row.organizationId || '',
      enabled: row.enabled,
    });
    setModalOpen(true);
  };

  const onProviderTypeChange = (value: string) => {
    setForm((prev) => ({
      ...prev,
      providerType: value,
      baseUrl: prev.baseUrl || PROVIDER_DEFAULT_BASE_URL[value] || '',
    }));
  };

  const save = async () => {
    setSaving(true);
    try {
      if (editingId) {
        await aiService.updateProvider(editingId, {
          name: form.name,
          baseUrl: form.baseUrl || undefined,
          apiKey: form.apiKey || undefined,
          organizationId: form.organizationId || undefined,
          enabled: form.enabled,
        });
        showToast(t('superAdmin.ai.providers.updateSuccess'), 'success');
      } else {
        await aiService.createProvider({
          name: form.name || form.providerType,
          providerType: form.providerType,
          baseUrl: form.baseUrl || undefined,
          apiKey: form.apiKey || undefined,
          organizationId: form.organizationId || undefined,
          enabled: form.enabled,
        });
        showToast(t('superAdmin.ai.providers.createSuccess'), 'success');
      }
      setModalOpen(false);
      await load();
      onChanged?.();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.providers.saveError'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const test = async (id: number) => {
    setTestingId(id);
    try {
      const { data } = await aiService.testProviderConnection(id);
      const result = data?.data;
      if (result?.success) {
        showToast(`${t('superAdmin.ai.providers.testSuccess')}${result.latencyMs ? ` Â· ${result.latencyMs}ms` : ''}`, 'success');
      } else {
        showToast(result?.message || t('superAdmin.ai.providers.testFailed'), 'error');
      }
      await load();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.providers.testFailed'), 'error');
    } finally {
      setTestingId(null);
    }
  };

  const activate = async (id: number) => {
    try {
      await aiService.activateProvider(id);
      showToast(t('superAdmin.ai.providers.activateSuccess'), 'success');
      await load();
      onChanged?.();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.providers.activateError'), 'error');
    }
  };

  const disable = async (id: number) => {
    try {
      await aiService.disableProvider(id);
      showToast(t('superAdmin.ai.providers.disableSuccess'), 'success');
      await load();
      onChanged?.();
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.providers.disableError'), 'error');
    }
  };

  const remove = async (row: AiProviderRow) => {
    // Defensive: never fire a request with a malformed id (stale row, etc.) —
    // fail fast in the UI instead of letting the backend reject it.
    if (!Number.isFinite(row.id)) {
      showToast(t('superAdmin.ai.providers.deleteError'), 'error');
      return;
    }
    if (row.isActive) {
      // Mirrors the backend rule (AI_PROVIDER_IN_USE) so the admin sees the
      // real reason immediately instead of firing a request that's always
      // going to be rejected.
      showToast(t('superAdmin.ai.providers.cannotDeleteActive'), 'warning');
      return;
    }
    if (!confirm(t('superAdmin.ai.providers.confirmDelete'))) return;
    try {
      await aiService.deleteProvider(row.id);
      showToast(t('superAdmin.ai.providers.deleteSuccess'), 'success');
      await load();
      onChanged?.();
    } catch (err: any) {
      const errorCode = err?.response?.data?.errorCode;
      const message = err?.response?.data?.message;
      if (errorCode === 'AI_PROVIDER_IN_USE') {
        // Provider was activated by someone else between page load and this
        // click — refresh so the row reflects reality instead of leaving a
        // stale "Delete" option showing.
        showToast(message || t('superAdmin.ai.providers.cannotDeleteActive'), 'warning');
        await load();
        return;
      }
      showToast(message || t('superAdmin.ai.providers.deleteError'), 'error');
    }
  };

  const connectionBadge = (status: string) => {
    if (status === 'CONNECTED') return <Badge variant="success">{t('superAdmin.ai.status.connected')}</Badge>;
    if (status === 'FAILED') return <Badge variant="danger">{t('superAdmin.ai.status.failed')}</Badge>;
    if (status === 'DISABLED') return <Badge variant="default">{t('superAdmin.ai.status.disabled')}</Badge>;
    return <Badge variant="warning">{t('superAdmin.ai.status.notTested')}</Badge>;
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={openCreate}
          className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors"
        >
          <Plus size={16} />
          {t('superAdmin.ai.providers.add')}
        </button>
      </div>

      <DataTable
        columns={[
          {
            key: 'name', header: t('superAdmin.ai.providers.colName'), render: (row: AiProviderRow) => (
              <div className="flex items-center gap-2">
                <span className="font-medium text-[#1e293b] dark:text-white">{row.name}</span>
                {row.isActive && <Badge variant="primary">{t('superAdmin.ai.providers.active')}</Badge>}
              </div>
            ),
          },
          { key: 'providerType', header: t('superAdmin.ai.providers.colType') },
          { key: 'apiKeyMasked', header: t('superAdmin.ai.providers.colKey'), render: (row: AiProviderRow) => row.apiKeyMasked || t('superAdmin.ai.providers.notConfigured') },
          { key: 'connectionStatus', header: t('superAdmin.ai.providers.colStatus'), render: (row: AiProviderRow) => connectionBadge(row.connectionStatus) },
          { key: 'enabledModelCount', header: t('superAdmin.ai.providers.colModels') },
          {
            key: 'actions', header: '', align: 'right', render: (row: AiProviderRow) => (
              <ActionMenu
                items={[
                  { label: t('superAdmin.ai.providers.edit'), icon: <Pencil size={14} />, onClick: () => openEdit(row) },
                  { label: testingId === row.id ? t('superAdmin.ai.providers.testing') : t('superAdmin.ai.providers.test'), icon: <PlugZap size={14} />, onClick: () => test(row.id), disabled: testingId === row.id },
                  row.isActive
                    ? { label: t('superAdmin.ai.providers.disable'), icon: <PowerOff size={14} />, onClick: () => disable(row.id) }
                    : { label: t('superAdmin.ai.providers.activate'), icon: <Power size={14} />, onClick: () => activate(row.id) },
                  {
                    label: row.isActive ? t('superAdmin.ai.providers.deleteBlockedActive') : t('superAdmin.ai.providers.delete'),
                    icon: <Trash2 size={14} />,
                    onClick: () => remove(row),
                    danger: true,
                    disabled: row.isActive,
                  },
                ]}
              />
            ),
          },
        ]}
        data={providers}
        loading={loading}
        keyExtractor={(row: AiProviderRow) => row.id}
        emptyTitle={t('superAdmin.ai.providers.emptyTitle')}
        emptyDescription={t('superAdmin.ai.providers.emptyDesc')}
      />

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('superAdmin.ai.providers.editTitle') : t('superAdmin.ai.providers.addTitle')}
        footer={
          <div className="flex justify-end gap-2">
            <button onClick={() => setModalOpen(false)} className="px-4 py-2.5 rounded-xl text-sm font-semibold text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5">
              {t('superAdmin.ai.common.cancel')}
            </button>
            <button
              onClick={save}
              disabled={saving || !form.name.trim()}
              className="px-4 py-2.5 rounded-xl text-sm font-semibold bg-[#0a0f2c] text-white hover:bg-[#0a0f2c]/90 disabled:opacity-50"
            >
              {saving ? t('superAdmin.ai.common.saving') : t('superAdmin.ai.common.save')}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <FormField label={t('superAdmin.ai.providers.fieldName')} required>
            <TextInput value={form.name} onChange={(v) => setForm((p) => ({ ...p, name: v }))} placeholder="Groq" />
          </FormField>
          {!editingId && (
            <FormField label={t('superAdmin.ai.providers.fieldType')}>
              <SelectInput
                value={form.providerType}
                onChange={onProviderTypeChange}
                options={PROVIDER_TYPES.map((pt) => ({ value: pt, label: pt }))}
              />
            </FormField>
          )}
          <FormField
            label={t('superAdmin.ai.providers.fieldBaseUrl')}
            hint={form.providerType === 'CUSTOM_OPENAI_COMPATIBLE' ? t('superAdmin.ai.providers.baseUrlHintCustom') : undefined}
          >
            <TextInput
              value={form.baseUrl}
              onChange={(v) => setForm((p) => ({ ...p, baseUrl: v }))}
              placeholder={PROVIDER_DEFAULT_BASE_URL[form.providerType] || 'https://...'}
              disabled={form.providerType !== 'CUSTOM_OPENAI_COMPATIBLE' && form.providerType !== 'GEMINI'}
            />
          </FormField>
          <FormField
            label={t('superAdmin.ai.providers.fieldApiKey')}
            hint={editingId ? t('superAdmin.ai.providers.apiKeyHintEdit') : t('superAdmin.ai.providers.apiKeyHintNew')}
          >
            <input
              type="password"
              autoComplete="new-password"
              value={form.apiKey}
              onChange={(e) => setForm((p) => ({ ...p, apiKey: e.target.value }))}
              placeholder={editingId ? t('superAdmin.ai.providers.apiKeyPlaceholderEdit') : ''}
              className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none focus:ring-2 ring-brand-100/50"
            />
          </FormField>
          <FormField label={t('superAdmin.ai.providers.fieldOrgId')}>
            <TextInput value={form.organizationId} onChange={(v) => setForm((p) => ({ ...p, organizationId: v }))} />
          </FormField>
          <ToggleSwitch checked={form.enabled} onChange={(v) => setForm((p) => ({ ...p, enabled: v }))} label={t('superAdmin.ai.providers.fieldEnabled')} />
        </div>
      </Modal>
    </div>
  );
}
