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
    private String reservationNumber;
    private Long vehicleId;
    private String vehicleMarque;
    private String vehicleName;
    private String vehiclePlate;
    private Long clientId;
    private String clientName;
    private String clientPhone;
    private LocalDate dateStart;
    private LocalDate startDate;
    private LocalTime startTime;
    private LocalDate dateEnd;
    private LocalDate endDate;
    private LocalTime endTime;
    private BigDecimal totalPrice;
    private BigDecimal estimatedPrice;
    private BigDecimal depositAmount;
    private String pickupLocation;
    private String returnLocation;
    private String status;
    private String source;
    private String paymentStatus;
    private Long tenantId;
    private DepositResponse deposit;
    private Long contractId;
    private boolean readOnly;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .reservationNumber("RES-" + reservation.getId())
                .vehicleId(reservation.getVehicle() != null ? reservation.getVehicle().getId() : null)
                .vehicleMarque(reservation.getVehicle() != null ? reservation.getVehicle().getMarque() : null)
                .vehicleName(reservation.getVehicle() != null ? reservation.getVehicle().getMarque() : null)
                .vehiclePlate(reservation.getVehicle() != null ? reservation.getVehicle().getPlate() : null)
                .clientId(reservation.getClient() != null ? reservation.getClient().getId() : null)
                .clientName(reservation.getClient() != null ? reservation.getClient().getName() : null)
                .clientPhone(reservation.getClient() != null ? reservation.getClient().getPhone() : null)
                .dateStart(reservation.getDateStart())
                .startDate(reservation.getDateStart())
                .startTime(reservation.getStartTime())
                .dateEnd(reservation.getDateEnd())
                .endDate(reservation.getDateEnd())
                .endTime(reservation.getEndTime())
                .totalPrice(reservation.getTotalPrice())
                .estimatedPrice(reservation.getTotalPrice())
                .depositAmount(reservation.getDepositAmount())
                .pickupLocation(reservation.getPickupLocation())
                .returnLocation(reservation.getReturnLocation())
                .status(reservation.getStatus() != null ? reservation.getStatus().name() : null)
                .source(reservation.getSource() != null ? reservation.getSource().name() : "MANUAL")
                .paymentStatus(reservation.getPaymentStatus())
                .tenantId(reservation.getTenant() != null ? reservation.getTenant().getId() : null)
                .contractId(reservation.getContract() != null ? reservation.getContract().getId() : null)
                .readOnly(reservation.getStatus() == com.carrental.entity.ReservationStatus.CONVERTED_TO_CONTRACT
                        || reservation.getContract() != null)
                .build();
    }

    public static ReservationResponse from(Reservation reservation, DepositResponse deposit) {
        ReservationResponse response = from(reservation);
        response.setDeposit(deposit);
        return response;
    }
}
