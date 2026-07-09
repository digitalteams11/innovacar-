export interface WidgetConfig {
  id: string;
  label: string;
  description: string;
  visible: boolean;
  order: number;
  pinned?: boolean;
}

export const DEFAULT_WIDGET_LAYOUT: WidgetConfig[] = [
  { id: 'stats',        label: 'Key Statistics',        description: 'Fleet, rentals, and revenue overview',  visible: true, order: 0, pinned: true },
  { id: 'setup',        label: 'Setup Checklist',       description: 'Onboarding guide shown until complete', visible: true, order: 1 },
  { id: 'actions',      label: 'Quick Actions',         description: 'Shortcuts to common tasks',             visible: true, order: 2 },
  { id: 'alerts',       label: 'Alerts & Notifications',description: 'Fleet and contract alerts',             visible: true, order: 3 },
  { id: 'fleet',        label: 'Fleet Overview',        description: 'All vehicles with status and details',  visible: true, order: 4 },
  { id: 'charts',       label: 'Calendar & Charts',     description: 'Booking trends, status, and calendar',  visible: true, order: 5 },
  { id: 'lower',        label: 'Vehicles & Clients',    description: 'Vehicle list, clients, maintenance',    visible: true, order: 6 },
  { id: 'pickups',      label: 'Pickups & Returns',     description: 'Upcoming pickups and returns',          visible: true, order: 7 },
  { id: 'gps',          label: 'GPS Quick Status',      description: 'GPS device status summary',             visible: true, order: 8 },
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
