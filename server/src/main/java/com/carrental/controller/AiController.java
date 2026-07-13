package com.carrental.controller;

import com.carrental.entity.AiProvider;
import com.carrental.entity.AiSettings;
import com.carrental.entity.User;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tenant-facing AI endpoints. The frontend talks only to these — never to a
 * provider directly. No API key ever appears in any response from this class.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiSettingsRepository aiSettingsRepository;
    private final AiProviderRepository aiProviderRepository;
    private final AiAssistantService aiAssistantService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        AiSettings settings = aiSettingsRepository.findAll().stream().findFirst().orElse(null);
        boolean enabled = settings != null && Boolean.TRUE.equals(settings.getGlobalEnabled());

        AiProvider activeProvider = enabled && settings.getActiveProviderId() != null
                ? aiProviderRepository.findById(settings.getActiveProviderId()).orElse(null) : null;
        boolean configured = activeProvider != null && Boolean.TRUE.equals(activeProvider.getEnabled());
        boolean reachable = configured && activeProvider.getConnectionStatus() == com.carrental.entity.AiConnectionStatus.CONNECTED;
        boolean fallbackMode = !enabled || !configured || !reachable;

        String message;
        if (!enabled) {
            message = "AI features are currently disabled.";
        } else if (activeProvider == null) {
            message = "No AI provider is configured.";
        } else if (!reachable) {
            message = "AI is unavailable. Local fallback is active.";
        } else {
            message = "AI is available.";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("configured", configured);
        data.put("reachable", reachable);
        data.put("fallbackMode", fallbackMode);
        data.put("provider", activeProvider != null ? activeProvider.getProviderType().name() : null);
        data.put("message", message);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {

        String message = stringOf(body, "message");
        String conversationId = stringOf(body, "conversationId");

        String module = null;
        String route = null;
        Object pageContextObj = body != null ? body.get("pageContext") : null;
        if (pageContextObj instanceof Map<?, ?> pageContext) {
            Object moduleVal = pageContext.get("module");
            Object routeVal = pageContext.get("route");
            if (moduleVal != null) module = moduleVal.toString();
            if (routeVal != null) route = routeVal.toString();
        }
        if (module == null && body != null && body.get("module") != null) {
            module = body.get("module").toString();
        }

        AiAssistantService.ChatResult result = aiAssistantService.chat(user, module, message, route, conversationId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", result.reply());
        data.put("conversationId", result.conversationId());
        data.put("suggestedActions", result.suggestedActions());
        data.put("sources", result.sources());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI response generated successfully.",
                "data", data
        ));
    }

    private static String stringOf(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }
}
