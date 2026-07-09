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
    public ResponseEntity<Map<String, Object>> getSettings() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(whiteLabelRepository.findByTenantId(tenantId)
                .map(this::response)
                .orElseGet(this::defaultResponse));
    }

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody WhiteLabelSettings body) {
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
        settings.setCustomDomain(blankToNull(body.getCustomDomain()));
        settings.setDomainStatus(body.getDomainStatus() != null ? body.getDomainStatus() : "PENDING");
        return ResponseEntity.ok(response(whiteLabelRepository.save(settings)));
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<Map<String, Object>> resolveDomain(@PathVariable String domain) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("settings", whiteLabelRepository.findByCustomDomain(domain)
                .map(this::response)
                .orElse(null));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> response(WhiteLabelSettings settings) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", settings.getId());
        data.put("tenantId", settings.getTenant() == null ? null : settings.getTenant().getId());
        data.put("logoUrl", settings.getLogoUrl() == null ? "" : settings.getLogoUrl());
        data.put("primaryColor", settings.getPrimaryColor() == null ? "#0b1437" : settings.getPrimaryColor());
        data.put("accentColor", settings.getAccentColor() == null ? "#c9a96e" : settings.getAccentColor());
        data.put("customDomain", settings.getCustomDomain() == null ? "" : settings.getCustomDomain());
        data.put("domainStatus", settings.getDomainStatus() == null ? "PENDING" : settings.getDomainStatus());
        return data;
    }

    private Map<String, Object> defaultResponse() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("logoUrl", "");
        data.put("primaryColor", "#0b1437");
        data.put("accentColor", "#c9a96e");
        data.put("customDomain", "");
        data.put("domainStatus", "PENDING");
        return data;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
