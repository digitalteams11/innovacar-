import { useState } from 'react';
import { X, Fuel, Gauge, FileText, AlertTriangle, CheckCircle2, RotateCcw } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';

interface ContractReturnModalProps {
  isOpen: boolean;
  contractId: number;
  onClose: () => void;
  onSuccess: () => void;
  /** Optional: fuel level recorded at departure for comparison */
  fuelLevelStart?: string;
  /** Optional: mileage recorded at departure for comparison */
  mileageStart?: number;
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

export default function ContractReturnModal({
  isOpen, contractId, onClose, onSuccess, fuelLevelStart, mileageStart,
}: ContractReturnModalProps) {
  const { showToast } = useToast();
  const [submitting, setSubmitting] = useState(false);

  const [fuelLevelEnd, setFuelLevelEnd] = useState('FULL');
  const [mileageEnd, setMileageEnd] = useState('');
  const [conditionEndNote, setConditionEndNote] = useState('');
  const [damageEndNote, setDamageEndNote] = useState('');
  const [extraFuelFee, setExtraFuelFee] = useState(0);
  const [extraMileageFee, setExtraMileageFee] = useState(0);
  const [damageFee, setDamageFee] = useState(0);

  const fuelWarning = fuelLevelStart
    ? fuelIndex(fuelLevelEnd) < fuelIndex(fuelLevelStart)
    : false;
  const mileageDriven = mileageStart && mileageEnd
    ? parseInt(mileageEnd) - mileageStart
    : null;

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await api.post(`/contracts/${contractId}/return-inspection`, {
        fuelLevelEnd,
        mileageEnd: mileageEnd ? parseInt(mileageEnd) : null,
        conditionEndNote: conditionEndNote || null,
        damageEndNote: damageEndNote || null,
        extraFuelFee: extraFuelFee > 0 ? extraFuelFee : null,
        extraMileageFee: extraMileageFee > 0 ? extraMileageFee : null,
        damageFee: damageFee > 0 ? damageFee : null,
      });
      showToast('Vehicle returned successfully. Contract completed.', 'success');
      onSuccess();
    } catch (err: any) {
      showToast(err?.userMessage ?? err?.message ?? 'Failed to process return', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full sm:max-w-lg max-h-[90vh] overflow-y-auto rounded-t-3xl sm:rounded-3xl shadow-2xl"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)' }}>

        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-5 py-4 border-b"
          style={{ background: 'var(--bg-card)', borderColor: 'var(--border-subtle)' }}>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-amber-500/15 flex items-center justify-center">
              <RotateCcw size={17} className="text-amber-500" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>Vehicle Return Inspection</h3>
              <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Contract #{contractId}</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 rounded-xl hover:opacity-70 transition-opacity"
            style={{ color: 'var(--text-muted)' }}>
            <X size={17} />
          </button>
        </div>

        <div className="p-5 space-y-5">

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

          {/* Mileage */}
          <div className="space-y-2">
            <label className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>
              <Gauge size={12} /> Mileage at Return
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
              className="w-full px-4 py-2.5 rounded-xl text-sm outline-none resize-none"
              style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
            />
            <textarea
              value={damageEndNote}
              onChange={e => setDamageEndNote(e.target.value)}
              placeholder="Damage observed at return (if any)..."
              rows={2}
              className="w-full px-4 py-2.5 rounded-xl text-sm outline-none resize-none"
              style={{ background: 'var(--bg-hover)', border: '1px solid var(--border-subtle)', color: 'var(--text-primary)' }}
            />
          </div>

          {/* Optional fees */}
          <div className="space-y-2">
            <label className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Optional Fees (MAD)</label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { label: 'Fuel Charge', val: extraFuelFee, set: setExtraFuelFee },
                { label: 'Mileage Fee', val: extraMileageFee, set: setExtraMileageFee },
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

          {/* Actions */}
          <div className="flex gap-3 pt-1">
            <button onClick={onClose} disabled={submitting}
              className="flex-1 py-3 rounded-xl text-sm font-semibold transition-all"
              style={{ background: 'var(--bg-hover)', color: 'var(--text-secondary)' }}>
              Cancel
            </button>
            <button onClick={handleSubmit} disabled={submitting}
              className="flex-1 py-3 rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-all"
              style={{ background: 'var(--brand-primary)', color: 'var(--brand-primary-foreground)', opacity: submitting ? 0.7 : 1 }}>
              {submitting
                ? <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                : <CheckCircle2 size={16} />
              }
              Confirm Return
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
