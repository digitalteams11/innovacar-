package com.carrental.dto.superadmin.datareset;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Shared body for both the preview and execute endpoints. The security
 * fields (currentPassword/twoFactorCode/confirmationCode) are only required
 * — and only validated — on execute.
 */
@Data
public class DataResetRequest {

    @NotNull
    private DataResetScope scope;

    @NotNull
    private DataResetAction action;

    private Long agencyId;

    private Long clientId;

    /** Allows DELETE_CLIENT to proceed even if the client has non-final contracts. */
    private boolean force;

    private String currentPassword;

    private String twoFactorCode;

    private String confirmationCode;
}
