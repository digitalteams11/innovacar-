package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AgencyDetailDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String taxId;

    private String status;
    private String planName;
    private boolean subscriptionActive;
    private LocalDate subscriptionEndDate;
    private LocalDate trialEndDate;
    private boolean inTrial;

    private Integer maxVehicles;
    private Integer maxEmployees;
    private Integer maxGpsDevices;
    private Integer maxReservations;
    private Integer storageLimitMb;

    private long currentVehicleCount;
    private long currentEmployeeCount;
    private long currentReservationCount;
    private long currentContractCount;
    private long currentGpsDeviceCount;

    private BigDecimal totalPayments;
    private BigDecimal totalRevenue;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
