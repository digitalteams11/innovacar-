package com.carrental.service;

import com.carrental.entity.AiProviderType;
import com.carrental.exception.AiServiceException;
import com.carrental.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCredentialEncryptionServiceTest {

    @Mock private EncryptionUtil encryptionUtil;
    @InjectMocks private AiCredentialEncryptionService service;

    @Test
    void encrypt_delegatesToEncryptionUtil() {
        when(encryptionUtil.encrypt("gsk_rawkey")).thenReturn("cipher");
        assertThat(service.encrypt("gsk_rawkey")).isEqualTo("cipher");
    }

    @Test
    void encrypt_blankInput_returnsNull() {
        assertThat(service.encrypt("")).isNull();
        assertThat(service.encrypt(null)).isNull();
    }

    @Test
    void decrypt_failure_throwsAiServiceExceptionWithKeyDecryptionFailedCode() {
        when(encryptionUtil.tryDecrypt("bad-cipher")).thenReturn(null);
        assertThatThrownBy(() -> service.decrypt("bad-cipher"))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getErrorCode())
                .isEqualTo("AI_KEY_DECRYPTION_FAILED");
    }

    @Test
    void mask_neverContainsFullRawKey() {
        String rawKey = "gsk_1234567890abcdefx92A";
        String masked = service.mask(rawKey, AiProviderType.GROQ);
        assertThat(masked).doesNotContain(rawKey);
        assertThat(masked).startsWith("gsk_").endsWith("x92A").contains("••••");
    }
}
