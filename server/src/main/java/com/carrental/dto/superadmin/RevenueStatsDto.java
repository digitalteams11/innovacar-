package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RevenueStatsDto {
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal yearlyRevenue;
    private BigDecimal avgRevenuePerAgency;
    private List<MonthlyRevenueDto> monthlyTrend;
    private List<PlanRevenueDto> revenueByPlan;
}
