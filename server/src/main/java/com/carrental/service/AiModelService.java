package com.carrental.service;

import com.carrental.dto.ai.AddModelRequest;
import com.carrental.dto.ai.AiModelDto;
import com.carrental.entity.AiModel;
import com.carrental.entity.AiModelSource;
import com.carrental.entity.AiProvider;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.service.provider.AiModelInfo;
import com.carrental.service.provider.AiProviderClient;
import com.carrental.service.provider.AiProviderClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository aiModelRepository;
    private final AiProviderRepository aiProviderRepository;
    private final AiCredentialEncryptionService credentialEncryptionService;
    private final AiProviderClientFactory providerClientFactory;

    @Transactional(readOnly = true)
    public List<AiModelDto> listModels(Long providerId) {
        return aiModelRepository.findAllByProviderId(providerId).stream().map(this::toDto).toList();
    }

    @Transactional
    public AiModelDto addModel(Long providerId, AddModelRequest request) {
        AiProvider provider = findProvider(providerId);
        if (aiModelRepository.findByProviderIdAndModelId(providerId, request.getModelId()).isPresent()) {
            throw new AiServiceException("Model already exists for this provider.", "AI_MODEL_ALREADY_EXISTS");
        }
        AiModel model = AiModel.builder()
                .providerId(provider.getId())
                .modelId(request.getModelId())
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getModelId())
                .contextWindow(request.getContextWindow())
                .inputPricePerMillion(request.getInputPricePerMillion())
                .outputPricePerMillion(request.getOutputPricePerMillion())
                .supportsStreaming(Boolean.TRUE.equals(request.getSupportsStreaming()))
                .supportsJsonMode(Boolean.TRUE.equals(request.getSupportsJsonMode()))
                .supportsToolCalling(Boolean.TRUE.equals(request.getSupportsToolCalling()))
                .source(AiModelSource.MANUAL)
                .build();
        boolean firstModel = aiModelRepository.countByProviderId(providerId) == 0;
        if (firstModel) {
            model.setDefaultModel(true);
        }
        return toDto(aiModelRepository.save(model));
    }

    @Transactional
    public List<AiModelDto> syncModels(Long providerId) {
        AiProvider provider = findProvider(providerId);
        AiProviderClient client = providerClientFactory.forType(provider.getProviderType());
        String apiKey = credentialEncryptionService.decrypt(provider.getApiKeyEncrypted());
        List<AiModelInfo> fetched = client.fetchAvailableModels(apiKey, provider.getBaseUrl());

        for (AiModelInfo info : fetched) {
            aiModelRepository.findByProviderIdAndModelId(providerId, info.modelId())
                    .ifPresentOrElse(existing -> {
                        existing.setContextWindow(info.contextWindow());
                        aiModelRepository.save(existing);
                    }, () -> aiModelRepository.save(AiModel.builder()
                            .providerId(providerId)
                            .modelId(info.modelId())
                            .displayName(info.displayName())
                            .contextWindow(info.contextWindow())
                            .source(AiModelSource.SYNCED)
                            .build()));
        }
        log.info("[AI_MODEL_SYNC] providerId={} syncedCount={}", providerId, fetched.size());
        return listModels(providerId);
    }

    @Transactional
    public AiModelDto setEnabled(Long modelId, boolean enabled) {
        AiModel model = findModel(modelId);
        model.setEnabled(enabled);
        return toDto(aiModelRepository.save(model));
    }

    @Transactional
    public AiModelDto setDefault(Long modelId) {
        AiModel model = findModel(modelId);
        aiModelRepository.findAllByProviderId(model.getProviderId()).forEach(m -> {
            if (Boolean.TRUE.equals(m.getDefaultModel())) {
                m.setDefaultModel(false);
                aiModelRepository.save(m);
            }
        });
        model.setDefaultModel(true);
        return toDto(aiModelRepository.save(model));
    }

    @Transactional
    public void deleteModel(Long modelId) {
        AiModel model = findModel(modelId);
        aiModelRepository.delete(model);
    }

    private AiProvider findProvider(Long providerId) {
        return aiProviderRepository.findById(providerId)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(AiServiceException::providerNotFound);
    }

    private AiModel findModel(Long modelId) {
        return aiModelRepository.findById(modelId)
                .orElseThrow(() -> new AiServiceException("AI model not found.", "AI_MODEL_NOT_FOUND"));
    }

    private AiModelDto toDto(AiModel model) {
        return AiModelDto.builder()
                .id(model.getId())
                .providerId(model.getProviderId())
                .modelId(model.getModelId())
                .displayName(model.getDisplayName())
                .enabled(model.getEnabled())
                .defaultModel(model.getDefaultModel())
                .defaultVisionModel(model.getDefaultVisionModel())
                .contextWindow(model.getContextWindow())
                .inputPricePerMillion(model.getInputPricePerMillion())
                .outputPricePerMillion(model.getOutputPricePerMillion())
                .supportsStreaming(model.getSupportsStreaming())
                .supportsJsonMode(model.getSupportsJsonMode())
                .supportsToolCalling(model.getSupportsToolCalling())
                .source(model.getSource().name())
                .build();
    }
}
