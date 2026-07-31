export interface WidgetConfig {
  id: string;
  label: string;
  description: string;
  visible: boolean;
  order: number;
  pinned?: boolean;
  /** RolePermissionService permission code required to see this widget — absent means "always visible if the pack allows it". */
  requiredPermission?: string;
  /** FeatureAccessService feature code required (e.g. Complete-pack-only widgets) — absent means "available on every pack". */
  requiredFeature?: string;
}

// A new widget only ever needs one entry added here — DashboardCustomizeModal,
// useDashboardLayout's visibility/ordering, and the mobile priority list all
// read from this single array. No other file needs to change (spec section 15:
// "any new dashboard widget must automatically appear in Customize Dashboard").
export const DEFAULT_WIDGET_LAYOUT: WidgetConfig[] = [
  { id: 'operationsCenter', label: "Today's Operations",   description: 'Pickups, returns, signatures, and payments due today', visible: true, order: 0, pinned: true },
  { id: 'actionQueue',      label: 'Action Required',       description: 'Prioritized list of issues needing attention',         visible: true, order: 1, pinned: true },
  { id: 'stats',        label: 'Key Statistics',        description: 'Fleet, rentals, and revenue overview',  visible: true, order: 2, pinned: true },
  { id: 'financialCenter',  label: 'Financial Control Center', description: 'Collected, unpaid, and profit vs previous period', visible: true, order: 3, requiredPermission: 'PAYMENT_VIEW' },
  { id: 'setup',        label: 'Setup Checklist',       description: 'Onboarding guide shown until complete', visible: true, order: 4 },
  { id: 'actions',      label: 'Quick Actions',         description: 'Shortcuts to common tasks',             visible: true, order: 5 },
  { id: 'alerts',       label: 'Alerts & Notifications',description: 'Fleet and contract alerts',             visible: true, order: 6 },
  { id: 'fleet',        label: 'Fleet Overview',        description: 'All vehicles with status and details',  visible: true, order: 7 },
  { id: 'vehicleProfitability', label: 'Vehicle Profitability', description: 'Top/lowest performing vehicles by profit and utilization', visible: true, order: 8, requiredFeature: 'ADVANCED_REPORTS' },
  { id: 'charts',       label: 'Calendar & Charts',     description: 'Booking trends, status, and calendar',  visible: true, order: 9 },
  { id: 'lower',        label: 'Vehicles & Clients',    description: 'Vehicle list, clients, maintenance',    visible: true, order: 10 },
  { id: 'pickups',      label: 'Pickups & Returns',     description: 'Upcoming pickups and returns',          visible: true, order: 11 },
  { id: 'gps',          label: 'GPS Quick Status',      description: 'GPS device status summary',             visible: true, order: 12 },
];

export interface DashboardStats {
  totalVehicles?: number; fleet?: number;
  rentedVehicles?: number; availableVehicles?: number; reservedVehicles?: number;
  totalRevenue?: number; monthlyRevenue?: number; paymentsToday?: number;
  totalClients?: number;
  reservationsToday?: number; upcomingReservations?: number; totalReservations?: number;
  activeContracts?: number; pendingContracts?: number; signedContracts?: number;
  totalDepositsHeld?: number; pendingReturns?: number; returnedDeposits?: number;
  depositDeductions?: number;
}

export interface DashboardVehicle {
  id: number; marque: string; statut: string; imageUrl?: string;
  category?: string; prixJour?: number; plate?: string;
}

export interface DashboardReservation {
  id: number; dateStart: string; dateEnd: string;
  vehicleMarque?: string; totalPrice?: number;
  status?: string; clientName?: string;
  pickupLocation?: string; returnLocation?: string;
  clientFirstName?: string; clientLastName?: string;
}

export interface ClientItem {
  id: number; fullName?: string; firstName?: string; lastName?: string;
  email?: string; phone?: string; gender?: string; totalContracts?: number;
}

export interface MaintenanceItem {
  id: number;
  vehicle?: { marque?: string; plate?: string };
  type?: string; priority?: string; scheduledDate?: string;
  description?: string; mileage?: number;
}

export interface HealthScore { score?: number; label?: string; risk?: string; }
