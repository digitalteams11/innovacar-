package com.carrental.dto.clientinfo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agency-side "what do we already know about this client" summary — shown
 * before sending a self-fill link, so the agency doesn't have to guess which
 * fields are worth sending the client a link for at all (see
 * ClientInformationRequestService#getClientCompleteness).
 */
@Data
@Builder
public class ClientCompletenessResponse {
    private String clientName;
    private List<String> availableFields;
    private List<String> missingFields;
}
