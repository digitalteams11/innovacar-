package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContractDocumentDto {

    private Long id;
    private String documentType;
    private String documentName;
    private String documentUrl;
    private Boolean isPresent;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
}
