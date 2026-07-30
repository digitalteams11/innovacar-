package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.entity.*;
import com.carrental.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportCalculationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private VehicleMaintenanceRepository maintenanceRepository;
    @Mock private ClientRepository clientRepository;

    private final ReportPeriodResolver periodResolver = new ReportPeriodResolver();

    private ReportCalculationService service() {
        return new ReportCalculationService(paymentRepository, contractRepository, reservationRepository,
                vehicleRepository, maintenanceRepository, clientRepository, periodResolver);
    }

    private Tenant tenant() {
        return Tenant.builder().id(1L).name("Test Agency").build();
    }

    @Test
    void revenueExcludesCancelledUnpaidRefundedAndDepositPayments() {
        List<Payment> payments = List.of(
                payment(PaymentType.RENTAL, PaymentStatus.PAID, "1000"),
                payment(PaymentType.RENTAL, PaymentStatus.CANCELLED, "500"),
                payment(PaymentType.RENTAL, PaymentStatus.PENDING, "700"),
                payment(PaymentType.REFUND, PaymentStatus.REFUNDED, "200"),
                payment(PaymentType.DEPOSIT_PAYMENT, PaymentStatus.PAID, "300"),
                payment(PaymentType.EXTRA_CHARGE, PaymentStatus.PARTIALLY_PAID, "150"));

        when(paymentRepository.findAllForReportingPeriod(eq(1L), any(), any())).thenReturn(payments);
        when(contractRepository.findAllOverlappingPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(contractRepository.findFirstContractStartDateByClient(1L)).thenReturn(List.of());
        when(reservationRepository.findAllStartingInPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(vehicleRepository.findAllByTenantId(1L)).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(1L), eq(MaintenanceStatus.COMPLETED), any(), any())).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(List.of());

        ReportPeriodResolver.Period period = periodResolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 2, 1));
        ReportDataset dataset = service().calculate(tenant(), ReportType.MONTHLY, period, ZoneOffset.UTC);

        // 1000 (RENTAL/PAID) + 150 (EXTRA_CHARGE/PARTIALLY_PAID) — everything else excluded.
        assertThat(dataset.financial().grossRevenue()).isEqualByComparingTo("1150.00");
        assertThat(dataset.financial().refunds()).isEqualByComparingTo("200.00");
        assertThat(dataset.financial().netRevenue()).isEqualByComparingTo("950.00");
    }

    @Test
    void expensesIncludeMaintenanceCostAndRefunds_profitAndLossHaveCorrectSign() {
        List<Payment> payments = List.of(
                payment(PaymentType.RENTAL, PaymentStatus.PAID, "1000"),
                payment(PaymentType.REFUND, PaymentStatus.REFUNDED, "200"));
        VehicleMaintenance maintenance = VehicleMaintenance.builder()
                .id(1L).status(MaintenanceStatus.COMPLETED).cost(new BigDecimal("100.00")).build();

        when(paymentRepository.findAllForReportingPeriod(eq(1L), any(), any())).thenReturn(payments);
        when(contractRepository.findAllOverlappingPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(contractRepository.findFirstContractStartDateByClient(1L)).thenReturn(List.of());
        when(reservationRepository.findAllStartingInPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(vehicleRepository.findAllByTenantId(1L)).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(1L), eq(MaintenanceStatus.COMPLETED), any(), any())).thenReturn(List.of(maintenance));
        when(maintenanceRepository.findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(List.of(maintenance));

        ReportPeriodResolver.Period period = periodResolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 2, 1));
        ReportDataset dataset = service().calculate(tenant(), ReportType.MONTHLY, period, ZoneOffset.UTC);

        // expenses = maintenance(100) + refunds(200) = 300; netRevenue = 1000 - 200 = 800; profit = 500
        assertThat(dataset.financial().expenses()).isEqualByComparingTo("300.00");
        assertThat(dataset.financial().profit()).isEqualByComparingTo("500.00");
        assertThat(dataset.financial().loss()).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeProfitProducesPositiveLossEqualToAbsoluteValue() {
        List<Payment> payments = List.of(payment(PaymentType.RENTAL, PaymentStatus.PAID, "100"));
        VehicleMaintenance expensiveMaintenance = VehicleMaintenance.builder()
                .id(2L).status(MaintenanceStatus.COMPLETED).cost(new BigDecimal("900.00")).build();

        when(paymentRepository.findAllForReportingPeriod(eq(1L), any(), any())).thenReturn(payments);
        when(contractRepository.findAllOverlappingPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(contractRepository.findFirstContractStartDateByClient(1L)).thenReturn(List.of());
        when(reservationRepository.findAllStartingInPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(vehicleRepository.findAllByTenantId(1L)).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(1L), eq(MaintenanceStatus.COMPLETED), any(), any())).thenReturn(List.of(expensiveMaintenance));
        when(maintenanceRepository.findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(List.of(expensiveMaintenance));

        ReportPeriodResolver.Period period = periodResolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 2, 1));
        ReportDataset dataset = service().calculate(tenant(), ReportType.MONTHLY, period, ZoneOffset.UTC);

        // profit = 100 - 900 = -800; loss must be the positive absolute value.
        assertThat(dataset.financial().profit()).isEqualByComparingTo("-800.00");
        assertThat(dataset.financial().loss()).isEqualByComparingTo("800.00");
    }

    @Test
    void outstandingBalanceSumsOnlyNonCancelledContractsWithPositiveRemainingAmount() {
        Contract active = Contract.builder().id(1L).status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 1, 5)).endDate(LocalDate.of(2026, 1, 10))
                .remainingAmount(new BigDecimal("400.00")).build();
        Contract cancelled = Contract.builder().id(2L).status(ContractStatus.CANCELLED)
                .startDate(LocalDate.of(2026, 1, 5)).endDate(LocalDate.of(2026, 1, 10))
                .remainingAmount(new BigDecimal("500.00")).build();
        Contract fullyPaid = Contract.builder().id(3L).status(ContractStatus.COMPLETED)
                .startDate(LocalDate.of(2026, 1, 5)).endDate(LocalDate.of(2026, 1, 10))
                .remainingAmount(BigDecimal.ZERO).build();

        when(paymentRepository.findAllForReportingPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(contractRepository.findAllOverlappingPeriod(eq(1L), any(), any())).thenReturn(List.of(active, cancelled, fullyPaid));
        when(contractRepository.findFirstContractStartDateByClient(1L)).thenReturn(List.of());
        when(reservationRepository.findAllStartingInPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(vehicleRepository.findAllByTenantId(1L)).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(1L), eq(MaintenanceStatus.COMPLETED), any(), any())).thenReturn(List.of());
        when(maintenanceRepository.findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(List.of());

        ReportPeriodResolver.Period period = periodResolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 2, 1));
        ReportDataset dataset = service().calculate(tenant(), ReportType.MONTHLY, period, ZoneOffset.UTC);

        assertThat(dataset.financial().outstandingBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void zeroPreviousValue_percentChangeIsUnavailableNotNaNOrInfinity() {
        ReportDataset.MetricChange change = ReportDataset.MetricChange.of(BigDecimal.ZERO, new BigDecimal("500"));

        assertThat(change.percentAvailable()).isFalse();
        assertThat(change.percentChange()).isNull();
        assertThat(change.absoluteChange()).isEqualByComparingTo("500");
    }

    @Test
    void nonZeroPreviousValue_percentChangeIsComputedCorrectly() {
        ReportDataset.MetricChange change = ReportDataset.MetricChange.of(new BigDecimal("1000"), new BigDecimal("1250"));

        assertThat(change.percentAvailable()).isTrue();
        assertThat(change.percentChange()).isEqualByComparingTo("25.000000");
    }

    private Payment payment(PaymentType type, PaymentStatus status, String amount) {
        return Payment.builder().type(type).status(status).amount(new BigDecimal(amount))
                .paymentDate(LocalDateTime.of(2026, 1, 15, 10, 0)).build();
    }
}
