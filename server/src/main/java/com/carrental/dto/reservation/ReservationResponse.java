package com.carrental.dto.reservation;

import com.carrental.dto.deposit.DepositResponse;
import com.carrental.entity.Reservation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read-only reservation projection.
 */
@Data
@Builder
public class ReservationResponse {

    private Long id;
    private Long vehicleId;
    private String vehicleMarque;
    private Long clientId;
    private String clientName;
    private LocalDate dateStart;
    private LocalTime startTime;
    private LocalDate dateEnd;
    private LocalTime endTime;
    private BigDecimal totalPrice;
    private BigDecimal depositAmount;
    private String pickupLocation;
    private String returnLocation;
    private String status;
    private String paymentStatus;
    private Long tenantId;
    private DepositResponse deposit;
    private Long contractId;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .vehicleId(reservation.getVehicle() != null ? reservation.getVehicle().getId() : null)
                .vehicleMarque(reservation.getVehicle() != null ? reservation.getVehicle().getMarque() : null)
                .clientId(reservation.getClient() != null ? reservation.getClient().getId() : null)
                .clientName(reservation.getClient() != null ? reservation.getClient().getName() : null)
                .dateStart(reservation.getDateStart())
                .startTime(reservation.getStartTime())
                .dateEnd(reservation.getDateEnd())
                .endTime(reservation.getEndTime())
                .totalPrice(reservation.getTotalPrice())
                .depositAmount(reservation.getDepositAmount())
                .pickupLocation(reservation.getPickupLocation())
                .returnLocation(reservation.getReturnLocation())
                .status(reservation.getStatus() != null ? reservation.getStatus().name() : null)
                .paymentStatus(reservation.getPaymentStatus())
                .tenantId(reservation.getTenant().getId())
                .contractId(reservation.getContract() != null ? reservation.getContract().getId() : null)
                .build();
    }

    public static ReservationResponse from(Reservation reservation, DepositResponse deposit) {
        ReservationResponse response = from(reservation);
        response.setDeposit(deposit);
        return response;
    }
}
