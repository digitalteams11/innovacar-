import { X, Sparkles, ArrowRight, Zap } from 'lucide-react';

interface UpgradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  featureName: string;
  featureDescription: string;
  requiredPlan: string;
  currentPlan: string;
  onUpgrade: () => void;
}

export default function UpgradeModal({
  isOpen,
  onClose,
  featureName,
  featureDescription,
  requiredPlan,
  currentPlan,
  onUpgrade,
}: UpgradeModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-elevated w-full max-w-md overflow-hidden animate-fade">
        {/* Header gradient */}
        <div className="bg-[#0a0f2c] px-6 py-8 text-center relative overflow-hidden">
          <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/cubes.png')] opacity-5" />
          <div className="relative z-10">
            <div className="w-14 h-14 rounded-2xl bg-accent-400/20 flex items-center justify-center mx-auto mb-4">
              <Sparkles size={26} className="text-accent-400" />
            </div>
            <h3 className="text-xl font-bold text-white">{featureName}</h3>
            <p className="text-slate-400 text-sm mt-1">{featureDescription}</p>
          </div>
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-1.5 hover:bg-white/10 rounded-lg transition-colors"
          >
            <X size={18} className="text-slate-400" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-xl">
            <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center shrink-0">
              <Zap size={18} className="text-amber-500" />
            </div>
            <div>
              <p className="text-sm font-medium text-[#1e293b]">
                Available in <span className="text-brand-600 font-bold">{requiredPlan}</span>
              </p>
              <p className="text-xs text-slate-500">
                Your current plan: {currentPlan || 'Trial'}
              </p>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">What you get:</p>
            <ul className="space-y-2">
              {[
                'Unlimited access to ' + featureName,
                'Priority support included',
                'Advanced analytics & reports',
                'Team collaboration tools',
              ].map((item, i) => (
                <li key={i} className="flex items-center gap-2 text-sm text-slate-600">
                  <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" />
                  {item}
                </li>
              ))}
            </ul>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              onClick={onUpgrade}
              className="flex-1 bg-[#0a0f2c] hover:bg-[#0a0f2c]/90 text-white py-3 rounded-xl text-sm font-semibold transition-colors flex items-center justify-center gap-2"
            >
              Upgrade Now <ArrowRight size={16} />
            </button>
            <button
              onClick={onClose}
              className="flex-1 bg-slate-100 hover:bg-slate-200 text-[#1e293b] py-3 rounded-xl text-sm font-semibold transition-colors"
            >
              Maybe Later
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
