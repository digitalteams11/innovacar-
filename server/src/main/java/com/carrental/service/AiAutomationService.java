package com.carrental.service;

import com.carrental.dto.ai.AiAutomationDto;
import com.carrental.dto.ai.UpdateAutomationRequest;
import com.carrental.entity.AiAutomation;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiAutomationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for the {@link AiAutomation} catalog. Note: only the automation code
 * {@code CHAT_ASSISTANT} is triggered by real backend logic today (see
 * {@code AiAssistantService}). All other seeded codes are configurable but
 * inert — {@link AiAutomation#getWired()} tells the UI which is which.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAutomationService {

    private final AiAutomationRepository aiAutomationRepository;

    @Transactional(readOnly = true)
    public List<AiAutomationDto> listAutomations() {
        return aiAutomationRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AiAutomationDto getAutomation(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public AiAutomationDto updateAutomation(Long id, UpdateAutomationRequest request) {
        AiAutomation automation = find(id);
        if (request.getName() != null) automation.setName(request.getName());
        if (request.getDescription() != null) automation.setDescription(request.getDescription());
        if (request.getEnabled() != null) automation.setEnabled(request.getEnabled());
        if (request.getProviderId() != null) automation.setProviderId(request.getProviderId());
        if (request.getModelId() != null) automation.setModelId(request.getModelId());
        if (request.getSystemPrompt() != null) automation.setSystemPrompt(request.getSystemPrompt());
        if (request.getUserPromptTemplate() != null) automation.setUserPromptTemplate(request.getUserPromptTemplate());
        if (request.getTemperature() != null) automation.setTemperature(request.getTemperature());
        if (request.getMaxOutputTokens() != null) automation.setMaxOutputTokens(request.getMaxOutputTokens());
        if (request.getAllowedRoles() != null) automation.setAllowedRoles(request.getAllowedRoles());
        return toDto(aiAutomationRepository.save(automation));
    }

    @Transactional
    public AiAutomationDto setEnabled(Long id, boolean enabled) {
        AiAutomation automation = find(id);
        automation.setEnabled(enabled);
        return toDto(aiAutomationRepository.save(automation));
    }

    @Transactional
    public void deleteAutomation(Long id) {
        AiAutomation automation = find(id);
        if (Boolean.TRUE.equals(automation.getWired())) {
            throw new AiServiceException(
                    "Cannot delete an automation wired to a live feature.", "AI_AUTOMATION_WIRED_DELETE_DENIED");
        }
        aiAutomationRepository.delete(automation);
    }

    private AiAutomation find(Long id) {
        return aiAutomationRepository.findById(id).orElseThrow(AiServiceException::automationNotFound);
    }

    private AiAutomationDto toDto(AiAutomation automation) {
        return AiAutomationDto.builder()
                .id(automation.getId())
                .code(automation.getCode())
                .name(automation.getName())
                .description(automation.getDescription())
                .featureType(automation.getFeatureType())
                .enabled(automation.getEnabled())
                .wired(automation.getWired())
                .providerId(automation.getProviderId())
                .modelId(automation.getModelId())
                .systemPrompt(automation.getSystemPrompt())
                .userPromptTemplate(automation.getUserPromptTemplate())
                .temperature(automation.getTemperature())
                .maxOutputTokens(automation.getMaxOutputTokens())
                .allowedRoles(automation.getAllowedRoles())
                .build();
    }
}
