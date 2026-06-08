package com.carrental.dto.deposit;

import com.carrental.entity.DepositStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class DepositResponse {

    private Long id;
    private Long contractId;
    private String contractNumber;
    private Long reservationId;
    private Long clientId;
    private String clientName;

    private String depositType;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private DepositStatus status;
    private String notes;

    private String conditionsText;
    private Boolean conditionsAccepted;
    private LocalDateTime conditionsAcceptedAt;

    private LocalDateTime receivedAt;
    private LocalDateTime heldAt;
    private LocalDateTime returnedAt;

    private BigDecimal damageDeduction;
    private BigDecimal cleaningDeduction;
    private BigDecimal lateFeeDeduction;
    private BigDecimal fuelDeduction;
    private BigDecimal otherDeduction;
    private BigDecimal totalDeductions;
    private BigDecimal returnedAmount;
    private BigDecimal calculatedReturnAmount;
    private String returnNotes;

    private String fuelLevelEnd;
    private Integer mileageEnd;
    private String interiorCondition;
    private String exteriorCondition;
    private String missingItems;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DepositResponse from(com.carrental.entity.Deposit deposit) {
        if (deposit == null) return null;
        return DepositResponse.builder()
                .id(deposit.getId())
                .contractId(deposit.getContract() != null ? deposit.getContract().getId() : null)
                .contractNumber(deposit.getContract() != null ? deposit.getContract().getContractNumber() : null)
                .reservationId(deposit.getReservation() != null ? deposit.getReservation().getId() : null)
                .clientId(deposit.getClient() != null ? deposit.getClient().getId() : null)
                .clientName(deposit.getClient() != null ? deposit.getClient().getName() : null)
                .depositType(deposit.getDepositType() != null ? deposit.getDepositType().name() : null)
                .amount(deposit.getAmount())
                .currency(deposit.getCurrency())
                .reference(deposit.getReference())
                .status(deposit.getStatus())
                .notes(deposit.getNotes())
                .conditionsText(deposit.getConditionsText())
                .conditionsAccepted(deposit.getConditionsAccepted())
                .conditionsAcceptedAt(deposit.getConditionsAcceptedAt())
                .receivedAt(deposit.getReceivedAt())
                .heldAt(deposit.getHeldAt())
                .returnedAt(deposit.getReturnedAt())
                .damageDeduction(deposit.getDamageDeduction())
                .cleaningDeduction(deposit.getCleaningDeduction())
                .lateFeeDeduction(deposit.getLateFeeDeduction())
                .fuelDeduction(deposit.getFuelDeduction())
                .otherDeduction(deposit.getOtherDeduction())
                .totalDeductions(deposit.getTotalDeductions())
                .returnedAmount(deposit.getReturnedAmount())
                .calculatedReturnAmount(deposit.getCalculatedReturnAmount())
                .returnNotes(deposit.getReturnNotes())
                .fuelLevelEnd(deposit.getFuelLevelEnd())
                .mileageEnd(deposit.getMileageEnd())
                .interiorCondition(deposit.getInteriorCondition())
                .exteriorCondition(deposit.getExteriorCondition())
                .missingItems(deposit.getMissingItems())
                .createdAt(deposit.getCreatedAt())
                .updatedAt(deposit.getUpdatedAt())
                .build();
    }
}
