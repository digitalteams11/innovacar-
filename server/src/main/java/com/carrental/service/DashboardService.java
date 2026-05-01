package com.carrental.service;

import com.carrental.dto.dashboard.DashboardResponse;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service to aggregate dashboard metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardMetrics() {
        Long tenantId = TenantContext.getCurrentTenantId();

        long totalVehicles = vehicleRepository.countByTenantId(tenantId);
        long rentedVehicles = vehicleRepository.countByTenantIdAndStatut(tenantId, VehicleStatus.RENTED);
        
        BigDecimal totalRevenue = paymentRepository.sumPaidAmountByTenantId(tenantId);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        return DashboardResponse.builder()
                .totalVehicles(totalVehicles)
                .rentedVehicles(rentedVehicles)
                .totalRevenue(totalRevenue)
                .build();
    }
}
