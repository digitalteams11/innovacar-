package com.carrental.service;

import com.carrental.dto.ai.AiProviderDto;
import com.carrental.dto.ai.CreateProviderRequest;
import com.carrental.dto.ai.TestProviderConnectionResponse;
import com.carrental.dto.ai.UpdateProviderRequest;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiProviderType;
import com.carrental.entity.AiConnectionStatus;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.AiUsageLogRepository;
import com.carrental.service.provider.AiProviderClient;
import com.carrental.service.provider.AiProviderClientFactory;
import com.carrental.service.provider.AiTestResult;
import com.carrental.service.provider.GroqProviderClient;
import com.carrental.service.provider.OpenAiCompatibleProviderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Super Admin CRUD for {@link AiProvider}. Enforces: at most one active
 * provider, empty API key on update preserves the existing key, providers
 * that are active or referenced by usage logs cannot be hard-deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiProviderRepository aiProviderRepository;
    private final AiModelRepository aiModelRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiCredentialEncryptionService credentialEncryptionService;
    private final AiCustomEndpointValidator customEndpointValidator;
    private final AiProviderClientFactory providerClientFactory;

    @Transactional(readOnly = true)
    public List<AiProviderDto> listProviders() {
        return aiProviderRepository.findAllByIsDeletedFalse().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AiProviderDto getProvider(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public AiProviderDto createProvider(CreateProviderRequest request) {
        AiProviderType type = parseType(request.getProviderType());
        String baseUrl = resolveBaseUrl(type, request.getBaseUrl());
        if (type == AiProviderType.CUSTOM_OPENAI_COMPATIBLE) {
            customEndpointValidator.validate(baseUrl);
        }

        AiProvider provider = AiProvider.builder()
                .name(request.getName() != null ? request.getName() : type.name())
                .providerType(type)
                .baseUrl(baseUrl)
                .organizationId(request.getOrganizationId())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .isActive(false)
                .connectionStatus(AiConnectionStatus.NOT_TESTED)
                .build();

        applyApiKey(provider, request.getApiKey(), type);

        AiProvider saved = aiProviderRepository.save(provider);
        log.info("[AI_PROVIDER_AUDIT] action=CREATE providerId={} type={}", saved.getId(), type);
        return toDto(saved);
    }

    @Transactional
    public AiProviderDto updateProvider(Long id, UpdateProviderRequest request) {
        AiProvider provider = find(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            provider.setName(request.getName().trim());
        }
        if (request.getBaseUrl() != null) {
            String baseUrl = resolveBaseUrl(provider.getProviderType(), request.getBaseUrl());
            if (provider.getProviderType() == AiProviderType.CUSTOM_OPENAI_COMPATIBLE) {
                customEndpointValidator.validate(baseUrl);
            }
            provider.setBaseUrl(baseUrl);
        }
        if (request.getOrganizationId() != null) {
            provider.setOrganizationId(request.getOrganizationId());
        }
        if (request.getEnabled() != null) {
            provider.setEnabled(request.getEnabled());
        }
        // Empty/blank apiKey preserves the existing encrypted key — never overwritten.
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            applyApiKey(provider, request.getApiKey(), provider.getProviderType());
        }

        AiProvider saved = aiProviderRepository.save(provider);
        log.info("[AI_PROVIDER_AUDIT] action=UPDATE providerId={}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteProvider(Long id) {
        AiProvider provider = find(id);
        if (Boolean.TRUE.equals(provider.getIsActive())) {
            throw AiServiceException.providerInUse();
        }
        if (aiUsageLogRepository.countByProviderId(id) > 0) {
            // Prefer soft-delete when referenced by usage logs — history must survive.
            provider.setIsDeleted(true);
            provider.setEnabled(false);
            aiProviderRepository.save(provider);
            log.info("[AI_PROVIDER_AUDIT] action=SOFT_DELETE providerId={}", id);
            return;
        }
        aiProviderRepository.delete(provider);
        log.info("[AI_PROVIDER_AUDIT] action=DELETE providerId={}", id);
    }

    @Transactional
    public AiProviderDto activate(Long id) {
        AiProvider provider = find(id);
        aiProviderRepository.findAllByIsDeletedFalse().forEach(p -> {
            if (Boolean.TRUE.equals(p.getIsActive())) {
                p.setIsActive(false);
                aiProviderRepository.save(p);
            }
        });
        provider.setIsActive(true);
        AiProvider saved = aiProviderRepository.save(provider);

        var settings = aiSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> aiSettingsRepository.save(com.carrental.entity.AiSettings.builder().build()));
        settings.setActiveProviderId(saved.getId());
        aiSettingsRepository.save(settings);

        log.info("[AI_PROVIDER_AUDIT] action=ACTIVATE providerId={}", id);
        return toDto(saved);
    }

    @Transactional
    public AiProviderDto disable(Long id) {
        AiProvider provider = find(id);
        provider.setEnabled(false);
        if (Boolean.TRUE.equals(provider.getIsActive())) {
            provider.setIsActive(false);
        }
        AiProvider saved = aiProviderRepository.save(provider);
        log.info("[AI_PROVIDER_AUDIT] action=DISABLE providerId={}", id);
        return toDto(saved);
    }

    @Transactional
    public TestProviderConnectionResponse testConnection(Long id, String tempApiKey) {
        AiProvider provider = find(id);
        String apiKey;
        if (tempApiKey != null && !tempApiKey.isBlank()) {
            apiKey = tempApiKey.trim();
        } else {
            apiKey = credentialEncryptionService.decrypt(provider.getApiKeyEncrypted());
        }

        AiProviderClient client = providerClientFactory.forType(provider.getProviderType());
        String model = aiModelRepository.findByProviderIdAndDefaultModelTrue(id)
                .map(com.carrental.entity.AiModel::getModelId)
                .orElse(defaultModelFor(provider.getProviderType()));

        AiTestResult result = client.testConnection(apiKey, provider.getBaseUrl(), model, 15);

        provider.setLastConnectionTestAt(LocalDateTime.now());
        provider.setConnectionStatus(result.success() ? AiConnectionStatus.CONNECTED : AiConnectionStatus.FAILED);
        provider.setLastConnectionError(result.success() ? null : result.message());
        provider.setLastTestLatencyMs(result.latencyMs());
        aiProviderRepository.save(provider);

        return TestProviderConnectionResponse.builder()
                .success(result.success())
                .provider(provider.getProviderType().name())
                .model(result.model())
                .latencyMs(result.latencyMs())
                .message(result.success() ? "Connection successful" : result.message())
                .errorCode(result.errorCode())
                .build();
    }

    private void applyApiKey(AiProvider provider, String rawKey, AiProviderType type) {
        provider.setApiKeyEncrypted(credentialEncryptionService.encrypt(rawKey));
        provider.setApiKeyMaskedHint(credentialEncryptionService.mask(rawKey, type));
    }

    private String resolveBaseUrl(AiProviderType type, String requestedBaseUrl) {
        if (requestedBaseUrl != null && !requestedBaseUrl.isBlank()) {
            return requestedBaseUrl.trim();
        }
        return switch (type) {
            case GROQ -> GroqProviderClient.DEFAULT_BASE_URL;
            case OPENAI -> OpenAiCompatibleProviderClient.OPENAI_DEFAULT_BASE_URL;
            case OPENROUTER -> OpenAiCompatibleProviderClient.OPENROUTER_DEFAULT_BASE_URL;
            case GEMINI, CUSTOM_OPENAI_COMPATIBLE -> null;
        };
    }

    private String defaultModelFor(AiProviderType type) {
        return switch (type) {
            case GROQ -> GroqProviderClient.DEFAULT_MODEL;
            case OPENAI, OPENROUTER, CUSTOM_OPENAI_COMPATIBLE -> OpenAiCompatibleProviderClient.DEFAULT_MODEL;
            case GEMINI -> "gemini-1.5-flash";
        };
    }

    private AiProviderType parseType(String raw) {
        try {
            return AiProviderType.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new AiServiceException("Unknown provider type: " + raw, "AI_INVALID_PROVIDER_TYPE");
        }
    }

    private AiProvider find(Long id) {
        return aiProviderRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(AiServiceException::providerNotFound);
    }

    private AiProviderDto toDto(AiProvider provider) {
        boolean configured = credentialEncryptionService.isConfigured(provider.getApiKeyEncrypted());
        return AiProviderDto.builder()
                .id(provider.getId())
                .name(provider.getName())
                .providerType(provider.getProviderType().name())
                .baseUrl(provider.getBaseUrl())
                .apiKeyConfigured(configured)
                .apiKeyMasked(provider.getApiKeyMaskedHint() != null ? provider.getApiKeyMaskedHint() : "")
                .organizationId(provider.getOrganizationId())
                .enabled(provider.getEnabled())
                .isActive(provider.getIsActive())
                .isFallback(provider.getIsFallback())
                .connectionStatus(provider.getConnectionStatus().name())
                .lastConnectionTestAt(provider.getLastConnectionTestAt())
                .lastConnectionError(provider.getLastConnectionError())
                .lastTestLatencyMs(provider.getLastTestLatencyMs())
                .enabledModelCount(aiModelRepository.findAllByProviderId(provider.getId()).stream()
                        .filter(m -> Boolean.TRUE.equals(m.getEnabled())).count())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build();
    }
}
