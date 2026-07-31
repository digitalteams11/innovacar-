package com.carrental.service;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.reporting.ReportCalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardIntelligenceServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private VehicleMaintenanceRepository maintenanceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ReportCalculationService reportCalculationService;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks private DashboardIntelligenceService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubEmptyBaseData() {
        lenient().when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
        lenient().when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
        lenient().when(reservationRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
        lenient().when(maintenanceRepository.findAllByTenantIdOrderByCreatedAtDesc(TENANT_ID)).thenReturn(List.of());
        lenient().when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());
        lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(java.util.Optional.of(Tenant.builder().id(TENANT_ID).status("ACTIVE").build()));
        lenient().when(reportCalculationService.computeFigures(any(), any(), any(), any())).thenReturn(emptyFigures());
    }

    private ReportCalculationService.PeriodFigures emptyFigures() {
        return new ReportCalculationService.PeriodFigures(
                new ReportDataset.FinancialSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new ReportDataset.OperationsSummary(0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0),
                new ReportDataset.FleetSummary(0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(),
                new ReportDataset.ClientsSummary(0, 0, List.of(), 0),
                new ReportDataset.MaintenanceSummary(0, 0, 0, BigDecimal.ZERO, null, 0, 0),
                List.of());
    }

    // ── operationsCenter doesn't blow up on an empty tenant, and every section is present ──

    @Test
    void operationsCenter_onEmptyTenant_returnsAllSectionsWithoutThrowing() {
        stubEmptyBaseData();

        Map<String, Object> result = service.operationsCenter();

        assertThat(result).containsKeys("todayOperations", "actionQueue", "financial", "vehicleProfitability",
                "paymentRisk", "maintenanceIntelligence", "fleetHealth", "contractPipeline", "reservationFunnel");
        assertThat((List<?>) result.get("todayOperations")).isEmpty();
        assertThat((List<?>) result.get("actionQueue")).isEmpty();
    }

    // ── Contract pipeline: cancelled contracts never counted, unpaid only for real debt ──

    @Test
    void contractPipeline_excludesCancelledContractsFromEveryBucket() {
        stubEmptyBaseData();
        Contract cancelledWithDebt = Contract.builder().id(1L).status(ContractStatus.CANCELLED)
                .remainingAmount(new BigDecimal("500")).endDate(LocalDate.now().minusDays(3)).build();
        Contract activeUnpaid = Contract.builder().id(2L).status(ContractStatus.ACTIVE)
                .remainingAmount(new BigDecimal("200")).endDate(LocalDate.now().plusDays(2)).build();
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(cancelledWithDebt, activeUnpaid));

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Long> pipeline = (Map<String, Long>) result.get("contractPipeline");

        assertThat(pipeline.get("unpaid")).isEqualTo(1L); // only activeUnpaid, not the cancelled one
        assertThat(pipeline.get("active")).isEqualTo(1L);
    }

    // ── Payment risk: overdue balance only counts contracts actually past their end date ──

    @Test
    void paymentRisk_excludesCancelledContractsAndOnlyCountsPastDueAsOverdue() {
        stubEmptyBaseData();
        Client client = Client.builder().id(9L).name("Amina").build();
        Contract overdue = Contract.builder().id(1L).contractNumber("CTR-1").status(ContractStatus.ACTIVE)
                .client(client).remainingAmount(new BigDecimal("300")).endDate(LocalDate.now().minusDays(1)).build();
        Contract notYetDue = Contract.builder().id(2L).contractNumber("CTR-2").status(ContractStatus.ACTIVE)
                .client(client).remainingAmount(new BigDecimal("100")).endDate(LocalDate.now().plusDays(10)).build();
        Contract cancelledWithDebt = Contract.builder().id(3L).contractNumber("CTR-3").status(ContractStatus.CANCELLED)
                .client(client).remainingAmount(new BigDecimal("999")).endDate(LocalDate.now().minusDays(5)).build();
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(overdue, notYetDue, cancelledWithDebt));

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) result.get("paymentRisk");

        assertThat(risk.get("totalOverdue")).isEqualTo(new BigDecimal("300"));
        assertThat(risk.get("clientsWithDebt")).isEqualTo(1L);
    }

    // ── Reservation funnel: never a misleading percentage when the denominator is zero ──

    @Test
    void reservationFunnel_returnsNullConversionRate_whenNoReservationsExist() {
        stubEmptyBaseData();

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Object> funnel = (Map<String, Object>) result.get("reservationFunnel");

        assertThat(funnel.get("pendingToConfirmedRate")).isNull();
        assertThat(funnel.get("confirmedToContractRate")).isNull();
        assertThat(funnel.get("contractToCompletedRate")).isNull();
    }

    @Test
    void reservationFunnel_computesRealConversionRates() {
        stubEmptyBaseData();
        Reservation pending = Reservation.builder().id(1L).status(ReservationStatus.PENDING).build();
        Reservation confirmed = Reservation.builder().id(2L).status(ReservationStatus.CONFIRMED).build();
        Reservation converted = Reservation.builder().id(3L).status(ReservationStatus.CONVERTED_TO_CONTRACT).build();
        when(reservationRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(pending, confirmed, converted));

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Object> funnel = (Map<String, Object>) result.get("reservationFunnel");

        assertThat(funnel.get("pending")).isEqualTo(1L);
        assertThat(funnel.get("confirmed")).isEqualTo(1L);
        assertThat(funnel.get("convertedToContract")).isEqualTo(1L);
        assertThat((BigDecimal) funnel.get("confirmedToContractRate")).isNotNull();
    }

    // ── Fleet health: percentages are always computed against the real fleet size ──

    @Test
    void fleetHealth_onEmptyFleet_isZeroSafeNotDivideByZero() {
        stubEmptyBaseData();

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) result.get("fleetHealth");

        assertThat(health.get("total")).isEqualTo(0);
    }

    @Test
    void fleetHealth_countsAvailableVehiclesAndComputesPercent() {
        stubEmptyBaseData();
        Vehicle available1 = Vehicle.builder().id(1L).statut(VehicleStatus.AVAILABLE).build();
        Vehicle available2 = Vehicle.builder().id(2L).statut(VehicleStatus.AVAILABLE).build();
        Vehicle rented = Vehicle.builder().id(3L).statut(VehicleStatus.RENTED).build();
        when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(available1, available2, rented));

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) result.get("fleetHealth");
        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) health.get("breakdown");
        @SuppressWarnings("unchecked")
        Map<String, Object> availableEntry = (Map<String, Object>) breakdown.get("available");

        assertThat(availableEntry.get("count")).isEqualTo(2L);
        assertThat((BigDecimal) availableEntry.get("percent")).isEqualByComparingTo("66.7");
    }

    // ── Action queue: overdue return + unpaid contract both surface as CRITICAL, sorted first ──

    @Test
    void actionQueue_ranksOverdueReturnAsCriticalBeforeNormalItems() {
        stubEmptyBaseData();
        Contract overdueReturn = Contract.builder().id(5L).contractNumber("CTR-5").status(ContractStatus.ACTIVE)
                .endDate(LocalDate.now().minusDays(2)).build();
        Client incompleteClient = Client.builder().id(1L).name("Karim").build(); // no email/phone/license
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(overdueReturn));
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(incompleteClient));

        Map<String, Object> result = service.operationsCenter();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queue = (List<Map<String, Object>>) result.get("actionQueue");

        assertThat(queue).isNotEmpty();
        assertThat(queue.get(0).get("priority")).isEqualTo("CRITICAL");
        assertThat(queue.get(0).get("entityId")).isEqualTo(5L);
    }
}
