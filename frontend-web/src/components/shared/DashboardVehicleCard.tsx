import type { ReactNode } from 'react';
import { Car, Gauge, Fuel, FileText, Calendar, User, Wrench, Eye, ArrowLeftRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { normalizeStatusCode, translateFuelLevel, translateFuelType, translateVehicleCategory, translateVehicleStatus } from '../../utils/statusLabels';
import { navigateToVehicleAction } from '../../lib/vehicleActions';
import { useToast } from '../../context/ToastContext';
import ActionMenu, { type ActionMenuItem } from './ActionMenu';

/* ─── types ─────────────────────────────────────────────────────────── */
export interface VehicleCardData {
  id: number;
  marque: string;
  plate?: string;
  category?: string;
  statut?: string;
  prixJour?: number;
  fuel?: string;
  fuelLevelCurrent?: string;
  mileageCurrent?: number;
  imageUrl?: string;
  insuranceExpiration?: string;
  technicalInspectionExpiration?: string;
  conditionStatus?: string;
  activeContractNumber?: string;
  activeContractId?: number;
  clientName?: string;
  contractEndDate?: string;
}

/* ─── fuel order for visual bar ─────────────────────────────────────── */
const FUEL_LEVELS = ['EMPTY', 'QUARTER', 'HALF', 'THREE_QUARTERS', 'FULL'];
function fuelPercent(level?: string): number {
  if (!level) return 0;
  const idx = FUEL_LEVELS.indexOf(level.toUpperCase());
  return idx < 0 ? 0 : (idx / (FUEL_LEVELS.length - 1)) * 100;
}

function fuelColor(level?: string): string {
  const pct = fuelPercent(level);
  if (pct <= 25) return 'bg-rose-500';
  if (pct <= 50) return 'bg-amber-400';
  return 'bg-emerald-500';
}

/* ─── status badge ───────────────────────────────────────────────────── */
const STATUS_CFG: Record<string, { cls: string }> = {
  AVAILABLE:      { cls: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/25' },
  RESERVED:       { cls: 'bg-amber-500/15  text-amber-600   dark:text-amber-300   border-amber-500/25'   },
  RENTED:         { cls: 'bg-blue-500/15   text-blue-600    dark:text-blue-300    border-blue-500/25'    },
  IN_MAINTENANCE: { cls: 'bg-rose-500/15   text-rose-600    dark:text-rose-300    border-rose-500/25'    },
  MAINTENANCE:    { cls: 'bg-rose-500/15   text-rose-600    dark:text-rose-300    border-rose-500/25'    },
  OUT_OF_SERVICE: { cls: 'bg-slate-500/15  text-slate-500   dark:text-slate-400   border-slate-500/25'   },
};

function StatusBadge({ statut }: { statut?: string }) {
  const key = normalizeStatusCode(statut) || 'AVAILABLE';
  const cfg = STATUS_CFG[key] ?? { cls: 'bg-slate-500/15 text-slate-500 border-slate-500/25' };
  return (
    <span className={`inline-flex shrink-0 items-center rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${cfg.cls}`}>
      {translateVehicleStatus(key)}
    </span>
  );
}

/* ─── avatar initial ─────────────────────────────────────────────────── */
function VehicleIcon({ imageUrl, marque }: { imageUrl?: string; marque: string }) {
  if (imageUrl) {
    return <img src={imageUrl} alt={marque} className="w-11 h-11 rounded-xl object-cover shrink-0" />;
  }
  return (
    <div className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
      style={{ background: 'var(--bg-card-hover)' }}>
      <Car size={20} style={{ color: 'var(--text-muted)' }} />
    </div>
  );
}

interface CardAction {
  key: string;
  label: string;
  icon: ReactNode;
  onClick: () => void;
}

/* ─── main card ──────────────────────────────────────────────────────── */
export default function DashboardVehicleCard({ v, onReturn }: { v: VehicleCardData; onReturn?: (id: number) => void }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { showToast } = useToast();
  const pct = fuelPercent(v.fuelLevelCurrent);
  const fuelLevelLabel = translateFuelLevel(v.fuelLevelCurrent) || '-';
  const fuelTypeLabel = translateFuelType(v.fuel);
  const status = normalizeStatusCode(v.statut) || 'AVAILABLE';

  // Every action on this card must carry v.id (the real database key) into
  // the destination workflow via the URL — never silently drop it, never
  // fall back to an index or the plate string. See lib/vehicleActions.ts.
  const goTo = (action: 'view' | 'reserve' | 'contract' | 'maintenance') => {
    navigateToVehicleAction(action, v.id, (path) => navigate(path), () => {
      showToast(t('common.vehicleActionInvalidId', 'Unable to open this action — the vehicle reference is missing.'), 'error');
    });
  };

  const viewAction: CardAction = { key: 'view', label: t('common.view'), icon: <Eye size={14} />, onClick: () => goTo('view') };
  const contractAction: CardAction = { key: 'contract', label: t('common.contract'), icon: <FileText size={14} />, onClick: () => goTo('contract') };
  const maintenanceAction: CardAction = { key: 'maintenance', label: t('common.maintenance'), icon: <Wrench size={14} />, onClick: () => goTo('maintenance') };
  const activeContractAction: CardAction | null = v.activeContractId
    ? { key: 'active-contract', label: t('vehicles.activeContract', 'Active contract'), icon: <FileText size={14} />, onClick: () => navigate(`/contracts/${v.activeContractId}`) }
    : null;
  const returnAction: CardAction | null = onReturn && v.activeContractId
    ? { key: 'return', label: t('common.return'), icon: <ArrowLeftRight size={14} />, onClick: () => onReturn(v.activeContractId!) }
    : null;

  // Exactly one primary + one secondary action visible, everything else in
  // the overflow menu — never five competing colored buttons on one card
  // (spec: "one primary action, one secondary action, optional overflow").
  let primary: CardAction = viewAction;
  let secondary: CardAction | null = null;
  let overflow: CardAction[] = [];

  if (status === 'AVAILABLE') {
    primary = { key: 'reserve', label: t('common.reserve'), icon: <Calendar size={14} />, onClick: () => goTo('reserve') };
    secondary = viewAction;
    overflow = [contractAction, maintenanceAction];
  } else if (status === 'RESERVED') {
    // No reservation id is threaded onto the dashboard vehicle-card payload
    // today, so "open reservation" isn't a real deep link yet — contract
    // creation is the next real actionable step for a reserved vehicle.
    primary = contractAction;
    secondary = viewAction;
    overflow = [maintenanceAction];
  } else if (status === 'RENTED' || status === 'ACTIVE') {
    primary = returnAction ?? viewAction;
    secondary = returnAction ? (activeContractAction ?? viewAction) : (activeContractAction ?? null);
    overflow = [maintenanceAction];
  } else if (status === 'IN_MAINTENANCE' || status === 'MAINTENANCE') {
    // No standalone maintenance-record detail view exists yet — View remains
    // the only real destination for a vehicle already in maintenance.
    primary = viewAction;
    secondary = null;
    overflow = [];
  } else {
    overflow = [maintenanceAction];
  }

  const overflowItems: ActionMenuItem[] = overflow
    .filter((a) => a.key !== primary.key && a.key !== secondary?.key)
    .map((a) => ({ label: a.label, icon: a.icon, onClick: a.onClick }));

  return (
    <div className="glass-card rounded-2xl p-4 flex flex-col gap-3 hover:shadow-lg transition-all duration-200"
      style={{ border: '1px solid var(--border-subtle)' }}>

      {/* Header — the vehicle name is never truncated: it wraps onto a
          second line instead of clipping (spec: "ensure the full vehicle
          name is readable"). Registration/category sit below it, always
          fully visible since they're short, fixed-format strings. */}
      <div className="flex items-start gap-3">
        <VehicleIcon imageUrl={v.imageUrl} marque={v.marque} />
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm leading-snug break-words" style={{ color: 'var(--text-primary)' }}>
            {v.marque}
          </p>
          <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {v.plate ?? '-'} {v.category ? ` - ${translateVehicleCategory(v.category)}` : ''}
          </p>
        </div>
        <StatusBadge statut={v.statut} />
      </div>

      {/* Fuel level bar — only rendered when real data exists (spec). */}
      {v.fuelLevelCurrent && (
        <div className="space-y-1">
          <div className="flex justify-between items-center">
            <span className="flex items-center gap-1 text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>
              <Fuel size={11} /> {t('vehicles.fuel')}
            </span>
            <span className="text-[10px] font-bold" style={{ color: 'var(--text-secondary)' }}>{fuelLevelLabel}</span>
          </div>
          <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--bg-card-hover)' }}>
            <div className={`h-full rounded-full transition-all ${fuelColor(v.fuelLevelCurrent)}`}
              style={{ width: `${pct}%` }} />
          </div>
        </div>
      )}

      {/* Mileage + fuel type + daily price */}
      <div className="flex gap-3 text-[11px]" style={{ color: 'var(--text-muted)' }}>
        {v.mileageCurrent != null && (
          <span className="flex items-center gap-1">
            <Gauge size={11} /> {v.mileageCurrent.toLocaleString()} km
          </span>
        )}
        {v.fuel && (
          <span className="flex items-center gap-1">
            <Fuel size={11} /> {fuelTypeLabel}
          </span>
        )}
        {v.prixJour != null && (
          <span className="ml-auto font-semibold" style={{ color: 'var(--text-primary)' }}>
            {v.prixJour} {t('vehicleSelector.madPerDay')}
          </span>
        )}
      </div>

      {/* Active contract info — shown only when relevant (spec). */}
      {v.activeContractNumber && (
        <div className="rounded-xl px-3 py-2 space-y-1"
          style={{ background: 'var(--bg-card-hover)', border: '1px solid var(--border-subtle)' }}>
          <div className="flex items-center gap-1.5 text-[10px]" style={{ color: 'var(--text-muted)' }}>
            <FileText size={11} />
            <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>
              {v.activeContractNumber}
            </span>
            {v.contractEndDate && (
              <span className="ml-auto flex items-center gap-1">
                <Calendar size={10} /> {v.contractEndDate}
              </span>
            )}
          </div>
          {v.clientName && (
            <div className="flex items-center gap-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
              <User size={10} /> {v.clientName}
            </div>
          )}
        </div>
      )}

      {/* Footer — one primary action (brand-colored), one secondary action
          (neutral outline), everything else behind a single overflow menu.
          No more than two visible buttons regardless of vehicle status, and
          maintenance no longer defaults to a large red/danger button just
          because it *can* be destructive-adjacent (spec section 7). */}
      <div className="flex items-center gap-2 pt-1 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
        <button onClick={primary.onClick}
          aria-label={primary.label}
          className="flex-1 flex items-center justify-center gap-1.5 min-h-10 text-xs font-bold px-3 py-2 rounded-xl transition-all hover:opacity-90 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)]"
          style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)' }}>
          {primary.icon} {primary.label}
        </button>
        {secondary && (
          <button onClick={secondary.onClick}
            aria-label={secondary.label}
            className="flex-1 flex items-center justify-center gap-1.5 min-h-10 text-xs font-bold px-3 py-2 rounded-xl border transition-all hover:bg-[var(--bg-hover)] active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)]"
            style={{ color: 'var(--text-secondary)', borderColor: 'var(--border-medium)' }}>
            {secondary.icon} {secondary.label}
          </button>
        )}
        {overflowItems.length > 0 && (
          <ActionMenu items={overflowItems} ariaLabel={t('common.moreActions', 'More actions')} />
        )}
      </div>
    </div>
  );
}
