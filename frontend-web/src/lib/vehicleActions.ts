/**
 * Centralized route builders for vehicle-specific card actions (View,
 * Reserve, Contract, Maintenance) so every vehicle card — dashboard, fleet
 * page, mobile, desktop viewport — carries the same vehicle.id through the
 * same query-param convention instead of each card inventing (or dropping)
 * it independently.
 *
 * Query params, not router state: destination pages must keep working after
 * a refresh or a shared link. This app already has a working convention for
 * this — Reservations.tsx and Contracts.tsx read `fromClientId` /
 * `fromReservationId` via useSearchParams to prefill their create modals —
 * so `fromVehicleId` (and Maintenance's existing `vehicleId`) match that
 * instead of introducing a second, route-param-based scheme the rest of the
 * app doesn't use. Router state may still be attached alongside (e.g.
 * `returnTo`) as a pure UX optimization; the vehicle id itself must never
 * live only in state, since state is lost on refresh/direct link.
 */
export interface VehicleActionRoutes {
  /** Fleet page, preselecting this vehicle's details view. */
  view: string;
  /** Reservation list, opening the create form preselected with this vehicle. */
  reserve: string;
  /** Contract list, opening the create form preselected with this vehicle. */
  contract: string;
  /** Maintenance list, opening the create form preselected with this vehicle. */
  maintenance: string;
}

export function vehicleActionRoutes(vehicleId: number): VehicleActionRoutes {
  return {
    view: `/vehicles?vehicleId=${vehicleId}`,
    reserve: `/reservations?fromVehicleId=${vehicleId}`,
    contract: `/contracts?fromVehicleId=${vehicleId}`,
    maintenance: `/maintenance?vehicleId=${vehicleId}`,
  };
}

/**
 * True only for a real, positive, finite integer database id — never an
 * array index, never a registration/plate string, never `NaN`/`undefined`
 * from a card rendered with incomplete data.
 */
export function isValidVehicleId(id: unknown): id is number {
  return typeof id === 'number' && Number.isInteger(id) && id > 0;
}

/**
 * Guard to call before any vehicle-specific navigation. Returns true and
 * navigates when the id is valid; otherwise logs a safe diagnostic (no PII,
 * just the offending id/action) and returns false so the caller can show a
 * compact inline error instead of navigating to a broken/generic page.
 */
export function navigateToVehicleAction(
  action: keyof VehicleActionRoutes,
  vehicleId: unknown,
  navigate: (path: string) => void,
  onInvalid?: (action: keyof VehicleActionRoutes, vehicleId: unknown) => void,
): boolean {
  if (!isValidVehicleId(vehicleId)) {
    console.error('[vehicleActions] refused to navigate — invalid vehicle id', { action, vehicleId });
    onInvalid?.(action, vehicleId);
    return false;
  }
  navigate(vehicleActionRoutes(vehicleId)[action]);
  return true;
}
