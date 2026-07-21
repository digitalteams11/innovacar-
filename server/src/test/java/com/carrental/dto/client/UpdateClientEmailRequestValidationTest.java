package com.carrental.dto.client;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the bean-validation constraints on UpdateClientEmailRequest, mirroring the controller's @Valid enforcement. */
class UpdateClientEmailRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void blankEmail_isRejected() {
        UpdateClientEmailRequest request = new UpdateClientEmailRequest();
        request.setEmail("  ");

        Set<ConstraintViolation<UpdateClientEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void invalidFormat_isRejected() {
        UpdateClientEmailRequest request = new UpdateClientEmailRequest();
        request.setEmail("not-an-email");

        Set<ConstraintViolation<UpdateClientEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void tooLong_isRejected() {
        UpdateClientEmailRequest request = new UpdateClientEmailRequest();
        request.setEmail("a".repeat(150) + "@example.com");

        Set<ConstraintViolation<UpdateClientEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void validEmail_isAccepted() {
        UpdateClientEmailRequest request = new UpdateClientEmailRequest();
        request.setEmail("client@example.com");

        Set<ConstraintViolation<UpdateClientEmailRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
