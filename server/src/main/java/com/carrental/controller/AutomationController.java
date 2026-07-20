package com.carrental.controller;

import com.carrental.entity.User;
import com.carrental.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Automation Center — Phase 1 (3 real agents). Every endpoint enforces Premium
 * access server-side via {@link AutomationService#assertAutomationCenterAccess()};
 * the frontend lock is a UX convenience only, never the actual gate.
 */
@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return automationService.overview();
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> agents() {
        return automationService.agents();
    }

    @PatchMapping("/agents/{key}")
    public Map<String, Object> updateAgent(@PathVariable String key, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return automationService.setAgentEnabled(key, enabled);
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() {
        return automationService.runs();
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        return automationService.alerts();
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(automationService.acknowledgeAlert(id, user != null ? user.getId() : null));
    }
}
