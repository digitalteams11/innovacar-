package com.carrental.dto.export;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat, format-agnostic row handed to the CSV/XLSX/PDF exporters — deliberately
 * contains only vehicle data. Client identity documents must never be joined
 * into a fleet export (see ClientIdentityDocument) regardless of caller.
 */
@Data
@Builder
public class VehicleExportRow {
    private Long id;
    private String brand;
    private String model;
    private String category;
    private String plate;
    private String status;
    private BigDecimal pricePerDay;
    private String fuel;
    private String transmission;
    private Integer seats;
    private Integer mileage;
    private String branch;
    private LocalDate nextMaintenanceDate;

    // Optional, gated by include-flags
    private BigDecimal depositAmount;
    private BigDecimal weeklyPrice;
    private BigDecimal monthlyPrice;
    private String gpsStatus;
    private Boolean gpsEnabled;
}
