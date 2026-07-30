import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '../../../i18n';
import GenerateReportButton from '../GenerateReportButton';

describe('GenerateReportButton', () => {
  it('idle: clicking calls onGenerate exactly once even on a rapid double click (no duplicate POST)', async () => {
    const user = userEvent.setup();
    let resolveGenerate: (value: any) => void = () => {};
    const onGenerate = vi.fn(() => new Promise((resolve) => { resolveGenerate = resolve; }));

    render(
      <GenerateReportButton
        reportType="MONTHLY"
        periodLabel="June 2026"
        onGenerate={onGenerate}
        onViewExisting={vi.fn()}
      />,
    );

    const button = screen.getByRole('button', { name: /generate monthly/i });
    await user.click(button);
    await user.click(button); // second click while the first request is still in flight

    expect(onGenerate).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();

    resolveGenerate({ success: true });
    await waitFor(() => expect(button).not.toBeDisabled());
  });

  it('already-exists: shows a neutral view state and never calls onGenerate', async () => {
    const user = userEvent.setup();
    const onGenerate = vi.fn();
    const onViewExisting = vi.fn();

    render(
      <GenerateReportButton
        reportType="MONTHLY"
        periodLabel="June 2026"
        existingReportId={123}
        onGenerate={onGenerate}
        onViewExisting={onViewExisting}
      />,
    );

    const button = screen.getByRole('button', { name: /view june 2026/i });
    await user.click(button);

    expect(onGenerate).not.toHaveBeenCalled();
    expect(onViewExisting).toHaveBeenCalledWith(123);
  });

  it('402 REPORTING_FEATURE_NOT_INCLUDED shows a locked/upgrade state, not a generic error icon', async () => {
    const user = userEvent.setup();
    const onGenerate = vi.fn().mockResolvedValue({
      success: false,
      errorCode: 'REPORTING_FEATURE_NOT_INCLUDED',
      message: 'Upgrade required.',
    });

    render(
      <GenerateReportButton
        reportType="YEARLY"
        periodLabel="2025"
        onGenerate={onGenerate}
        onViewExisting={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: /generate yearly/i }));

    await waitFor(() => expect(screen.getByRole('tooltip')).toHaveTextContent('Upgrade required.'));
  });

  it('409 REPORT_ALREADY_EXISTS from a race does not enter the error state', async () => {
    const user = userEvent.setup();
    const onGenerate = vi.fn().mockResolvedValue({ success: false, errorCode: 'REPORT_ALREADY_EXISTS' });

    render(
      <GenerateReportButton
        reportType="MONTHLY"
        periodLabel="June 2026"
        onGenerate={onGenerate}
        onViewExisting={vi.fn()}
      />,
    );

    const button = screen.getByRole('button', { name: /generate monthly/i });
    await user.click(button);

    await waitFor(() => expect(button).not.toBeDisabled());
    expect(button).not.toHaveClass('ring-red-400/40');
  });

  it('generic failure shows the precise backend message on hover, not a global toast', async () => {
    const user = userEvent.setup();
    const onGenerate = vi.fn().mockResolvedValue({
      success: false,
      errorCode: 'REPORT_GENERATION_FAILED',
      message: 'The report-generation service is temporarily unavailable.',
    });

    render(
      <GenerateReportButton
        reportType="MONTHLY"
        periodLabel="June 2026"
        onGenerate={onGenerate}
        onViewExisting={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: /generate monthly/i }));

    await waitFor(() => expect(screen.getByRole('tooltip'))
      .toHaveTextContent('The report-generation service is temporarily unavailable.'));
  });
});
