import { Car } from 'lucide-react';

// ── Tier types ─────────────────────────────────────────────────────────────────

export type PlanTier =
  | 'TRIAL'
  | 'FREE'
  | 'BASIC'
  | 'STANDARD'
  | 'PREMIUM'
  | 'SUSPENDED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'CANCELLING';

interface BadgeConfig {
  label: string;
  tier: PlanTier;
  wrapCls: string;
  textCls: string;
  tooltip: string;
}

// All Tailwind classes are static strings so the build scanner sees them fully.
const BADGE_CONFIGS: Record<PlanTier, BadgeConfig> = {
  TRIAL: {
    label: 'Trial',
    tier: 'TRIAL',
    wrapCls: 'bg-violet-500/10 border-violet-400/30',
    textCls: 'text-violet-600 dark:text-violet-400',
    tooltip: 'Current plan: Trial',
  },
  FREE: {
    label: 'Free',
    tier: 'FREE',
    wrapCls: 'bg-slate-400/10 border-slate-400/25',
    textCls: 'text-slate-500 dark:text-slate-400',
    tooltip: 'Current plan: Free',
  },
  BASIC: {
    label: 'Basic',
    tier: 'BASIC',
    wrapCls: 'bg-blue-500/10 border-blue-400/30',
    textCls: 'text-blue-600 dark:text-blue-400',
    tooltip: 'Current plan: Basic',
  },
  STANDARD: {
    label: 'Standard',
    tier: 'STANDARD',
    wrapCls: 'bg-teal-500/10 border-teal-400/30',
    textCls: 'text-teal-600 dark:text-teal-400',
    tooltip: 'Current plan: Standard',
  },
  PREMIUM: {
    label: 'Premium',
    tier: 'PREMIUM',
    wrapCls: 'bg-amber-500/10 border-amber-400/30',
    textCls: 'text-amber-600 dark:text-amber-400',
    tooltip: 'Current plan: Premium',
  },
  SUSPENDED: {
    label: 'Suspended',
    tier: 'SUSPENDED',
    wrapCls: 'bg-rose-500/10 border-rose-400/30',
    textCls: 'text-rose-600 dark:text-rose-400',
    tooltip: 'Account suspended — contact support',
  },
  EXPIRED: {
    label: 'Expired',
    tier: 'EXPIRED',
    wrapCls: 'bg-red-500/10 border-red-400/30',
    textCls: 'text-red-600 dark:text-red-400',
    tooltip: 'Subscription expired — renew to restore access',
  },
  CANCELLED: {
    label: 'Cancelled',
    tier: 'CANCELLED',
    wrapCls: 'bg-slate-400/10 border-slate-300/25',
    textCls: 'text-slate-500 dark:text-slate-400',
    tooltip: 'Subscription cancelled',
  },
  CANCELLING: {
    label: 'Cancelling',
    tier: 'CANCELLING',
    wrapCls: 'bg-orange-500/10 border-orange-400/30',
    textCls: 'text-orange-600 dark:text-orange-400',
    tooltip: 'Cancellation scheduled — access continues until period end',
  },
};

/**
 * Resolves the display tier from a planCode + status combination.
 * Status overrides always win: SUSPENDED > EXPIRED > CANCELLED > CANCELLING > plan tier.
 */
export function getPlanBadgeConfig(planCode?: string, status?: string): BadgeConfig {
  const plan = (planCode || '').toUpperCase().trim();
  const st   = (status   || '').toUpperCase().trim();

  // Hard status overrides — always shown regardless of plan
  if (st === 'SUSPENDED')        return BADGE_CONFIGS.SUSPENDED;
  if (st === 'EXPIRED')          return BADGE_CONFIGS.EXPIRED;
  if (st === 'CANCELLED')        return BADGE_CONFIGS.CANCELLED;
  if (st === 'CANCEL_SCHEDULED') return BADGE_CONFIGS.CANCELLING;

  // Trial — plan or status driven
  if (plan === 'TRIAL' || st === 'TRIAL') return BADGE_CONFIGS.TRIAL;

  // Paid tiers (most generous match wins)
  if (plan === 'PREMIUM' || plan === 'ENTERPRISE' || plan.includes('PREMIUM') || plan.includes('ENTERPRISE')) {
    return BADGE_CONFIGS.PREMIUM;
  }
  if (
    plan === 'STANDARD' || plan === 'PRO' || plan === 'PROFESSIONAL' || plan === 'GROWTH' ||
    plan.includes('STANDARD') || plan.includes('PROFESSIONAL')
  ) {
    return BADGE_CONFIGS.STANDARD;
  }
  if (plan === 'BASIC' || plan === 'STARTER' || plan.includes('BASIC') || plan.includes('STARTER')) {
    return BADGE_CONFIGS.BASIC;
  }

  return BADGE_CONFIGS.FREE;
}

// ── Component ──────────────────────────────────────────────────────────────────

interface SubscriptionBadgeProps {
  planCode?: string;
  status?: string;
  /** 'sm' for navbar, 'md' for dropdown identity block */
  size?: 'sm' | 'md';
  /** Whether to render the plan label text next to the car icon */
  showLabel?: boolean;
  /** Show tooltip on hover (default true) */
  showTooltip?: boolean;
  className?: string;
}

export default function SubscriptionBadge({
  planCode,
  status,
  size = 'sm',
  showLabel = true,
  showTooltip = true,
  className = '',
}: SubscriptionBadgeProps) {
  const config  = getPlanBadgeConfig(planCode, status);
  const isMd    = size === 'md';
  const iconSz  = isMd ? 12 : 10;
  const sizeCls = isMd
    ? 'px-2.5 py-1 text-[11px] gap-1.5'
    : 'px-2 py-0.5 text-[10px] gap-1';

  return (
    <span className={`group relative inline-flex items-center ${className}`}>
      {/* Pill */}
      <span
        className={`inline-flex items-center rounded-full border font-semibold transition-colors ${sizeCls} ${config.wrapCls} ${config.textCls}`}
        aria-label={config.tooltip}
      >
        <Car size={iconSz} aria-hidden="true" className="shrink-0" />
        {showLabel && <span>{config.label}</span>}
      </span>

      {/* Tooltip */}
      {showTooltip && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 z-50 opacity-0 scale-95 group-hover:opacity-100 group-hover:scale-100 transition-all duration-150 origin-bottom whitespace-nowrap rounded-lg bg-[var(--bg-card)] border border-[var(--border-subtle)] shadow-lg px-2.5 py-1.5 text-[11px] font-medium text-[var(--text-primary)]"
        >
          {config.tooltip}
        </span>
      )}
    </span>
  );
}
