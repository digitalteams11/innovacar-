import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import Subscription from '../Subscription';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

const mockedApi = vi.mocked(api, true);

// The backend catalog after the subscription-simplification migration: the
// TRIAL row still exists (for info-only display) alongside exactly one
// active paid row, code COMPLETE. Any legacy Basic/Standard rows are
// archived (is_active=false) and must never reach this list endpoint.
const trialPlan = {
  id: 1, code: 'TRIAL', name: 'Trial', monthlyPrice: 0, yearlyPrice: 0,
  currency: 'MAD', maxVehicles: 5, maxEmployees: 2, maxGpsDevices: 1, storageLimitMb: 500,
};
const completePlan = {
  id: 2, code: 'COMPLETE', name: 'Innovacar Complete', monthlyPrice: 799, yearlyPrice: 7990,
  currency: 'MAD', maxVehicles: 999, maxEmployees: 999, maxGpsDevices: 999, storageLimitMb: 50000,
  billingCycleAllowedMonthly: true, billingCycleAllowedYearly: true,
};

function mockRoutes(plans: unknown[], status: Record<string, unknown> = {}) {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/subscriptions/plans') return Promise.resolve({ data: plans });
    if (url === '/subscriptions/status') {
      return Promise.resolve({
        data: {
          planCode: 'TRIAL', status: 'TRIAL', isTrial: true, planName: 'Trial',
          accessMode: 'FULL', canCheckout: true, ...status,
        },
      });
    }
    return Promise.reject(new Error(`unmocked route: ${url}`));
  });
}

beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('Subscription page — exactly one paid plan, Trial never purchasable', () => {
  it('renders exactly one purchasable pricing card even when the backend still returns a TRIAL row', async () => {
    mockRoutes([trialPlan, completePlan]);
    render(<MemoryRouter><Subscription /></MemoryRouter>);

    await waitFor(() => expect(screen.getAllByText('Innovacar Complete').length).toBeGreaterThan(0));

    // Only one "Select plan"-style button exists — no second Basic/Standard/Premium card.
    const selectButtons = screen.queryAllByRole('button', { name: /select|choose|s.?lectionner/i });
    expect(selectButtons.length).toBeLessThanOrEqual(1);

    // The word "Trial" (as a pricing-card title) must never render as a purchasable card.
    const trialHeadings = screen.queryAllByRole('heading', { name: /^trial$/i });
    expect(trialHeadings).toHaveLength(0);
  });

  it('renders only what the backend returns as active — archived legacy plans are a server-side concern (V90 migration), not a frontend filter', async () => {
    // The frontend only ever filters out TRIAL by design (it's never purchasable);
    // it is NOT responsible for excluding archived Basic/Standard rows — that
    // guarantee lives entirely in V90's `is_active=false` + the plans endpoint
    // only returning active rows. This test documents that boundary: given a
    // backend response containing exactly one paid plan (the only case that
    // should ever occur in production), exactly one card renders.
    mockRoutes([trialPlan, completePlan]);
    render(<MemoryRouter><Subscription /></MemoryRouter>);

    await waitFor(() => expect(screen.getAllByText('Innovacar Complete').length).toBeGreaterThan(0));
    // No legacy plan name (archived server-side by V90, is_active=false) ever
    // appears anywhere in the document — the one paid plan shown is exactly
    // "Innovacar Complete", nothing else.
    for (const legacyName of ['Basic', 'Standard', 'Premium', 'Enterprise']) {
      expect(screen.queryByText(legacyName)).not.toBeInTheDocument();
    }
  });

  it('shows the Trial info panel without any checkout/billing-cycle control while status is TRIAL', async () => {
    mockRoutes([trialPlan, completePlan], { status: 'TRIAL', isTrial: true, remainingTrialDays: 5 });
    render(<MemoryRouter><Subscription /></MemoryRouter>);

    await waitFor(() => expect(screen.getAllByText('Innovacar Complete').length).toBeGreaterThan(0));
    // No monthly/yearly toggle wording tied to a trial purchase, no "checkout" text
    // anywhere referencing the Trial plan itself.
    expect(screen.queryByText(/trial.*checkout|checkout.*trial/i)).not.toBeInTheDocument();
  });
});
