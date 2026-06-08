package com.carrental.controller;

import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

        if (updates.containsKey("name")) {
            tenant.setName(updates.get("name"));
        }
        if (updates.containsKey("email")) {
            tenant.setEmail(updates.get("email"));
        }
        if (updates.containsKey("address")) {
            tenant.setAddress(updates.get("address"));
        }
        if (updates.containsKey("phone")) {
            tenant.setPhone(updates.get("phone"));
        }
        if (updates.containsKey("taxId")) {
            tenant.setTaxId(updates.get("taxId"));
        }
        if (updates.containsKey("city")) {
            tenant.setCity(updates.get("city"));
        }
        if (updates.containsKey("country")) {
            tenant.setCountry(updates.get("country"));
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
}
