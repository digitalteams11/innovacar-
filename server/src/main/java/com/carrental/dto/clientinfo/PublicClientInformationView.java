package com.carrental.dto.clientinfo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public (unauthenticated) view of a request — deliberately contains no
 * internal ID, tenant ID, contract ID, or admin-only data (spec section 4).
 * The token itself (in the URL) is the only identifier the client ever sees.
 */
@Data
@Builder
public class PublicClientInformationView {
    private String temporaryName;
    private String preferredLanguage;
    private String agencyName;
    private String agencyLogo;
    private LocalDateTime expiresAt;
    /** True once the client has already submitted — form should show a read-only confirmation instead. */
    private boolean alreadySubmitted;
}
