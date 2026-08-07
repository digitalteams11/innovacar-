import i18n from '../i18n';

/**
 * Single source of truth for locale-aware date/time formatting. Every
 * component should use these helpers instead of calling
 * `toLocaleDateString(undefined, ...)` or `toLocaleString('default', ...)`
 * directly — `undefined`/`'default'` resolve to the *browser's* locale, not
 * the app's active i18n language, which is why dates kept rendering in
 * English even when Arabic/French was selected.
 */

export const LOCALE_MAP: Record<string, string> = {
  ar: 'ar-MA',
  fr: 'fr-FR',
  en: 'en-US',
};

export function resolveLocale(lang?: string): string {
  const language = lang || i18n.resolvedLanguage || i18n.language || 'en';
  const base = language.split('-')[0];
  return LOCALE_MAP[base] || LOCALE_MAP.en;
}

type DateInput = Date | string | number | null | undefined;

function toDate(date: DateInput): Date | null {
  if (date === null || date === undefined || date === '') return null;
  const d = date instanceof Date ? date : new Date(date);
  return isNaN(d.getTime()) ? null : d;
}

/** Full date, e.g. "17 juillet 2026" / "17 يوليو 2026" / "July 17, 2026". */
export function formatDate(date: DateInput, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  return new Intl.DateTimeFormat(resolveLocale(lang), { year: 'numeric', month: 'long', day: 'numeric' }).format(d);
}

/** Compact date, e.g. "17 juil." / "17 يوليو" / "Jul 17". */
export function formatShortDate(date: DateInput, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  return new Intl.DateTimeFormat(resolveLocale(lang), { month: 'short', day: 'numeric' }).format(d);
}

/** Calendar header label, e.g. "juillet 2026" / "يوليو 2026" / "July 2026". */
export function formatMonthYear(date: DateInput, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  return new Intl.DateTimeFormat(resolveLocale(lang), { month: 'long', year: 'numeric' }).format(d);
}

/** Weekday name, e.g. "Vendredi" / "الجمعة" / "Friday" (or 'short' style). */
export function formatWeekday(date: DateInput, style: 'long' | 'short' = 'long', lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  return new Intl.DateTimeFormat(resolveLocale(lang), { weekday: style }).format(d);
}

/** Date + time, e.g. "17 juil. 2026, 09:00". */
export function formatDateTime(date: DateInput, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  return new Intl.DateTimeFormat(resolveLocale(lang), {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(d);
}

type TranslateFn = (key: string, opts?: Record<string, unknown>) => string;

/**
 * Single source of truth for "time ago" labels (notification bell, notification
 * page, anywhere else a short relative timestamp is shown). `date` must be a
 * server-supplied timestamp that unambiguously carries its UTC offset (an
 * ISO string ending in "Z" or with a numeric offset — see
 * `UtcDateTimeUtil`/`UtcLocalDateTimeSerializer` on the backend). `new
 * Date(...)` then parses it correctly regardless of the viewer's own
 * timezone; passing an offset-less string here would silently reintroduce
 * the "5-minute-old notification shows as 1 hour ago" bug this utility
 * exists to fix.
 *
 * Buckets (deliberately NOT Intl.RelativeTimeFormat, whose wording/rounding
 * doesn't match the product's exact copy, e.g. "in 2 hours" vs "1 hour ago"
 * for a stale clock, or "5 minutes ago" vs the required "5 min ago"):
 *   < 1 min      → "Just now"
 *   1–59 min     → "N min ago"
 *   60 min–23h59 → "N hour(s) ago"
 *   1 day        → "Yesterday"
 *   2–6 days     → "N days ago"
 *   ≥ 7 days     → localized date (formatShortDate)
 */
export function formatRelativeTime(date: DateInput, t: TranslateFn, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  // Clamp negative diffs (clock skew / a timestamp that arrives fractionally
  // "in the future" due to request latency) to 0 rather than showing a
  // nonsensical negative minute count.
  const diffMs = Math.max(0, Date.now() - d.getTime());
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return t('notifications.justNow');
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('notifications.hoursAgo', { count: hours });
  const days = Math.floor(hours / 24);
  if (days === 1) return t('notifications.yesterday');
  if (days < 7) return t('notifications.daysAgo', { count: days });
  return formatShortDate(d, lang);
}

/**
 * Locale-correct weekday labels for a full week (index 0 = Sunday), using
 * Intl so weekday names never need to be hand-translated per language.
 */
export function getWeekdayLabels(style: 'long' | 'short' | 'narrow' = 'short', lang?: string): string[] {
  const formatter = new Intl.DateTimeFormat(resolveLocale(lang), { weekday: style });
  const base = new Date(Date.UTC(2023, 0, 1)); // a known Sunday
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(base);
    d.setUTCDate(base.getUTCDate() + i);
    return formatter.format(d);
  });
}

/** Locale-correct month labels (index 0 = January). */
export function getMonthLabels(style: 'long' | 'short' = 'long', lang?: string): string[] {
  const formatter = new Intl.DateTimeFormat(resolveLocale(lang), { month: style });
  return Array.from({ length: 12 }, (_, i) => formatter.format(new Date(Date.UTC(2023, i, 1))));
}
