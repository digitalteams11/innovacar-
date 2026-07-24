import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { ToastProvider, useToast } from '../ToastContext';

// NotificationSoundContext (a dependency of ToastProvider) itself depends on
// AuthContext for the user's sound preference — irrelevant to dedup/cap
// behavior under test here, so stub it out rather than wiring a full auth tree.
vi.mock('../NotificationSoundContext', () => ({
  useNotificationSound: () => ({ playSound: () => {} }),
}));

function Harness({ fire }: { fire: (toast: ReturnType<typeof useToast>['toast']) => void }) {
  const { toast } = useToast();
  useEffect(() => {
    fire(toast);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return null;
}

function renderToasts(fire: (toast: ReturnType<typeof useToast>['toast']) => void) {
  return render(
    <ToastProvider>
      <Harness fire={fire} />
    </ToastProvider>,
  );
}

describe('ToastProvider — deduplication and visible cap', () => {
  it('does not show two toasts with the identical message and type at once', () => {
    renderToasts((toast) => {
      toast.success('Saved');
      toast.success('Saved');
    });
    expect(screen.getAllByText('Saved')).toHaveLength(1);
  });

  it('allows the same message with a different type (not treated as a duplicate)', () => {
    renderToasts((toast) => {
      toast.success('Saved');
      toast.error('Saved');
    });
    expect(screen.getAllByText('Saved')).toHaveLength(2);
  });

  it('never shows more than 3 toasts at once, keeping the newest', () => {
    renderToasts((toast) => {
      toast.info('First');
      toast.info('Second');
      toast.info('Third');
      toast.info('Fourth');
    });
    expect(screen.queryByText('First')).not.toBeInTheDocument();
    expect(screen.getByText('Second')).toBeInTheDocument();
    expect(screen.getByText('Third')).toBeInTheDocument();
    expect(screen.getByText('Fourth')).toBeInTheDocument();
  });

  it('sanitizes leaked technical error details instead of showing them raw', () => {
    renderToasts((toast) => {
      toast.error('NullPointerException at com.carrental.service.Foo');
    });
    expect(screen.queryByText(/nullpointerexception/i)).not.toBeInTheDocument();
    expect(screen.getByText(/unable to complete this action/i)).toBeInTheDocument();
  });
});
