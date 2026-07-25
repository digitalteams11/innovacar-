import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import ContractDetails from '../ContractDetails';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ tenant: { id: 1, name: 'Test Agency', agencySignature: null, agencyStampUrl: null } }),
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

// This page's own signing/inspection sub-components are unrelated to the
// resend-email and signature-display logic under test here, and each pulls
// in its own heavy dependency tree (canvas signing, file upload, etc.) —
// stub them out to keep this a focused test of ContractDetails itself.
vi.mock('../../components/shared/SignaturePad', () => ({ default: () => null }));
vi.mock('../../components/shared/QRCodeModal', () => ({ default: () => null }));
vi.mock('../../components/shared/VehicleInspection', () => ({ default: () => null }));
vi.mock('../../components/shared/ReturnInspectionModal', () => ({ default: () => null }));
vi.mock('../../components/InspectionGallery', () => ({ default: () => null }));
vi.mock('../../components/Modal', () => ({ default: () => null }));
vi.mock('../../components/shared/AddClientEmailModal', () => ({ default: () => null }));

const mockedApi = vi.mocked(api, true);

const baseContract = {
  id: 42,
  contractNumber: 'CTR-2026-00042',
  status: 'PARTIALLY_SIGNED',
  contractType: 'STANDARD',
  contractLanguage: 'en',
  clientId: 7,
  clientFullName: 'Jane Doe',
  ownerSigned: false,
  clientSigned: false,
  ownerSignature: null,
  clientSignature: null,
  pdfUrl: null,
};

function mockApiGetByRoute(overrides: Record<string, unknown> = {}) {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/contracts/42') return Promise.resolve({ data: { ...baseContract, ...overrides } });
    if (url === '/contracts/42/email-status') {
      return Promise.resolve({
        data: { hasClientEmail: true, clientEmail: 'jane@example.com', lastStatus: null, lastErrorCode: null, lastSentAt: null },
      });
    }
    if (url === '/contracts/42/inspections') return Promise.resolve({ data: [] });
    return Promise.reject(new Error(`unmocked route: ${url}`));
  });
}

function renderContract() {
  return render(
    <MemoryRouter initialEntries={['/contracts/42']}>
      <Routes>
        <Route path="/contracts/:id" element={<ContractDetails />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('ContractDetails — signatures', () => {
  it('shows "Not signed yet" for both sides when neither has signed', async () => {
    mockApiGetByRoute();
    renderContract();
    const notices = await screen.findAllByText(/not signed yet/i);
    expect(notices.length).toBe(2);
  });

  it('renders the signature image (not a blank box) once a signature exists', async () => {
    mockApiGetByRoute({
      ownerSigned: true,
      ownerSignature: 'data:image/png;base64,AAAA',
      clientSigned: true,
      clientSignature: 'data:image/png;base64,BBBB',
    });
    renderContract();
    const agencyImgs = await screen.findAllByAltText(/agency signature/i);
    expect(agencyImgs.some((img) => img.getAttribute('src') === 'data:image/png;base64,AAAA')).toBe(true);
    const clientImgs = await screen.findAllByAltText(/client signature/i);
    expect(clientImgs.some((img) => img.getAttribute('src') === 'data:image/png;base64,BBBB')).toBe(true);
    // Not "Not signed yet" anywhere once both sides are actually signed.
    expect(screen.queryByText(/not signed yet/i)).not.toBeInTheDocument();
  });

  it('falls back to a clear message instead of a blank image when signed=true but no image data exists', async () => {
    mockApiGetByRoute({ ownerSigned: true, ownerSignature: null });
    renderContract();
    await waitFor(() => expect(screen.getByText(/agency signature applied/i)).toBeInTheDocument());
    expect(screen.getByText(/signature image unavailable/i)).toBeInTheDocument();
    expect(screen.queryByAltText(/agency signature/i)).not.toBeInTheDocument();
  });
});

describe('ContractDetails — resend email', () => {
  it('keeps the resend/send button enabled (not gated on signature state)', async () => {
    mockApiGetByRoute();
    renderContract();
    const button = await screen.findByRole('button', { name: /send email/i });
    expect(button).toBeEnabled();
  });

  it('shows the awaiting-signatures notice (not a silent no-op) when the contract is not fully signed', async () => {
    mockApiGetByRoute();
    renderContract();
    expect(await screen.findByText(/both signatures are required/i)).toBeInTheDocument();
  });

  it('does not show the awaiting-signatures notice once fully signed', async () => {
    mockApiGetByRoute({
      ownerSigned: true, ownerSignature: 'data:image/png;base64,AAAA',
      clientSigned: true, clientSignature: 'data:image/png;base64,BBBB',
    });
    renderContract();
    await screen.findAllByAltText(/agency signature/i);
    expect(screen.queryByText(/both signatures are required/i)).not.toBeInTheDocument();
  });

  it('remains enabled after a previous successful send (label switches to Resend)', async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === '/contracts/42') return Promise.resolve({ data: baseContract });
      if (url === '/contracts/42/email-status') {
        return Promise.resolve({
          data: { hasClientEmail: true, clientEmail: 'jane@example.com', lastStatus: 'SENT', lastErrorCode: null, lastSentAt: new Date().toISOString() },
        });
      }
      if (url === '/contracts/42/inspections') return Promise.resolve({ data: [] });
      return Promise.reject(new Error(`unmocked route: ${url}`));
    });
    renderContract();
    const button = await screen.findByRole('button', { name: /resend email/i });
    expect(button).toBeEnabled();
  });
});
