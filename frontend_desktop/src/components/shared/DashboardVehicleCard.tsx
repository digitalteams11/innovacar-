import { Car, Gauge, Fuel, FileText, Calendar, User, Wrench, Eye, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { translateFuelLevel, translateFuelType, translateVehicleCategory, translateVehicleStatus } from '../../utils/statusLabels';

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
  const key = (statut || 'AVAILABLE').toUpperCase();
  const cfg = STATUS_CFG[key] ?? { cls: 'bg-slate-500/15 text-slate-500 border-slate-500/25' };
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${cfg.cls}`}>
      {translateVehicleStatus(key)}
    </span>
  );
}

/* ─── avatar initial ─────────────────────────────────────────────────── */
function VehicleIcon({ imageUrl, marque }: { imageUrl?: string; marque: string }) {
  if (imageUrl) {
    return <img src={imageUrl} alt={marque} className="w-10 h-10 rounded-xl object-cover shrink-0" />;
  }
  return (
    <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
      style={{ background: 'var(--bg-card-hover)' }}>
      <Car size={20} style={{ color: 'var(--text-muted)' }} />
    </div>
  );
}

/* ─── main card ──────────────────────────────────────────────────────── */
export default function DashboardVehicleCard({ v, onReturn }: { v: VehicleCardData; onReturn?: (id: number) => void }) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const pct = fuelPercent(v.fuelLevelCurrent);
  const fuelLevelLabel = translateFuelLevel(v.fuelLevelCurrent) || '-';
  const fuelTypeLabel = translateFuelType(v.fuel);

  return (
    <div className="glass-card rounded-2xl p-4 flex flex-col gap-3 hover:shadow-lg transition-all duration-200"
      style={{ border: '1px solid var(--border-subtle)' }}>

      {/* Header */}
      <div className="flex items-start gap-3">
        <VehicleIcon imageUrl={v.imageUrl} marque={v.marque} />
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm leading-tight truncate" style={{ color: 'var(--text-primary)' }}>
            {v.marque}
          </p>
          <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {v.plate ?? '-'} {v.category ? ` - ${translateVehicleCategory(v.category)}` : ''}
          </p>
        </div>
        <StatusBadge statut={v.statut} />
      </div>

      {/* Fuel level bar */}
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

      {/* Mileage + fuel type */}
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

      {/* Active contract info */}
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

      {/* Quick actions */}
      <div className="flex gap-2 flex-wrap pt-1 border-t" style={{ borderColor: 'var(--border-subtle)' }}>
        <button onClick={() => navigate(`/vehicles`)}
          className="flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80"
          style={{ background: 'var(--bg-card-hover)', color: 'var(--text-secondary)' }}>
          <Eye size={11} /> {t('common.view')}
        </button>
        {v.statut === 'AVAILABLE' && (
          <>
            <button onClick={() => navigate('/reservations')}
              className="flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80"
              style={{ background: 'rgba(16,185,129,0.1)', color: 'rgb(16,185,129)' }}>
              <Calendar size={11} /> {t('common.reserve')}
            </button>
            <button onClick={() => navigate('/contracts')}
              className="flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80"
              style={{ background: 'rgba(59,130,246,0.1)', color: 'rgb(59,130,246)' }}>
              <FileText size={11} /> {t('common.contract')}
            </button>
          </>
        )}
        {(v.statut === 'RENTED' || v.statut === 'ACTIVE') && onReturn && v.activeContractId && (
          <button onClick={() => onReturn(v.activeContractId!)}
            className="flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80"
            style={{ background: 'rgba(245,158,11,0.1)', color: 'rgb(245,158,11)' }}>
            <Plus size={11} /> {t('common.return')}
          </button>
        )}
        {v.statut !== 'IN_MAINTENANCE' && v.statut !== 'MAINTENANCE' && (
          <button onClick={() => navigate('/maintenance')}
            className="flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1.5 rounded-lg transition-all hover:opacity-80"
            style={{ background: 'rgba(239,68,68,0.1)', color: 'rgb(239,68,68)' }}>
            <Wrench size={11} /> {t('common.maintenance')}
          </button>
        )}
      </div>
    </div>
  );
}
