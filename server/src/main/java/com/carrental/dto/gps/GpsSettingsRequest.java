package com.carrental.dto.gps;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to save or update GPS provider configuration.
 */
@Data
public class GpsSettingsRequest {

    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "^(IOPGPS|TRACCAR|WIALON|GPSWOX|CUSTOM)$", message = "Invalid provider")
    @Size(max = 30)
    private String provider;

    @Size(max = 200)
    private String appId;

    @Size(max = 500)
    private String apiKey;

    @Size(max = 300)
    private String baseUrl;

    @Size(max = 100)
    private String deviceGroupId;

    @Size(max = 300)
    private String webhookUrl;

    private Boolean enabled;
}
