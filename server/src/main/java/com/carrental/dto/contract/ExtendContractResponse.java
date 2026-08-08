package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

/** Response for {@code POST /api/contracts/{id}/extend} — the updated contract plus the new extension line item. */
@Data
@Builder
public class ExtendContractResponse {
    private ContractResponse contract;
    private ContractExtensionResponse extension;
}
