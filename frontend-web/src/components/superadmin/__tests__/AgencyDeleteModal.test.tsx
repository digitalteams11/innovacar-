import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '../../../i18n';
import AgencyDeleteModal from '../AgencyDeleteModal';
import { superAdminApi } from '../../../api/superAdminApi';

vi.mock('../../../api/superAdminApi', () => ({
  superAdminApi: {
    getAgencyDeletionImpact: vi.fn(),
    deleteAgencyPermanently: vi.fn(),
  },
}));

const AGENCY = { id: 5, name: 'Atlas Rentals', email: 'owner@atlas.example' };

const EMPTY_IMPACT = {
  agencyId: 5, agencyName: 'Atlas Rentals',
  users: 0, vehicles: 0, clients: 0, reservations: 0, contracts: 0, activeContracts: 0,
  payments: 0, invoices: 0, documents: 0, activeSubscription: false, protectedAgency: false,
  canDeleteImmediately: true, blockingReasons: [],
};

const BLOCKED_IMPACT = {
  ...EMPTY_IMPACT,
  users: 3, activeSubscription: true, canDeleteImmediately: false,
  blockingReasons: [
    { code: 'ACTIVE_SUBSCRIPTION', message: 'The agency still has an active subscription.' },
    { code: 'EXISTING_USERS', message: 'The agency still has 3 user account(s).' },
  ],
};

describe('AgencyDeleteModal', () => {
  beforeEach(() => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockReset();
    vi.mocked(superAdminApi.deleteAgencyPermanently).mockReset();
  });

  it('shows the real impact summary, not a browser confirm()', async () => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockResolvedValue({ data: EMPTY_IMPACT } as any);
    render(<AgencyDeleteModal isOpen agency={AGENCY} onClose={vi.fn()} onDeleted={vi.fn()} />);

    await waitFor(() => expect(screen.getByText(/atlas\.example/)).toBeInTheDocument());
    expect(superAdminApi.getAgencyDeletionImpact).toHaveBeenCalledWith(5);
  });

  it('displays blocking reasons and keeps the confirm button disabled', async () => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockResolvedValue({ data: BLOCKED_IMPACT } as any);
    render(<AgencyDeleteModal isOpen agency={AGENCY} onClose={vi.fn()} onDeleted={vi.fn()} />);

    await waitFor(() => expect(screen.getByText(/active subscription/i)).toBeInTheDocument());
    expect(screen.getByText(/3 user account/i)).toBeInTheDocument();
    // No type-to-confirm field is even shown when deletion is blocked.
    expect(screen.queryByRole('button', { name: /delete permanently/i })).toBeDisabled();
  });

  it('typing the wrong agency name keeps the button disabled', async () => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockResolvedValue({ data: EMPTY_IMPACT } as any);
    render(<AgencyDeleteModal isOpen agency={AGENCY} onClose={vi.fn()} onDeleted={vi.fn()} />);

    await waitFor(() => screen.getByRole('textbox'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Wrong Name' } });
    fireEvent.click(screen.getByRole('checkbox'));

    expect(screen.getByRole('button', { name: /delete permanently/i })).toBeDisabled();
  });

  it('typing the exact name and checking the box enables the button; confirming calls the API', async () => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockResolvedValue({ data: EMPTY_IMPACT } as any);
    vi.mocked(superAdminApi.deleteAgencyPermanently).mockResolvedValue({ data: { success: true } } as any);
    const onDeleted = vi.fn();

    render(<AgencyDeleteModal isOpen agency={AGENCY} onClose={vi.fn()} onDeleted={onDeleted} />);

    await waitFor(() => screen.getByRole('textbox'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Atlas Rentals' } });
    fireEvent.click(screen.getByRole('checkbox'));

    const confirmButton = screen.getByRole('button', { name: /delete permanently/i });
    expect(confirmButton).not.toBeDisabled();
    fireEvent.click(confirmButton);

    await waitFor(() => expect(superAdminApi.deleteAgencyPermanently).toHaveBeenCalledWith(5, 'Atlas Rentals'));
    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith(5));
  });

  it('a blocking server response on confirm shows the precise reason, not a generic error', async () => {
    vi.mocked(superAdminApi.getAgencyDeletionImpact).mockResolvedValue({ data: EMPTY_IMPACT } as any);
    vi.mocked(superAdminApi.deleteAgencyPermanently).mockRejectedValue({
      response: { status: 409, data: { message: 'Agency cannot be deleted because related active data exists.' } },
      userMessage: 'Agency cannot be deleted because related active data exists.',
    });

    render(<AgencyDeleteModal isOpen agency={AGENCY} onClose={vi.fn()} onDeleted={vi.fn()} />);

    await waitFor(() => screen.getByRole('textbox'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Atlas Rentals' } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: /delete permanently/i }));

    await waitFor(() => expect(screen.getByText(/related active data exists/i)).toBeInTheDocument());
  });
});
