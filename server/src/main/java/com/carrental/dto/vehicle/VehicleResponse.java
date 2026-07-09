package com.carrental.dto.vehicle;

import com.carrental.entity.GpsDeviceStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only vehicle projection returned by all vehicle endpoints.
 */
@Data
@Builder
public class VehicleResponse {

    private Long          id;
    private String        marque;
    private BigDecimal    prixJour;
    private VehicleStatus statut;
    private String        category;
    private String        plate;
    private String        fuel;
    private String        transmission;
    private String        imageUrl;
    private Long          tenantId;

    // GPS fields
    private String        gpsDeviceId;
    private String        gpsImei;
    private Double        lastLatitude;
    private Double        lastLongitude;
    private LocalDateTime lastGpsUpdate;
    private GpsDeviceStatus gpsStatus;
    private Double        lastSpeed;
    private Boolean       gpsEnabled;

    // ── Static factory ───────────────────────────────────────────────────────

    public static VehicleResponse from(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .marque(v.getMarque() != null ? v.getMarque() : "")
                .prixJour(v.getPrixJour() != null ? v.getPrixJour() : BigDecimal.ZERO)
                .statut(v.getStatut() != null ? v.getStatut() : VehicleStatus.AVAILABLE)
                .category(v.getCategory() != null ? v.getCategory() : "")
                .plate(v.getPlate() != null ? v.getPlate() : "")
                .fuel(v.getFuel() != null ? v.getFuel() : "")
                .transmission(v.getTransmission() != null ? v.getTransmission() : "")
                .imageUrl(v.getImageUrl() != null ? v.getImageUrl() : "")
                .tenantId(v.getTenant() == null ? null : v.getTenant().getId())
                .gpsDeviceId(v.getGpsDeviceId())
                .gpsImei(v.getGpsImei())
                .lastLatitude(v.getLastLatitude())
                .lastLongitude(v.getLastLongitude())
                .lastGpsUpdate(v.getLastGpsUpdate())
                .gpsStatus(v.getGpsStatus())
                .lastSpeed(v.getLastSpeed())
                .gpsEnabled(Boolean.TRUE.equals(v.getGpsEnabled()))
                .build();
    }
}
