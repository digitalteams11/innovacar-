package com.carrental.dto.superadmin;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the bean-validation constraints Spring MVC enforces on TestSendRequest before the controller ever runs. */
class TestSendRequestValidationTest {

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

    private TestSendRecipient recipient(String email) {
        TestSendRecipient r = new TestSendRecipient();
        r.setEmail(email);
        r.setSourceType("EXTERNAL");
        return r;
    }

    @Test
    void emptyRecipients_isRejected() {
        TestSendRequest request = new TestSendRequest();
        request.setRecipients(List.of());

        Set<ConstraintViolation<TestSendRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void moreThanTenRecipients_isRejected() {
        List<TestSendRecipient> recipients = new ArrayList<>();
        for (int i = 0; i < 11; i++) recipients.add(recipient("user" + i + "@example.com"));
        TestSendRequest request = new TestSendRequest();
        request.setRecipients(recipients);

        Set<ConstraintViolation<TestSendRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void tenRecipients_isAccepted() {
        List<TestSendRecipient> recipients = new ArrayList<>();
        for (int i = 0; i < 10; i++) recipients.add(recipient("user" + i + "@example.com"));
        TestSendRequest request = new TestSendRequest();
        request.setRecipients(recipients);

        Set<ConstraintViolation<TestSendRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void invalidEmailFormat_isRejected() {
        TestSendRequest request = new TestSendRequest();
        request.setRecipients(List.of(recipient("not-an-email")));

        Set<ConstraintViolation<TestSendRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void blankEmail_isRejected() {
        TestSendRequest request = new TestSendRequest();
        request.setRecipients(List.of(recipient("  ")));

        Set<ConstraintViolation<TestSendRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
