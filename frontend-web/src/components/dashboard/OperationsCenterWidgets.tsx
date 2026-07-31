import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  RefreshCw, AlertTriangle, Clock, CheckCircle2, TrendingUp, TrendingDown,
  Wallet, Car,
} from 'lucide-react';
import { useOperationsCenter, type OperationItem, type ActionItem, type OperationsCenterError } from '../../hooks/useOperationsCenter';

/* ── Shared small pieces ─────────────────────────────────────────────────── */

/** 401/403/404/500/network never share one message — each names the real reason. */
function widgetErrorMessage(t: ReturnType<typeof useTranslation>['t'], error: OperationsCenterError): string {
  switch (error.status) {
    case 401: return t('dashboard.widgetErrorSessionExpired', 'Your session has expired. Please sign in again.');
    case 403: return t('dashboard.widgetErrorForbidden', 'You do not have permission to view this widget.');
    case 404: return t('dashboard.widgetErrorNotFound', 'This data is not available right now.');
    default: return t('dashboard.widgetErrorServer', 'This widget could not load. Please try again.');
  }
}

/** Compact inline error — never a giant global toast (spec section 25): a small retry icon + short text, local to this one card. Retry only shows for genuinely retriable failures (5xx/network) — retrying a 401/403/404 changes nothing. */
function WidgetError({ error, onRetry }: { error: OperationsCenterError; onRetry: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-between gap-2 rounded-xl border border-rose-500/20 bg-rose-500/5 px-4 py-3 text-xs text-rose-600">
      <span>{widgetErrorMessage(t, error)}</span>
      {error.retriable && (
        <button type="button" onClick={onRetry} className="flex items-center gap-1 font-semibold hover:underline" title={t('common.retry', 'Retry')}>
          <RefreshCw size={12} /> {t('common.retry', 'Retry')}
        </button>
      )}
    </div>
  );
}

function WidgetSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="shimmer h-10 rounded-lg" />
      ))}
    </div>
  );
}

function WidgetShell({ title, icon: Icon, count, children }: {
  title: string; icon: React.ElementType; count?: number; children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl border p-5" style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border-subtle)' }}>
      <div className="mb-3 flex items-center gap-2">
        <Icon size={15} style={{ color: 'var(--text-muted)' }} />
        <h2 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h2>
        {count != null && count > 0 && (
          <span className="rounded-full px-2 py-0.5 text-[10px] font-bold" style={{ backgroundColor: 'var(--bg-hover)', color: 'var(--text-muted)' }}>
            {count}
          </span>
        )}
      </div>
      {children}
    </div>
  );
}

function money(n: number | undefined | null) {
  return (n ?? 0).toLocaleString('fr-MA', { maximumFractionDigits: 0 });
}

function navigateToEntity(navigate: ReturnType<typeof useNavigate>, entityType: string, entityId: number | null) {
  if (entityId == null) return;
  switch (entityType) {
    case 'CONTRACT': navigate(`/contracts/${entityId}`); return;
    case 'VEHICLE': navigate(`/vehicles?vehicleId=${entityId}`); return;
    case 'RESERVATION': navigate(`/reservations?reservationId=${entityId}`); return;
    case 'MAINTENANCE': navigate(`/maintenance?vehicleId=${entityId}`); return;
    case 'CLIENT': navigate(`/clients?viewClientId=${entityId}`); return;
    case 'SUBSCRIPTION': navigate('/settings?tab=billing'); return;
    default: return;
  }
}

/* ── 1. Today's Operations ───────────────────────────────────────────────── */

const OP_ACTION_LABEL: Record<string, string> = {
  CONFIRM_RESERVATION: 'dashboard.ops.confirmReservation',
  VIEW_RESERVATION: 'dashboard.ops.viewReservation',
  CREATE_CONTRACT: 'dashboard.ops.createContract',
  START_RETURN_INSPECTION: 'dashboard.ops.startReturn',
  OPEN_CONTRACT: 'dashboard.ops.openContract',
  RECORD_PAYMENT: 'dashboard.ops.recordPayment',
  OPEN_MAINTENANCE: 'dashboard.ops.openMaintenance',
  VIEW_VEHICLE: 'dashboard.ops.viewVehicle',
};
const OP_CATEGORY_LABEL: Record<string, string> = {
  PICKUP_TODAY: 'dashboard.ops.pickupToday',
  RETURN_TODAY: 'dashboard.ops.returnToday',
  OVERDUE_RETURN: 'dashboard.ops.overdueReturn',
  RESERVATION_WAITING_CONFIRMATION: 'dashboard.ops.waitingConfirmation',
  CONTRACT_WAITING_SIGNATURE: 'dashboard.ops.waitingSignature',
  PAYMENT_DUE_TODAY: 'dashboard.ops.paymentDueToday',
  MAINTENANCE_STARTING_TODAY: 'dashboard.ops.maintenanceToday',
  VEHICLE_AVAILABLE_TODAY: 'dashboard.ops.vehicleAvailableToday',
};

function OperationRow({ item }: { item: OperationItem }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => navigateToEntity(navigate, item.entityType, item.entityId)}
      onKeyDown={(e) => { if (e.key === 'Enter') navigateToEntity(navigate, item.entityType, item.entityId); }}
      className="flex min-h-11 cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2.5 hover:bg-[var(--bg-hover)]"
    >
      <div className="min-w-0">
        <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
          {t(OP_CATEGORY_LABEL[item.category] || item.category, item.category)}
        </p>
        <p className="mt-0.5 truncate text-[11px]" style={{ color: 'var(--text-muted)' }}>
          {[item.clientName, item.vehicle, item.time].filter(Boolean).join(' · ') || '—'}
        </p>
      </div>
      <span className="shrink-0 text-[11px] font-semibold" style={{ color: 'var(--mobile-link)' }}>
        {t(OP_ACTION_LABEL[item.action] || item.action, item.action)}
      </span>
    </div>
  );
}

export function TodayOperationsWidget() {
  const { t } = useTranslation();
  const { data, loading, error, retry } = useOperationsCenter();

  return (
    <WidgetShell title={t('dashboard.ops.title', "Today's Operations")} icon={Clock} count={data?.todayOperations.length}>
      {error ? <WidgetError error={error} onRetry={retry} /> : loading ? <WidgetSkeleton /> : (
        data && data.todayOperations.length > 0 ? (
          <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
            {data.todayOperations.slice(0, 8).map((item, i) => <OperationRow key={i} item={item} />)}
          </div>
        ) : (
          <div className="flex items-center gap-2 py-4 text-xs" style={{ color: 'var(--text-muted)' }}>
            <CheckCircle2 size={14} className="text-emerald-500" />
            {t('dashboard.ops.empty', 'No pickups or returns scheduled today.')}
          </div>
        )
      )}
    </WidgetShell>
  );
}

/* ── 2. Action Required Queue ────────────────────────────────────────────── */

const PRIORITY_STYLE: Record<string, string> = {
  CRITICAL: 'bg-rose-500/10 text-rose-600',
  HIGH: 'bg-amber-500/10 text-amber-600',
  NORMAL: 'bg-slate-500/10 text-slate-500',
};

function ActionRow({ item }: { item: ActionItem }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => navigateToEntity(navigate, item.entityType, item.entityId)}
      onKeyDown={(e) => { if (e.key === 'Enter') navigateToEntity(navigate, item.entityType, item.entityId); }}
      className="flex min-h-11 cursor-pointer items-start gap-3 rounded-lg px-3 py-2.5 hover:bg-[var(--bg-hover)]"
    >
      <span className={`mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold uppercase ${PRIORITY_STYLE[item.priority]}`}>
        {t(`dashboard.priority.${item.priority}`, item.priority)}
      </span>
      <div className="min-w-0">
        <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>{item.title}</p>
        <p className="mt-0.5 text-[11px]" style={{ color: 'var(--text-muted)' }}>{item.reason}</p>
      </div>
    </div>
  );
}

export function ActionQueueWidget() {
  const { t } = useTranslation();
  const { data, loading, error, retry } = useOperationsCenter();

  return (
    <WidgetShell title={t('dashboard.actionQueue.title', 'Action Required')} icon={AlertTriangle} count={data?.actionQueue.length}>
      {error ? <WidgetError error={error} onRetry={retry} /> : loading ? <WidgetSkeleton /> : (
        data && data.actionQueue.length > 0 ? (
          <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
            {data.actionQueue.slice(0, 8).map((item, i) => <ActionRow key={i} item={item} />)}
          </div>
        ) : (
          <div className="flex items-center gap-2 py-4 text-xs" style={{ color: 'var(--text-muted)' }}>
            <CheckCircle2 size={14} className="text-emerald-500" />
            {t('dashboard.actionQueue.empty', 'Everything is under control.')}
          </div>
        )
      )}
    </WidgetShell>
  );
}

/* ── 3. Financial Control Center ─────────────────────────────────────────── */

function ChangeBadge({ change, label }: { change: { absoluteChange: number; percentChange: number | null; percentAvailable: boolean } | undefined; label: string }) {
  const { t } = useTranslation();
  if (!change) return null;
  if (!change.percentAvailable || change.percentChange == null) {
    return <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>{label}</span>;
  }
  const up = change.percentChange >= 0;
  return (
    <span className={`inline-flex items-center gap-0.5 text-[10px] font-semibold ${up ? 'text-emerald-500' : 'text-rose-500'}`}>
      {up ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
      {Math.abs(change.percentChange).toFixed(0)}% {t(label)}
    </span>
  );
}

export function FinancialControlCenterWidget() {
  const { t } = useTranslation();
  const { data, loading, error, retry } = useOperationsCenter();
  const f = data?.financial;

  return (
    <WidgetShell title={t('dashboard.financial.title', 'Financial Control Center')} icon={Wallet}>
      {error ? <WidgetError error={error} onRetry={retry} /> : loading ? <WidgetSkeleton rows={4} /> : !f ? (
        <p className="py-4 text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.empty', 'No payments recorded for this period.')}</p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.collectedToday', 'Collected today')}</p>
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{money(f.collectedToday)}</p>
            <ChangeBadge change={f.vsYesterday} label="dashboard.financial.vsYesterday" />
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.collectedMonth', 'Collected this month')}</p>
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{money(f.collectedThisMonth)}</p>
            <ChangeBadge change={f.vsLastMonth} label="dashboard.financial.vsLastMonth" />
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.unpaid', 'Unpaid balance')}</p>
            <p className="text-base font-bold text-amber-600">{money(f.unpaidBalance)}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.overdue', 'Overdue')}</p>
            <p className="text-base font-bold text-rose-600">{money(f.overdueBalance)}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.maintenanceExpenses', 'Maintenance expenses')}</p>
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{money(f.maintenanceExpenses)}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.netProfit', 'Net profit (est.)')}</p>
            <p className={`text-base font-bold ${f.netProfitEstimate >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>{money(f.netProfitEstimate)}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.avgRental', 'Avg. rental value')}</p>
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{money(f.averageRentalValue)}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase" style={{ color: 'var(--text-muted)' }}>{t('dashboard.financial.refunds', 'Refunds')}</p>
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>{money(f.refunds)}</p>
          </div>
        </div>
      )}
    </WidgetShell>
  );
}

/* ── 4. Vehicle Profitability (Complete pack) ────────────────────────────── */

export function VehicleProfitabilityWidget() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data, loading, error, retry } = useOperationsCenter();
  const vp = data?.vehicleProfitability;

  const rows = vp?.hasData ? [
    { key: 'topRevenue', label: t('dashboard.profitability.topRevenue', 'Top revenue'), entry: vp.topRevenue },
    { key: 'bestUtilization', label: t('dashboard.profitability.bestUtilization', 'Best utilization'), entry: vp.bestUtilization },
    { key: 'highestMaintenanceCost', label: t('dashboard.profitability.highestMaintenance', 'Highest maintenance cost'), entry: vp.highestMaintenanceCost },
    { key: 'lowestUtilization', label: t('dashboard.profitability.lowestUtilization', 'Lowest utilization'), entry: vp.lowestUtilization },
    { key: 'negativeContribution', label: t('dashboard.profitability.negative', 'Negative contribution'), entry: vp.negativeContribution },
  ].filter((r) => r.entry) : [];

  return (
    <WidgetShell title={t('dashboard.profitability.title', 'Vehicle Profitability')} icon={Car}>
      {error ? <WidgetError error={error} onRetry={retry} /> : loading ? <WidgetSkeleton /> : rows.length === 0 ? (
        <p className="py-4 text-xs" style={{ color: 'var(--text-muted)' }}>{t('dashboard.profitability.empty', 'Not enough rental activity yet to compute profitability.')}</p>
      ) : (
        <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
          {rows.map((r) => (
            <div key={r.key} role="button" tabIndex={0}
              onClick={() => navigate(`/vehicles?vehicleId=${r.entry!.vehicleId}`)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/vehicles?vehicleId=${r.entry!.vehicleId}`); }}
              className="flex min-h-11 cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2.5 hover:bg-[var(--bg-hover)]">
              <div className="min-w-0">
                <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>{r.label}</p>
                <p className="text-xs font-semibold truncate" style={{ color: 'var(--text-primary)' }}>{r.entry!.label}</p>
              </div>
              <span className={`shrink-0 text-xs font-bold ${r.entry!.profitContribution >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                {money(r.entry!.profitContribution)}
              </span>
            </div>
          ))}
        </div>
      )}
    </WidgetShell>
  );
}
