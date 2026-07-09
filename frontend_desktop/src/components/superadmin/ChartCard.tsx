import React from 'react';
import { ResponsiveContainer } from 'recharts';
import SafeChartContainer from '../shared/SafeChartContainer';

interface ChartCardProps {
  title: string;
  children: React.ReactNode;
  height?: number;
  loading?: boolean;
  action?: React.ReactNode;
}

export default function ChartCard({ title, children, height = 280, loading, action }: ChartCardProps) {
  if (loading) {
    return (
      <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft animate-pulse">
        <div className="h-5 w-32 bg-slate-200 dark:bg-slate-700 rounded mb-6" />
        <div className="h-[280px] bg-slate-100 dark:bg-slate-800 rounded-xl" />
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-[#1a2332]/70 rounded-2xl p-4 sm:p-6 border border-[#e8e6e1]/80 dark:border-white/5 shadow-soft">
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-base font-bold text-[#1e293b] dark:text-white">{title}</h3>
        {action}
      </div>
      <SafeChartContainer style={{ height }} minHeight={height}>
        <ResponsiveContainer width="100%" height="100%">
          {children as any}
        </ResponsiveContainer>
      </SafeChartContainer>
    </div>
  );
}
