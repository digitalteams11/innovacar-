import { Sparkles } from 'lucide-react';

interface PriceSummaryCardProps {
  monthlyPrice: number;
  currency?: string;
  billingStartDate: string;
}

/**
 * "Due today vs. due after trial" breakdown — the single most important
 * trust-builder on a trial checkout: it makes explicit, in numbers, that
 * nothing is charged now.
 */
export default function PriceSummaryCard({ monthlyPrice, currency = 'MAD', billingStartDate }: PriceSummaryCardProps) {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-success-100 bg-gradient-to-br from-success-50 to-white p-5 shadow-[0_12px_32px_-16px_rgba(16,185,129,0.35)]">
      <Sparkles size={72} strokeWidth={1.25} className="pointer-events-none absolute -right-4 -top-4 text-success-500/10" />
      <div className="relative flex items-center justify-between">
        <span className="text-sm font-medium text-slate-600">Total due today</span>
        <span className="text-xl font-black tracking-tight text-success-600">{currency} 0.00</span>
      </div>
      <div className="relative my-3 h-px w-full bg-success-100" />
      <div className="relative flex items-center justify-between">
        <span className="text-sm font-medium text-slate-600">Total after trial</span>
        <span className="text-sm font-bold text-[#1e293b]">
          {currency} {monthlyPrice.toFixed(2)}
          <span className="font-medium text-slate-400">/month</span>
        </span>
      </div>
      <p className="relative mt-3 text-xs font-medium leading-relaxed text-slate-500">
        Billing starts on <span className="font-bold text-[#1e293b]">{billingStartDate}</span> — cancel anytime before then and you won't be charged.
      </p>
    </div>
  );
}
