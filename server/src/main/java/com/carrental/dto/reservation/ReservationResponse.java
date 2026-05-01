package com.carrental.dto.reservation;

import com.carrental.entity.Reservation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only reservation projection.
 */
@Data
@Builder
public class ReservationResponse {

    private Long id;
    private Long vehicleId;
    private String vehicleMarque;
    private LocalDate dateStart;
    private LocalDate dateEnd;
    private BigDecimal totalPrice;
    private Long tenantId;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .vehicleId(reservation.getVehicle().getId())
                .vehicleMarque(reservation.getVehicle().getMarque())
                .dateStart(reservation.getDateStart())
                .dateEnd(reservation.getDateEnd())
                .totalPrice(reservation.getTotalPrice())
                .tenantId(reservation.getTenant().getId())
                .build();
    }
}
