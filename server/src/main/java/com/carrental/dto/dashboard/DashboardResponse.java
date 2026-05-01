package com.carrental.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Dashboard KPIs projection.
 */
@Data
@Builder
public class DashboardResponse {
    private long totalVehicles;
    private long rentedVehicles;
    private BigDecimal totalRevenue;
}
