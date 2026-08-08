package com.carrental.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the bug that produced a misleading generic
 * "We could not complete this request" toast on the New Invoice modal: the
 * user-facing {@code message} for a constraint-violation conflict must never
 * contain the word "constraint" — the frontend's {@code isSafeBusinessMessage}
 * filter (axios.ts) treats any message containing that word as an internal-
 * detail leak and silently replaces it with a generic fallback, so a message
 * that says "constraint" is never actually shown to the user.
 */
class GlobalExceptionHandlerDataIntegrityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private DataIntegrityViolationException violationFor(String constraintName) {
        SQLException sqlException = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"" + constraintName + "\"", "23505");
        org.hibernate.exception.ConstraintViolationException cve =
                new org.hibernate.exception.ConstraintViolationException(
                        "could not execute statement", sqlException, constraintName);
        return new DataIntegrityViolationException("could not execute statement", cve);
    }

    @Test
    void invoiceContractUniqueConstraintReturnsASafeSpecificMessage() {
        var response = handler.handleDataIntegrity(violationFor("uq_invoice_tenant_contract_active"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body.get("errorCode")).isEqualTo("INVOICE_ALREADY_EXISTS_FOR_CONTRACT");
        String message = (String) body.get("message");
        assertThat(message).doesNotContainIgnoringCase("constraint");
        assertThat(message).isNotBlank();
    }

    @Test
    void unrecognizedConstraintFallsBackToAGenericButStillSafeMessage() {
        var response = handler.handleDataIntegrity(violationFor("some_future_unmapped_constraint"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body.get("errorCode")).isEqualTo("DATA_CONFLICT");
        String message = (String) body.get("message");
        // The message itself must stay safe (no "constraint" word) even though the
        // constraint name is still available for correlation in the `data` field.
        assertThat(message).doesNotContainIgnoringCase("constraint");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("constraint")).isEqualTo("some_future_unmapped_constraint");
    }
}
