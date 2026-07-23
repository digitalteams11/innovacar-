package com.carrental.dto.clientinfo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** What the admin fills in to send a client a self-fill link. */
@Data
public class CreateClientInformationRequestRequest {
    /** Optional — prefills/validates against a pre-existing, tenant-owned client. */
    private Long clientId;
    @NotBlank
    private String temporaryName;
    /** Optional — at least one of phone/email is required (enforced in the service, not via annotation, since either may be absent). */
    private String phone;
    private String email;
    private String preferredLanguage;
    private Long contractId;
    /** Hours until the link expires. Defaults to 48 if omitted. */
    private Integer expiresInHours;
    /** Subset of {EMAIL, WHATSAPP}. Defaults to every channel with a valid destination if omitted. */
    private List<String> deliveryChannels;
}
