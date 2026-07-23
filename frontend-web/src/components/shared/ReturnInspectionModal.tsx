import { useState } from 'react';
import { X, Shield, AlertCircle, Calculator, CheckCircle2 } from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';

interface ReturnInspectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  depositId: number;
  depositAmount: number;
  contractId: number;
  onSuccess: () => void;
}

export default function ReturnInspectionModal({
  isOpen,
  onClose,
  depositId,
  depositAmount,
  onSuccess,
}: ReturnInspectionModalProps) {
  const { showToast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);

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

  const handleSubmit = async () => {
    if (totalDeductions > depositAmount) {
      showToast('Total deductions cannot exceed deposit amount', 'warning');
      return;
    }
    setIsSubmitting(true);
    try {
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
      showToast('Deposit return processed successfully', 'success');
      onSuccess();
      onClose();
    } catch (err: any) {
      showToast((err as any).userMessage || 'Unable to process return. Please try again later.', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-3xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white z-10 flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-brand-100 rounded-xl flex items-center justify-center">
              <Shield size={20} className="text-brand-500" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-[#1e293b]">Vehicle Return Inspection</h3>
              <p className="text-xs text-slate-400">Process deposit return after inspection</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="p-6 space-y-6">
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
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 resize-none" />
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
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 ring-brand-100 resize-none" />
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

          {/* Actions */}
          <div className="flex gap-3">
            <button onClick={onClose}
              className="flex-1 py-3 bg-slate-100 text-slate-700 rounded-xl font-semibold text-sm hover:bg-slate-200 transition-all">
              Cancel
            </button>
            <button onClick={handleSubmit} disabled={isSubmitting || totalDeductions > depositAmount}
              className="flex-1 py-3 bg-brand-500 text-white rounded-xl font-semibold text-sm hover:bg-brand-600 transition-all disabled:opacity-50 flex items-center justify-center gap-2">
              {isSubmitting ? (
                <span className="animate-spin w-4 h-4 border-2 border-white border-t-transparent rounded-full" />
              ) : (
                <CheckCircle2 size={18} />
              )}
              Confirm Return
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
