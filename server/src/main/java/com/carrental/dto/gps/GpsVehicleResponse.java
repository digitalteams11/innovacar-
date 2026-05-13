package com.carrental.dto.gps;

import com.carrental.entity.GpsDeviceStatus;
import com.carrental.entity.Vehicle;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vehicle projection enriched with GPS tracking data.
 */
@Data
@Builder
public class GpsVehicleResponse {

    private Long id;
    private String marque;
    private BigDecimal prixJour;
    private String category;
    private String plate;
    private String fuel;
    private String transmission;
    private String imageUrl;

    // GPS fields
    private String gpsDeviceId;
    private String gpsImei;
    private Double lastLatitude;
    private Double lastLongitude;
    private LocalDateTime lastGpsUpdate;
    private GpsDeviceStatus gpsStatus;
    private Double lastSpeed;
    private Boolean gpsEnabled;

    // Derived
    private Long tenantId;
    private String statusLabel;

    public static GpsVehicleResponse from(Vehicle v) {
        return GpsVehicleResponse.builder()
                .id(v.getId())
                .marque(v.getMarque())
                .prixJour(v.getPrixJour())
                .category(v.getCategory())
                .plate(v.getPlate())
                .fuel(v.getFuel())
                .transmission(v.getTransmission())
                .imageUrl(v.getImageUrl())
                .gpsDeviceId(v.getGpsDeviceId())
                .gpsImei(v.getGpsImei())
                .lastLatitude(v.getLastLatitude())
                .lastLongitude(v.getLastLongitude())
                .lastGpsUpdate(v.getLastGpsUpdate())
                .gpsStatus(v.getGpsStatus())
                .lastSpeed(v.getLastSpeed())
                .gpsEnabled(v.getGpsEnabled())
                .tenantId(v.getTenant().getId())
                .statusLabel(v.getGpsStatus() != null ? v.getGpsStatus().name() : "NO_DEVICE")
                .build();
    }
}
