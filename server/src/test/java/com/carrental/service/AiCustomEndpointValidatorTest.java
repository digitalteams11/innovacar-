package com.carrental.service;

import com.carrental.exception.AiServiceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class AiCustomEndpointValidatorTest {

    @Test
    void validate_httpLocalhost_rejected() throws Exception {
        AiCustomEndpointValidator validator = new AiCustomEndpointValidator();
        assertThatThrownBy(() -> validator.validate("http://localhost:8080/v1"))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_INVALID_CUSTOM_ENDPOINT");
    }

    @Test
    void validate_privateNetworkAddress_rejected() {
        AiCustomEndpointValidator validator = new AiCustomEndpointValidator();
        assertThatThrownBy(() -> validator.validate("https://192.168.1.5/v1"))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validate_missingHost_rejected() {
        AiCustomEndpointValidator validator = new AiCustomEndpointValidator();
        assertThatThrownBy(() -> validator.validate("https:///v1"))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validate_devModeAllowsLocalhost() throws Exception {
        AiCustomEndpointValidator validator = new AiCustomEndpointValidator();
        Field devMode = AiCustomEndpointValidator.class.getDeclaredField("devMode");
        devMode.setAccessible(true);
        devMode.set(validator, true);

        assertThatCode(() -> validator.validate("http://localhost:8080/v1")).doesNotThrowAnyException();
    }
}
