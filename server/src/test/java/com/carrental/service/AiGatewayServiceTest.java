package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiModel;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiProviderType;
import com.carrental.entity.AiSettings;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.AiUsageLogRepository;
import com.carrental.service.provider.AiCallResult;
import com.carrental.service.provider.AiProviderClient;
import com.carrental.service.provider.AiProviderClientFactory;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock private AiProviderRepository aiProviderRepository;
    @Mock private AiModelRepository aiModelRepository;
    @Mock private AiSettingsRepository aiSettingsRepository;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private AiCredentialEncryptionService credentialEncryptionService;
    @Mock private AiProviderClientFactory providerClientFactory;
    @Mock private AiProviderClient providerClient;

    @InjectMocks private AiGatewayService gatewayService;

    private AiSettings settings;
    private AiProvider provider;
    private AiModel model;

    @BeforeEach
    void setUp() {
        settings = AiSettings.builder().id(1L).globalEnabled(true).build();
        provider = AiProvider.builder().id(10L).providerType(AiProviderType.GROQ).enabled(true).isActive(true).build();
        model = AiModel.builder().id(20L).providerId(10L).modelId("llama-3.3-70b-versatile").defaultModel(true).build();
    }

    @Test
    void execute_globalDisabled_throwsDisabled() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(AiSettings.builder().globalEnabled(false).build()));
        assertThatThrownBy(() -> gatewayService.execute("CHAT_ASSISTANT", "sys", "hi"))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_DISABLED");
    }

    @Test
    void execute_noActiveProvider_throwsNoActiveProvider() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gatewayService.execute("CHAT_ASSISTANT", "sys", "hi"))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_NO_ACTIVE_PROVIDER");
    }

    @Test
    void execute_providerDisabled_throwsProviderDisabled() {
        provider.setEnabled(false);
        when(aiSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(Optional.of(provider));
        assertThatThrownBy(() -> gatewayService.execute("CHAT_ASSISTANT", "sys", "hi"))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_PROVIDER_DISABLED");
    }

    @Test
    void execute_success_returnsNormalizedResponseAndRecordsUsage() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(Optional.of(provider));
        when(aiModelRepository.findByProviderIdAndDefaultModelTrue(10L)).thenReturn(Optional.of(model));
        when(providerClientFactory.forType(AiProviderType.GROQ)).thenReturn(providerClient);
        when(credentialEncryptionService.decrypt(any())).thenReturn("raw-key");
        when(providerClient.generate(any())).thenReturn(AiCallResult.ok("Hello!", 5L, 3L, 120L));

        AiExecuteResponse response = gatewayService.execute("CHAT_ASSISTANT", "sys", "hi");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("GROQ");
        assertThat(response.getContent()).isEqualTo("Hello!");
        assertThat(response.isFallbackUsed()).isFalse();
    }

    @Test
    void execute_transientFailureWithFallback_usesFallbackAndRecordsBothAttempts() {
        AiProvider fallbackProvider = AiProvider.builder().id(11L).providerType(AiProviderType.OPENAI).enabled(true).build();
        settings.setFallbackEnabled(true);
        settings.setFallbackProviderId(11L);

        when(aiSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(Optional.of(provider));
        when(aiProviderRepository.findById(11L)).thenReturn(Optional.of(fallbackProvider));
        when(aiModelRepository.findByProviderIdAndDefaultModelTrue(10L)).thenReturn(Optional.empty());
        when(aiModelRepository.findAllByProviderId(10L)).thenReturn(List.of());
        when(aiModelRepository.findByProviderIdAndDefaultModelTrue(11L)).thenReturn(Optional.empty());
        when(aiModelRepository.findAllByProviderId(11L)).thenReturn(List.of());
        when(providerClientFactory.forType(AiProviderType.GROQ)).thenReturn(providerClient);
        AiProviderClient fallbackClient = org.mockito.Mockito.mock(AiProviderClient.class);
        when(providerClientFactory.forType(AiProviderType.OPENAI)).thenReturn(fallbackClient);
        when(credentialEncryptionService.decrypt(any())).thenReturn("raw-key");
        when(providerClient.generate(any())).thenReturn(AiCallResult.fail("AI_PROVIDER_TIMEOUT", "timed out", 100L));
        when(fallbackClient.generate(any())).thenReturn(AiCallResult.ok("Fallback reply", 2L, 2L, 90L));

        AiExecuteResponse response = gatewayService.execute("CHAT_ASSISTANT", "sys", "hi");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getProvider()).isEqualTo("OPENAI");
    }

    @Test
    void execute_bothPrimaryAndFallbackFail_throwsServiceException() {
        AiProvider fallbackProvider = AiProvider.builder().id(11L).providerType(AiProviderType.OPENAI).enabled(true).build();
        settings.setFallbackEnabled(true);
        settings.setFallbackProviderId(11L);

        when(aiSettingsRepository.findAll()).thenReturn(List.of(settings));
        when(aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(Optional.of(provider));
        when(aiProviderRepository.findById(11L)).thenReturn(Optional.of(fallbackProvider));
        when(aiModelRepository.findByProviderIdAndDefaultModelTrue(10L)).thenReturn(Optional.empty());
        when(aiModelRepository.findAllByProviderId(10L)).thenReturn(List.of());
        when(aiModelRepository.findByProviderIdAndDefaultModelTrue(11L)).thenReturn(Optional.empty());
        when(aiModelRepository.findAllByProviderId(11L)).thenReturn(List.of());
        when(providerClientFactory.forType(AiProviderType.GROQ)).thenReturn(providerClient);
        AiProviderClient fallbackClient = org.mockito.Mockito.mock(AiProviderClient.class);
        when(providerClientFactory.forType(AiProviderType.OPENAI)).thenReturn(fallbackClient);
        when(credentialEncryptionService.decrypt(any())).thenReturn("raw-key");
        when(providerClient.generate(any())).thenReturn(AiCallResult.fail("AI_PROVIDER_TIMEOUT", "timed out", 100L));
        when(fallbackClient.generate(any())).thenReturn(AiCallResult.fail("AI_SERVICE_UNAVAILABLE", "down", 90L));

        assertThatThrownBy(() -> gatewayService.execute("CHAT_ASSISTANT", "sys", "hi"))
                .isInstanceOf(AiServiceException.class);
    }
}
