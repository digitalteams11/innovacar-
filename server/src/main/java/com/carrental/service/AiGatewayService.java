package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiModel;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiSettings;
import com.carrental.entity.AiUsageLog;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.AiUsageLogRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.provider.AiCallRequest;
import com.carrental.service.provider.AiCallResult;
import com.carrental.service.provider.AiProviderClient;
import com.carrental.service.provider.AiProviderClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The single entry point every AI-consuming module must call. No other
 * service may call a provider client directly — this centralizes provider
 * selection, credential handling, usage logging, and fallback so business
 * logic never has to know which provider is active.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    private static final Set<String> TRANSIENT_ERROR_CODES = Set.of(
            "AI_PROVIDER_TIMEOUT", "AI_SERVICE_UNAVAILABLE", "AI_PROVIDER_UNREACHABLE",
            "AI_QUOTA_EXCEEDED", "AI_NETWORK_ERROR");

    private final AiProviderRepository aiProviderRepository;
    private final AiModelRepository aiModelRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiCredentialEncryptionService credentialEncryptionService;
    private final AiProviderClientFactory providerClientFactory;

    @Transactional
    public AiExecuteResponse execute(String automationCode, String systemInstruction, String userPrompt) {
        AiSettings settings = aiSettingsRepository.findAll().stream().findFirst().orElse(null);
        if (settings == null || !Boolean.TRUE.equals(settings.getGlobalEnabled())) {
            throw AiServiceException.disabled();
        }

        AiProvider provider = aiProviderRepository.findByIsActiveTrueAndIsDeletedFalse()
                .orElseThrow(AiServiceException::noActiveProvider);
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw AiServiceException.providerDisabled();
        }

        AiModel model = resolveModel(provider.getId());
        String requestId = UUID.randomUUID().toString();
        long callStart = System.currentTimeMillis();

        AiCallResult result = attempt(provider, model, systemInstruction, userPrompt, settings);
        boolean fallbackUsed = false;

        if (!result.success() && Boolean.TRUE.equals(settings.getFallbackEnabled())
                && TRANSIENT_ERROR_CODES.contains(result.errorCode())
                && settings.getFallbackProviderId() != null) {
            Optional<AiProvider> fallbackProviderOpt = aiProviderRepository.findById(settings.getFallbackProviderId());
            if (fallbackProviderOpt.isPresent() && Boolean.TRUE.equals(fallbackProviderOpt.get().getEnabled())
                    && !Boolean.TRUE.equals(fallbackProviderOpt.get().getIsDeleted())) {
                AiProvider fallbackProvider = fallbackProviderOpt.get();
                AiModel fallbackModel = settings.getFallbackModelId() != null
                        ? aiModelRepository.findById(settings.getFallbackModelId()).orElse(null)
                        : resolveModel(fallbackProvider.getId());
                log.warn("[AI_GATEWAY] Primary provider id={} failed with {} — attempting fallback provider id={}",
                        provider.getId(), result.errorCode(), fallbackProvider.getId());
                AiCallResult fallbackResult = attempt(fallbackProvider, fallbackModel, systemInstruction, userPrompt, settings);
                if (fallbackResult.success()) {
                    provider = fallbackProvider;
                    model = fallbackModel;
                    result = fallbackResult;
                    fallbackUsed = true;
                }
            }
        }

        if (!result.success()) {
            throw new AiServiceException(
                    result.message() != null ? result.message() : "AI service is unavailable. Please try again later.",
                    result.errorCode() != null ? result.errorCode() : "AI_SERVICE_UNAVAILABLE");
        }

        return AiExecuteResponse.builder()
                .success(true)
                .requestId(requestId)
                .provider(provider.getProviderType().name())
                .model(model != null ? model.getModelId() : null)
                .content(result.text())
                .inputTokens(result.inputTokens())
                .outputTokens(result.outputTokens())
                .totalTokens((result.inputTokens() != null ? result.inputTokens() : 0)
                        + (result.outputTokens() != null ? result.outputTokens() : 0))
                .latencyMs(System.currentTimeMillis() - callStart)
                .fallbackUsed(fallbackUsed)
                .build();
    }

    private AiCallResult attempt(AiProvider provider, AiModel model, String systemInstruction, String userPrompt, AiSettings settings) {
        AiProviderClient client = providerClientFactory.forType(provider.getProviderType());
        String apiKey = credentialEncryptionService.decrypt(provider.getApiKeyEncrypted());

        AiCallRequest request = new AiCallRequest(
                apiKey,
                provider.getBaseUrl(),
                model != null ? model.getModelId() : null,
                systemInstruction,
                userPrompt,
                settings.getRequestTimeoutSeconds(),
                clampMaxTokens(settings.getMaxOutputTokens(), model),
                settings.getTemperature()
        );

        AiCallResult result = client.generate(request);
        recordUsage(provider, model, result);
        return result;
    }

    /**
     * The admin-configured max_tokens is a single global value, but some models
     * (esp. Groq's smaller/guard models) have a context window far below it —
     * providers reject max_tokens >= context_window with an HTTP 400. Clamp
     * against the model's known context window before sending so the common
     * case never round-trips a failed request; {@link com.carrental.service.provider.GroqProviderClient}
     * still retries reactively off the provider's error text for models whose
     * window isn't known here.
     */
    private Integer clampMaxTokens(Integer configured, AiModel model) {
        int requested = configured != null ? configured : 4096;
        if (model == null || model.getContextWindow() == null || model.getContextWindow() <= 0) {
            return requested;
        }
        long cap = Math.max(1, model.getContextWindow() - 1);
        return (int) Math.min(requested, cap);
    }

    private AiModel resolveModel(Long providerId) {
        return aiModelRepository.findByProviderIdAndDefaultModelTrue(providerId)
                .or(() -> aiModelRepository.findAllByProviderId(providerId).stream().filter(m -> Boolean.TRUE.equals(m.getEnabled())).findFirst())
                .orElse(null);
    }

    private void recordUsage(AiProvider provider, AiModel model, AiCallResult result) {
        try {
            Long userId = currentUserId();
            Long tenantId = TenantContext.getCurrentTenantId();
            aiUsageLogRepository.save(AiUsageLog.builder()
                    .providerId(provider.getId())
                    .modelId(model != null ? model.getId() : null)
                    .agencyId(tenantId)
                    .userId(userId)
                    .status(result.success() ? "SUCCESS" : "FAILED")
                    .inputTokens(result.inputTokens())
                    .outputTokens(result.outputTokens())
                    .totalTokens((result.inputTokens() != null ? result.inputTokens() : 0)
                            + (result.outputTokens() != null ? result.outputTokens() : 0))
                    .latencyMs(result.latencyMs())
                    .errorCode(result.errorCode())
                    .build());
        } catch (Exception ex) {
            log.error("Unable to persist AI usage log for provider id={}", provider.getId(), ex);
        }
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.carrental.entity.User user) {
            return user.getId();
        }
        return null;
    }
}
