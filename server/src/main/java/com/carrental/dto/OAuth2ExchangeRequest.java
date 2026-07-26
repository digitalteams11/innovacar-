package com.carrental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/oauth2/exchange} — trades the
 * single-use code {@code OAuth2LoginSuccessHandler} redirected the browser
 * back with for the real JWT pair. Carries an opaque exchange code only,
 * never a Google token or a real access/refresh token.
 */
@Data
public class OAuth2ExchangeRequest {

    @NotBlank(message = "Exchange code is required")
    private String code;
}
