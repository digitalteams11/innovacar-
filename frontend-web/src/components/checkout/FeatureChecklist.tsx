import type { LucideIcon } from 'lucide-react';
import { Check } from 'lucide-react';

export interface ChecklistFeature {
  icon: LucideIcon;
  label: string;
}

/**
 * Reusable feature checklist for pricing/checkout surfaces — an icon chip
 * plus a checkmark, not just a bullet, so each row reads as "included"
 * rather than a plain description list.
 */
export default function FeatureChecklist({ features }: { features: ChecklistFeature[] }) {
  return (
    <ul className="grid grid-cols-1 gap-x-4 gap-y-2.5 sm:grid-cols-2">
      {features.map(({ icon: Icon, label }) => (
        <li
          key={label}
          className="group flex items-center gap-3 rounded-xl px-1.5 py-1 transition-colors hover:bg-brand-50/60"
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-500 transition-transform duration-200 group-hover:scale-105">
            <Icon size={16} strokeWidth={2.25} />
          </span>
          <span className="text-sm font-medium text-[#1e293b]">{label}</span>
          <span className="ml-auto flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-success-50 text-success-600">
            <Check size={12} strokeWidth={3} />
          </span>
        </li>
      ))}
    </ul>
  );
}
