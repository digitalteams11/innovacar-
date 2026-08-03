import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '../../i18n';
import i18n from '../../i18n';
import api from '../../api/axios';
import Vehicles from '../Vehicles';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock('../../context/ConfirmContext', () => ({
  useConfirm: () => vi.fn(async () => true),
  usePromptText: () => vi.fn(async () => null),
}));

const mockedApi = vi.mocked(api, true);

const baseVehicle = {
  id: 1,
  marque: 'Toyota Corolla',
  category: 'Sedan',
  plate: 'A-1',
  statut: 'AVAILABLE',
  prixJour: 100,
  fuel: 'Essence',
  transmission: 'Manual',
  imageUrl: '',
};

function mockVehicles(vehicles: unknown[]) {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === '/vehicles') return Promise.resolve({ data: vehicles });
    if (url === '/vehicles/trash') return Promise.resolve({ data: [] });
    if (url === '/subscriptions/status') return Promise.resolve({ data: {} });
    return Promise.reject(new Error(`unmocked route: ${url}`));
  });
}

beforeEach(async () => {
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('Vehicles — seat count rendering', () => {
  it('renders "5 Seats" for a vehicle with 5 seats', async () => {
    mockVehicles([{ ...baseVehicle, id: 1, seatCount: 5 }]);
    render(<MemoryRouter><Vehicles /></MemoryRouter>);
    expect(await screen.findByText('5 Seats')).toBeInTheDocument();
  });

  it('renders "7 Seats" for a vehicle with 7 seats', async () => {
    mockVehicles([{ ...baseVehicle, id: 2, seatCount: 7 }]);
    render(<MemoryRouter><Vehicles /></MemoryRouter>);
    expect(await screen.findByText('7 Seats')).toBeInTheDocument();
  });

  it('renders "2 Seat" (singular) for a vehicle with a single seat', async () => {
    mockVehicles([{ ...baseVehicle, id: 3, seatCount: 1 }]);
    render(<MemoryRouter><Vehicles /></MemoryRouter>);
    expect(await screen.findByText('1 Seat')).toBeInTheDocument();
  });

  it('never renders a blank "Seats" label with no number', async () => {
    mockVehicles([{ ...baseVehicle, id: 4, seatCount: 5 }]);
    render(<MemoryRouter><Vehicles /></MemoryRouter>);
    await screen.findByText('5 Seats');
    expect(screen.queryByText('Seats')).not.toBeInTheDocument();
    expect(screen.queryByText(/^\s*Seats\s*$/)).not.toBeInTheDocument();
  });

  it('shows "Seats not specified" when seatCount is null, never a fake default', async () => {
    mockVehicles([{ ...baseVehicle, id: 5, seatCount: null }]);
    render(<MemoryRouter><Vehicles /></MemoryRouter>);
    expect(await screen.findByText('Seats not specified')).toBeInTheDocument();
    expect(screen.queryByText(/seats/i, { selector: 'span' })?.textContent).not.toMatch(/^5 /);
  });
});
