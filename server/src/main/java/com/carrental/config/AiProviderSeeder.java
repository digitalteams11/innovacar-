package com.carrental.config;

import com.carrental.entity.AiConnectionStatus;
import com.carrental.entity.AiModel;
import com.carrental.entity.AiModelSource;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiProviderType;
import com.carrental.entity.AiSettings;
import com.carrental.repository.AiModelRepository;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.service.AiCredentialEncryptionService;
import com.carrental.service.provider.GroqProviderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default Groq provider on a fresh install (no Gemini migration ran,
 * so {@code ai_providers} is still empty). Existing installs that already
 * migrated a Gemini row via V51 are left untouched — Groq only becomes the
 * default for brand-new installs, never force-switched onto an existing
 * tenant's working config.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderSeeder implements ApplicationRunner {

    private final AiProviderRepository aiProviderRepository;
    private final AiModelRepository aiModelRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final AiCredentialEncryptionService credentialEncryptionService;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.default-model:llama-3.3-70b-versatile}")
    private String groqDefaultModel;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (aiProviderRepository.existsByIsDeletedFalse()) {
            return;
        }

        boolean hasKey = groqApiKey != null && !groqApiKey.isBlank();
        AiProvider provider = AiProvider.builder()
                .name("Groq")
                .providerType(AiProviderType.GROQ)
                .baseUrl(GroqProviderClient.DEFAULT_BASE_URL)
                .enabled(true)
                .isActive(hasKey)
                .connectionStatus(AiConnectionStatus.NOT_TESTED)
                .build();

        if (hasKey) {
            provider.setApiKeyEncrypted(credentialEncryptionService.encrypt(groqApiKey));
            provider.setApiKeyMaskedHint(credentialEncryptionService.mask(groqApiKey, AiProviderType.GROQ));
        }

        AiProvider saved = aiProviderRepository.save(provider);

        aiModelRepository.save(AiModel.builder()
                .providerId(saved.getId())
                .modelId(groqDefaultModel)
                .displayName(groqDefaultModel)
                .defaultModel(true)
                .enabled(true)
                .supportsStreaming(true)
                .supportsJsonMode(true)
                .supportsToolCalling(true)
                .source(AiModelSource.MANUAL)
                .build());

        if (hasKey) {
            AiSettings settings = aiSettingsRepository.findAll().stream().findFirst()
                    .orElseGet(() -> aiSettingsRepository.save(AiSettings.builder().build()));
            settings.setActiveProviderId(saved.getId());
            settings.setGlobalEnabled(true);
            aiSettingsRepository.save(settings);
            log.info("[AI_SEEDER] Seeded and activated default Groq provider from GROQ_API_KEY env var.");
        } else {
            log.info("[AI_SEEDER] No GROQ_API_KEY env var — seeded an inactive Groq provider row. " +
                    "Configure via Super Admin -> AI & Automation -> Providers.");
        }
    }
}
