import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Save } from 'lucide-react';
import { FilterSelect, FormField, TextArea, Badge } from '..';
import { aiService } from '../../../api/aiService';
import { useToast } from '../../../context/ToastContext';
import type { AiAutomationRow } from './types';

/** Suggested variables shown per automation category â€” purely a UI hint, never evaluated client-side. */
const VARIABLES_BY_CATEGORY: Record<string, string[]> = {
  CONTRACT: ['clientName', 'vehicleName', 'startDate', 'endDate', 'amountDue'],
  NOTIFICATION: ['clientName', 'vehicleName', 'dueDate', 'amountDue'],
  SUPPORT: ['clientName', 'ticketSubject', 'lastMessage'],
  REPORTING: ['periodLabel', 'totalRevenue', 'totalReservations'],
  UTILITY: ['inputText', 'targetLanguage'],
  CHAT: [],
};

export default function AiPromptsTab() {
  const { t } = useTranslation();
  const { showToast } = useToast();
  const [automations, setAutomations] = useState<AiAutomationRow[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [userPromptTemplate, setUserPromptTemplate] = useState('');
  const [saving, setSaving] = useState(false);
  const userTemplateRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await aiService.getAutomations();
        const list: AiAutomationRow[] = data?.data || [];
        setAutomations(list);
        if (list.length > 0) setSelectedId(String(list[0].id));
      } catch {
        setAutomations([]);
      }
    })();
  }, []);

  const selected = automations.find((a) => String(a.id) === selectedId);

  useEffect(() => {
    setSystemPrompt(selected?.systemPrompt || '');
    setUserPromptTemplate(selected?.userPromptTemplate || '');
  }, [selectedId]);

  const insertVariable = (name: string) => {
    const token = `{{${name}}}`;
    setUserPromptTemplate((prev) => `${prev}${prev && !prev.endsWith(' ') && !prev.endsWith('\n') ? ' ' : ''}${token}`);
    userTemplateRef.current?.focus();
  };

  const save = async () => {
    if (!selected) return;
    setSaving(true);
    try {
      await aiService.updateAutomation(selected.id, { systemPrompt, userPromptTemplate });
      showToast(t('superAdmin.ai.prompts.saveSuccess'), 'success');
      setAutomations((prev) => prev.map((a) => (a.id === selected.id ? { ...a, systemPrompt, userPromptTemplate } : a)));
    } catch (err: any) {
      showToast(err?.response?.data?.message || t('superAdmin.ai.prompts.saveError'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const variables = selected?.featureType ? (VARIABLES_BY_CATEGORY[selected.featureType] || []) : [];

  return (
    <div className="space-y-4">
      <FilterSelect
        options={automations.map((a) => ({ value: String(a.id), label: a.name }))}
        value={selectedId}
        onChange={setSelectedId}
        placeholder={t('superAdmin.ai.prompts.selectAutomation')}
        className="w-80"
      />

      {selected && (
        <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6 space-y-4">
          {!selected.wired && (
            <div className="rounded-xl border border-amber-200 dark:border-amber-800/40 bg-amber-50 dark:bg-amber-900/10 px-4 py-2.5 text-xs text-amber-700 dark:text-amber-400">
              {t('superAdmin.ai.prompts.notWiredNotice')}
            </div>
          )}

          <FormField label={t('superAdmin.ai.prompts.systemPrompt')} hint={t('superAdmin.ai.prompts.systemPromptHint')}>
            <TextArea value={systemPrompt} onChange={setSystemPrompt} rows={4} />
          </FormField>

          <FormField label={t('superAdmin.ai.prompts.userTemplate')} hint={t('superAdmin.ai.prompts.userTemplateHint')}>
            <textarea
              ref={userTemplateRef}
              value={userPromptTemplate}
              onChange={(e) => setUserPromptTemplate(e.target.value)}
              rows={5}
              className="w-full px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white outline-none focus:ring-2 ring-brand-100/50 resize-none font-mono"
            />
          </FormField>

          {variables.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">{t('superAdmin.ai.prompts.availableVariables')}</p>
              <div className="flex flex-wrap gap-2">
                {variables.map((v) => (
                  <button key={v} type="button" onClick={() => insertVariable(v)}>
                    <Badge variant="info" className="cursor-pointer hover:opacity-80">{`{{${v}}}`}</Badge>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-end">
            <button
              onClick={save}
              disabled={saving}
              className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50"
            >
              <Save size={16} />
              {saving ? t('superAdmin.ai.common.saving') : t('superAdmin.ai.common.save')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
