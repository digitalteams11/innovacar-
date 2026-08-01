package com.carrental.controller;

import com.carrental.entity.Announcement;
import com.carrental.entity.AnnouncementDismissal;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.AnnouncementDismissalRepository;
import com.carrental.repository.AnnouncementRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform announcements: Super Admin manages and broadcasts them under
 * {@code /api/super-admin/announcements}; any authenticated agency user
 * reads the currently-active ones for their own tenant/role/plan under
 * {@code /api/announcements/active} (intentionally outside {@code
 * /api/super-admin/**} so it isn't blocked by the Super-Admin-only rule).
 */
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final TenantRepository tenantRepository;
    private final AnnouncementDismissalRepository announcementDismissalRepository;

    // ── Super Admin management ──────────────────────────────────────────────────

    @GetMapping("/api/super-admin/announcements")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> list() {
        List<Map<String, Object>> data = announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(Map.of("success", true, "message", "Announcements loaded", "data", data));
    }

    @PostMapping("/api/super-admin/announcements")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Announcement announcement = Announcement.builder()
                .title(required(body, "title"))
                .message(required(body, "message"))
                .audience(Announcement.Audience.valueOf(String.valueOf(body.getOrDefault("audience", "ALL"))))
                .audienceValue(body.get("audienceValue") == null ? null : body.get("audienceValue").toString())
                .priority(Announcement.Priority.valueOf(String.valueOf(body.getOrDefault("priority", "NORMAL"))))
                .startsAt(parseDateTime(body.get("startsAt")))
                .endsAt(parseDateTime(body.get("endsAt")))
                .active(Boolean.TRUE.equals(body.getOrDefault("active", true)))
                .type(Announcement.Type.valueOf(String.valueOf(body.getOrDefault("type", "GENERIC"))))
                .platform(body.get("platform") == null ? null : Announcement.Platform.valueOf(body.get("platform").toString()))
                .version(body.get("version") == null ? null : body.get("version").toString())
                .dismissible(Boolean.TRUE.equals(body.getOrDefault("dismissible", true)))
                .cooldownDays(body.get("cooldownDays") == null ? 30 : Integer.parseInt(body.get("cooldownDays").toString()))
                .actionUrl(body.get("actionUrl") == null ? null : body.get("actionUrl").toString())
                .createdBy(currentEmail())
                .build();
        Announcement saved = announcementRepository.save(announcement);
        return ResponseEntity.ok(Map.of("success", true, "message", "Announcement created", "data", toResponse(saved)));
    }

    @PutMapping("/api/super-admin/announcements/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found"));
        if (body.get("title") != null) announcement.setTitle(body.get("title").toString());
        if (body.get("message") != null) announcement.setMessage(body.get("message").toString());
        if (body.get("audience") != null) announcement.setAudience(Announcement.Audience.valueOf(body.get("audience").toString()));
        if (body.containsKey("audienceValue")) announcement.setAudienceValue(body.get("audienceValue") == null ? null : body.get("audienceValue").toString());
        if (body.get("priority") != null) announcement.setPriority(Announcement.Priority.valueOf(body.get("priority").toString()));
        if (body.containsKey("startsAt")) announcement.setStartsAt(parseDateTime(body.get("startsAt")));
        if (body.containsKey("endsAt")) announcement.setEndsAt(parseDateTime(body.get("endsAt")));
        if (body.get("active") != null) announcement.setActive(Boolean.TRUE.equals(body.get("active")));
        if (body.get("type") != null) announcement.setType(Announcement.Type.valueOf(body.get("type").toString()));
        if (body.containsKey("platform")) announcement.setPlatform(body.get("platform") == null ? null : Announcement.Platform.valueOf(body.get("platform").toString()));
        if (body.containsKey("version")) announcement.setVersion(body.get("version") == null ? null : body.get("version").toString());
        if (body.get("dismissible") != null) announcement.setDismissible(Boolean.TRUE.equals(body.get("dismissible")));
        if (body.get("cooldownDays") != null) announcement.setCooldownDays(Integer.parseInt(body.get("cooldownDays").toString()));
        if (body.containsKey("actionUrl")) announcement.setActionUrl(body.get("actionUrl") == null ? null : body.get("actionUrl").toString());
        Announcement saved = announcementRepository.save(announcement);
        return ResponseEntity.ok(Map.of("success", true, "message", "Announcement updated", "data", toResponse(saved)));
    }

    @PatchMapping("/api/super-admin/announcements/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found"));
        announcement.setActive(Boolean.TRUE.equals(body.get("active")));
        announcementRepository.save(announcement);
        return ResponseEntity.ok(Map.of("success", true,
                "message", announcement.isActive() ? "Announcement activated" : "Announcement deactivated"));
    }

    // ── Agency-facing read ───────────────────────────────────────────────────────

    @GetMapping("/api/announcements/active")
    public ResponseEntity<Map<String, Object>> active(@RequestParam(required = false) String platform) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantId == null ? null : tenantRepository.findById(tenantId).orElse(null);
        String role = currentRole();
        Long userId = currentUserId();
        Set<Long> dismissedIds = userId == null ? Set.of()
                : announcementDismissalRepository.findByUserId(userId).stream()
                        .map(AnnouncementDismissal::getAnnouncementId).collect(java.util.stream.Collectors.toSet());
        Map<Long, LocalDateTime> dismissedAt = userId == null ? Map.of()
                : announcementDismissalRepository.findByUserId(userId).stream()
                        .collect(java.util.stream.Collectors.toMap(AnnouncementDismissal::getAnnouncementId, AnnouncementDismissal::getDismissedAt));

        List<Map<String, Object>> visible = announcementRepository.findActive(LocalDateTime.now()).stream()
                .filter(a -> matchesAudience(a, tenant, role))
                .filter(a -> matchesPlatform(a, platform))
                .filter(a -> !dismissedIds.contains(a.getId()) || isCooldownElapsed(a, dismissedAt.get(a.getId())))
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "message", "Active announcements loaded", "data", visible));
    }

    /**
     * Persists that the current user dismissed this announcement — see
     * AnnouncementDismissal. The frontend also keeps a localStorage mirror
     * for instant re-render, but this is the source of truth across
     * sessions/devices, and what the cooldown-days check above relies on.
     */
    @PostMapping("/api/announcements/{id}/dismiss")
    public ResponseEntity<Map<String, Object>> dismiss(@PathVariable Long id) {
        Long userId = currentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("Must be authenticated to dismiss an announcement");
        }
        AnnouncementDismissal dismissal = announcementDismissalRepository.findByAnnouncementIdAndUserId(id, userId)
                .orElseGet(() -> AnnouncementDismissal.builder().announcementId(id).userId(userId).build());
        dismissal.setDismissedAt(LocalDateTime.now());
        announcementDismissalRepository.save(dismissal);
        return ResponseEntity.ok(Map.of("success", true, "message", "Announcement dismissed"));
    }

    private boolean matchesPlatform(Announcement a, String requestPlatform) {
        if (a.getPlatform() == null) return true;
        if (requestPlatform == null) return false;
        return a.getPlatform().name().equalsIgnoreCase(requestPlatform);
    }

    private boolean isCooldownElapsed(Announcement a, LocalDateTime dismissedAt) {
        if (dismissedAt == null) return true;
        int cooldownDays = a.getCooldownDays() <= 0 ? 30 : a.getCooldownDays();
        return dismissedAt.plusDays(cooldownDays).isBefore(LocalDateTime.now());
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof User user ? user.getId() : null;
    }

    private boolean matchesAudience(Announcement a, Tenant tenant, String role) {
        return switch (a.getAudience()) {
            case ALL -> true;
            case SELECTED_AGENCIES -> tenant != null && a.getAudienceValue() != null
                    && Arrays.asList(a.getAudienceValue().split(",")).contains(String.valueOf(tenant.getId()));
            case PLAN -> tenant != null && a.getAudienceValue() != null
                    && a.getAudienceValue().equalsIgnoreCase(tenant.getPlanName());
            case ROLE -> role != null && a.getAudienceValue() != null && a.getAudienceValue().equalsIgnoreCase(role);
        };
    }

    private String currentRole() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof User user && user.getRole() != null ? user.getRole().name() : null;
    }

    private String currentEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof User user ? user.getEmail() : "system";
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString();
    }

    private LocalDateTime parseDateTime(Object value) {
        return value == null || value.toString().isBlank() ? null : LocalDateTime.parse(value.toString());
    }

    private Map<String, Object> toResponse(Announcement a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("title", a.getTitle());
        map.put("message", a.getMessage());
        map.put("audience", a.getAudience());
        map.put("audienceValue", a.getAudienceValue());
        map.put("priority", a.getPriority());
        map.put("startsAt", a.getStartsAt());
        map.put("endsAt", a.getEndsAt());
        map.put("active", a.isActive());
        map.put("type", a.getType());
        map.put("platform", a.getPlatform());
        map.put("version", a.getVersion());
        map.put("dismissible", a.isDismissible());
        map.put("cooldownDays", a.getCooldownDays());
        map.put("actionUrl", a.getActionUrl());
        map.put("createdBy", a.getCreatedBy());
        map.put("createdAt", a.getCreatedAt());
        return map;
    }
}
