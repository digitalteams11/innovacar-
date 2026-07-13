package com.carrental.controller;

import com.carrental.dto.ai.CreateProviderRequest;
import com.carrental.dto.ai.UpdateProviderRequest;
import com.carrental.service.AiProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/ai/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AiProviderController {

    private final AiProviderService aiProviderService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return success("Providers loaded successfully.", aiProviderService.listProviders());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateProviderRequest request) {
        return success("Provider created successfully.", aiProviderService.createProvider(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return success("Provider loaded successfully.", aiProviderService.getProvider(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody UpdateProviderRequest request) {
        return success("Provider updated successfully.", aiProviderService.updateProvider(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        aiProviderService.deleteProvider(id);
        return success("Provider deleted successfully.", Map.of("id", id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        String tempApiKey = body != null && body.get("apiKey") instanceof String s ? s : null;
        var result = aiProviderService.testConnection(id, tempApiKey);
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "data", result
        ));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable Long id) {
        return success("Provider activated.", aiProviderService.activate(id));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Map<String, Object>> disable(@PathVariable Long id) {
        return success("Provider disabled.", aiProviderService.disable(id));
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data));
    }
}
