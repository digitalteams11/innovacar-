package com.carrental.controller;

import com.carrental.entity.AiSettings;
import com.carrental.entity.User;
import com.carrental.service.AiAssistantService;
import com.carrental.service.GeminiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tenant-facing AI endpoints. The frontend talks only to these — never to
 * Gemini directly. The API key never appears in any response from this class.
 *
 * <p>Request format for chat:
 * <pre>
 * {
 *   "message": "How do I create a contract?",
 *   "conversationId": "optional-uuid",
 *   "pageContext": { "route": "/contracts", "module": "contracts" }
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final GeminiClientService geminiClientService;
    private final AiAssistantService aiAssistantService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        AiSettings settings = geminiClientService.loadSettings();
        boolean enabled = Boolean.TRUE.equals(settings.getEnabled());
        boolean configured = settings.getApiKeyEncrypted() != null && !settings.getApiKeyEncrypted().isBlank();
        boolean reachable = enabled && configured && Boolean.TRUE.equals(settings.getLastTestSuccess());
        boolean fallbackMode = !enabled || !configured || !reachable;

        String message;
        if (!enabled) {
            message = "AI features are currently disabled.";
        } else if (!configured) {
            message = "Gemini API key is not configured.";
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
        data.put("message", message);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {

        String message = stringOf(body, "message");
        String conversationId = stringOf(body, "conversationId");

        // pageContext: { "route": "/contracts", "module": "contracts" }
        String module = null;
        String route = null;
        Object pageContextObj = body != null ? body.get("pageContext") : null;
        if (pageContextObj instanceof Map<?, ?> pageContext) {
            Object moduleVal = pageContext.get("module");
            Object routeVal = pageContext.get("route");
            if (moduleVal != null) module = moduleVal.toString();
            if (routeVal != null) route = routeVal.toString();
        }
        // Legacy fallback: "module" at top level
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
