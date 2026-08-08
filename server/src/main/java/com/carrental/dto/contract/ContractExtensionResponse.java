package com.carrental.dto.contract;

import com.carrental.entity.ContractExtension;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only projection of a {@link ContractExtension} — the extension timeline entry. */
@Data
@Builder
public class ContractExtensionResponse {

    private Long id;
    private Long contractId;
    private Integer additionalDays;
    private BigDecimal additionalAmount;
    private LocalDate previousEndDate;
    private LocalDate newEndDate;
    private String reason;
    private String performedBy;
    private LocalDateTime createdAt;

    public static ContractExtensionResponse from(ContractExtension extension) {
        return ContractExtensionResponse.builder()
                .id(extension.getId())
                .contractId(extension.getContract() != null ? extension.getContract().getId() : null)
                .additionalDays(extension.getAdditionalDays())
                .additionalAmount(extension.getAdditionalAmount())
                .previousEndDate(extension.getPreviousEndDate())
                .newEndDate(extension.getNewEndDate())
                .reason(extension.getReason())
                .performedBy(extension.getPerformedBy())
                .createdAt(extension.getCreatedAt())
                .build();
    }
}
