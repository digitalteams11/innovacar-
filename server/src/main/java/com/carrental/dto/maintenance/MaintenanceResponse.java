package com.carrental.dto.maintenance;

import com.carrental.entity.MaintenanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
public class MaintenanceResponse {
    /** Non-fatal issues that happened while building this response (e.g. a
     *  best-effort notification failed to save). The core work order/status
     *  change itself always succeeded when this DTO is returned. */
    @Builder.Default
    private List<String> warnings = Collections.emptyList();

    private Long id;
    private Long agencyId;
    private Long vehicleId;
    private String vehicle;
    private String plate;
    private String title;
    private String serviceProvider;
    private LocalDateTime scheduledDate;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedDate;
    private LocalDateTime completedAt;
    private BigDecimal cost;
    private Integer mileage;
    private String description;
    private MaintenanceStatus status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
