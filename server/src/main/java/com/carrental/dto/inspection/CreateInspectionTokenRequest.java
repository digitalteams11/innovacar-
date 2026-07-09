package com.carrental.dto.inspection;

import com.carrental.entity.InspectionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInspectionTokenRequest {
    private Long reservationId;
    private Long contractId;

    @NotNull(message = "Inspection type is required")
    private InspectionType type;

    private String frontendUrl;
}
