package com.carrental.dto.user;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for {@code PUT /api/users/me/preferences} — personal,
 * per-user UI preferences. Only non-null fields are applied.
 */
@Data
public class UpdatePreferencesRequest {

    @Pattern(regexp = "en|fr|ar", message = "language must be one of: en, fr, ar")
    private String language;

    @Pattern(regexp = "light|dark|auto", message = "themeMode must be one of: light, dark, auto")
    private String themeMode;
}
