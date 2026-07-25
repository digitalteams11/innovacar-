import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { BackendHealthGate } from '../App';

// checkHealth is the only thing BackendHealthGate depends on for reachability —
// stub it directly so tests control exactly how many consecutive checks fail.
const checkHealthMock = vi.fn();
vi.mock('../lib/api', () => ({
  checkHealth: (...args: unknown[]) => checkHealthMock(...args),
  API_BASE_URL: 'http://localhost:8082/api',
  API_ORIGIN: 'http://localhost:8082',
}));

async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('BackendHealthGate', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    checkHealthMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('one failed core health check does not show the full unavailable screen', async () => {
    checkHealthMock.mockResolvedValue(false);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();

    expect(screen.getByText('App content')).toBeInTheDocument();
    expect(screen.queryByText(/service temporarily unavailable/i)).not.toBeInTheDocument();
  });

  it('a second consecutive failure shows a non-blocking reconnecting indicator, not the full screen', async () => {
    checkHealthMock.mockResolvedValue(false);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();

    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    await flush();

    expect(screen.getByText(/reconnecting/i)).toBeInTheDocument();
    expect(screen.queryByText(/service temporarily unavailable/i)).not.toBeInTheDocument();
    expect(screen.getByText('App content')).toBeInTheDocument();
  });

  it('shows the full unavailable overlay only after 3 consecutive core health failures', async () => {
    checkHealthMock.mockResolvedValue(false);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); }); // 2nd failure
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); }); // 3rd failure
    await flush();

    expect(screen.getByText(/service temporarily unavailable/i)).toBeInTheDocument();
    // Already-mounted app content must stay mounted underneath the overlay.
    expect(screen.getByText('App content')).toBeInTheDocument();
  });

  it('a successful retry after failures restores normal state and clears the overlay', async () => {
    checkHealthMock.mockResolvedValueOnce(false)
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(false)
      .mockResolvedValue(true);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    await flush();
    expect(screen.getByText(/service temporarily unavailable/i)).toBeInTheDocument();

    await act(async () => { await vi.advanceTimersByTimeAsync(8000); }); // next backoff tick succeeds
    await flush();

    expect(screen.queryByText(/service temporarily unavailable/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/reconnecting/i)).not.toBeInTheDocument();
    expect(screen.getByText('App content')).toBeInTheDocument();
  });

  it('the Retry now button triggers an immediate recheck instead of waiting for backoff', async () => {
    checkHealthMock.mockResolvedValue(false);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    await flush();
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    await flush();
    expect(screen.getByText(/service temporarily unavailable/i)).toBeInTheDocument();

    const callsBefore = checkHealthMock.mock.calls.length;
    checkHealthMock.mockResolvedValue(true);
    await act(async () => {
      screen.getByRole('button', { name: /retry now/i }).click();
      await Promise.resolve();
    });
    await flush();

    expect(checkHealthMock.mock.calls.length).toBeGreaterThan(callsBefore);
    expect(screen.queryByText(/service temporarily unavailable/i)).not.toBeInTheDocument();
  });

  it('shows an offline banner while the browser is offline and rechecks immediately when back online', async () => {
    checkHealthMock.mockResolvedValue(true);
    render(<BackendHealthGate><div>App content</div></BackendHealthGate>);
    await flush();

    await act(async () => {
      window.dispatchEvent(new Event('offline'));
    });
    await flush();
    expect(screen.getByText(/you're offline/i)).toBeInTheDocument();

    const callsBefore = checkHealthMock.mock.calls.length;
    await act(async () => {
      window.dispatchEvent(new Event('online'));
      await Promise.resolve();
    });
    await flush();

    expect(screen.queryByText(/you're offline/i)).not.toBeInTheDocument();
    expect(checkHealthMock.mock.calls.length).toBeGreaterThan(callsBefore);
  });
});
