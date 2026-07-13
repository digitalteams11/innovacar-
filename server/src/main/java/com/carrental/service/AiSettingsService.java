package com.carrental.service;

import com.carrental.dto.ai.AiSettingsDto;
import com.carrental.dto.ai.UpdateAiSettingsRequest;
import com.carrental.entity.AiModel;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiSettings;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Super Admin CRUD for global AI settings — cross-cutting flags/limits only.
 * Provider/model/credential configuration is managed by {@link AiProviderService}
 * and {@link AiModelService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsRepository aiSettingsRepository;
    private final AiProviderRepository aiProviderRepository;
    private final AiModelRepository aiModelRepository;

    @Transactional(readOnly = true)
    public AiSettingsDto getSettings() {
        return toDto(loadOrCreate());
    }

    @Transactional
    public AiSettingsDto updateSettings(UpdateAiSettingsRequest request) {
        AiSettings settings = loadOrCreate();

        if (request.getGlobalEnabled() != null) settings.setGlobalEnabled(request.getGlobalEnabled());
        if (request.getFallbackProviderId() != null) settings.setFallbackProviderId(request.getFallbackProviderId());
        if (request.getFallbackModelId() != null) settings.setFallbackModelId(request.getFallbackModelId());
        if (request.getFallbackEnabled() != null) settings.setFallbackEnabled(request.getFallbackEnabled());
        if (request.getTemperature() != null) settings.setTemperature(clampTemperature(request.getTemperature()));
        if (request.getMaxOutputTokens() != null) settings.setMaxOutputTokens(clampMaxTokens(request.getMaxOutputTokens()));
        if (request.getRequestTimeoutSeconds() != null) settings.setRequestTimeoutSeconds(clampTimeout(request.getRequestTimeoutSeconds()));
        if (request.getMaxRetries() != null) settings.setMaxRetries(Math.max(0, Math.min(3, request.getMaxRetries())));
        if (request.getSystemPrompt() != null) settings.setSystemPrompt(request.getSystemPrompt());
        if (request.getEnableChat() != null) settings.setEnableChat(request.getEnableChat());
        if (request.getEnableReports() != null) settings.setEnableReports(request.getEnableReports());
        if (request.getEnableTranslations() != null) settings.setEnableTranslations(request.getEnableTranslations());
        if (request.getEnableSupportAssistant() != null) settings.setEnableSupportAssistant(request.getEnableSupportAssistant());
        if (request.getEnableGuideGenerator() != null) settings.setEnableGuideGenerator(request.getEnableGuideGenerator());
        if (request.getEnableAutomationSuggestions() != null) settings.setEnableAutomationSuggestions(request.getEnableAutomationSuggestions());
        if (request.getEnableImageGeneration() != null) settings.setEnableImageGeneration(request.getEnableImageGeneration());
        if (request.getMonthlyTokenLimit() != null) settings.setMonthlyTokenLimit(Math.max(0, request.getMonthlyTokenLimit()));
        if (request.getDailyRequestLimit() != null) settings.setDailyRequestLimit(Math.max(0, request.getDailyRequestLimit()));
        if (request.getAuditAllActions() != null) settings.setAuditAllActions(request.getAuditAllActions());

        AiSettings saved = aiSettingsRepository.save(settings);
        log.info("[AI_SETTINGS_AUDIT] action=UPDATE globalEnabled={} activeProviderId={}",
                saved.getGlobalEnabled(), saved.getActiveProviderId());
        return toDto(saved);
    }

    private AiSettings loadOrCreate() {
        return aiSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> aiSettingsRepository.save(AiSettings.builder().build()));
    }

    private Integer clampTimeout(int value) {
        return Math.max(5, Math.min(120, value));
    }

    private Integer clampMaxTokens(int value) {
        return Math.max(64, Math.min(32_768, value));
    }

    private Double clampTemperature(double value) {
        return Math.max(0.0, Math.min(2.0, value));
    }

    private AiSettingsDto toDto(AiSettings settings) {
        AiProvider activeProvider = settings.getActiveProviderId() != null
                ? aiProviderRepository.findById(settings.getActiveProviderId()).orElse(null) : null;
        AiModel activeModel = settings.getActiveModelId() != null
                ? aiModelRepository.findById(settings.getActiveModelId()).orElse(null) : null;

        return AiSettingsDto.builder()
                .globalEnabled(settings.getGlobalEnabled())
                .activeProviderId(settings.getActiveProviderId())
                .activeProviderName(activeProvider != null ? activeProvider.getName() : null)
                .activeModelId(settings.getActiveModelId())
                .activeModelName(activeModel != null ? activeModel.getModelId() : null)
                .fallbackProviderId(settings.getFallbackProviderId())
                .fallbackModelId(settings.getFallbackModelId())
                .fallbackEnabled(settings.getFallbackEnabled())
                .temperature(settings.getTemperature())
                .maxOutputTokens(settings.getMaxOutputTokens())
                .requestTimeoutSeconds(settings.getRequestTimeoutSeconds())
                .maxRetries(settings.getMaxRetries())
                .systemPrompt(settings.getSystemPrompt())
                .enableChat(settings.getEnableChat())
                .enableReports(settings.getEnableReports())
                .enableTranslations(settings.getEnableTranslations())
                .enableSupportAssistant(settings.getEnableSupportAssistant())
                .enableGuideGenerator(settings.getEnableGuideGenerator())
                .enableAutomationSuggestions(settings.getEnableAutomationSuggestions())
                .enableImageGeneration(settings.getEnableImageGeneration())
                .monthlyTokenLimit(settings.getMonthlyTokenLimit())
                .dailyRequestLimit(settings.getDailyRequestLimit())
                .auditAllActions(settings.getAuditAllActions())
                .build();
    }
}
