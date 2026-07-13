package com.carrental.controller;

import com.carrental.dto.ai.UpdateAiSettingsRequest;
import com.carrental.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Super Admin → AI & Automation → Settings. Manages only the global,
 * cross-cutting AI flags/limits row. Provider/model CRUD lives in
 * {@link AiProviderController} / {@link AiModelController}; usage/audit data
 * in {@link AiUsageController}.
 */
@RestController
@RequestMapping("/api/super-admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return success("AI settings loaded successfully.", aiSettingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody UpdateAiSettingsRequest request) {
        return success("AI settings saved successfully.", aiSettingsService.updateSettings(request));
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data));
    }
}
