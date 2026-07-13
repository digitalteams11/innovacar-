package com.carrental.dto.ai;

import lombok.Data;

/** {@code apiKey} left blank/null preserves the existing encrypted key. */
@Data
public class UpdateProviderRequest {
    private String name;
    private String baseUrl;
    private String apiKey;
    private String organizationId;
    private Boolean enabled;
}
