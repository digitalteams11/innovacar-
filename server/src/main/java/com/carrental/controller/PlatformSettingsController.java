package com.carrental.controller;

import com.carrental.dto.superadmin.BrandingSettingsDto;
import com.carrental.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Super Admin → Platform Settings → Branding.
 * Distinct from the legacy generic {@code /api/super-admin/settings} endpoint
 * in {@link SuperAdminController}, which still serves the Themes/SMTP/
 * Security/Features tabs untouched.
 */
@RestController
@RequestMapping("/api/super-admin/settings/branding")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getBranding() {
        return success(platformSettingsService.getBranding());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateBranding(@RequestBody BrandingSettingsDto dto) {
        return success(platformSettingsService.updateBranding(dto));
    }

    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadLogo(@RequestParam("file") MultipartFile file) {
        return success(platformSettingsService.uploadLogo(file));
    }

    @PostMapping(value = "/favicon", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadFavicon(@RequestParam("file") MultipartFile file) {
        return success(platformSettingsService.uploadFavicon(file));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetToDefault() {
        return success(platformSettingsService.resetToDefault());
    }

    private ResponseEntity<Map<String, Object>> success(BrandingSettingsDto data) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Branding settings saved successfully.",
                "data", data
        ));
    }
}
