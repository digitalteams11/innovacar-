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

/** Relative time, e.g. "in 3 days" / "il y a 2 heures" / "منذ 5 دقائق". */
export function formatRelativeTime(date: DateInput, lang?: string): string {
  const d = toDate(date);
  if (!d) return '';
  const diffSec = Math.round((d.getTime() - Date.now()) / 1000);
  const rtf = new Intl.RelativeTimeFormat(resolveLocale(lang), { numeric: 'auto' });
  const divisions: Array<[number, Intl.RelativeTimeFormatUnit]> = [
    [60, 'second'], [60, 'minute'], [24, 'hour'], [7, 'day'], [4.34524, 'week'], [12, 'month'], [Number.POSITIVE_INFINITY, 'year'],
  ];
  let duration = diffSec;
  for (const [amount, unit] of divisions) {
    if (Math.abs(duration) < amount) return rtf.format(Math.round(duration), unit);
    duration /= amount;
  }
  return rtf.format(Math.round(duration), 'year');
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
