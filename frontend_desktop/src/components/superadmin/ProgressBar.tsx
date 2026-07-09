

interface ProgressBarProps {
  current: number;
  max: number;
  label?: string;
  size?: 'sm' | 'md';
  showPercentage?: boolean;
}

export default function ProgressBar({ current, max, label, size = 'md', showPercentage = true }: ProgressBarProps) {
  const percentage = max > 0 ? Math.min(100, Math.round((current / max) * 100)) : 0;
  const barHeight = size === 'sm' ? 'h-1.5' : 'h-2';

  const getColor = () => {
    if (percentage >= 90) return 'bg-rose-500';
    if (percentage >= 75) return 'bg-amber-500';
    return 'bg-emerald-500';
  };

  return (
    <div className="w-full">
      {label && (
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">{label}</span>
          {showPercentage && (
            <span className="text-xs font-medium text-[#1e293b] dark:text-white">
              {current} / {max}
            </span>
          )}
        </div>
      )}
      <div className={`w-full ${barHeight} bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden`}>
        <div
          className={`${barHeight} ${getColor()} rounded-full transition-all duration-500`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
