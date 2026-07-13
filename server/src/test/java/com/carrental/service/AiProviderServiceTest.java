package com.carrental.service;

import com.carrental.dto.ai.CreateProviderRequest;
import com.carrental.dto.ai.UpdateProviderRequest;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiProviderType;
import com.carrental.entity.AiSettings;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.AiUsageLogRepository;
import com.carrental.service.provider.AiProviderClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProviderServiceTest {

    @Mock private AiProviderRepository aiProviderRepository;
    @Mock private AiModelRepository aiModelRepository;
    @Mock private AiSettingsRepository aiSettingsRepository;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private AiCredentialEncryptionService credentialEncryptionService;
    @Mock private AiCustomEndpointValidator customEndpointValidator;
    @Mock private AiProviderClientFactory providerClientFactory;

    @InjectMocks private AiProviderService providerService;

    @Test
    void updateProvider_emptyApiKey_preservesExistingEncryptedKey() {
        AiProvider existing = AiProvider.builder()
                .id(5L).providerType(AiProviderType.GROQ)
                .apiKeyEncrypted("existing-cipher").apiKeyMaskedHint("gsk_••••••••••••abcd")
                .build();
        when(aiProviderRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(aiModelRepository.findAllByProviderId(5L)).thenReturn(List.of());
        when(aiProviderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProviderRequest request = new UpdateProviderRequest();
        request.setApiKey(""); // blank — must not overwrite

        providerService.updateProvider(5L, request);

        assertThat(existing.getApiKeyEncrypted()).isEqualTo("existing-cipher");
        assertThat(existing.getApiKeyMaskedHint()).isEqualTo("gsk_••••••••••••abcd");
        verify(credentialEncryptionService, never()).encrypt(any());
    }

    @Test
    void activate_flipsOffAllOtherActiveProvidersAndSetsSettings() {
        AiProvider toActivate = AiProvider.builder().id(2L).providerType(AiProviderType.GROQ).isActive(false).isDeleted(false).build();
        AiProvider currentlyActive = AiProvider.builder().id(1L).providerType(AiProviderType.GEMINI).isActive(true).isDeleted(false).build();

        when(aiProviderRepository.findById(2L)).thenReturn(Optional.of(toActivate));
        when(aiProviderRepository.findAllByIsDeletedFalse()).thenReturn(List.of(currentlyActive, toActivate));
        when(aiProviderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiSettingsRepository.findAll()).thenReturn(List.of(AiSettings.builder().id(1L).build()));
        when(aiModelRepository.findAllByProviderId(any())).thenReturn(List.of());

        providerService.activate(2L);

        assertThat(currentlyActive.getIsActive()).isFalse();
        assertThat(toActivate.getIsActive()).isTrue();
        verify(aiSettingsRepository, atLeastOnce()).save(any());
    }

    @Test
    void deleteProvider_active_throwsProviderInUse() {
        AiProvider active = AiProvider.builder().id(3L).providerType(AiProviderType.GROQ).isActive(true).isDeleted(false).build();
        when(aiProviderRepository.findById(3L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> providerService.deleteProvider(3L))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_PROVIDER_IN_USE");
    }

    @Test
    void deleteProvider_referencedByUsageLogs_softDeletesInsteadOfHardDelete() {
        AiProvider inactive = AiProvider.builder().id(4L).providerType(AiProviderType.GROQ).isActive(false).isDeleted(false).build();
        when(aiProviderRepository.findById(4L)).thenReturn(Optional.of(inactive));
        when(aiUsageLogRepository.countByProviderId(4L)).thenReturn(5L);
        when(aiProviderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        providerService.deleteProvider(4L);

        assertThat(inactive.getIsDeleted()).isTrue();
        verify(aiProviderRepository, never()).delete(any());
    }

    @Test
    void createProvider_customEndpoint_isValidatedForSsrf() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("CUSTOM_OPENAI_COMPATIBLE");
        request.setBaseUrl("http://localhost:8080");
        request.setApiKey("some-key");

        doThrow(AiServiceException.invalidCustomEndpoint("localhost/loopback endpoints are not allowed."))
                .when(customEndpointValidator).validate("http://localhost:8080");

        assertThatThrownBy(() -> providerService.createProvider(request))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_INVALID_CUSTOM_ENDPOINT");
    }
}
