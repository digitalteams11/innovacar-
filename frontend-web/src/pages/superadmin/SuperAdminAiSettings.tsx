import { useEffect, useRef, useState } from 'react';
import { Save, Sparkles, PlugZap, ShieldAlert, ListChecks, Eye, EyeOff, X, CheckCircle2, XCircle, AlertCircle, Clock, Trash2, Copy } from 'lucide-react';
import { PageHeader, FormField, TextInput, ToggleSwitch } from '../../components/superadmin';
import { aiService } from '../../api/aiService';
import { useToast } from '../../context/ToastContext';

interface AiSettings {
  enabled?: boolean;
  provider?: string;
  apiKeyConfigured?: boolean;
  apiKeyMasked?: string;
  textModel?: string;
  visionModel?: string;
  timeoutSeconds?: number;
  maxTokens?: number;
  temperature?: number;
  enableChat?: boolean;
  enableReports?: boolean;
  enableTranslations?: boolean;
  enableSupportAssistant?: boolean;
  enableGuideGenerator?: boolean;
  enableAutomationSuggestions?: boolean;
  enableImageGeneration?: boolean;
  monthlyTokenLimit?: number;
  dailyRequestLimit?: number;
  auditAllActions?: boolean;
  apiKeyStatus?: 'CONFIGURED' | 'MISSING' | 'DECRYPTION_FAILED' | 'EMPTY_AFTER_DECRYPT';
  lastTestedAt?: string;
  lastTestSuccess?: boolean;
  lastTestMessage?: string;
  lastTestErrorCode?: string;
}

interface AuditLogRow {
  id: number;
  feature: string;
  promptCategory?: string;
  model?: string;
  status: string;
  errorCode?: string;
  createdAt: string;
  agencyId?: number;
}

type StatusBadge = 'connected' | 'not-configured' | 'error' | 'disabled';

function getStatusBadge(settings: AiSettings): StatusBadge {
  if (!settings.enabled) return 'disabled';
  if (settings.apiKeyStatus === 'DECRYPTION_FAILED') return 'error';
  if (!settings.apiKeyConfigured) return 'not-configured';
  if (settings.lastTestSuccess === false) return 'error';
  if (settings.lastTestSuccess === true) return 'connected';
  return 'not-configured';
}

const STATUS_LABELS: Record<StatusBadge, { label: string; className: string }> = {
  connected: { label: 'Connected', className: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400' },
  'not-configured': { label: 'Not configured', className: 'bg-amber-50 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400' },
  error: { label: 'Error', className: 'bg-rose-50 text-rose-700 dark:bg-rose-900/20 dark:text-rose-400' },
  disabled: { label: 'Disabled', className: 'bg-slate-100 text-slate-500 dark:bg-white/5 dark:text-slate-400' },
};

export default function SuperAdminAiSettings() {
  const { showToast } = useToast();
  const [settings, setSettings] = useState<AiSettings>({});
  const [newApiKey, setNewApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [lastTestDebug, setLastTestDebug] = useState<{
    code: string | undefined;
    humanMsg: string;
    debugInfo: string;
    copyDebug: () => void;
  } | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogRow[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [clearingLogs, setClearingLogs] = useState(false);
  const [deletingLogId, setDeletingLogId] = useState<number | null>(null);
  const keyInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchSettings();
    fetchAuditLogs();
  }, []);

  const fetchSettings = async () => {
    setLoading(true);
    try {
      const { data } = await aiService.getSettings();
      setSettings(data?.data || {});
    } catch {
      showToast('Unable to load AI settings. Please try again later.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const fetchAuditLogs = async () => {
    setAuditLoading(true);
    try {
      const { data } = await aiService.getAuditLogs(0, 20);
      setAuditLogs(data?.data?.items || []);
    } catch {
      setAuditLogs([]);
    } finally {
      setAuditLoading(false);
    }
  };

  const deleteLog = async (id: number) => {
    setDeletingLogId(id);
    try {
      await aiService.deleteAuditLog(id);
      setAuditLogs((prev) => prev.filter((r) => r.id !== id));
    } catch {
      showToast('Unable to delete this log entry. Please try again.', 'error');
    } finally {
      setDeletingLogId(null);
    }
  };

  const clearAuditLogs = async () => {
    if (!confirm('Delete all AI audit log entries? This cannot be undone.')) return;
    setClearingLogs(true);
    try {
      await aiService.clearAuditLogs();
      setAuditLogs([]);
      showToast('AI audit logs cleared.', 'success');
    } catch {
      showToast('Unable to clear audit logs. Please try again.', 'error');
    } finally {
      setClearingLogs(false);
    }
  };

  const updateField = (field: keyof AiSettings, value: any) => {
    setSettings((prev) => ({ ...prev, [field]: value }));
  };

  const save = async () => {
    setSaving(true);
    try {
      const payload: any = { ...settings };
      if (newApiKey.trim()) payload.apiKey = newApiKey.trim();
      const { data } = await aiService.updateSettings(payload);
      setSettings(data?.data || settings);
      setNewApiKey('');
      showToast('AI settings saved successfully.', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || err?.userMessage || 'Unable to save AI settings.', 'error');
    } finally {
      setSaving(false);
    }
  };

  /** Human-readable message for each AI error code â€” used in test connection results. */
  const aiTestErrorMessage = (code: string | undefined, raw?: string): string => {
    switch (code) {
      case 'AI_API_KEY_MISSING':
      case 'AI_KEY_NOT_CONFIGURED':
        return 'No API key configured. Enter and save a Gemini API key first.';
      case 'AI_API_KEY_INVALID':
      case 'AI_INVALID_API_KEY':
        return 'Your Gemini API key is invalid. Create a new key at aistudio.google.com and save it in AI settings.';
      case 'AI_PROVIDER_AUTH_FORBIDDEN':
        return 'Gemini access forbidden (HTTP 403). The API key may lack Generative Language API permissions, or your Google Cloud project has API restrictions enabled.';
      case 'AI_MODEL_NOT_FOUND':
        return 'The selected Gemini model is not available for your API key. Try gemini-1.5-flash in the model field and save.';
      case 'AI_QUOTA_EXCEEDED':
        return 'Your Google AI quota is exceeded (HTTP 429). Try gemini-1.5-flash, use another Google AI key/project, or wait for the quota to reset.';
      case 'AI_BAD_REQUEST':
        return raw || 'Gemini rejected the request (HTTP 400). Check the model name and request format.';
      case 'AI_PROVIDER_TIMEOUT':
        return 'Gemini did not respond in time. Try increasing the timeout in AI settings.';
      case 'AI_PROVIDER_EMPTY_RESPONSE':
        return 'Gemini responded but returned no text. The model may have filtered the test prompt.';
      case 'AI_PROVIDER_HTTP_ERROR':
        return raw || 'Gemini returned an unexpected HTTP error. Check AI settings and server logs.';
      case 'AI_PROVIDER_URL_MISSING':
        return 'Gemini backend URL is missing. The server attempted to call Gemini without a valid host. This is a backend configuration error â€” not a Google outage.';
      case 'AI_NETWORK_ERROR':
        return 'Network error: the server cannot reach generativelanguage.googleapis.com. Check outbound internet access and firewall rules.';
      case 'AI_PROVIDER_UNREACHABLE':
        return 'Connection to Gemini was refused or reset. Check the server\'s firewall rules and outbound internet access.';
      case 'AI_SERVICE_UNAVAILABLE':
        return 'Gemini service is currently unavailable. This may be a temporary Google outage â€” try again later.';
      case 'AI_KEY_DECRYPTION_FAILED':
        return 'Saved API key is corrupted. Re-enter and save the key again.';
      default:
        return raw || 'Gemini connection failed. Check AI settings and server logs for details.';
    }
  };

  /** Returns debug text that is safe to copy â€” never includes the API key. */
  const buildDebugInfo = (
    code: string | undefined,
    message: string | undefined,
    data: { provider?: string; model?: string; httpStatus?: number; safeProviderMessage?: string } | undefined,
  ): string => {
    const lines = [
      `errorCode: ${code ?? 'unknown'}`,
      `provider: ${data?.provider ?? 'GEMINI'}`,
      `model: ${data?.model ?? 'unknown'}`,
      `httpStatus: ${data?.httpStatus ?? 'n/a'}`,
      `safeProviderMessage: ${data?.safeProviderMessage ?? 'n/a'}`,
      `message: ${message ?? 'n/a'}`,
      `timestamp: ${new Date().toISOString()}`,
    ];
    return lines.join('\n');
  };

  const testConnection = async () => {
    setTesting(true);
    try {
      const { data } = await aiService.testConnection(newApiKey.trim() || undefined);

      if (data?.success) {
        const latency = data?.data?.latencyMs;
        const fallback = data?.data?.fallbackMessage;
        const model = data?.data?.model;
        if (fallback) {
          // Primary model failed but a fallback worked
          showToast(`${fallback}${latency ? ` (${latency}ms)` : ''}`, 'warning');
        } else {
          showToast(
            `Gemini connection successful${model ? ` Â· ${model}` : ''}${latency ? ` Â· ${latency}ms` : ''}.`,
            'success',
          );
        }
      } else {
        // data.errorCode is at the top level (not nested in data.data) for failure responses
        const code: string | undefined = data?.errorCode;
        const humanMsg = aiTestErrorMessage(code, data?.message);
        const debugInfo = buildDebugInfo(code, data?.message, data?.data);

        // Show the human-readable message in a toast
        showToast(humanMsg, 'error');

        // Offer "Copy debug info" via a second quiet toast (no API key included)
        const copyDebug = () => {
          navigator.clipboard.writeText(debugInfo).catch(() => undefined);
        };
        // Store in state so JSX can render a copy button below the test button
        setLastTestDebug({ code, humanMsg, debugInfo, copyDebug });
      }

      fetchSettings();
      fetchAuditLogs();
    } catch (err: any) {
      // Network-level error (axios threw â€” response body may still have errorCode)
      const code: string | undefined = err?.response?.data?.errorCode ?? err?.errorCode;
      const raw: string | undefined = err?.response?.data?.message ?? err?.message;
      showToast(aiTestErrorMessage(code, raw), 'error');
      setLastTestDebug(null);
    } finally {
      setTesting(false);
    }
  };

  const clearApiKey = async () => {
    if (!confirm('Remove the stored API key? AI features will stop working until a new key is saved.')) return;
    setClearing(true);
    try {
      const { data } = await aiService.clearApiKey();
      setSettings(data?.data || { ...settings, apiKeyConfigured: false, apiKeyMasked: '' });
      setNewApiKey('');
      showToast('API key cleared.', 'success');
    } catch (err: any) {
      showToast(err?.response?.data?.message || 'Unable to clear API key.', 'error');
    } finally {
      setClearing(false);
    }
  };

  const toggles: Array<{ key: keyof AiSettings; label: string }> = [
    { key: 'enableChat', label: 'AI Chat Assistant' },
    { key: 'enableReports', label: 'AI Reports & Analysis' },
    { key: 'enableTranslations', label: 'AI Translations' },
    { key: 'enableSupportAssistant', label: 'AI Support Reply Suggestions' },
    { key: 'enableGuideGenerator', label: 'AI Help Guide Generator' },
    { key: 'enableAutomationSuggestions', label: 'AI Automation Suggestions' },
    { key: 'enableImageGeneration', label: 'AI Image / Visual Generation' },
  ];

  const badge = getStatusBadge(settings);
  const badgeInfo = STATUS_LABELS[badge];

  return (
    <div className="space-y-6 animate-fade">
      <PageHeader title="AI & Automation" subtitle="Gemini integration â€” configured and audited entirely on the backend.">
        <div className="flex items-center gap-2">
          <span className={`px-2.5 py-1 rounded-lg text-xs font-semibold ${badgeInfo.className}`}>
            {badgeInfo.label}
          </span>
          <button
            onClick={save}
            disabled={saving || loading}
            className="flex items-center gap-2 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60"
          >
            <Save size={16} />
            {saving ? 'Saving...' : 'Save Settings'}
          </button>
        </div>
      </PageHeader>

      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6 max-w-3xl space-y-6">
        <div className="flex items-center gap-3">
          <Sparkles size={20} className="text-[#0a0f2c] dark:text-white" />
          <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Gemini Configuration</h3>
        </div>

        <ToggleSwitch
          checked={settings.enabled || false}
          onChange={(v) => updateField('enabled', v)}
          label="AI features enabled platform-wide"
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField
            label="Gemini API Key"
            hint={
              settings.apiKeyConfigured
                ? 'A key is already stored (encrypted). Enter a new key to replace it.'
                : 'No key configured. Enter your Gemini API key.'
            }
          >
            <div className="relative">
              <input
                ref={keyInputRef}
                type={showKey ? 'text' : 'password'}
                value={newApiKey}
                onChange={(e) => setNewApiKey(e.target.value)}
                placeholder={settings.apiKeyConfigured ? settings.apiKeyMasked || 'â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢â€¢' : 'Enter Gemini API key'}
                autoComplete="new-password"
                className="w-full px-4 py-2.5 pr-10 rounded-xl border border-[#e8e6e1] dark:border-white/5 bg-white dark:bg-[#1e293b] text-sm text-[#1e293b] dark:text-white placeholder:text-slate-400 outline-none focus:ring-2 ring-brand-100 dark:ring-brand-400/20"
              />
              <button
                type="button"
                onClick={() => { setShowKey((v) => !v); keyInputRef.current?.focus(); }}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                tabIndex={-1}
                title={showKey ? 'Hide key' : 'Show key'}
              >
                {showKey ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
            {settings.apiKeyConfigured && (
              <button
                type="button"
                onClick={clearApiKey}
                disabled={clearing}
                className="mt-1.5 flex items-center gap-1 text-xs text-rose-600 hover:text-rose-700 dark:text-rose-400 disabled:opacity-50"
              >
                <X size={12} />
                {clearing ? 'Clearing...' : 'Clear stored API key'}
              </button>
            )}
          </FormField>
          <FormField label="Provider">
            <TextInput value={settings.provider || 'GEMINI'} onChange={() => {}} disabled />
          </FormField>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Text Model">
            <TextInput
              value={settings.textModel || ''}
              onChange={(v) => updateField('textModel', v)}
              placeholder="gemini-1.5-flash"
            />
          </FormField>
          <FormField label="Vision Model">
            <TextInput
              value={settings.visionModel || ''}
              onChange={(v) => updateField('visionModel', v)}
              placeholder="gemini-1.5-flash"
            />
          </FormField>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <FormField label="Timeout (seconds)">
            <TextInput
              value={settings.timeoutSeconds ?? ''}
              onChange={(v) => updateField('timeoutSeconds', Number(v))}
              type="number"
            />
          </FormField>
          <FormField label="Max Tokens">
            <TextInput
              value={settings.maxTokens ?? ''}
              onChange={(v) => updateField('maxTokens', Number(v))}
              type="number"
            />
          </FormField>
          <FormField label="Temperature">
            <TextInput
              value={settings.temperature ?? ''}
              onChange={(v) => updateField('temperature', Number(v))}
              type="number"
            />
          </FormField>
        </div>

        {/* AI Status Card */}
        <div className="rounded-xl border border-[#e8e6e1]/60 dark:border-white/5 bg-slate-50 dark:bg-white/3 p-4 space-y-2">
          <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 dark:text-slate-500 mb-3">Current AI Status</h4>
          <div className="grid grid-cols-2 gap-2 text-sm">
            <span className="text-slate-500 dark:text-slate-400">Provider</span>
            <span className="font-medium text-[#1e293b] dark:text-white">{settings.provider || 'GEMINI'}</span>

            <span className="text-slate-500 dark:text-slate-400">API Key</span>
            <span className={`font-medium flex items-center gap-1 ${
              settings.apiKeyStatus === 'CONFIGURED'
                ? 'text-emerald-600 dark:text-emerald-400'
                : settings.apiKeyStatus === 'DECRYPTION_FAILED'
                ? 'text-rose-600 dark:text-rose-400'
                : 'text-amber-600 dark:text-amber-400'
            }`}>
              {settings.apiKeyStatus === 'CONFIGURED'
                ? <><CheckCircle2 size={13} /> Configured</>
                : settings.apiKeyStatus === 'DECRYPTION_FAILED'
                ? <><XCircle size={13} /> Cannot decrypt â€” re-save required</>
                : <><AlertCircle size={13} /> Not configured</>
              }
            </span>
            {settings.apiKeyStatus === 'DECRYPTION_FAILED' && (
              <span className="col-span-2 text-xs text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-900/20 rounded-lg px-3 py-2">
                Your saved API key cannot be used. Paste the Gemini API key again and click Save & Test.
              </span>
            )}

            <span className="text-slate-500 dark:text-slate-400">Platform AI</span>
            <span className={`font-medium flex items-center gap-1 ${settings.enabled ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400'}`}>
              {settings.enabled ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
              {settings.enabled ? 'Enabled' : 'Disabled'}
            </span>

            <span className="text-slate-500 dark:text-slate-400">Chat Feature</span>
            <span className={`font-medium flex items-center gap-1 ${settings.enableChat ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400'}`}>
              {settings.enableChat ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
              {settings.enableChat ? 'Enabled' : 'Disabled'}
            </span>

            {settings.lastTestedAt && (
              <>
                <span className="text-slate-500 dark:text-slate-400">Last Test</span>
                <span className={`font-medium flex items-center gap-1 ${settings.lastTestSuccess ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                  {settings.lastTestSuccess ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
                  {settings.lastTestSuccess
                    ? 'Passed'
                    : settings.lastTestErrorCode
                      ? settings.lastTestErrorCode.replace(/_/g, ' ')
                      : 'Failed'}
                  <span className="text-slate-400 dark:text-slate-500 font-normal flex items-center gap-0.5 ml-1">
                    <Clock size={11} />
                    {new Date(settings.lastTestedAt).toLocaleString()}
                  </span>
                </span>
              </>
            )}
          </div>
        </div>

        {/* Test Connection */}
        <div className="space-y-3">
          <div className="flex items-center gap-3 flex-wrap">
            <button
              type="button"
              onClick={() => { setLastTestDebug(null); testConnection(); }}
              disabled={testing || (!settings.apiKeyConfigured && !newApiKey.trim())}
              title={!settings.apiKeyConfigured && !newApiKey.trim() ? 'Enter an API key first' : undefined}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-[#e8e6e1] dark:border-white/10 text-sm font-semibold text-[#1e293b] dark:text-white hover:bg-slate-50 dark:hover:bg-white/5 transition-colors disabled:opacity-50"
            >
              <PlugZap size={16} />
              {testing ? 'Testing...' : 'Test Gemini Connection'}
            </button>
            {newApiKey.trim() && (
              <span className="text-xs text-amber-600 dark:text-amber-400">Will test with the unsaved key above</span>
            )}
          </div>

          {/* Debug info panel â€” shown after a failed test, never contains the API key */}
          {lastTestDebug && (
            <div className="rounded-xl border border-rose-200 dark:border-rose-800/40 bg-rose-50 dark:bg-rose-900/10 p-4 space-y-2">
              <div className="flex items-start justify-between gap-3">
                <div className="space-y-1">
                  <p className="text-xs font-bold text-rose-700 dark:text-rose-400 uppercase tracking-wide">
                    {lastTestDebug.code ?? 'Connection failed'}
                  </p>
                  <p className="text-sm text-rose-800 dark:text-rose-300">{lastTestDebug.humanMsg}</p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    lastTestDebug.copyDebug();
                    showToast('Debug info copied to clipboard (no API key included).', 'success');
                  }}
                  title="Copy debug info (no API key)"
                  className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-rose-300 dark:border-rose-700 text-xs font-medium text-rose-700 dark:text-rose-400 hover:bg-rose-100 dark:hover:bg-rose-900/30 transition-colors"
                >
                  <Copy size={12} />
                  Copy debug info
                </button>
              </div>
              <pre className="text-[10px] text-rose-600/70 dark:text-rose-400/60 font-mono overflow-x-auto whitespace-pre-wrap break-all">
                {lastTestDebug.debugInfo}
              </pre>
            </div>
          )}
        </div>

        {/* Feature Toggles */}
        <div className="pt-4 border-t border-[#e8e6e1]/40 dark:border-white/5 space-y-4">
          <h4 className="text-sm font-bold text-[#1e293b] dark:text-white">Feature Toggles</h4>
          {toggles.map((toggle) => (
            <ToggleSwitch
              key={toggle.key}
              checked={Boolean(settings[toggle.key])}
              onChange={(v) => updateField(toggle.key, v)}
              label={toggle.label}
            />
          ))}
        </div>

        {/* Limits */}
        <div className="pt-4 border-t border-[#e8e6e1]/40 dark:border-white/5 grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Daily Request Limit (per user)">
            <TextInput
              value={settings.dailyRequestLimit ?? ''}
              onChange={(v) => updateField('dailyRequestLimit', Number(v))}
              type="number"
            />
          </FormField>
          <FormField label="Monthly Token Limit (platform)">
            <TextInput
              value={settings.monthlyTokenLimit ?? ''}
              onChange={(v) => updateField('monthlyTokenLimit', Number(v))}
              type="number"
            />
          </FormField>
        </div>

        <ToggleSwitch
          checked={settings.auditAllActions || false}
          onChange={(v) => updateField('auditAllActions', v)}
          label="Audit all AI actions"
        />

        <div className="flex items-start gap-2 text-xs text-slate-500 bg-slate-50 dark:bg-white/5 rounded-xl p-3">
          <ShieldAlert size={14} className="shrink-0 mt-0.5" />
          <span>
            The API key is encrypted at rest (AES-256-GCM) and never returned to any frontend, including this page.
            Saving a new key replaces the stored one. Leaving the field blank keeps the existing key.
            Use "Clear stored API key" to remove it completely.
          </span>
        </div>
      </div>

      {/* Audit Logs */}
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft p-4 sm:p-6 max-w-3xl space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <ListChecks size={20} className="text-[#0a0f2c] dark:text-white" />
            <h3 className="text-base font-bold text-[#1e293b] dark:text-white">Recent AI Audit Logs</h3>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={fetchAuditLogs}
              disabled={auditLoading || clearingLogs}
              className="text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 disabled:opacity-50"
            >
              {auditLoading ? 'Loading...' : 'Refresh'}
            </button>
            {auditLogs.length > 0 && (
              <button
                onClick={clearAuditLogs}
                disabled={clearingLogs || auditLoading}
                className="flex items-center gap-1 text-xs text-rose-500 hover:text-rose-700 dark:hover:text-rose-400 disabled:opacity-50"
              >
                <Trash2 size={13} />
                {clearingLogs ? 'Clearing...' : 'Clear All'}
              </button>
            )}
          </div>
        </div>

        {auditLoading ? (
          <p className="text-sm text-slate-400">Loading...</p>
        ) : auditLogs.length === 0 ? (
          <p className="text-sm text-slate-400">No AI calls have been logged yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 uppercase tracking-wide">
                  <th className="py-2 pr-4">Feature</th>
                  <th className="py-2 pr-4">Category</th>
                  <th className="py-2 pr-4">Model</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Agency</th>
                  <th className="py-2 pr-4">When</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((row) => (
                  <tr key={row.id} className="border-t border-[#e8e6e1]/40 dark:border-white/5 group">
                    <td className="py-2 pr-4 font-medium text-[#1e293b] dark:text-white">{row.feature}</td>
                    <td className="py-2 pr-4 text-slate-500">{row.promptCategory || 'â€”'}</td>
                    <td className="py-2 pr-4 text-slate-500">{row.model || 'â€”'}</td>
                    <td className={`py-2 pr-4 font-medium ${row.status === 'SUCCESS' ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                      {row.status}{row.errorCode ? ` (${row.errorCode})` : ''}
                    </td>
                    <td className="py-2 pr-4 text-slate-500">{row.agencyId ?? 'Platform'}</td>
                    <td className="py-2 pr-4 text-slate-400 text-xs">{new Date(row.createdAt).toLocaleString()}</td>
                    <td className="py-2 text-right">
                      <button
                        onClick={() => deleteLog(row.id)}
                        disabled={deletingLogId === row.id || clearingLogs}
                        title="Delete this entry"
                        className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 disabled:opacity-30 transition-opacity"
                      >
                        {deletingLogId === row.id
                          ? <span className="text-[10px]">â€¦</span>
                          : <Trash2 size={13} />}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

