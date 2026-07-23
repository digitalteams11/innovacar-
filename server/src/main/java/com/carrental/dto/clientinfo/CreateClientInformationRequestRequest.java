package com.carrental.dto.clientinfo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** What the admin fills in to send a client a self-fill link (spec section 3). */
@Data
public class CreateClientInformationRequestRequest {
    @NotBlank
    private String temporaryName;
    @NotBlank
    private String phone;
    private String email;
    private String preferredLanguage;
    private Long contractId;
    /** Hours until the link expires. Defaults to 48 (spec section 8) if omitted. */
    private Integer expiresInHours;
}
