import { TrendingUp, TrendingDown } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export type StatCardTone = 'neutral' | 'blue' | 'emerald' | 'amber' | 'rose' | 'violet';

interface StatCardProps {
  title: string;
  value: string | number;
  change?: number;
  changeType?: 'up' | 'down';
  icon: LucideIcon;
  onClick?: () => void;
  loading?: boolean;
  tone?: StatCardTone;
}

const toneClasses: Record<StatCardTone, string> = {
  neutral: 'bg-[#0a0f2c]/5 dark:bg-white/5 text-[#0a0f2c] dark:text-white/80 group-hover:bg-[#0a0f2c] dark:group-hover:bg-white/10 group-hover:text-white',
  blue: 'bg-blue-50 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400 group-hover:bg-blue-600 group-hover:text-white',
  emerald: 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 group-hover:bg-emerald-600 group-hover:text-white',
  amber: 'bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400 group-hover:bg-amber-600 group-hover:text-white',
  rose: 'bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 group-hover:bg-rose-600 group-hover:text-white',
  violet: 'bg-violet-50 dark:bg-violet-500/10 text-violet-600 dark:text-violet-400 group-hover:bg-violet-600 group-hover:text-white',
};

export default function StatCard({ title, value, change, changeType, icon: Icon, onClick, loading, tone = 'neutral' }: StatCardProps) {
  if (loading) {
    return (
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft animate-pulse">
        <div className="flex items-start justify-between mb-4">
          <div className="w-10 h-10 rounded-xl bg-slate-200 dark:bg-slate-700" />
          <div className="w-16 h-6 rounded-lg bg-slate-200 dark:bg-slate-700" />
        </div>
        <div className="w-24 h-4 rounded bg-slate-200 dark:bg-slate-700 mb-2" />
        <div className="w-16 h-8 rounded bg-slate-200 dark:bg-slate-700" />
      </div>
    );
  }

  return (
    <div
      onClick={onClick}
      className={`bg-white dark:bg-[#1a2332]/70 rounded-2xl p-3 sm:p-5 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft hover:shadow-md hover:-translate-y-0.5 transition-all ${onClick ? 'cursor-pointer' : ''} group`}
    >
      <div className="flex items-start justify-between mb-4">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center transition-colors ${toneClasses[tone]}`}>
          <Icon size={18} strokeWidth={2} />
        </div>
        {change !== undefined && (
          <div className={`flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-lg ${
            changeType === 'up' ? 'text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10' : 'text-rose-700 dark:text-rose-400 bg-rose-50 dark:bg-rose-500/10'
          }`}>
            {changeType === 'up' ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            {change}%
          </div>
        )}
      </div>
      <p className="text-slate-500 dark:text-slate-400 text-xs sm:text-sm font-medium mb-1">{title}</p>
      <p className="text-[#1e293b] dark:text-white text-xl sm:text-2xl font-bold tracking-tight">{value}</p>
    </div>
  );
}
