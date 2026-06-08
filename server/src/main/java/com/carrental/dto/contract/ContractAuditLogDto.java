package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContractAuditLogDto {

    private Long id;
    private String action;
    private String description;
    private String performedBy;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
