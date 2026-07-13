package com.carrental.service.provider;

import com.carrental.entity.AiProviderType;
import com.carrental.exception.AiServiceException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link AiProviderClient} for a given {@link AiProviderType}.
 * OPENAI, OPENROUTER, and CUSTOM_OPENAI_COMPATIBLE all route to the same
 * {@link OpenAiCompatibleProviderClient} bean since they share one API shape.
 */
@Component
public class AiProviderClientFactory {

    private final Map<AiProviderType, AiProviderClient> clients = new EnumMap<>(AiProviderType.class);

    public AiProviderClientFactory(List<AiProviderClient> registeredClients,
                                    OpenAiCompatibleProviderClient openAiCompatibleProviderClient) {
        for (AiProviderClient client : registeredClients) {
            clients.put(client.getProviderType(), client);
        }
        clients.put(AiProviderType.OPENAI, openAiCompatibleProviderClient);
        clients.put(AiProviderType.OPENROUTER, openAiCompatibleProviderClient);
        clients.put(AiProviderType.CUSTOM_OPENAI_COMPATIBLE, openAiCompatibleProviderClient);
    }

    public AiProviderClient forType(AiProviderType type) {
        AiProviderClient client = clients.get(type);
        if (client == null) {
            throw AiServiceException.noActiveProvider();
        }
        return client;
    }
}
