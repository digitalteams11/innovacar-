import { describe, it, expect } from 'vitest';
import {
  previousClosedMonth, previousClosedYear, targetPeriodFor, periodLabelFor,
  parsePeriodYearMonth, reportCoversPeriod,
} from '../reportPeriods';

describe('previousClosedMonth', () => {
  it('generated on 2026-07-30 targets June 2026 (the previous complete month)', () => {
    expect(previousClosedMonth(new Date(2026, 6, 30))).toEqual({ year: 2026, month: 6 });
  });

  it('month is always 1-12 (1-based), never JavaScript\'s zero-based Date.getMonth()', () => {
    const result = previousClosedMonth(new Date(2026, 6, 30));
    expect(result.month).toBeGreaterThanOrEqual(1);
    expect(result.month).toBeLessThanOrEqual(12);
  });

  it('rolls back across a year boundary: January targets December of the previous year', () => {
    expect(previousClosedMonth(new Date(2026, 0, 15))).toEqual({ year: 2025, month: 12 });
  });
});

describe('previousClosedYear', () => {
  it('generated on 2026-07-30 targets 2025 (the previous complete year)', () => {
    expect(previousClosedYear(new Date(2026, 6, 30))).toEqual({ year: 2025 });
  });
});

describe('targetPeriodFor', () => {
  it('MONTHLY delegates to previousClosedMonth', () => {
    expect(targetPeriodFor('MONTHLY', new Date(2026, 6, 30))).toEqual({ year: 2026, month: 6 });
  });

  it('YEARLY delegates to previousClosedYear and never includes a month field', () => {
    const result = targetPeriodFor('YEARLY', new Date(2026, 6, 30));
    expect(result).toEqual({ year: 2025 });
    expect(result).not.toHaveProperty('month');
  });
});

describe('periodLabelFor', () => {
  it('formats a monthly period as "Month Year"', () => {
    expect(periodLabelFor('MONTHLY', { year: 2026, month: 6 })).toBe('June 2026');
  });

  it('formats a yearly period as just the year', () => {
    expect(periodLabelFor('YEARLY', { year: 2025 })).toBe('2025');
  });
});

describe('parsePeriodYearMonth', () => {
  it('reads year/month from a zone-less LocalDateTime string without any Date/timezone interpretation', () => {
    expect(parsePeriodYearMonth('2026-06-01T00:00:00')).toEqual({ year: 2026, month: 6 });
  });
});

describe('reportCoversPeriod', () => {
  it('matches a MONTHLY report against the exact target year/month', () => {
    const report = { reportType: 'MONTHLY' as const, periodStart: '2026-06-01T00:00:00' };
    expect(reportCoversPeriod(report, 'MONTHLY', { year: 2026, month: 6 })).toBe(true);
    expect(reportCoversPeriod(report, 'MONTHLY', { year: 2026, month: 7 })).toBe(false);
    expect(reportCoversPeriod(report, 'MONTHLY', { year: 2025, month: 6 })).toBe(false);
  });

  it('matches a YEARLY report against the exact target year only', () => {
    const report = { reportType: 'YEARLY' as const, periodStart: '2025-01-01T00:00:00' };
    expect(reportCoversPeriod(report, 'YEARLY', { year: 2025 })).toBe(true);
    expect(reportCoversPeriod(report, 'YEARLY', { year: 2026 })).toBe(false);
  });

  it('never matches across report types', () => {
    const monthly = { reportType: 'MONTHLY' as const, periodStart: '2026-06-01T00:00:00' };
    expect(reportCoversPeriod(monthly, 'YEARLY', { year: 2026 })).toBe(false);
  });
});
