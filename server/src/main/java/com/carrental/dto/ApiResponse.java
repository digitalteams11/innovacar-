package com.carrental.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized API response wrapper for all endpoints.
 * Ensures consistent response format across the application.
 *
 * @param <T> The type of data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String severity;
    private String requestId;
    private String timestamp;

    /**
     * Create a success response with data payload.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .severity("success")
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Create a success response without data payload.
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .severity("success")
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Create an error response.
     */
    public static ApiResponse<Void> error(String message, String severity, String requestId) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .severity(severity)
                .requestId(requestId)
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Create a warning response.
     */
    public static ApiResponse<Void> warning(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .severity("warning")
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Create an info response.
     */
    public static ApiResponse<Void> info(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .severity("info")
                .timestamp(Instant.now().toString())
                .build();
    }
}
