package com.carrental.service;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.PaymentRepository;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DepositRepository depositRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private InvoiceRepository invoiceRepository;

    @InjectMocks private DashboardService dashboardService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getDashboardMetrics_returnsCorrectAggregations() {
        when(vehicleRepository.countByTenantId(TENANT_ID)).thenReturn(10L);
        when(vehicleRepository.countAvailableByTenantId(TENANT_ID)).thenReturn(4L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RESERVED)).thenReturn(2L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RENTED)).thenReturn(4L);
        when(contractRepository.findAllByTenantIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(invoiceRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.sumCollectedRentalRevenueByTenantId(TENANT_ID)).thenReturn(new BigDecimal("1500.50"));
        when(paymentRepository.sumCollectedRentalRevenueBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findTop5ByTenantIdOrderByPaymentDateDesc(TENANT_ID)).thenReturn(Collections.emptyList());

        DashboardResponse res = dashboardService.getDashboardMetrics();

        assertThat(res.getTotalVehicles()).isEqualTo(10L);
        assertThat(res.getRentedVehicles()).isEqualTo(4L);
        assertThat(res.getAvailableVehicles()).isEqualTo(4L);
        assertThat(res.getReservedVehicles()).isEqualTo(2L);
        assertThat(res.getTotalRevenue()).isEqualByComparingTo("1500.50");
    }

    @Test
    void getDashboardMetrics_handlesNullRevenue() {
        when(vehicleRepository.countByTenantId(TENANT_ID)).thenReturn(0L);
        when(vehicleRepository.countAvailableByTenantId(TENANT_ID)).thenReturn(0L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RESERVED)).thenReturn(0L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RENTED)).thenReturn(0L);
        when(contractRepository.findAllByTenantIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(invoiceRepository.findAllByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.sumCollectedRentalRevenueByTenantId(TENANT_ID)).thenReturn(null);
        when(paymentRepository.sumCollectedRentalRevenueBetween(any(), any(), any())).thenReturn(null);
        when(paymentRepository.findTop5ByTenantIdOrderByPaymentDateDesc(TENANT_ID)).thenReturn(Collections.emptyList());

        DashboardResponse res = dashboardService.getDashboardMetrics();

        assertThat(res.getTotalVehicles()).isEqualTo(0L);
        assertThat(res.getRentedVehicles()).isEqualTo(0L);
        assertThat(res.getTotalRevenue()).isEqualByComparingTo("0.00");
        assertThat(res.getReservationsThisMonth()).isZero();
        assertThat(res.getPendingContracts()).isZero();
        assertThat(res.getSignedContracts()).isZero();
        assertThat(res.getPaymentsToday()).isEqualByComparingTo("0.00");
        assertThat(res.getUpcomingReturns()).isEmpty();
        assertThat(res.getRecentActivity()).isEmpty();
        assertThat(res.getAlerts()).isEmpty();
    }
}
