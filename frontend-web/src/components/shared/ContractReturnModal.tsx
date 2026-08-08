import { Fuel, Gauge, FileText, AlertTriangle, CheckCircle2, RotateCcw } from 'lucide-react';
import api from '../../api/axios';
import Modal from '../Modal';
import { useInlineAction } from '../../hooks/useInlineAction';
import AnimatedStatusIcon from './AnimatedStatusIcon';
import Tooltip from './Tooltip';
import { cn } from '../../lib/utils';
import { useState } from 'react';

interface ContractReturnModalProps {
  isOpen: boolean;
  contractId: number;
  onClose: () => void;
  onSuccess: () => void;
  /** Optional: fuel level recorded at departure for comparison */
  fuelLevelStart?: string;
  /** Optional: mileage recorded at departure for comparison */
  mileageStart?: number;
  /** Contract total BEFORE this return's fees — used for the pre-validation recap only;
   *  the authoritative recalculation always happens server-side (see ContractService#processReturnInspection). */
  baseTotal?: number;
  /** Already collected on this contract — used for the recap's "remaining to pay" estimate. */
  paidAmount?: number;
  currency?: string;
}

const FUEL_LEVELS = ['EMPTY', 'QUARTER', 'HALF', 'THREE_QUARTERS', 'FULL'];
const FUEL_LABELS: Record<string, string> = {
  EMPTY: 'Empty',
  QUARTER: '1/4',
  HALF: '1/2',
  THREE_QUARTERS: '3/4',
  FULL: 'Full',
};

function fuelIndex(level?: string): number {
  if (!level) return -1;
  return FUEL_LEVELS.indexOf(level.toUpperCase());
}

/**
 * Migrated onto the shared Modal component (was a bespoke `fixed inset-0
 * z-50` overlay before) — that z-50 was numerically equal to
 * BottomNavigation's z-50, and since this modal rendered inline inside
 * page content (before BottomNavigation in the DOM, not through a portal),
 * equal-z-index stacking fell back to DOM order and BottomNavigation
 * (later in Layout.tsx's markup) painted over this modal's footer. Modal.tsx
 * renders through a document.body portal at z-[var(--z-modal-overlay)]
 * (1000), unambiguously above the bottom nav (50) regardless of DOM
 * position, which is the actual fix for "Confirm/Cancel not reachable."
 */
export default function ContractReturnModal({
  isOpen, contractId, onClose, onSuccess, fuelLevelStart, mileageStart,
  baseTotal, paidAmount, currency = 'MAD',
}: ContractReturnModalProps) {
  const [fuelLevelEnd, setFuelLevelEnd] = useState('FULL');
  const [mileageEnd, setMileageEnd] = useState('');
  const [conditionEndNote, setConditionEndNote] = useState('');
  const [damageEndNote, setDamageEndNote] = useState('');
  const [extraFuelFee, setExtraFuelFee] = useState(0);
  const [damageFee, setDamageFee] = useState(0);

  const fuelWarning = fuelLevelStart
    ? fuelIndex(fuelLevelEnd) < fuelIndex(fuelLevelStart)
    : false;
  const mileageDriven = mileageStart && mileageEnd
    ? parseInt(mileageEnd) - mileageStart
    : null;

  // Pre-validation estimate only — the real total/remaining are always
  // recalculated server-side (ContractService#processReturnInspection +
  // InvoiceService#syncInvoiceForContract) once "Confirm Return" is clicked.
  const hasRecap = baseTotal != null;
  const estimatedTotal = (baseTotal ?? 0) + extraFuelFee + damageFee;
  const estimatedRemaining = Math.max(estimatedTotal - (paidAmount ?? 0), 0);

  const submitAction = useInlineAction(async () => {
    await api.post(`/contracts/${contractId}/return-inspection`, {
      fuelLevelEnd,
      mileageEnd: mileageEnd ? parseInt(mileageEnd) : null,
      conditionEndNote: conditionEndNote || null,
      damageEndNote: damageEndNote || null,
      extraFuelFee: extraFuelFee > 0 ? extraFuelFee : null,
      damageFee: damageFee > 0 ? damageFee : null,
    });
    // The parent closes this modal and refreshes the contract on success —
    // that transition is the confirmation, no toast needed.
    onSuccess();
  }, { context: 'contract-return-inspection' });
  const submitting = submitAction.phase === 'loading';

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      maxWidth="max-w-lg"
      title={
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-amber-500/15 flex items-center justify-center shrink-0">
            <RotateCcw size={17} className="text-amber-500" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>Vehicle Return Inspection</p>
            <p className="text-[11px] font-normal" style={{ color: 'var(--text-muted)' }}>Contract #{contractId}</p>
          </div>
        </div>
      }
      footer={
        <div className="flex gap-3">
          <button onClick={onClose} disabled={submitting}
            className="flex-1 py-3 rounded-xl text-sm font-semibold transition-all min-h-[44px]"
            style={{ background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}>
            Cancel
          </button>
          <Tooltip label={submitAction.phase === 'error' ? submitAction.errorMessage : null}>
            <button onClick={() => submitAction.run()} disabled={submitting}
              className={cn(
                'flex-1 py-3 rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-all min-h-[44px]',
                submitAction.phase === 'error' && 'ring-2 ring-red-400',
              )}
              style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)', opacity: submitting ? 0.7 : 1 }}>
              <AnimatedStatusIcon phase={submitAction.phase} idleIcon={CheckCircle2} size={16} />
              Confirm Return
            </button>
          </Tooltip>
        </div>
      }
    >
      <div className="space-y-5">
        {/* Fuel Level */}
        <div className="space-y-2">
          <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
            <Fuel size={12} /> Fuel Level at Return
          </label>
          <div className="grid grid-cols-5 gap-1.5">
            {FUEL_LEVELS.map(level => (
              <button key={level} type="button"
                onClick={() => setFuelLevelEnd(level)}
                className={`py-2 rounded-xl text-[10px] font-bold border transition-all ${
                  fuelLevelEnd === level
                    ? 'border-amber-500 bg-amber-500/15 text-amber-600 dark:text-amber-300'
                    : 'border-transparent hover:border-[var(--border-subtle)]'
                }`}
                style={{ color: fuelLevelEnd !== level ? 'var(--text-muted)' : undefined,
                  background: fuelLevelEnd !== level ? 'var(--bg-hover)' : undefined }}>
                {FUEL_LABELS[level]}
              </button>
            ))}
          </div>

          {/* Fuel comparison warning */}
          {fuelWarning && (
            <div className="flex items-center gap-2 p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/25">
              <AlertTriangle size={13} className="text-amber-500 shrink-0" />
              <p className="text-[11px] text-amber-600 dark:text-amber-300 font-medium">
                Fuel returned lower than departure ({FUEL_LABELS[fuelLevelStart!.toUpperCase()] ?? fuelLevelStart} → {FUEL_LABELS[fuelLevelEnd]}). Consider adding a fuel charge.
              </p>
            </div>
          )}
        </div>

        {/* Mileage — kept here on purpose: this value only updates the vehicle's
            own fleet-mileage tracker (Vehicle.mileageCurrent) so odometer
            history keeps working. It is never stored on or shown in the
            contract/PDF itself — that was removed as a deliberate business
            decision (Moroccan rental contracts here no longer use mileage). */}
        <div className="space-y-2">
          <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
            <Gauge size={12} /> Mileage at Return (fleet record only)
          </label>
          <input
            type="number"
            value={mileageEnd}
            onChange={e => setMileageEnd(e.target.value)}
            placeholder="Final odometer reading (km)"
            className="w-full px-4 py-2.5 rounded-xl text-sm outline-none"
            style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
          />
          {mileageDriven != null && mileageDriven >= 0 && (
            <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
              Distance driven: <strong style={{ color: 'var(--text-secondary)' }}>{mileageDriven.toLocaleString()} km</strong>
              {mileageStart && <span> (from {mileageStart.toLocaleString()} km)</span>}
            </p>
          )}
        </div>

        {/* Condition notes */}
        <div className="space-y-2">
          <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
            <FileText size={12} /> Condition Notes
          </label>
          <textarea
            value={conditionEndNote}
            onChange={e => setConditionEndNote(e.target.value)}
            placeholder="General vehicle condition at return..."
            rows={2}
            className="w-full px-4 py-2.5 rounded-xl text-sm outline-none resize-none max-h-40"
            style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
          />
          <textarea
            value={damageEndNote}
            onChange={e => setDamageEndNote(e.target.value)}
            placeholder="Damage observed at return (if any)..."
            rows={2}
            className="w-full px-4 py-2.5 rounded-xl text-sm outline-none resize-none max-h-40"
            style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
          />
        </div>

        {/* Optional fees */}
        <div className="space-y-2">
          <label className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Optional Fees (MAD)</label>
          <div className="grid grid-cols-2 gap-2">
            {[
              { label: 'Fuel Charge', val: extraFuelFee, set: setExtraFuelFee },
              { label: 'Damage Fee', val: damageFee, set: setDamageFee },
            ].map(f => (
              <div key={f.label} className="space-y-1">
                <p className="text-[10px] font-medium" style={{ color: 'var(--text-muted)' }}>{f.label}</p>
                <input
                  type="number"
                  min={0}
                  value={f.val || ''}
                  onChange={e => f.set(Number(e.target.value))}
                  placeholder="0"
                  className="w-full px-3 py-2 rounded-xl text-sm outline-none"
                  style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
                />
              </div>
            ))}
          </div>
        </div>

        {/* Financial recap — shown before validation so nothing is signed off blind */}
        {hasRecap && (
          <div className="rounded-2xl p-4 space-y-2 border" style={{ background: 'var(--bg-hover)', borderColor: 'var(--border-subtle)' }}>
            <div className="flex items-center gap-2 mb-1" style={{ color: 'var(--text-muted)' }}>
              <span className="text-xs font-bold uppercase tracking-wider">Récapitulatif</span>
            </div>
            <div className="flex justify-between text-sm">
              <span style={{ color: 'var(--text-muted)' }}>Location</span>
              <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{(baseTotal ?? 0).toLocaleString()} {currency}</span>
            </div>
            {extraFuelFee > 0 && (
              <div className="flex justify-between text-sm text-amber-600 dark:text-amber-400">
                <span>Carburant</span>
                <span className="font-medium">+ {extraFuelFee.toLocaleString()} {currency}</span>
              </div>
            )}
            {damageFee > 0 && (
              <div className="flex justify-between text-sm text-amber-600 dark:text-amber-400">
                <span>Dommages</span>
                <span className="font-medium">+ {damageFee.toLocaleString()} {currency}</span>
              </div>
            )}
            <div className="h-px my-2" style={{ background: 'var(--border-subtle)' }} />
            <div className="flex justify-between text-sm">
              <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>TOTAL</span>
              <span className="font-bold" style={{ color: 'var(--text-primary)' }}>{estimatedTotal.toLocaleString()} {currency}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span style={{ color: 'var(--text-muted)' }}>Déjà payé</span>
              <span className="font-medium" style={{ color: 'var(--text-primary)' }}>{(paidAmount ?? 0).toLocaleString()} {currency}</span>
            </div>
            <div className="flex justify-between text-base pt-1">
              <span className="font-bold" style={{ color: 'var(--text-primary)' }}>Reste à payer</span>
              <span className={cn('font-black', estimatedRemaining > 0 ? 'text-danger-600' : 'text-success-600')}>
                {estimatedRemaining.toLocaleString()} {currency}
              </span>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
