package com.carrental.controller;

import com.carrental.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureAccessController {

    private final FeatureAccessService featureAccessService;

    @GetMapping("/access")
    public ResponseEntity<Map<String, Object>> getAccessMatrix() {
        return ResponseEntity.ok(featureAccessService.getCurrentTenantAccess());
    }

    @GetMapping("/check/{featureCode}")
    public ResponseEntity<Map<String, Object>> checkFeature(@PathVariable String featureCode) {
        return ResponseEntity.ok(featureAccessService.checkCurrentTenantFeature(featureCode));
    }
}
