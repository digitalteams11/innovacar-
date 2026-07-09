package com.carrental.controller;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Tenant;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsControllerTest {

    private static final Long TENANT_ID = 1L;

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ReservationRepository reservationRepository;

    @InjectMocks private ReportsController reportsController;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getRevenueGroupsPaidInvoicesByIssueMonth() {
        when(invoiceRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(
                invoice("INV-001", LocalDate.of(2026, 1, 10), "300.00", InvoiceStatus.PAID),
                invoice("INV-002", LocalDate.of(2026, 2, 5), "900.00", InvoiceStatus.PAID),
                invoice("INV-003", LocalDate.of(2026, 2, 20), "1000.00", InvoiceStatus.PENDING)
        ));

        List<Map<String, Object>> revenue = reportsController
                .getRevenue(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28))
                .getBody();

        assertThat(revenue).hasSize(2);
        assertThat(revenue.get(0))
                .containsEntry("name", "Jan")
                .containsEntry("month", "2026-01");
        assertThat((BigDecimal) revenue.get(0).get("revenue")).isEqualByComparingTo("300.00");
        assertThat(revenue.get(1))
                .containsEntry("name", "Feb")
                .containsEntry("month", "2026-02");
        assertThat((BigDecimal) revenue.get(1).get("revenue")).isEqualByComparingTo("900.00");
    }

    @Test
    void getVehicleUtilizationDoesNotInventRowsForEmptyFleet() {
        List<Map<String, Object>> utilization = reportsController.getVehicleUtilization().getBody();

        assertThat(utilization).isEmpty();
    }

    @Test
    void getVehicleUtilizationReturnsRealVehicleStatusCounts() {
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.AVAILABLE)).thenReturn(2L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RESERVED)).thenReturn(1L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RENTED)).thenReturn(3L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.MAINTENANCE)).thenReturn(1L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.OUT_OF_SERVICE)).thenReturn(1L);

        List<Map<String, Object>> utilization = reportsController.getVehicleUtilization().getBody();

        assertThat(utilization).containsExactly(
                Map.of("name", "Available", "value", 2L),
                Map.of("name", "Reserved", "value", 1L),
                Map.of("name", "Rented", "value", 3L),
                Map.of("name", "Maintenance", "value", 1L),
                Map.of("name", "Out of Service", "value", 1L)
        );
    }

    private Invoice invoice(String number, LocalDate issueDate, String amount, InvoiceStatus status) {
        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Agency")
                .email("agency@test.com")
                .subscriptionActive(true)
                .build();

        return Invoice.builder()
                .invoiceNumber(number)
                .issueDate(issueDate)
                .dueDate(issueDate.plusDays(7))
                .amount(new BigDecimal(amount))
                .status(status)
                .tenant(tenant)
                .build();
    }
}
