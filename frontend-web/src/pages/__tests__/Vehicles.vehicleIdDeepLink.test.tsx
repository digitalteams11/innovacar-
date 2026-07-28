import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import Vehicles from '../Vehicles';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const showToast = vi.fn();
vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast }),
}));

const mockedApi = vi.mocked(api, true);

const vehicleA = {
  id: 11, marque: 'Hyundai Tucson', brand: 'Hyundai', model: 'Tucson',
  category: 'SUV', plate: 'A-11111', statut: 'AVAILABLE', prixJour: 350,
  fuel: 'Diesel', transmission: 'Automatic', imageUrl: '',
};
const vehicleB = {
  id: 22, marque: 'Dacia Logan', brand: 'Dacia', model: 'Logan',
  category: 'Sedan', plate: 'B-22222', statut: 'AVAILABLE', prixJour: 150,
  fuel: 'Essence', transmission: 'Manual', imageUrl: '',
};

function mockApiRoutes(byUrl: Record<string, unknown>) {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/vehicles') return Promise.resolve({ data: [vehicleA, vehicleB] });
    if (url === '/vehicles/trash') return Promise.resolve({ data: [] });
    if (url === '/subscriptions/status') return Promise.resolve({ data: {} });
    if (url in byUrl) {
      const value = byUrl[url];
      if (value instanceof Error) return Promise.reject(value);
      return Promise.resolve({ data: value });
    }
    return Promise.reject(new Error(`unmocked route: ${url}`));
  });
}

beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('Vehicles — ?vehicleId= deep link (View action from other cards)', () => {
  it('opens the details modal for the exact vehicle referenced by ?vehicleId=', async () => {
    mockApiRoutes({ '/vehicles/11': vehicleA });
    render(
      <MemoryRouter initialEntries={['/vehicles?vehicleId=11']}>
        <Vehicles />
      </MemoryRouter>
    );
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { level: 4 })).toHaveTextContent('Hyundai Tucson');
    expect(mockedApi.get).toHaveBeenCalledWith('/vehicles/11');
  });

  it('a different vehicleId opens that different vehicle, not a stale/previous one', async () => {
    mockApiRoutes({ '/vehicles/22': vehicleB });
    render(
      <MemoryRouter initialEntries={['/vehicles?vehicleId=22']}>
        <Vehicles />
      </MemoryRouter>
    );
    const dialog = await screen.findByRole('dialog');
    // Both vehicles are in the underlying fleet list (and thus in the page
    // somewhere) — the assertion that matters is which one the *modal*
    // opened for, not whether the other vehicle's card is visible elsewhere.
    expect(within(dialog).getByRole('heading', { level: 4 })).toHaveTextContent('Dacia Logan');
    expect(mockedApi.get).toHaveBeenCalledWith('/vehicles/22');
  });

  it('shows a safe "not found" toast and does not crash when the vehicle id does not exist', async () => {
    mockApiRoutes({
      '/vehicles/999': Object.assign(new Error('Not Found'), { response: { status: 404 } }),
    });
    render(
      <MemoryRouter initialEntries={['/vehicles?vehicleId=999']}>
        <Vehicles />
      </MemoryRouter>
    );
    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Vehicle not found.', 'error'));
    // Page did not crash and no modal was left open on invalid data.
    expect(screen.getByRole('heading', { name: /fleet management/i })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('never treats a non-numeric/invalid vehicleId as a valid database id', async () => {
    mockApiRoutes({});
    render(
      <MemoryRouter initialEntries={['/vehicles?vehicleId=not-a-number']}>
        <Vehicles />
      </MemoryRouter>
    );
    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Vehicle not found.', 'error'));
    expect(mockedApi.get).not.toHaveBeenCalledWith(expect.stringContaining('/vehicles/not-a-number'));
  });
});
