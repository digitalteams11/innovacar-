import type { TFunction } from 'i18next';

/**
 * Badge color for the trial "Active" pill — stays neutral while many days
 * remain, escalates to amber at <=5 days and a stronger warning at <=1 day.
 * Deliberately never red while the trial still has meaningful time left.
 */
export function trialBadgeClass(daysRemaining: number): string {
  if (daysRemaining <= 1) {
    return 'bg-rose-50 text-rose-700 border-rose-200';
  }
  if (daysRemaining <= 5) {
    return 'bg-amber-50 text-amber-700 border-amber-200';
  }
  return 'bg-blue-50 text-blue-700 border-blue-200';
}

/**
 * "Ends today" / "N day(s) remaining · Ends on <date>" line for an active
 * trial. Never returns anything resembling "Renews on" — trials are not a
 * recurring subscription.
 */
export function trialCountdownText(
  daysRemaining: number,
  endsAtIso: string | null | undefined,
  t: TFunction,
  formatDate: (value?: string) => string,
): string {
  const countdown = daysRemaining <= 0
    ? t('subscription.trialCard.endsToday')
    : t('subscription.trialCard.daysRemaining', { count: daysRemaining });
  if (!endsAtIso) return countdown;
  return `${countdown} · ${t('subscription.trialCard.endsOn', { date: formatDate(endsAtIso) })}`;
}
