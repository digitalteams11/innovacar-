package com.carrental.dto.gps;

import lombok.Builder;
import lombok.Data;

/**
 * GPS dashboard statistics.
 */
@Data
@Builder
public class GpsDashboardStats {

    private Long totalTracked;
    private Long online;
    private Long offline;
    private Long moving;
    private Long stopped;
    private Long idle;
    private Long outOfZone;
    private Long activeAlerts;
    private Long alertsToday;
    private Double totalDistanceTodayKm;
}
