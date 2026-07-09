package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Agency (tenant) info REST controller.
 *
 * <pre>
 * GET  /api/agency  – get current tenant info       [authenticated]
 * PUT  /api/agency  – update tenant info            [ADMIN]
 * </pre>
 */
@RestController
@RequestMapping("/api/agency")
@RequiredArgsConstructor
public class AgencyController {

    private final TenantRepository tenantRepository;

    // ── GET /api/agency ──────────────────────────────────────────────────────

    /**
     * Returns the current tenant's agency information.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAgency() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", tenant.getId());
        result.put("name", tenant.getName() != null ? tenant.getName() : "");
        result.put("email", tenant.getEmail() != null ? tenant.getEmail() : "");
        result.put("address", tenant.getAddress() != null ? tenant.getAddress() : "");
        result.put("phone", tenant.getPhone() != null ? tenant.getPhone() : "");
        result.put("taxId", tenant.getTaxId() != null ? tenant.getTaxId() : "");
        result.put("city", tenant.getCity() != null ? tenant.getCity() : "");
        result.put("country", tenant.getCountry() != null ? tenant.getCountry() : "");
        result.put("logoUrl", tenant.getLogoUrl() != null ? tenant.getLogoUrl() : "");
        result.put("agencySignature", tenant.getAgencySignature() != null ? tenant.getAgencySignature() : "");
        result.put("agencyStampUrl", tenant.getAgencyStampUrl() != null ? tenant.getAgencyStampUrl() : "");
        result.put("termsAndConditions", tenant.getTermsAndConditions() != null ? tenant.getTermsAndConditions() : "");
        result.put("subscriptionActive", tenant.isSubscriptionActive());
        result.put("subscriptionEndDate", tenant.getSubscriptionEndDate() != null ? tenant.getSubscriptionEndDate().toString() : "");
        result.put("balance", tenant.getBalance() != null ? tenant.getBalance() : java.math.BigDecimal.ZERO);
        result.put("hasFreeAccess", tenant.hasActiveFreeAccess());
        result.put("freeAccessUntil", tenant.getFreeAccessUntil());
        result.put("freeAccessReason", tenant.getFreeAccessReason());
        return ResponseEntity.ok(result);
    }

    // ── PUT /api/agency ──────────────────────────────────────────────────────

    /**
     * Updates the current tenant's agency information. ADMIN-only.
     */
    @PutMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> updateAgency(@RequestBody Map<String, String> updates) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        // name and email are unique per-tenant columns — only re-validate them
        // when the caller is actually changing the value, and exclude this
        // tenant's own row so re-saving the same agency never looks like a
        // duplicate of itself.
        if (updates.containsKey("name")) {
            String name = updates.get("name");
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("Agency information is required.");
            }
            if (!name.equals(tenant.getName()) && tenantRepository.existsByNameAndIdNot(name, tenantId)) {
                throw new IllegalStateException("This agency name is already used by another agency.");
            }
            tenant.setName(name);
        }
        if (updates.containsKey("email")) {
            String email = updates.get("email");
            if (StringUtils.hasText(email)
                    && !email.equals(tenant.getEmail())
                    && tenantRepository.existsByEmailAndIdNot(email, tenantId)) {
                throw new IllegalStateException("This email is already used by another agency.");
            }
            if (StringUtils.hasText(email)) {
                tenant.setEmail(email);
            }
        }
        if (updates.containsKey("address")) {
            tenant.setAddress(StringUtils.hasText(updates.get("address")) ? updates.get("address") : null);
        }
        if (updates.containsKey("phone")) {
            tenant.setPhone(StringUtils.hasText(updates.get("phone")) ? updates.get("phone") : null);
        }
        if (updates.containsKey("taxId")) {
            tenant.setTaxId(StringUtils.hasText(updates.get("taxId")) ? updates.get("taxId") : null);
        }
        if (updates.containsKey("city")) {
            tenant.setCity(StringUtils.hasText(updates.get("city")) ? updates.get("city") : null);
        }
        if (updates.containsKey("country")) {
            tenant.setCountry(StringUtils.hasText(updates.get("country")) ? updates.get("country") : null);
        }
        if (updates.containsKey("logoUrl")) {
            tenant.setLogoUrl(updates.get("logoUrl"));
        }
        if (updates.containsKey("agencySignature")) {
            tenant.setAgencySignature(updates.get("agencySignature"));
        }
        if (updates.containsKey("agencyStampUrl")) {
            tenant.setAgencyStampUrl(updates.get("agencyStampUrl"));
        }
        if (updates.containsKey("termsAndConditions")) {
            tenant.setTermsAndConditions(updates.get("termsAndConditions"));
        }

        Tenant saved = tenantRepository.save(tenant);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", saved.getId());
        result.put("name", saved.getName() != null ? saved.getName() : "");
        result.put("email", saved.getEmail() != null ? saved.getEmail() : "");
        result.put("address", saved.getAddress() != null ? saved.getAddress() : "");
        result.put("phone", saved.getPhone() != null ? saved.getPhone() : "");
        result.put("taxId", saved.getTaxId() != null ? saved.getTaxId() : "");
        result.put("city", saved.getCity() != null ? saved.getCity() : "");
        result.put("country", saved.getCountry() != null ? saved.getCountry() : "");
        result.put("logoUrl", saved.getLogoUrl() != null ? saved.getLogoUrl() : "");
        result.put("agencySignature", saved.getAgencySignature() != null ? saved.getAgencySignature() : "");
        result.put("agencyStampUrl", saved.getAgencyStampUrl() != null ? saved.getAgencyStampUrl() : "");
        result.put("termsAndConditions", saved.getTermsAndConditions() != null ? saved.getTermsAndConditions() : "");
        return ResponseEntity.ok(result);
    }

    // ── POST /api/agency/settings/logo ──────────────────────────────────────

    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> uploadLogo(@RequestParam("file") MultipartFile file) {
        return storeAgencyAsset(file, "logo");
    }

    // ── DELETE /api/agency/settings/logo ────────────────────────────────────

    @DeleteMapping("/settings/logo")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteLogo() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        tenant.setLogoUrl(null);
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/agency/settings/stamp ─────────────────────────────────────

    @PostMapping(value = "/settings/stamp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> uploadStamp(@RequestParam("file") MultipartFile file) {
        return storeAgencyAsset(file, "stamp");
    }

    // ── DELETE /api/agency/settings/stamp ───────────────────────────────────

    @DeleteMapping("/settings/stamp")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteStamp() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        tenant.setAgencyStampUrl(null);
        tenantRepository.save(tenant);
        return ResponseEntity.noContent().build();
    }

    // ── Shared file-storage helper ───────────────────────────────────────────

    private static final long MAX_ASSET_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter ASSET_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private ResponseEntity<Map<String, Object>> storeAgencyAsset(MultipartFile file, String assetType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }
        if (file.getSize() > MAX_ASSET_BYTES) {
            throw new IllegalArgumentException("File must be under 5 MB.");
        }

        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        String ext = resolveExtension(file.getOriginalFilename(), contentType);
        String ts = ASSET_TS.format(LocalDateTime.now());
        String fileName = assetType + "_" + ts + ext;

        try {
            Path dir = Path.of("uploads", "agency", String.valueOf(tenantId));
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store " + assetType + " file: " + e.getMessage());
        }

        String url = "/uploads/agency/" + tenantId + "/" + fileName;
        if ("logo".equals(assetType)) {
            tenant.setLogoUrl(url);
        } else {
            tenant.setAgencyStampUrl(url);
        }
        tenantRepository.save(tenant);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("url", url);
        body.put(assetType + "Url", url);
        return ResponseEntity.ok(body);
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0) return originalFilename.substring(dot).toLowerCase();
        }
        if (contentType.contains("png"))  return ".png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        if (contentType.contains("gif"))  return ".gif";
        if (contentType.contains("webp")) return ".webp";
        return ".png";
    }
}
