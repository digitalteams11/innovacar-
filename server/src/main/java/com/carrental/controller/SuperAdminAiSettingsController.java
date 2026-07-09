package com.carrental.controller;

import com.carrental.dto.ai.UpdateAiSettingsRequest;
import com.carrental.entity.AiAuditLog;
import com.carrental.service.AiAuditService;
import com.carrental.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Super Admin → Settings → AI & Automation. Manages the platform's single
 * Gemini configuration row. Only SUPER_ADMIN can reach these (enforced both
 * by {@code SecurityConfig}'s {@code /api/super-admin/**} rule and this
 * class-level {@code @PreAuthorize}).
 */
@RestController
@RequestMapping("/api/super-admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAiSettingsController {

    private final AiSettingsService aiSettingsService;
    private final AiAuditService aiAuditService;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return success("AI settings loaded successfully.", aiSettingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody UpdateAiSettingsRequest request) {
        return success("AI settings saved successfully.", aiSettingsService.updateSettings(request));
    }

    /**
     * Test Gemini connection. Accepts an optional {@code { "apiKey": "..." }}
     * body so the admin can test a key they typed but have not saved yet.
     * If body is absent or apiKey is blank, the stored encrypted key is used.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody(required = false) Map<String, Object> body) {

        String tempApiKey = null;
        if (body != null && body.get("apiKey") instanceof String s && !s.isBlank()) {
            tempApiKey = s;
        }

        AiSettingsService.TestResult result = aiSettingsService.testConnection(tempApiKey);

        if (result.success()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("provider",         result.provider());
            data.put("model",            result.model());
            data.put("latencyMs",        result.latencyMs());
            if (result.fallbackMessage() != null) {
                data.put("fallbackMessage", result.fallbackMessage());
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.message(),
                    "data",    data
            ));
        } else {
            // Debug data — never includes the API key
            Map<String, Object> debugData = new LinkedHashMap<>();
            debugData.put("provider",            result.provider());
            debugData.put("model",               result.model());
            debugData.put("httpStatus",          result.httpStatus());
            debugData.put("safeProviderMessage", result.safeProviderMessage());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success",   false);
            response.put("errorCode", result.errorCode() != null ? result.errorCode() : "AI_SERVICE_UNAVAILABLE");
            response.put("message",   result.message());
            response.put("data",      debugData);
            if (result.latencyMs() != null) response.put("latencyMs", result.latencyMs());
            return ResponseEntity.ok(response);  // always 200 so frontend reads the body
        }
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AiAuditLog> result = aiAuditService.listAll(PageRequest.of(page, Math.min(size, 100)));
        List<Map<String, Object>> items = result.getContent().stream().map(this::toMap).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("page", result.getNumber());
        data.put("totalPages", result.getTotalPages());
        data.put("totalElements", result.getTotalElements());
        return success("AI audit logs loaded successfully.", data);
    }

    @DeleteMapping("/audit-logs/{id}")
    public ResponseEntity<Map<String, Object>> deleteAuditLog(@PathVariable Long id) {
        aiAuditService.deleteById(id);
        return success("AI audit log entry deleted.", Map.of("id", id));
    }

    @DeleteMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> clearAuditLogs() {
        long deleted = aiAuditService.clearAll();
        return success("AI audit logs cleared.", Map.of("deletedCount", deleted));
    }

    private Map<String, Object> toMap(AiAuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("userId", log.getUserId());
        map.put("agencyId", log.getAgencyId());
        map.put("role", log.getRole());
        map.put("feature", log.getFeature());
        map.put("promptCategory", log.getPromptCategory());
        map.put("model", log.getModel());
        map.put("inputTokensEstimate", log.getInputTokensEstimate());
        map.put("outputTokensEstimate", log.getOutputTokensEstimate());
        map.put("status", log.getStatus());
        map.put("errorCode", log.getErrorCode());
        map.put("createdAt", log.getCreatedAt());
        return map;
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data));
    }
}
