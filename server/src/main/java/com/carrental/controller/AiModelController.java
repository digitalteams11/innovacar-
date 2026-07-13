package com.carrental.controller;

import com.carrental.dto.ai.AddModelRequest;
import com.carrental.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AiModelController {

    private final AiModelService aiModelService;

    @GetMapping("/providers/{providerId}/models")
    public ResponseEntity<Map<String, Object>> list(@PathVariable Long providerId) {
        return success("Models loaded successfully.", aiModelService.listModels(providerId));
    }

    @PostMapping("/providers/{providerId}/models")
    public ResponseEntity<Map<String, Object>> add(@PathVariable Long providerId, @RequestBody AddModelRequest request) {
        return success("Model added successfully.", aiModelService.addModel(providerId, request));
    }

    @PostMapping("/providers/{providerId}/models/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable Long providerId) {
        return success("Models synced successfully.", aiModelService.syncModels(providerId));
    }

    @PutMapping("/models/{modelId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long modelId,
                                                        @RequestBody Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            return success("Model updated.", aiModelService.setEnabled(modelId, Boolean.TRUE.equals(body.get("enabled"))));
        }
        if (Boolean.TRUE.equals(body.get("defaultModel"))) {
            return success("Default model updated.", aiModelService.setDefault(modelId));
        }
        return success("No change.", null);
    }

    @DeleteMapping("/models/{modelId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long modelId) {
        aiModelService.deleteModel(modelId);
        return success("Model deleted.", Map.of("id", modelId));
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data == null ? Map.of() : data));
    }
}
