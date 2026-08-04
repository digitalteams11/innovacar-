import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import Invoices from '../Invoices';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock('../../context/ConfirmContext', () => ({
  useConfirm: () => vi.fn(async () => true),
  usePromptText: () => vi.fn(async () => null),
}));

vi.mock('../../context/PermissionContext', () => ({
  usePermissions: () => ({ hasPermission: () => true }),
}));

const mockedApi = vi.mocked(api, true);

const baseInvoice = {
  id: 1,
  invoiceNumber: 'INV-2026-00001',
  clientName: 'Jane Doe',
  clientId: 10,
  issueDate: '2026-08-01',
  dueDate: '2026-08-15',
  amount: 4000,
  status: 'PENDING',
};

function mockBaseRoutes() {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/invoices') return Promise.resolve({ data: [baseInvoice] });
    return Promise.reject(new Error(`unmocked route: ${url}`));
  });
}

// A never-resolving PDF response used to observe the "in flight" state
// without racing the test against a real promise resolution.
function pendingBlobResponse() {
  return new Promise(() => { /* never resolves within the test */ });
}

function pdfBlobResponse() {
  return Promise.resolve({
    data: new Blob(['%PDF-1.4 fake'], { type: 'application/pdf' }),
    headers: {
      'content-type': 'application/pdf',
      'content-disposition': 'attachment; filename="factures-2026-08.pdf"',
    },
  });
}

beforeEach(async () => {
  vi.clearAllMocks();
  mockBaseRoutes();
  await i18n.changeLanguage('en');
  // jsdom has no real object URL support — stub it so the download flow doesn't throw.
  (globalThis as any).URL.createObjectURL = vi.fn(() => 'blob:mock');
  (globalThis as any).URL.revokeObjectURL = vi.fn();
});

async function openExportMenu() {
  const trigger = await screen.findByRole('button', { name: /export options/i });
  fireEvent.click(trigger);
}

describe('Invoices — PDF export is the primary action, CSV stays secondary', () => {
  it('the PDF export action calls the backend PDF endpoint, never CSV', async () => {
    mockedApi.post.mockImplementation((url: string) =>
      url === '/invoices/export/pdf' ? pdfBlobResponse() : Promise.reject(new Error(`unexpected POST ${url}`)));

    render(<MemoryRouter><Invoices /></MemoryRouter>);
    await openExportMenu();

    const pdfItem = await screen.findByRole('menuitem', { name: /exporter en pdf|export as pdf|export.*pdf/i });
    fireEvent.click(pdfItem);

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith(
      '/invoices/export/pdf',
      expect.anything(),
      expect.objectContaining({ responseType: 'blob' }),
    ));
    // The CSV endpoint must never be hit by the PDF action.
    expect(mockedApi.get).not.toHaveBeenCalledWith('/invoices/export/csv', expect.anything());
  });

  it('CSV export remains available as a secondary action and hits its own endpoint', async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === '/invoices') return Promise.resolve({ data: [baseInvoice] });
      if (url === '/invoices/export/csv') return Promise.resolve({
        data: new Blob(['id,amount\n1,4000'], { type: 'text/csv' }),
        headers: {
          'content-type': 'text/csv',
          'content-disposition': 'attachment; filename="factures-2026-08.csv"',
        },
      });
      return Promise.reject(new Error(`unmocked route: ${url}`));
    });

    render(<MemoryRouter><Invoices /></MemoryRouter>);
    await openExportMenu();

    const csvItem = await screen.findByRole('menuitem', { name: /exporter en csv|export as csv|export.*csv/i });
    fireEvent.click(csvItem);

    await waitFor(() => expect(mockedApi.get).toHaveBeenCalledWith(
      '/invoices/export/csv',
      expect.objectContaining({ responseType: 'blob' }),
    ));
    expect(mockedApi.post).not.toHaveBeenCalledWith('/invoices/export/pdf', expect.anything(), expect.anything());
  });

  it('prevents duplicate export clicks while a request is in flight', async () => {
    mockedApi.post.mockImplementation((url: string) =>
      url === '/invoices/export/pdf' ? pendingBlobResponse() : Promise.reject(new Error(`unexpected POST ${url}`)));

    render(<MemoryRouter><Invoices /></MemoryRouter>);
    const trigger = await screen.findByRole('button', { name: /export options/i });
    fireEvent.click(trigger);
    const pdfItem = await screen.findByRole('menuitem', { name: /exporter en pdf|export as pdf|export.*pdf/i });
    fireEvent.click(pdfItem);

    // The trigger itself must now be disabled — a second click must not
    // fire a second export request while the first is still pending.
    await waitFor(() => expect(screen.getByRole('button', { name: /export options/i })).toBeDisabled());
    fireEvent.click(screen.getByRole('button', { name: /export options/i }));
    expect(mockedApi.post).toHaveBeenCalledTimes(1);
  });
});
