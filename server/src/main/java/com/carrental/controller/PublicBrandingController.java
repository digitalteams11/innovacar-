package com.carrental.controller;

import com.carrental.entity.WhiteLabelSettings;
import com.carrental.repository.WhiteLabelSettingsRepository;
import com.carrental.service.DomainVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Unauthenticated, host-based tenant branding resolution. Public/login pages call this
 * before any JWT exists to find out which agency owns the domain the visitor is on.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicBrandingController {

    private final WhiteLabelSettingsRepository whiteLabelRepository;
    private final DomainVerificationService domainVerificationService;

    @GetMapping("/branding")
    public ResponseEntity<Map<String, Object>> resolveBranding(
            @RequestParam(required = false) String host,
            HttpServletRequest request) {
        String hostname = normalizeHost(host != null ? host : request.getHeader("Host"));
        if (hostname == null) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        Optional<WhiteLabelSettings> settings = whiteLabelRepository.findByCustomDomain(hostname);
        if (settings.isEmpty()) {
            String baseDomain = domainVerificationService.getBaseDomain();
            if (hostname.endsWith("." + baseDomain)) {
                String slug = hostname.substring(0, hostname.length() - baseDomain.length() - 1);
                settings = whiteLabelRepository.findBySubdomain(slug);
            }
        }

        if (settings.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        WhiteLabelSettings s = settings.get();
        return ResponseEntity.ok(Map.of(
                "found", true,
                "logoUrl", s.getLogoUrl() == null ? "" : s.getLogoUrl(),
                "primaryColor", s.getPrimaryColor() == null ? "#0b1437" : s.getPrimaryColor(),
                "accentColor", s.getAccentColor() == null ? "#c9a96e" : s.getAccentColor(),
                "tenantName", s.getTenant() == null ? "" : s.getTenant().getName()
        ));
    }

    private String normalizeHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) return null;
        String hostname = rawHost.trim().toLowerCase();
        int colonIdx = hostname.indexOf(':');
        if (colonIdx >= 0) hostname = hostname.substring(0, colonIdx);
        return hostname;
    }
}
