package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.entity.WhiteLabelSettings;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.WhiteLabelSettingsRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/white-label")
@RequiredArgsConstructor
public class WhiteLabelController {

    private final WhiteLabelSettingsRepository whiteLabelRepository;
    private final TenantRepository tenantRepository;
    private final FeatureAccessService featureAccessService;

    @GetMapping
    public ResponseEntity<WhiteLabelSettings> getSettings() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(whiteLabelRepository.findByTenantId(tenantId).orElse(null));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WhiteLabelSettings> saveSettings(@RequestBody WhiteLabelSettings body) {
        if (!featureAccessService.isEnabledForCurrentTenant("WHITE_LABEL")) {
            throw new IllegalStateException("WHITE_LABEL is not enabled for this subscription plan");
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        WhiteLabelSettings settings = whiteLabelRepository.findByTenantId(tenantId).orElseGet(WhiteLabelSettings::new);
        settings.setTenant(tenant);
        settings.setLogoUrl(body.getLogoUrl());
        settings.setPrimaryColor(body.getPrimaryColor());
        settings.setAccentColor(body.getAccentColor());
        settings.setCustomDomain(body.getCustomDomain());
        settings.setDomainStatus(body.getDomainStatus() != null ? body.getDomainStatus() : "PENDING");
        return ResponseEntity.ok(whiteLabelRepository.save(settings));
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<Map<String, Object>> resolveDomain(@PathVariable String domain) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("settings", whiteLabelRepository.findByCustomDomain(domain).orElse(null));
        return ResponseEntity.ok(result);
    }
}
