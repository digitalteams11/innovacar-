package com.carrental.dto.ai;

import lombok.Data;

@Data
public class CreateProviderRequest {
    private String name;
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String organizationId;
    private Boolean enabled;
}
