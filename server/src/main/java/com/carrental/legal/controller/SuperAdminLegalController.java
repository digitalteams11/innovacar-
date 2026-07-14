package com.carrental.legal.controller;

import com.carrental.entity.User;
import com.carrental.legal.dto.*;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.service.DataRetentionService;
import com.carrental.legal.service.LegalVersionService;
import com.carrental.legal.service.PrivacyRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Super Admin authoring & compliance-ops console: draft/publish legal
 * document versions, review acceptance stats, triage privacy requests, and
 * maintain the published data-retention schedule. Mounted under
 * /api/super-admin, already restricted to SUPER_ADMIN by SecurityConfig; the
 * class-level annotation mirrors the existing SuperAdminController pattern.
 */
@RestController
@RequestMapping("/api/super-admin/legal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminLegalController {

    private final LegalVersionService legalVersionService;
    private final PrivacyRequestService privacyRequestService;
    private final DataRetentionService dataRetentionService;

    // ── Document versions ────────────────────────────────────────────────────

    @GetMapping("/documents/{type}/versions")
    public List<LegalDocumentVersionDto> listVersions(
            @PathVariable LegalDocumentType type, @RequestParam(required = false) String locale) {
        return locale != null
                ? legalVersionService.listVersions(type, locale)
                : legalVersionService.listAllVersionsForType(type);
    }

    @GetMapping("/documents/versions/{versionId}")
    public LegalDocumentVersionDto getVersion(@PathVariable Long versionId) {
        return legalVersionService.getVersionForAdmin(versionId);
    }

    @PostMapping("/documents/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public LegalDocumentVersionDto createDraft(Authentication authentication, @Valid @RequestBody CreateLegalDocumentVersionRequest request) {
        User admin = currentUser(authentication);
        return legalVersionService.createDraft(request, admin.getId(), admin.getEmail());
    }

    @PutMapping("/documents/versions/{versionId}")
    public LegalDocumentVersionDto updateDraft(
            Authentication authentication, @PathVariable Long versionId, @Valid @RequestBody UpdateLegalDocumentVersionRequest request) {
        return legalVersionService.updateDraft(versionId, request, currentUser(authentication).getId());
    }

    @PostMapping("/documents/versions/{versionId}/publish")
    public LegalDocumentVersionDto publish(Authentication authentication, @PathVariable Long versionId) {
        return legalVersionService.publish(versionId, currentUser(authentication).getId());
    }

    @GetMapping("/documents/versions/{versionId}/stats")
    public LegalDocumentVersionStatsDto getStats(@PathVariable Long versionId) {
        return legalVersionService.getAcceptanceStats(versionId);
    }

    // ── Privacy requests ─────────────────────────────────────────────────────

    @GetMapping("/privacy-requests")
    public List<PrivacyRequestDto> listPrivacyRequests() {
        return privacyRequestService.getAllForAdmin();
    }

    @PatchMapping("/privacy-requests/{requestId}")
    public PrivacyRequestDto updatePrivacyRequest(
            Authentication authentication, @PathVariable Long requestId, @Valid @RequestBody PrivacyRequestStatusUpdateRequest request) {
        return privacyRequestService.updateStatus(requestId, request, currentUser(authentication).getId());
    }

    // ── Data retention schedule ──────────────────────────────────────────────

    @GetMapping("/data-retention")
    public List<DataRetentionEntryDto> listRetentionEntries() {
        return dataRetentionService.listAll();
    }

    @PostMapping("/data-retention")
    @ResponseStatus(HttpStatus.CREATED)
    public DataRetentionEntryDto createRetentionEntry(@Valid @RequestBody UpsertDataRetentionEntryRequest request) {
        return dataRetentionService.create(request);
    }

    @PutMapping("/data-retention/{id}")
    public DataRetentionEntryDto updateRetentionEntry(@PathVariable Long id, @Valid @RequestBody UpsertDataRetentionEntryRequest request) {
        return dataRetentionService.update(id, request);
    }

    @DeleteMapping("/data-retention/{id}")
    public void deleteRetentionEntry(@PathVariable Long id) {
        dataRetentionService.delete(id);
    }

    private User currentUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("Authenticated user not found");
        }
        return user;
    }
}
