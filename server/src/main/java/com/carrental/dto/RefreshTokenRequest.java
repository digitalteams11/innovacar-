package com.carrental.dto;

import lombok.Data;

/**
 * Request body for token refresh.
 */
@Data
public class RefreshTokenRequest {

    private String refreshToken;
}
