package com.carrental.dto.clientinfo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveClientInformationRequest {
    public enum Action { LINK_EXISTING, CREATE_NEW }

    @NotNull
    private Action action;

    /** Required when action == LINK_EXISTING. */
    private Long existingClientId;
}
