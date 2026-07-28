import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isValidVehicleId, navigateToVehicleAction, vehicleActionRoutes } from '../vehicleActions';

describe('vehicleActionRoutes', () => {
  it('builds every action route from the real vehicle database id, not an index', () => {
    expect(vehicleActionRoutes(42)).toEqual({
      view: '/vehicles?vehicleId=42',
      reserve: '/reservations?fromVehicleId=42',
      contract: '/contracts?fromVehicleId=42',
      maintenance: '/maintenance?vehicleId=42',
    });
  });

  it('two different vehicles produce two different, non-colliding sets of routes', () => {
    const a = vehicleActionRoutes(1);
    const b = vehicleActionRoutes(2);
    expect(a.view).not.toBe(b.view);
    expect(a.reserve).not.toBe(b.reserve);
    expect(a.contract).not.toBe(b.contract);
    expect(a.maintenance).not.toBe(b.maintenance);
  });
});

describe('isValidVehicleId', () => {
  it('accepts a positive integer', () => {
    expect(isValidVehicleId(1)).toBe(true);
    expect(isValidVehicleId(42)).toBe(true);
  });

  it.each([0, -1, NaN, Infinity, 1.5])('rejects %p', (value) => {
    expect(isValidVehicleId(value)).toBe(false);
  });

  it('rejects non-numeric values (registration string, undefined, null, object)', () => {
    expect(isValidVehicleId('A-12345')).toBe(false);
    expect(isValidVehicleId(undefined)).toBe(false);
    expect(isValidVehicleId(null)).toBe(false);
    expect(isValidVehicleId({})).toBe(false);
  });
});

describe('navigateToVehicleAction', () => {
  let navigateMock: ReturnType<typeof vi.fn>;
  let onInvalidMock: ReturnType<typeof vi.fn>;
  let navigate: (path: string) => void;
  let onInvalid: (action: string, vehicleId: unknown) => void;

  beforeEach(() => {
    navigateMock = vi.fn();
    onInvalidMock = vi.fn();
    navigate = navigateMock as unknown as (path: string) => void;
    onInvalid = onInvalidMock as unknown as (action: string, vehicleId: unknown) => void;
  });

  it('navigates to the exact vehicle route for a valid id', () => {
    const ok = navigateToVehicleAction('reserve', 7, navigate, onInvalid);
    expect(ok).toBe(true);
    expect(navigateMock).toHaveBeenCalledWith('/reservations?fromVehicleId=7');
    expect(onInvalidMock).not.toHaveBeenCalled();
  });

  it('refuses to navigate and reports the failure for an invalid id (undefined)', () => {
    const ok = navigateToVehicleAction('maintenance', undefined, navigate, onInvalid);
    expect(ok).toBe(false);
    expect(navigateMock).not.toHaveBeenCalled();
    expect(onInvalidMock).toHaveBeenCalledWith('maintenance', undefined);
  });

  it('refuses to navigate for a plate/registration string mistakenly passed as the id', () => {
    const ok = navigateToVehicleAction('contract', 'B-99999', navigate, onInvalid);
    expect(ok).toBe(false);
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it('refuses to navigate for a zero/negative id (e.g. a stray array index)', () => {
    expect(navigateToVehicleAction('view', 0, navigate, onInvalid)).toBe(false);
    expect(navigateToVehicleAction('view', -1, navigate, onInvalid)).toBe(false);
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
