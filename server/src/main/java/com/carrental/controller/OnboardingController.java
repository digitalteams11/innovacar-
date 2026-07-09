package com.carrental.controller;

import com.carrental.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(onboardingService.status());
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, Boolean> updates) {
        return ResponseEntity.ok(onboardingService.update(updates));
    }
}
