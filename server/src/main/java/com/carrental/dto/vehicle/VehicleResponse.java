package com.carrental.dto.vehicle;

import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

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
    private Long          tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static VehicleResponse from(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .marque(v.getMarque())
                .prixJour(v.getPrixJour())
                .statut(v.getStatut())
                .tenantId(v.getTenant().getId())
                .build();
    }
}
