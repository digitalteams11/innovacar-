import type { LucideIcon } from 'lucide-react';
import { ShieldCheck, RotateCcw, BadgeCheck } from 'lucide-react';

interface TrustBadge {
  icon: LucideIcon;
  label: string;
}

const DEFAULT_BADGES: TrustBadge[] = [
  { icon: ShieldCheck, label: 'Secure payment' },
  { icon: RotateCcw, label: 'Cancel anytime' },
  { icon: BadgeCheck, label: 'No charge today' },
];

/** Small inline trust-signal row used under pricing/checkout CTAs. */
export default function TrustBadges({ badges = DEFAULT_BADGES }: { badges?: TrustBadge[] }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {badges.map(({ icon: Icon, label }) => (
        <div
          key={label}
          className="flex items-center gap-1.5 rounded-full border border-slate-100 bg-slate-50/80 px-3 py-1.5 text-xs font-semibold text-slate-600"
        >
          <Icon size={14} className="text-success-500" />
          {label}
        </div>
      ))}
    </div>
  );
}
