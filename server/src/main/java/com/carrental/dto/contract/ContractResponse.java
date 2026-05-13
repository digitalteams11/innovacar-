package com.carrental.dto.contract;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only contract projection returned by all contract endpoints.
 */
@Data
@Builder
public class ContractResponse {

    private Long           id;
    private String         contractNumber;
    private String         clientName;
    private String         vehicleMarque;
    private LocalDate      startDate;
    private LocalDate      endDate;
    private ContractStatus status;
    private BigDecimal     totalAmount;
    private Long           tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static ContractResponse from(Contract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .contractNumber(contract.getContractNumber())
                .clientName(contract.getClientName())
                .vehicleMarque(contract.getVehicleMarque())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .status(contract.getStatus())
                .totalAmount(contract.getTotalAmount())
                .tenantId(contract.getTenant().getId())
                .build();
    }
}
