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

        return ResponseEntity.ok(Map.of(
                "id", tenant.getId(),
                "name", tenant.getName() != null ? tenant.getName() : "",
                "email", tenant.getEmail() != null ? tenant.getEmail() : "",
                "address", tenant.getAddress() != null ? tenant.getAddress() : "",
                "phone", tenant.getPhone() != null ? tenant.getPhone() : "",
                "taxId", tenant.getTaxId() != null ? tenant.getTaxId() : "",
                "city", tenant.getCity() != null ? tenant.getCity() : "",
                "country", tenant.getCountry() != null ? tenant.getCountry() : "",
                "subscriptionActive", tenant.isSubscriptionActive(),
                "subscriptionEndDate", tenant.getSubscriptionEndDate() != null ? tenant.getSubscriptionEndDate().toString() : ""
        ));
    }

    // ── PUT /api/agency ──────────────────────────────────────────────────────

    /**
     * Updates the current tenant's agency information. ADMIN-only.
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
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

        Tenant saved = tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "name", saved.getName() != null ? saved.getName() : "",
                "email", saved.getEmail() != null ? saved.getEmail() : "",
                "address", saved.getAddress() != null ? saved.getAddress() : "",
                "phone", saved.getPhone() != null ? saved.getPhone() : "",
                "taxId", saved.getTaxId() != null ? saved.getTaxId() : "",
                "city", saved.getCity() != null ? saved.getCity() : "",
                "country", saved.getCountry() != null ? saved.getCountry() : ""
        ));
    }
}
