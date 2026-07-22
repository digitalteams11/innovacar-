import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Sparkles, RefreshCw, Loader2, AlertTriangle, CheckCircle2, XCircle,
  Activity, Bell, PauseCircle, PlayCircle,
} from 'lucide-react';
import { GlassPageHeader } from '../components/GlassPageHeader';
import { automationApi } from '../api/automationApi';

type AgentState = {
  id: number;
  key: string;
  enabled: boolean;
  status: string;
  lastRunAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  successCount: number;
  failureCount: number;
};

type RunRecord = {
  id: number;
  agentKey: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  durationMs: number | null;
  resultSummary: string | null;
  errorCode: string | null;
  sanitizedErrorMessage: string | null;
};

type AlertRecord = {
  id: number;
  agentKey: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  title: string;
  message: string | null;
  acknowledged: boolean;
  createdAt: string;
};

type Overview = {
  activeAgents: number;
  totalAgents: number;
  runsToday: number;
  successfulRunsToday: number;
  failedRunsToday: number;
  openAlerts: number;
  agents: AgentState[];
};

const STATUS_STYLE: Record<string, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/20',
  PAUSED: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-white/5 dark:text-slate-300 dark:border-white/10',
  DEGRADED: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/20',
  ERROR: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:border-rose-500/20',
  DISABLED: 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-white/5 dark:text-slate-400 dark:border-white/10',
  REQUIRES_CONFIGURATION: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-500/10 dark:text-blue-300 dark:border-blue-500/20',
};

const SEVERITY_STYLE: Record<string, string> = {
  INFO: 'bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-500/10 dark:text-blue-300 dark:border-blue-500/20',
  WARNING: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/20',
  CRITICAL: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:border-rose-500/20',
};

export default function AutomationCenter() {
  const { t, i18n } = useTranslation();

  const formatDate = (iso: string | null) => {
    if (!iso) return '—';
    return new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
  };
  const [overview, setOverview] = useState<Overview | null>(null);
  const [runs, setRuns] = useState<RunRecord[]>([]);
  const [alerts, setAlerts] = useState<AlertRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busyKey, setBusyKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const [overviewRes, runsRes, alertsRes] = await Promise.all([
        automationApi.getOverview(),
        automationApi.getRuns(),
        automationApi.getAlerts(),
      ]);
      setOverview(overviewRes.data);
      setRuns(runsRes.data);
      setAlerts(alertsRes.data);
    } catch (err) {
      console.error(err);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const toggleAgent = async (agent: AgentState) => {
    setBusyKey(agent.key);
    try {
      await automationApi.setAgentEnabled(agent.key, !agent.enabled);
      await load();
    } catch (err) {
      console.error(err);
    } finally {
      setBusyKey(null);
    }
  };

  const acknowledge = async (alert: AlertRecord) => {
    setBusyKey(`alert-${alert.id}`);
    try {
      await automationApi.acknowledgeAlert(alert.id);
      await load();
    } catch (err) {
      console.error(err);
    } finally {
      setBusyKey(null);
    }
  };

  if (loading && !overview) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center gap-3">
        <Loader2 className="animate-spin text-brand-500" size={28} />
        <p className="text-sm text-[var(--text-muted)]">{t('aiAutomation.loading')}</p>
      </div>
    );
  }

  if (error && !overview) {
    return (
      <div className="min-h-[40vh] flex flex-col items-center justify-center gap-3 text-center p-6">
        <XCircle className="text-rose-500" size={32} />
        <p className="text-sm text-[var(--text-muted)]">{t('aiAutomation.loadError')}</p>
        <button onClick={load} className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-brand-500 text-white text-sm font-semibold">
          <RefreshCw size={14} /> {t('aiAutomation.actions.retry')}
        </button>
      </div>
    );
  }

  const metrics = [
    { label: t('aiAutomation.stats.activeAgents'), value: overview?.activeAgents ?? 0, icon: Activity, tone: 'text-brand-500' },
    { label: t('aiAutomation.stats.runsToday'), value: overview?.runsToday ?? 0, icon: Sparkles, tone: 'text-blue-500' },
    { label: t('aiAutomation.stats.successfulRuns'), value: overview?.successfulRunsToday ?? 0, icon: CheckCircle2, tone: 'text-emerald-500' },
    { label: t('aiAutomation.stats.failedRuns'), value: overview?.failedRunsToday ?? 0, icon: XCircle, tone: 'text-rose-500' },
    { label: t('aiAutomation.stats.openAlerts'), value: overview?.openAlerts ?? 0, icon: Bell, tone: 'text-amber-500' },
  ];

  return (
    <div className="space-y-6 pb-10">
      <GlassPageHeader
        title={t('nav.automationCenter', 'AI & Automation')}
        subtitle={t('aiAutomation.subtitle')}
        icon={Sparkles}
        actions={
          <button onClick={load} disabled={loading} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-[var(--border-subtle)] text-sm font-semibold text-[var(--text-secondary)] disabled:opacity-50 min-h-[44px]">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('aiAutomation.refresh')}
          </button>
        }
      />

      <section className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        {metrics.map((metric) => (
          <article key={metric.label} className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] p-4">
            <metric.icon size={18} className={metric.tone} />
            <strong className="mt-3 block text-xl text-[var(--text-primary)]">{metric.value}</strong>
            <span className="text-xs text-[var(--text-muted)]">{metric.label}</span>
          </article>
        ))}
      </section>

      <section>
        <h2 className="text-sm font-bold text-[var(--text-primary)] mb-3">{t('aiAutomation.sections.agents')}</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(overview?.agents ?? []).map((agent) => (
            <article key={agent.key} className="rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-sm font-bold text-[var(--text-primary)]">{t(`agents.${agent.key}.name`, agent.key)}</p>
                  <p className="mt-1 text-xs text-[var(--text-muted)] leading-5">{t(`agents.${agent.key}.description`, '')}</p>
                </div>
                <span className={`shrink-0 rounded-lg border px-2 py-1 text-[10px] font-bold uppercase ${STATUS_STYLE[agent.status] || STATUS_STYLE.ACTIVE}`}>
                  {t(`status.agent.${agent.status}`, agent.status)}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs text-[var(--text-muted)]">
                <div>{t('aiAutomation.labels.lastRun')}: {formatDate(agent.lastRunAt)}</div>
                <div>{t('aiAutomation.labels.successes')}: {agent.successCount}</div>
                <div>{t('aiAutomation.labels.lastSuccess')}: {formatDate(agent.lastSuccessAt)}</div>
                <div>{t('aiAutomation.labels.failures')}: {agent.failureCount}</div>
              </div>
              <button
                onClick={() => toggleAgent(agent)}
                disabled={busyKey === agent.key}
                className="inline-flex items-center gap-2 rounded-xl border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)] disabled:opacity-50 min-h-[44px]"
              >
                {agent.enabled ? <PauseCircle size={14} /> : <PlayCircle size={14} />}
                {agent.enabled ? t('aiAutomation.actions.disableAgent') : t('aiAutomation.actions.enableAgent')}
              </button>
            </article>
          ))}
        </div>
      </section>

      <section>
        <h2 className="text-sm font-bold text-[var(--text-primary)] mb-3">{t('aiAutomation.sections.alerts')}</h2>
        {alerts.length === 0 ? (
          <p className="text-sm text-[var(--text-muted)]">{t('aiAutomation.emptyStates.noAlerts')}</p>
        ) : (
          <div className="space-y-2">
            {alerts.map((alert) => (
              <div key={alert.id} className="flex items-start gap-3 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] p-4">
                <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-500" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="text-sm font-semibold text-[var(--text-primary)]">{alert.title}</p>
                    <span className={`rounded-md border px-1.5 py-0.5 text-[10px] font-bold uppercase ${SEVERITY_STYLE[alert.severity]}`}>{t(`status.severity.${alert.severity}`, alert.severity)}</span>
                  </div>
                  {alert.message && <p className="mt-1 text-xs text-[var(--text-muted)]">{alert.message}</p>}
                  <p className="mt-1 text-[10px] text-[var(--text-muted)]">{formatDate(alert.createdAt)}</p>
                </div>
                {!alert.acknowledged && (
                  <button
                    onClick={() => acknowledge(alert)}
                    disabled={busyKey === `alert-${alert.id}`}
                    className="shrink-0 rounded-lg border border-[var(--border-subtle)] px-3 py-2 text-xs font-semibold text-[var(--text-secondary)] disabled:opacity-50 min-h-[44px]"
                  >
                    {t('aiAutomation.actions.acknowledge')}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="text-sm font-bold text-[var(--text-primary)] mb-3">{t('aiAutomation.sections.recentRuns')}</h2>
        {runs.length === 0 ? (
          <p className="text-sm text-[var(--text-muted)]">{t('aiAutomation.emptyStates.noRuns')}</p>
        ) : (
          <div className="space-y-2">
            {runs.slice(0, 20).map((run) => (
              <div key={run.id} className="flex items-center gap-3 rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-card-solid)] p-3">
                {run.status === 'SUCCESS' ? (
                  <CheckCircle2 size={16} className="shrink-0 text-emerald-500" aria-label={t('status.runStatus.SUCCESS') as string} />
                ) : (
                  <XCircle size={16} className="shrink-0 text-rose-500" aria-label={t('status.runStatus.FAILED') as string} />
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-[var(--text-primary)]">{t(`agents.${run.agentKey}.name`, run.agentKey)}</p>
                  <p className="text-[11px] text-[var(--text-muted)] truncate">
                    {run.status === 'SUCCESS'
                      ? run.resultSummary
                      : (run.errorCode ? t(`runErrors.${run.errorCode}`, run.sanitizedErrorMessage || '') : run.sanitizedErrorMessage)}
                  </p>
                </div>
                <span className="shrink-0 text-[10px] text-[var(--text-muted)]">{formatDate(run.startedAt)}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
