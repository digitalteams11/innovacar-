import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, ArrowRight, X } from 'lucide-react';
import { useSubscription } from '../hooks/useSubscription';

export default function TrialBanner() {
  const navigate = useNavigate();
  const { status: subscription } = useSubscription();
  const [dismissed, setDismissed] = useState(false);

  if (!subscription || dismissed) return null;

  const showTrialBanner = subscription.planCode === 'TRIAL'
    && subscription.status === 'TRIAL'
    && subscription.isTrial === true;
  if (!showTrialBanner) return null;

  const daysRemaining = subscription.remainingTrialDays;
  return (
    <div className={`border-b px-4 py-3 ${daysRemaining <= 7 ? 'bg-amber-50 border-amber-200' : 'bg-blue-50 border-blue-200'}`}>
      <div className="flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <Clock size={18} className={`shrink-0 ${daysRemaining <= 7 ? 'text-amber-500' : 'text-blue-500'}`} />
          <p className={`text-sm font-medium ${daysRemaining <= 7 ? 'text-amber-700' : 'text-blue-700'}`}>
            Trial ends in {daysRemaining} day{daysRemaining !== 1 ? 's' : ''}. Upgrade anytime to unlock your full potential.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/subscription')}
            className={`inline-flex items-center gap-1.5 text-sm font-semibold transition-colors ${
              daysRemaining <= 7
                ? 'text-amber-700 hover:text-amber-800 underline underline-offset-2'
                : 'text-blue-700 hover:text-blue-800 underline underline-offset-2'
            }`}
          >
            View Plans <ArrowRight size={14} />
          </button>
          <button
            onClick={() => setDismissed(true)}
            className={`${daysRemaining <= 7 ? 'text-amber-400 hover:text-amber-600' : 'text-blue-400 hover:text-blue-600'}`}
          >
            <X size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
