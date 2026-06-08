package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthlyRevenueDto {
    private String month;
    private BigDecimal revenue;
    private long newAgencies;
    private long churnedAgencies;
}
