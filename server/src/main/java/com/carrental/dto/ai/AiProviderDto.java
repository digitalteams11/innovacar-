package com.carrental.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Never includes the raw API key — only {@link #apiKeyMasked}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderDto {
    private Long id;
    private String name;
    private String providerType;
    private String baseUrl;
    private boolean apiKeyConfigured;
    private String apiKeyMasked;
    private String organizationId;
    private Boolean enabled;
    private Boolean isActive;
    private Boolean isFallback;
    private String connectionStatus;
    private LocalDateTime lastConnectionTestAt;
    private String lastConnectionError;
    private Long lastTestLatencyMs;
    private long enabledModelCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
