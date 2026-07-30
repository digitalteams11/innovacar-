import type { ReportType } from '../types/reports';

export interface TargetPeriod {
  year: number;
  /** 1-12, MONTHLY only. */
  month?: number;
}

/**
 * The previous complete calendar month, e.g. run on 2026-07-30 -> { year: 2026, month: 6 }.
 * `Date.getMonth()` is zero-based (0=Jan) — for the *current* month that value
 * is numerically identical to the *previous* month's 1-based number (e.g. in
 * July, getMonth()===6, and June is month 6 in 1-based form), so no +/-1 slip
 * is needed except at the January rollover, handled explicitly below.
 */
export function previousClosedMonth(reference: Date = new Date()): Required<TargetPeriod> {
  const zeroBasedCurrentMonth = reference.getMonth();
  if (zeroBasedCurrentMonth === 0) {
    return { year: reference.getFullYear() - 1, month: 12 };
  }
  return { year: reference.getFullYear(), month: zeroBasedCurrentMonth };
}

/** The previous complete calendar year, e.g. run on 2026-07-30 -> { year: 2025 }. */
export function previousClosedYear(reference: Date = new Date()): TargetPeriod {
  return { year: reference.getFullYear() - 1 };
}

export function targetPeriodFor(type: ReportType, reference: Date = new Date()): TargetPeriod {
  return type === 'MONTHLY' ? previousClosedMonth(reference) : previousClosedYear(reference);
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export function periodLabelFor(type: ReportType, period: TargetPeriod): string {
  if (type === 'YEARLY') return String(period.year);
  return `${MONTH_NAMES[(period.month ?? 1) - 1]} ${period.year}`;
}

/**
 * Reads the literal calendar year/month out of the backend's `periodStart`
 * string without going through `Date` — the backend serializes a plain
 * `LocalDateTime` with no timezone marker (e.g. "2026-06-01T00:00:00"), and
 * `new Date(...)` on a zone-less date-time string is parsed as *local*
 * browser time, not UTC, which would silently shift the year/month near
 * month/year boundaries depending on the viewer's timezone.
 */
export function parsePeriodYearMonth(periodStartIso: string): { year: number; month: number } {
  const [datePart] = periodStartIso.split('T');
  const [year, month] = datePart.split('-').map(Number);
  return { year, month };
}

/** Does this report cover the given tenant-facing target period? Used to detect an already-generated period before firing a duplicate request. */
export function reportCoversPeriod(
  report: { reportType: ReportType; periodStart: string },
  type: ReportType,
  period: TargetPeriod,
): boolean {
  if (report.reportType !== type) return false;
  const { year, month } = parsePeriodYearMonth(report.periodStart);
  if (type === 'YEARLY') return year === period.year;
  return year === period.year && month === period.month;
}
