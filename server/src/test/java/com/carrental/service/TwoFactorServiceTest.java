package com.carrental.service;

import com.carrental.repository.UserRepository;
import com.carrental.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DeviceSecurityService deviceSecurityService;

    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorService = new TwoFactorService(
                encryptionUtil,
                userRepository,
                passwordEncoder,
                deviceSecurityService
        );
    }

    @Test
    void generatesRfcCompatibleSixDigitTotp() {
        String code = twoFactorService.generateCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1);

        assertThat(code).isEqualTo("287082");
    }

    @Test
    void generatedSecretsHaveAuthenticatorCompatibleEntropy() {
        String secret = twoFactorService.generateSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    }
}
