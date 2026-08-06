import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '../../../i18n';
import SendEmailButton from '../SendEmailButton';
import type { ReportRow, SendEmailResult } from '../../../hooks/useReports';

function baseReport(overrides: Partial<ReportRow> = {}): ReportRow {
  return {
    id: 1,
    reportType: 'MONTHLY',
    periodStart: '2026-07-01',
    periodEnd: '2026-08-01',
    status: 'GENERATED',
    emailStatus: 'NOT_SENT',
    generatedAt: '2026-08-01T00:00:00Z',
    emailSentAt: null,
    language: 'en',
    ...overrides,
  };
}

describe('SendEmailButton', () => {
  it('a report still generating is disabled and shows the real reason, not a vague message', () => {
    const report = baseReport({ status: 'GENERATING' });
    render(<SendEmailButton report={report} onSend={vi.fn()} disabled />);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    expect(screen.getByRole('tooltip')).toHaveTextContent(/still being generated/i);
  });

  it('a report whose generation failed is disabled and tells the user to regenerate, not "not ready yet"', () => {
    const report = baseReport({ status: 'FAILED' });
    render(<SendEmailButton report={report} onSend={vi.fn()} disabled />);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    const tooltipText = screen.getByRole('tooltip').textContent || '';
    expect(tooltipText).toMatch(/regenerate/i);
    expect(tooltipText).not.toMatch(/not ready yet/i);
  });

  it('a GENERATED report is enabled (ready state)', () => {
    const report = baseReport({ status: 'GENERATED' });
    render(<SendEmailButton report={report} onSend={vi.fn()} disabled={false} />);

    expect(screen.getByRole('button')).not.toBeDisabled();
    expect(screen.getByRole('tooltip')).toHaveTextContent(/send/i);
  });

  it('clicking a ready button calls onSend and shows success', async () => {
    const report = baseReport({ status: 'GENERATED' });
    const onSend = vi.fn<(id: number) => Promise<SendEmailResult>>().mockResolvedValue({
      success: true,
      data: { reportId: 1, emailStatus: 'SENT', emailSentAt: '2026-08-06T10:00:00Z', recipient: 'owner@example.com' },
    });

    render(<SendEmailButton report={report} onSend={onSend} disabled={false} />);
    fireEvent.click(screen.getByRole('button'));

    await waitFor(() => expect(onSend).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.getByRole('tooltip').textContent).toMatch(/sent/i));
  });

  it('a failed send re-enables the button for retry instead of staying stuck', async () => {
    const report = baseReport({ status: 'GENERATED' });
    const onSend = vi.fn<(id: number) => Promise<SendEmailResult>>().mockResolvedValue({
      success: false,
      errorCode: 'REPORT_EMAIL_PROVIDER_REJECTED',
    });

    render(<SendEmailButton report={report} onSend={onSend} disabled={false} />);
    fireEvent.click(screen.getByRole('button'));

    await waitFor(() => expect(onSend).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByRole('button')).not.toBeDisabled());
  });

  it('never renders the literal "not ready yet" text for a GENERATING or FAILED report', () => {
    for (const status of ['GENERATING', 'FAILED'] as const) {
      const { unmount } = render(
        <SendEmailButton report={baseReport({ status })} onSend={vi.fn()} disabled />
      );
      expect(screen.queryByText(/not ready yet/i)).not.toBeInTheDocument();
      unmount();
    }
  });
});
