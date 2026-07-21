package com.carrental.dto.superadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** One recipient in a Super Admin test-send request. */
@Data
public class TestSendRecipient {
    @NotBlank
    @Email
    private String email;

    /** AGENCY | USER | EXTERNAL — informational only, never trusted for authorization. */
    @NotBlank
    private String sourceType;

    /** Set for AGENCY/USER recipients picked from the directory; null for a manually-typed EXTERNAL address. */
    private Long sourceId;
}
