package com.carrental.legal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AcceptDocumentRequest {

    @NotNull(message = "documentVersionId is required")
    private Long documentVersionId;

    /** SIGNUP, SETTINGS_PAGE, FORCED_REACCEPTANCE, ... — defaults to SETTINGS_PAGE if omitted. */
    private String captureContext;
}
