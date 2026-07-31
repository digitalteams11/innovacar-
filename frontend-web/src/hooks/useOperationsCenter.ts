import { useEffect, useState, useCallback } from 'react';
import api from '../api/axios';

export interface OperationItem {
  category: string; clientName: string | null; vehicle: string | null;
  time: string | null; status: string | null; entityType: string; entityId: number; action: string;
}
export interface ActionItem {
  priority: 'CRITICAL' | 'HIGH' | 'NORMAL'; category: string; title: string; reason: string;
  entityType: string; entityId: number | null; action: string;
}
export interface FinancialControlCenter {
  collectedToday: number; collectedThisMonth: number; unpaidBalance: number; overdueBalance: number;
  refunds: number; maintenanceExpenses: number; netProfitEstimate: number; averageRentalValue: number;
  yearToDateRevenue: number;
  vsYesterday: { previous: number; current: number; absoluteChange: number; percentChange: number | null; percentAvailable: boolean };
  vsLastMonth: { previous: number; current: number; absoluteChange: number; percentChange: number | null; percentAvailable: boolean };
}
export interface VehiclePerformanceEntry {
  vehicleId: number; label: string; revenue: number; expenses: number; profitContribution: number; utilizationRate: number;
}
export interface VehicleProfitability {
  hasData: boolean;
  topRevenue?: VehiclePerformanceEntry; bestUtilization?: VehiclePerformanceEntry;
  highestMaintenanceCost?: VehiclePerformanceEntry; lowestUtilization?: VehiclePerformanceEntry;
  negativeContribution?: VehiclePerformanceEntry;
}
export interface OperationsCenterData {
  todayOperations: OperationItem[];
  actionQueue: ActionItem[];
  financial: FinancialControlCenter | null;
  vehicleProfitability: VehicleProfitability | null;
  paymentRisk: {
    totalOverdue: number; clientsWithDebt: number; contractsWithPartialPayment: number; dueThisWeek: number;
    highestUnpaidContract?: { contractId: number; contractNumber: string; clientName: string | null; amount: number };
  } | null;
  maintenanceIntelligence: {
    dueSoon: number; overdue: number; inProgress: number; planned: number; completedThisMonth: number;
    totalCostThisMonth: number; repeatedProblems: number;
    mostExpensiveVehicle?: { vehicleId: number; label: string; cost: number };
  } | null;
  fleetHealth: {
    total: number;
    breakdown: Record<string, { count: number; percent: number }>;
  } | null;
  contractPipeline: Record<string, number> | null;
  reservationFunnel: {
    pending: number; confirmed: number; convertedToContract: number; completed: number; cancelled: number;
    pendingToConfirmedRate: number | null; confirmedToContractRate: number | null; contractToCompletedRate: number | null;
  } | null;
}

// Module-level cache/dedup — several widgets on the same dashboard render at
// once and all need this same payload; without this every one of them would
// fire its own request for identical data (spec section 26: "avoid duplicate
// calls").
let cached: OperationsCenterData | null = null;
let inFlight: Promise<OperationsCenterData> | null = null;

async function fetchOperationsCenter(force: boolean): Promise<OperationsCenterData> {
  if (!force && cached) return cached;
  if (!force && inFlight) return inFlight;
  inFlight = api.get('/dashboard/operations-center')
    .then(({ data }) => {
      cached = data?.data as OperationsCenterData;
      return cached;
    })
    .finally(() => { inFlight = null; });
  return inFlight;
}

export function invalidateOperationsCenter() {
  cached = null;
  inFlight = null;
}

export function useOperationsCenter() {
  const [data, setData] = useState<OperationsCenterData | null>(cached);
  const [loading, setLoading] = useState(!cached);
  const [error, setError] = useState(false);

  const load = useCallback((force = false) => {
    setLoading(true);
    setError(false);
    fetchOperationsCenter(force)
      .then((result) => setData(result))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  // Reuses the same 'rentcar-data-updated' event Vehicles/Reservations/
  // Contracts/Maintenance already dispatch after a business action, so every
  // existing dispatch point refreshes these widgets for free (spec section 23:
  // refresh only the affected queries, not a full page reload).
  useEffect(() => {
    const onRefresh = () => load(true);
    window.addEventListener('rentcar-data-updated', onRefresh);
    return () => window.removeEventListener('rentcar-data-updated', onRefresh);
  }, [load]);

  return { data, loading, error, retry: () => load(true) };
}
