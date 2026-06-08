import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Clock, AlertTriangle, ArrowRight, X } from 'lucide-react';

export default function TrialBanner() {
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState<any>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const { data } = await api.get('/subscriptions/status');
        setSubscription(data);
      } catch (err) {
        // silent fail
      }
    };
    fetchStatus();
  }, []);

  if (!subscription || dismissed) return null;

  const { inTrial, daysRemaining, subscriptionActive, planName } = subscription;

  // Don't show banner for active paid subscriptions
  if (!inTrial && subscriptionActive && planName && planName !== 'Trial') return null;

  const isExpired = daysRemaining <= 0;
  const isUrgent = daysRemaining <= 7 && daysRemaining > 0;

  if (isExpired) {
    return (
      <div className="bg-rose-50 border-b border-rose-200 px-4 py-3">
        <div className="flex items-center justify-between max-w-7xl mx-auto">
          <div className="flex items-center gap-3">
            <AlertTriangle size={18} className="text-rose-500 shrink-0" />
            <p className="text-sm text-rose-700 font-medium">
              Your trial has expired. Upgrade now to keep your data and continue using all features.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/subscription')}
              className="text-sm font-semibold text-rose-700 hover:text-rose-800 underline underline-offset-2"
            >
              Upgrade
            </button>
            <button onClick={() => setDismissed(true)} className="text-rose-400 hover:text-rose-600">
              <X size={16} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`border-b px-4 py-3 ${isUrgent ? 'bg-amber-50 border-amber-200' : 'bg-blue-50 border-blue-200'}`}>
      <div className="flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <Clock size={18} className={`shrink-0 ${isUrgent ? 'text-amber-500' : 'text-blue-500'}`} />
          <p className={`text-sm font-medium ${isUrgent ? 'text-amber-700' : 'text-blue-700'}`}>
            {inTrial
              ? `Trial ends in ${daysRemaining} day${daysRemaining !== 1 ? 's' : ''}. Upgrade anytime to unlock your full potential.`
              : `Your ${planName || 'subscription'} expires in ${daysRemaining} day${daysRemaining !== 1 ? 's' : ''}.`}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/subscription')}
            className={`inline-flex items-center gap-1.5 text-sm font-semibold transition-colors ${
              isUrgent
                ? 'text-amber-700 hover:text-amber-800 underline underline-offset-2'
                : 'text-blue-700 hover:text-blue-800 underline underline-offset-2'
            }`}
          >
            View Plans <ArrowRight size={14} />
          </button>
          <button
            onClick={() => setDismissed(true)}
            className={`${isUrgent ? 'text-amber-400 hover:text-amber-600' : 'text-blue-400 hover:text-blue-600'}`}
          >
            <X size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
