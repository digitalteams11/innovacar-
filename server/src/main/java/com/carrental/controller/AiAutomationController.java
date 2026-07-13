package com.carrental.controller;

import com.carrental.dto.ai.UpdateAutomationRequest;
import com.carrental.service.AiAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/ai/automations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AiAutomationController {

    private final AiAutomationService aiAutomationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return success("Automations loaded successfully.", aiAutomationService.listAutomations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return success("Automation loaded successfully.", aiAutomationService.getAutomation(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody UpdateAutomationRequest request) {
        return success("Automation updated successfully.", aiAutomationService.updateAutomation(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        aiAutomationService.deleteAutomation(id);
        return success("Automation deleted.", Map.of("id", id));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enable(@PathVariable Long id) {
        return success("Automation enabled.", aiAutomationService.setEnabled(id, true));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Map<String, Object>> disable(@PathVariable Long id) {
        return success("Automation disabled.", aiAutomationService.setEnabled(id, false));
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data));
    }
}
