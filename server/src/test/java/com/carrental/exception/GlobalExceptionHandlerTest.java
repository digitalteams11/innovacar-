package com.carrental.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Regression test for the "DELETE /api/super-admin/ai/providers/{id}
     * always returns 400" bug: deleting the active provider is a state
     * conflict, not a malformed request, so it must map to 409 — matching
     * the rest of the codebase's convention for "blocked by current state"
     * errors (AdminLockoutException, VehicleConflictException, IllegalStateException).
     */
    @Test
    void aiProviderInUse_mapsToConflictNotBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleAiService(AiServiceException.providerInUse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("errorCode", "AI_PROVIDER_IN_USE");
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void aiProviderNotFound_mapsToNotFound() {
        ResponseEntity<Map<String, Object>> response = handler.handleAiService(AiServiceException.providerNotFound());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Guards the other half of the reported bug: a non-numeric path variable
     * (e.g. a stale/undefined provider id from the frontend) must produce a
     * clean JSON 400 with an explanatory message, not Spring's bare default
     * error response.
     */
    @Test
    void methodArgumentTypeMismatch_returnsCleanBadRequestWithMessage() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getValue()).thenReturn("undefined");

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "INVALID_PATH_PARAMETER");
        assertThat((String) response.getBody().get("message")).contains("id");
    }
}
