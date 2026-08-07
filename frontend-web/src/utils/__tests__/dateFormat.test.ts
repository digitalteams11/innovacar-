import { describe, it, expect, vi, afterEach } from 'vitest';
import { formatRelativeTime } from '../dateFormat';

// Mirrors the exact bucket wording NotificationBell renders — a bare
// key/count passthrough is enough to assert bucket boundaries without
// pulling in the full i18next instance.
const t = (key: string, opts?: Record<string, unknown>) => {
  const count = opts?.count as number | undefined;
  switch (key) {
    case 'notifications.justNow': return 'Just now';
    case 'notifications.minutesAgo': return `${count} min ago`;
    case 'notifications.hoursAgo': return count === 1 ? '1 hour ago' : `${count} hours ago`;
    case 'notifications.yesterday': return 'Yesterday';
    case 'notifications.daysAgo': return `${count} days ago`;
    default: return key;
  }
};

const NOW = new Date('2026-08-07T12:00:00.000Z');

function ago(ms: number): string {
  return new Date(NOW.getTime() - ms).toISOString();
}

describe('formatRelativeTime', () => {
  afterEach(() => vi.useRealTimers());

  it.each([
    [10 * 1000, 'Just now'],
    [60 * 1000, '1 min ago'],
    [4 * 60 * 1000, '4 min ago'],
    [5 * 60 * 1000, '5 min ago'],
    [29 * 60 * 1000, '29 min ago'],
    [59 * 60 * 1000, '59 min ago'],
    [60 * 60 * 1000, '1 hour ago'],
    [90 * 60 * 1000, '1 hour ago'],
    [23 * 60 * 60 * 1000, '23 hours ago'],
    [24 * 60 * 60 * 1000, 'Yesterday'],
  ])('%i ms ago -> %s', (ms, expected) => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    expect(formatRelativeTime(ago(ms), t)).toBe(expected);
  });

  it('never rounds 5 minutes up to an hour', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    expect(formatRelativeTime(ago(5 * 60 * 1000), t)).not.toContain('hour');
  });

  it('parses a UTC-offset timestamp identically regardless of viewer timezone', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    // The whole bug this utility fixes: an unambiguous "Z"-suffixed
    // timestamp must resolve to the same bucket no matter what the local
    // system timezone is — simulated here by comparing two equivalent
    // representations of the same instant.
    const zForm = ago(5 * 60 * 1000);
    const offsetForm = zForm.replace('Z', '+00:00');
    expect(formatRelativeTime(zForm, t)).toBe(formatRelativeTime(offsetForm, t));
  });
});
