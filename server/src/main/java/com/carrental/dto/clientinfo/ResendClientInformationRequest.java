package com.carrental.dto.clientinfo;

import lombok.Data;

import java.util.List;

/** Body for POST /api/client-information-requests/{id}/resend. */
@Data
public class ResendClientInformationRequest {
    /** Subset of {EMAIL, WHATSAPP} to retry. Defaults to every available channel if omitted. */
    private List<String> channels;
}
