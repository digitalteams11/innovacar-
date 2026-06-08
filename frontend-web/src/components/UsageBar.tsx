import React from 'react';

interface UsageBarProps {
  label: string;
  current: number;
  max: number;
  icon?: React.ReactNode;
  showPercentage?: boolean;
  warningThreshold?: number;
}

export default function UsageBar({
  label,
  current,
  max,
  icon,
  showPercentage = true,
  warningThreshold = 85,
}: UsageBarProps) {
  const percentage = max > 0 ? Math.min(100, Math.round((current / max) * 100)) : 0;
  const isWarning = percentage >= warningThreshold;
  const isFull = percentage >= 100;

  const barColor = isFull
    ? 'bg-rose-500'
    : isWarning
    ? 'bg-amber-500'
    : 'bg-brand-500';

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          {icon && <span className="text-slate-400">{icon}</span>}
          <span className="text-sm font-medium text-[#1e293b]">{label}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className={`text-sm font-semibold ${isFull ? 'text-rose-600' : isWarning ? 'text-amber-600' : 'text-[#1e293b]'}`}>
            {current} / {max}
          </span>
          {showPercentage && (
            <span className={`text-xs px-2 py-0.5 rounded-lg font-medium ${
              isFull ? 'bg-rose-50 text-rose-600' : isWarning ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500'
            }`}>
              {percentage}%
            </span>
          )}
        </div>
      </div>
      <div className="h-2.5 bg-slate-100 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${barColor}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
