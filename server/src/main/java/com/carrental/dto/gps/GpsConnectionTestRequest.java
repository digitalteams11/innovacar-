package com.carrental.dto.gps;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to test GPS provider connection without saving credentials.
 */
@Data
public class GpsConnectionTestRequest {

    @NotBlank(message = "Provider is required")
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
}
