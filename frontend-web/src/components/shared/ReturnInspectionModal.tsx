import { useState } from 'react';
import { Shield, AlertCircle, Calculator, CheckCircle2 } from 'lucide-react';
import api from '../../api/axios';
import Modal from '../Modal';
import { useInlineAction } from '../../hooks/useInlineAction';
import AnimatedStatusIcon from './AnimatedStatusIcon';
import Tooltip from './Tooltip';
import { cn } from '../../lib/utils';

interface ReturnInspectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  depositId: number;
  depositAmount: number;
  contractId: number;
  onSuccess: () => void;
}

/**
 * Migrated onto the shared Modal component (was a bespoke `fixed inset-0
 * z-50` overlay before) — see ContractReturnModal.tsx for why that z-index
 * let the mobile bottom nav paint over this modal's footer.
 */
export default function ReturnInspectionModal({
  isOpen,
  onClose,
  depositId,
  depositAmount,
  onSuccess,
}: ReturnInspectionModalProps) {
  const [fuelLevel, setFuelLevel] = useState('Full');
  const [interiorCondition, setInteriorCondition] = useState('Clean');
  const [exteriorCondition, setExteriorCondition] = useState('Clean');
  const [missingItems, setMissingItems] = useState('');

  const [damageCost, setDamageCost] = useState(0);
  const [cleaningCost, setCleaningCost] = useState(0);
  const [lateFee, setLateFee] = useState(0);
  const [fuelCharge, setFuelCharge] = useState(0);
  const [otherCharge, setOtherCharge] = useState(0);
  const [returnNotes, setReturnNotes] = useState('');

  const totalDeductions = damageCost + cleaningCost + lateFee + fuelCharge + otherCharge;
  const returnedAmount = Math.max(depositAmount - totalDeductions, 0);

  const submitAction = useInlineAction(async () => {
    if (totalDeductions > depositAmount) {
      throw new Error('Total deductions cannot exceed the deposit amount.');
    }
    await api.post(`/deposits/${depositId}/return`, {
      damageDeduction: damageCost,
      cleaningDeduction: cleaningCost,
      lateFeeDeduction: lateFee,
      fuelDeduction: fuelCharge,
      otherDeduction: otherCharge,
      returnNotes,
      fuelLevelEnd: fuelLevel,
      interiorCondition,
      exteriorCondition,
      missingItems,
    });
    onSuccess();
    onClose();
  }, { context: 'deposit-return' });
  const isSubmitting = submitAction.phase === 'loading';

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      maxWidth="max-w-2xl"
      title={
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-brand-100 rounded-xl flex items-center justify-center shrink-0">
            <Shield size={20} className="text-brand-500" />
          </div>
          <div className="min-w-0">
            <p className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>Vehicle Return Inspection</p>
            <p className="text-xs font-normal" style={{ color: 'var(--text-muted)' }}>Process deposit return after inspection</p>
          </div>
        </div>
      }
      footer={
        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 py-3 bg-slate-100 text-slate-700 rounded-xl font-semibold text-sm hover:bg-slate-200 transition-all min-h-[44px]">
            Cancel
          </button>
          <Tooltip label={submitAction.phase === 'error' ? submitAction.errorMessage : null}>
            <button onClick={() => submitAction.run()} disabled={isSubmitting || totalDeductions > depositAmount}
              className={cn(
                'flex-1 py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all disabled:opacity-50 flex items-center justify-center gap-2 min-h-[44px]',
                submitAction.phase === 'error' && 'ring-2 ring-red-400',
              )}>
              <AnimatedStatusIcon phase={submitAction.phase} idleIcon={CheckCircle2} size={18} />
              Confirm Return
            </button>
          </Tooltip>
        </div>
      }
    >
      <div className="space-y-6">
        {/* Inspection Checklist */}
        <div className="space-y-4">
          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400">Inspection Checklist</h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-500">Fuel Level</label>
              <select value={fuelLevel} onChange={(e) => setFuelLevel(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100">
                <option>Full</option>
                <option>3/4</option>
                <option>1/2</option>
                <option>1/4</option>
                <option>Empty</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-500">Interior Condition</label>
              <select value={interiorCondition} onChange={(e) => setInteriorCondition(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100">
                <option>Clean</option>
                <option>Light Dirt</option>
                <option>Heavy Dirt</option>
                <option>Damage</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-500">Exterior Condition</label>
              <select value={exteriorCondition} onChange={(e) => setExteriorCondition(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100">
                <option>Clean</option>
                <option>Light Scratches</option>
                <option>Damage</option>
              </select>
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-500">Missing Items / Notes</label>
            <textarea value={missingItems} onChange={(e) => setMissingItems(e.target.value)}
              placeholder="Describe any missing items or other observations..."
              rows={2}
              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 resize-none max-h-40" />
          </div>
        </div>

        {/* Deductions */}
        <div className="space-y-4">
          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400">Deductions</h4>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {[
              { label: 'Damage Cost', value: damageCost, setter: setDamageCost },
              { label: 'Cleaning Cost', value: cleaningCost, setter: setCleaningCost },
              { label: 'Late Return Fee', value: lateFee, setter: setLateFee },
              { label: 'Fuel Charge', value: fuelCharge, setter: setFuelCharge },
              { label: 'Other Charges', value: otherCharge, setter: setOtherCharge },
            ].map((field) => (
              <div key={field.label} className="space-y-1">
                <label className="text-xs font-medium text-slate-500">{field.label}</label>
                <div className="relative">
                  <input type="number" min={0} value={field.value} onChange={(e) => field.setter(Number(e.target.value))}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 pe-12" />
                  <span className="absolute end-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">MAD</span>
                </div>
              </div>
            ))}
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-500">Return Notes</label>
            <textarea value={returnNotes} onChange={(e) => setReturnNotes(e.target.value)}
              placeholder="Additional notes about the return..."
              rows={2}
              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 resize-none max-h-40" />
          </div>
        </div>

        {/* Calculation */}
        <div className="bg-slate-50 rounded-2xl p-4 space-y-2 border border-slate-200">
          <div className="flex items-center gap-2 text-slate-500 mb-2">
            <Calculator size={14} />
            <span className="text-xs font-bold uppercase tracking-wider">Deposit Calculation</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Deposit</span>
            <span className="font-bold text-[#1e293b]">{depositAmount} MAD</span>
          </div>
          {damageCost > 0 && (
            <div className="flex justify-between text-sm text-danger-600">
              <span>Damage</span>
              <span className="font-medium">- {damageCost} MAD</span>
            </div>
          )}
          {cleaningCost > 0 && (
            <div className="flex justify-between text-sm text-danger-600">
              <span>Cleaning</span>
              <span className="font-medium">- {cleaningCost} MAD</span>
            </div>
          )}
          {lateFee > 0 && (
            <div className="flex justify-between text-sm text-danger-600">
              <span>Late Fee</span>
              <span className="font-medium">- {lateFee} MAD</span>
            </div>
          )}
          {fuelCharge > 0 && (
            <div className="flex justify-between text-sm text-danger-600">
              <span>Fuel</span>
              <span className="font-medium">- {fuelCharge} MAD</span>
            </div>
          )}
          {otherCharge > 0 && (
            <div className="flex justify-between text-sm text-danger-600">
              <span>Other</span>
              <span className="font-medium">- {otherCharge} MAD</span>
            </div>
          )}
          <div className="h-px bg-slate-200 my-2" />
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Total Deductions</span>
            <span className="font-bold text-danger-600">- {totalDeductions} MAD</span>
          </div>
          <div className="flex justify-between text-base">
            <span className="font-bold text-[#1e293b]">Deposit Returned</span>
            <span className="font-black text-success-600">{returnedAmount} MAD</span>
          </div>
          {totalDeductions > depositAmount && (
            <div className="flex items-center gap-2 text-danger-600 text-xs mt-2">
              <AlertCircle size={14} />
              <span>Total deductions exceed deposit amount</span>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
