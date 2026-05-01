package com.carrental.service;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.PaymentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private PaymentRepository paymentRepository;

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
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RENTED)).thenReturn(4L);
        when(paymentRepository.sumPaidAmountByTenantId(TENANT_ID)).thenReturn(new BigDecimal("1500.50"));

        DashboardResponse res = dashboardService.getDashboardMetrics();

        assertThat(res.getTotalVehicles()).isEqualTo(10L);
        assertThat(res.getRentedVehicles()).isEqualTo(4L);
        assertThat(res.getTotalRevenue()).isEqualByComparingTo("1500.50");
    }

    @Test
    void getDashboardMetrics_handlesNullRevenue() {
        when(vehicleRepository.countByTenantId(TENANT_ID)).thenReturn(0L);
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.RENTED)).thenReturn(0L);
        when(paymentRepository.sumPaidAmountByTenantId(TENANT_ID)).thenReturn(null);

        DashboardResponse res = dashboardService.getDashboardMetrics();

        assertThat(res.getTotalVehicles()).isEqualTo(0L);
        assertThat(res.getRentedVehicles()).isEqualTo(0L);
        assertThat(res.getTotalRevenue()).isEqualByComparingTo("0.00");
    }
}
