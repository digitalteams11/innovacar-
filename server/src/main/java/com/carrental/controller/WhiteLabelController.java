package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.entity.WhiteLabelSettings;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.WhiteLabelSettingsRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.DomainVerificationService;
import com.carrental.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/white-label")
@RequiredArgsConstructor
public class WhiteLabelController {

    private final WhiteLabelSettingsRepository whiteLabelRepository;
    private final TenantRepository tenantRepository;
    private final FeatureAccessService featureAccessService;
    private final DomainVerificationService domainVerificationService;

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
        settings.setLogoUrl(validateLogo(body.getLogoUrl()));
        settings.setPrimaryColor(body.getPrimaryColor());
        settings.setAccentColor(body.getAccentColor());

        String newDomain = blankToNull(body.getCustomDomain());
        String newSubdomain = normalizeSubdomain(body.getSubdomain());

        if (newDomain != null && newSubdomain != null) {
            throw new IllegalArgumentException("Choose either a custom domain or an Innovacar subdomain, not both");
        }

        if (newSubdomain != null) {
            if (!domainVerificationService.isValidSubdomainSlug(newSubdomain)) {
                throw new IllegalArgumentException("Subdomain must be lowercase letters, numbers and hyphens only");
            }
            whiteLabelRepository.findBySubdomain(newSubdomain).ifPresent(existing -> {
                if (!Objects.equals(existing.getTenant().getId(), tenantId)) {
                    throw new IllegalArgumentException("This subdomain is already taken");
                }
            });
            settings.setSubdomain(newSubdomain);
            settings.setCustomDomain(null);
            settings.setVerificationToken(null);
            settings.setDnsVerifiedAt(null);
            settings.setLastCheckedAt(null);
            settings.setLastCheckError(null);
            // We own the base domain, so there's no external DNS record to verify — but reaching this
            // subdomain still requires a reverse proxy/wildcard routing that isn't deployed yet (see runbook),
            // so this stops at DNS_VERIFIED just like a custom domain, never a fake ACTIVE.
            settings.setDnsVerifiedAt(domainVerificationService.now());
            settings.setDomainStatus("DNS_VERIFIED");
        } else if (newDomain != null) {
            boolean domainChanged = !newDomain.equalsIgnoreCase(settings.getCustomDomain());
            whiteLabelRepository.findByCustomDomain(newDomain).ifPresent(existing -> {
                if (!Objects.equals(existing.getTenant().getId(), tenantId)) {
                    throw new IllegalArgumentException("This domain is already registered by another agency");
                }
            });
            settings.setCustomDomain(newDomain);
            settings.setSubdomain(null);
            if (domainChanged) {
                settings.setVerificationToken(domainVerificationService.generateVerificationToken());
                settings.setDnsVerifiedAt(null);
                settings.setLastCheckedAt(null);
                settings.setLastCheckError(null);
                settings.setDomainStatus("PENDING");
            }
        } else {
            settings.setCustomDomain(null);
            settings.setSubdomain(null);
            settings.setVerificationToken(null);
            settings.setDnsVerifiedAt(null);
            settings.setLastCheckedAt(null);
            settings.setLastCheckError(null);
            settings.setDomainStatus("NONE");
        }

        return ResponseEntity.ok(response(whiteLabelRepository.save(settings)));
    }

    @PostMapping("/domain/verify")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> verifyDomain() {
        if (!featureAccessService.isEnabledForCurrentTenant("WHITE_LABEL")) {
            throw new IllegalStateException("WHITE_LABEL is not enabled for this subscription plan");
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        WhiteLabelSettings settings = whiteLabelRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("No white-label settings saved yet"));
        if (settings.getCustomDomain() == null) {
            throw new IllegalStateException("No custom domain configured — nothing to verify");
        }

        DomainVerificationService.VerificationResult result = domainVerificationService.verify(settings);
        settings.setLastCheckedAt(domainVerificationService.now());
        settings.setLastCheckError(result.error());
        if (result.success()) {
            settings.setDomainStatus("DNS_VERIFIED");
            settings.setDnsVerifiedAt(domainVerificationService.now());
        } else if (!"ACTIVE".equals(settings.getDomainStatus())) {
            settings.setDomainStatus("FAILED");
        }
        return ResponseEntity.ok(response(whiteLabelRepository.save(settings)));
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<Map<String, Object>> resolveDomain(@PathVariable String domain) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("settings", whiteLabelRepository.findByCustomDomain(domain)
                .or(() -> whiteLabelRepository.findBySubdomain(domain))
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
        data.put("subdomain", settings.getSubdomain() == null ? "" : settings.getSubdomain());
        data.put("subdomainFull", settings.getSubdomain() == null ? ""
                : settings.getSubdomain() + "." + domainVerificationService.getBaseDomain());
        data.put("domainStatus", settings.getDomainStatus() == null ? "NONE" : settings.getDomainStatus());
        data.put("lastCheckedAt", settings.getLastCheckedAt());
        data.put("lastCheckError", settings.getLastCheckError());
        data.put("dnsVerifiedAt", settings.getDnsVerifiedAt());
        if (settings.getCustomDomain() != null && settings.getVerificationToken() != null) {
            DomainVerificationService.DnsInstructions instructions =
                    domainVerificationService.buildDnsInstructions(settings);
            data.put("dnsInstructions", Map.of(
                    "txtRecordName", instructions.txtRecordName(),
                    "txtRecordValue", instructions.txtRecordValue(),
                    "cnameRecordName", instructions.cnameRecordName(),
                    "cnameRecordValue", instructions.cnameRecordValue()
            ));
        } else {
            data.put("dnsInstructions", null);
        }
        return data;
    }

    private Map<String, Object> defaultResponse() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("logoUrl", "");
        data.put("primaryColor", "#0b1437");
        data.put("accentColor", "#c9a96e");
        data.put("customDomain", "");
        data.put("subdomain", "");
        data.put("subdomainFull", "");
        data.put("domainStatus", "NONE");
        data.put("lastCheckedAt", null);
        data.put("lastCheckError", null);
        data.put("dnsVerifiedAt", null);
        data.put("dnsInstructions", null);
        return data;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private String normalizeSubdomain(String value) {
        String slug = domainVerificationService.slugify(value);
        return slug == null || slug.isBlank() ? null : slug;
    }

    /** Base64 data URLs only, capped at ~2MB, PNG/JPEG/SVG only — no object storage exists in this codebase. */
    private String validateLogo(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return null;
        if (logoUrl.startsWith("data:")) {
            if (!logoUrl.matches("^data:image/(png|jpeg|jpg|svg\\+xml);base64,.*")) {
                throw new IllegalArgumentException("Logo must be a PNG, JPEG or SVG image");
            }
            if (logoUrl.length() > 2_800_000) { // ~2MB of binary data once base64-decoded
                throw new IllegalArgumentException("Logo must be smaller than 2MB");
            }
        }
        return logoUrl;
    }
}
