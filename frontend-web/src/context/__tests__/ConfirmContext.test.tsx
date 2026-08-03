import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '../../i18n';
import { ConfirmProvider, useConfirm, usePromptText } from '../ConfirmContext';

function ConfirmHarness({ onResult, tone, action }: {
  onResult: (v: boolean) => void;
  tone?: 'default' | 'danger';
  action?: () => Promise<void>;
}) {
  const confirm = useConfirm();
  return (
    <button
      type="button"
      onClick={async () => {
        const result = await confirm({
          title: 'Cancel this contract?',
          description: 'Contract CTR-1 will be marked as cancelled.',
          confirmLabel: 'Cancel contract',
          cancelLabel: 'Keep contract',
          tone,
          action,
        });
        onResult(result);
      }}
    >
      Trigger
    </button>
  );
}

function PromptHarness({ onResult }: { onResult: (v: string | null) => void }) {
  const promptText = usePromptText();
  return (
    <button
      type="button"
      onClick={async () => {
        const result = await promptText({ title: 'Enter a value', required: true });
        onResult(result);
      }}
    >
      Trigger
    </button>
  );
}

describe('ConfirmContext — useConfirm', () => {
  it('never touches window.confirm/alert/prompt', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockImplementation(() => { throw new Error('native confirm called'); });
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => { throw new Error('native alert called'); });
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={() => {}} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(alertSpy).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
    alertSpy.mockRestore();
  });

  it('opens an Innovacar dialog showing the title and description, not a native one', () => {
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={() => {}} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Cancel this contract?')).toBeInTheDocument();
    expect(screen.getByText('Contract CTR-1 will be marked as cancelled.')).toBeInTheDocument();
  });

  it('resolves false and fires no action when the Keep/Cancel button is clicked', async () => {
    const onResult = vi.fn();
    const action = vi.fn(async () => {});
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={onResult} action={action} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    fireEvent.click(screen.getByText('Keep contract'));
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(false));
    expect(action).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('calls the action exactly once and resolves true on success', async () => {
    const onResult = vi.fn();
    const action = vi.fn(async () => {});
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={onResult} action={action} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    fireEvent.click(screen.getByText('Cancel contract'));
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(true));
    expect(action).toHaveBeenCalledTimes(1);
  });

  it('a double-click on confirm while loading only runs the action once', async () => {
    let resolveAction: () => void = () => {};
    const action = vi.fn(() => new Promise<void>((resolve) => { resolveAction = resolve; }));
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={() => {}} action={action} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    const confirmBtn = screen.getByText('Cancel contract');
    fireEvent.click(confirmBtn);
    fireEvent.click(confirmBtn);
    fireEvent.click(confirmBtn);
    resolveAction();
    await waitFor(() => expect(action).toHaveBeenCalledTimes(1));
  });

  it('keeps the dialog open and shows a classified error when the action rejects', async () => {
    const action = vi.fn(async () => {
      const err = new Error('failed') as Error & { userMessage?: string };
      err.userMessage = 'You do not have permission to perform this action.';
      throw err;
    });
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={() => {}} action={action} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    fireEvent.click(screen.getByText('Cancel contract'));
    expect(await screen.findByText('You do not have permission to perform this action.')).toBeInTheDocument();
    // Dialog stays open on failure — never closes before the backend succeeds.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('closes a safe (non-loading) dialog on Escape and resolves false', async () => {
    const onResult = vi.fn();
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={onResult} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(false));
  });

  it('does not default-focus the destructive confirm button', () => {
    render(
      <ConfirmProvider>
        <ConfirmHarness onResult={() => {}} tone="danger" />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    expect(document.activeElement).toHaveTextContent('Keep contract');
  });

  it('promptText returns null (not empty string) when cancelled', async () => {
    const onResult = vi.fn();
    render(
      <ConfirmProvider>
        <PromptHarness onResult={onResult} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByText('Trigger'));
    fireEvent.click(screen.getByText('Cancel'));
    await waitFor(() => expect(onResult).toHaveBeenCalledWith(null));
  });
});
