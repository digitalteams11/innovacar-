package com.carrental.service;

import com.carrental.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Canonical password policy: 8+ characters, uppercase, lowercase, digit,
 * special character. The minimum was previously inconsistent across the
 * codebase (10 in several frontend forms, 12 for Super Admin bootstrap) —
 * this locks in the single backend source of truth at 8.
 */
class PasswordPolicyServiceTest {

    private final PasswordHistoryRepository historyRepository = mock(PasswordHistoryRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordPolicyService service = new PasswordPolicyService(historyRepository, passwordEncoder);

    @Test
    void sevenCharacterPassword_isRejected() {
        assertThatThrownBy(() -> service.validate("Aa1!abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WEAK_PASSWORD");
    }

    @Test
    void eightCharacterPassword_isAccepted() {
        assertThatCode(() -> service.validate("Aa1!abcd")).doesNotThrowAnyException();
    }

    @Test
    void missingUppercase_isRejected() {
        assertThatThrownBy(() -> service.validate("aa1!abcd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingLowercase_isRejected() {
        assertThatThrownBy(() -> service.validate("AA1!ABCD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingDigit_isRejected() {
        assertThatThrownBy(() -> service.validate("Aa!abcde")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingSpecialCharacter_isRejected() {
        assertThatThrownBy(() -> service.validate("Aa1abcde")).isInstanceOf(IllegalArgumentException.class);
    }
}
