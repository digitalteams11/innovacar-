package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlanRevenueDto {
    private String planName;
    private long agencyCount;
    private BigDecimal revenue;
}
