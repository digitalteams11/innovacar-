package com.carrental.controller;

import com.carrental.entity.DesktopRelease;
import com.carrental.repository.DesktopDownloadEventRepository;
import com.carrental.repository.DesktopReleaseRepository;
import com.carrental.entity.User;
import com.carrental.service.DesktopReleaseValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Super-Admin-only desktop release management: create/update draft releases,
 * publish/deprecate/withdraw them, and view download analytics. Mirrors the
 * AnnouncementController convention (Map-based request/response, no separate
 * DTO/service layer beyond the pure {@link DesktopReleaseValidator} rules).
 * Only Super Admin may reach this — see SecurityConfig's
 * {@code /api/super-admin/**} -> hasRole('SUPER_ADMIN') rule; normal agency
 * admins have no path to publish a release.
 */
@RestController
@RequestMapping("/api/super-admin/desktop/releases")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminDesktopReleaseController {

    private final DesktopReleaseRepository desktopReleaseRepository;
    private final DesktopDownloadEventRepository desktopDownloadEventRepository;
    private final DesktopReleaseValidator validator;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Map<String, Object>> data = desktopReleaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(Map.of("success", true, "message", "Releases loaded", "data", data));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String version = required(body, "version");
        String semanticVersion = String.valueOf(body.getOrDefault("semanticVersion", version));
        String downloadUrl = required(body, "downloadUrl");

        if (!validator.isValidSemanticVersion(semanticVersion)) {
            throw new IllegalArgumentException("semanticVersion must be a valid MAJOR.MINOR.PATCH version");
        }
        if (!validator.isAllowedDownloadUrl(downloadUrl)) {
            throw new IllegalArgumentException("downloadUrl must be an HTTPS URL on an approved release-hosting domain");
        }
        String sha256 = body.get("sha256") == null ? null : body.get("sha256").toString();
        if (!validator.isValidSha256(sha256)) {
            throw new IllegalArgumentException("sha256 must be a 64-character hex digest");
        }

        DesktopRelease release = DesktopRelease.builder()
                .platform(DesktopRelease.Platform.valueOf(String.valueOf(body.getOrDefault("platform", "WINDOWS"))))
                .architecture(DesktopRelease.Architecture.valueOf(String.valueOf(body.getOrDefault("architecture", "X64"))))
                .version(version)
                .semanticVersion(semanticVersion)
                .channel(DesktopRelease.Channel.valueOf(String.valueOf(body.getOrDefault("channel", "STABLE"))))
                .status(DesktopRelease.Status.DRAFT)
                .fileName(required(body, "fileName"))
                .downloadUrl(downloadUrl)
                .fileSizeBytes(body.get("fileSizeBytes") == null ? null : Long.valueOf(body.get("fileSizeBytes").toString()))
                .sha256(sha256)
                .minimumOs(body.get("minimumOs") == null ? "Windows 10" : body.get("minimumOs").toString())
                .mandatoryUpdate(Boolean.TRUE.equals(body.get("mandatoryUpdate")))
                .releaseNotesEn(str(body, "releaseNotesEn"))
                .releaseNotesFr(str(body, "releaseNotesFr"))
                .releaseNotesAr(str(body, "releaseNotesAr"))
                .createdBy(currentEmail())
                .build();

        DesktopRelease saved = desktopReleaseRepository.save(release);
        return ResponseEntity.ok(Map.of("success", true, "message", "Release created as draft", "data", toResponse(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        DesktopRelease release = mustFind(id);
        if (release.getStatus() == DesktopRelease.Status.PUBLISHED) {
            throw new IllegalArgumentException("A published release's metadata is immutable — withdraw it and create a new one instead");
        }

        if (body.get("version") != null) release.setVersion(body.get("version").toString());
        if (body.get("semanticVersion") != null) {
            String semanticVersion = body.get("semanticVersion").toString();
            if (!validator.isValidSemanticVersion(semanticVersion)) {
                throw new IllegalArgumentException("semanticVersion must be a valid MAJOR.MINOR.PATCH version");
            }
            release.setSemanticVersion(semanticVersion);
        }
        if (body.get("downloadUrl") != null) {
            String downloadUrl = body.get("downloadUrl").toString();
            if (!validator.isAllowedDownloadUrl(downloadUrl)) {
                throw new IllegalArgumentException("downloadUrl must be an HTTPS URL on an approved release-hosting domain");
            }
            release.setDownloadUrl(downloadUrl);
        }
        if (body.containsKey("sha256")) {
            String sha256 = body.get("sha256") == null ? null : body.get("sha256").toString();
            if (!validator.isValidSha256(sha256)) {
                throw new IllegalArgumentException("sha256 must be a 64-character hex digest");
            }
            release.setSha256(sha256);
        }
        if (body.get("fileName") != null) release.setFileName(body.get("fileName").toString());
        if (body.get("fileSizeBytes") != null) release.setFileSizeBytes(Long.valueOf(body.get("fileSizeBytes").toString()));
        if (body.get("minimumOs") != null) release.setMinimumOs(body.get("minimumOs").toString());
        if (body.get("mandatoryUpdate") != null) release.setMandatoryUpdate(Boolean.TRUE.equals(body.get("mandatoryUpdate")));
        if (body.get("channel") != null) release.setChannel(DesktopRelease.Channel.valueOf(body.get("channel").toString()));
        if (body.containsKey("releaseNotesEn")) release.setReleaseNotesEn(str(body, "releaseNotesEn"));
        if (body.containsKey("releaseNotesFr")) release.setReleaseNotesFr(str(body, "releaseNotesFr"));
        if (body.containsKey("releaseNotesAr")) release.setReleaseNotesAr(str(body, "releaseNotesAr"));

        DesktopRelease saved = desktopReleaseRepository.save(release);
        return ResponseEntity.ok(Map.of("success", true, "message", "Release updated", "data", toResponse(saved)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable Long id) {
        DesktopRelease release = mustFind(id);
        if (!validator.isAllowedDownloadUrl(release.getDownloadUrl())) {
            throw new IllegalArgumentException("Cannot publish: downloadUrl is not an approved HTTPS release host");
        }
        if (!validator.isValidSemanticVersion(release.getSemanticVersion())) {
            throw new IllegalArgumentException("Cannot publish: semanticVersion is not valid");
        }
        release.setStatus(DesktopRelease.Status.PUBLISHED);
        release.setPublishedAt(LocalDateTime.now());
        desktopReleaseRepository.save(release);
        return ResponseEntity.ok(Map.of("success", true, "message", "Release " + release.getVersion() + " published"));
    }

    @PostMapping("/{id}/deprecate")
    public ResponseEntity<Map<String, Object>> deprecate(@PathVariable Long id) {
        DesktopRelease release = mustFind(id);
        release.setStatus(DesktopRelease.Status.DEPRECATED);
        desktopReleaseRepository.save(release);
        return ResponseEntity.ok(Map.of("success", true, "message", "Release " + release.getVersion() + " deprecated"));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@PathVariable Long id) {
        DesktopRelease release = mustFind(id);
        release.setStatus(DesktopRelease.Status.WITHDRAWN);
        desktopReleaseRepository.save(release);
        return ResponseEntity.ok(Map.of("success", true, "message", "Release " + release.getVersion() + " withdrawn"));
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        Map<String, Long> byVersion = new LinkedHashMap<>();
        desktopDownloadEventRepository.countByVersion().forEach(row -> byVersion.put(row.getVersion(), row.getTotal()));

        Map<String, Long> bySource = new LinkedHashMap<>();
        desktopDownloadEventRepository.countBySource().forEach(row -> bySource.put(row.getSource().name(), row.getTotal()));

        DesktopRelease latestPublished = desktopReleaseRepository
                .findLatestPublished(DesktopRelease.Platform.WINDOWS, DesktopRelease.Architecture.X64, DesktopRelease.Channel.STABLE)
                .orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("downloadsByVersion", byVersion);
        data.put("downloadsBySource", bySource);
        data.put("currentPublishedVersion", latestPublished == null ? null : latestPublished.getVersion());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private DesktopRelease mustFind(Long id) {
        return desktopReleaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Release not found"));
    }

    private String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.toString();
    }

    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private String currentEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof User user ? user.getEmail() : "system";
    }

    private Map<String, Object> toResponse(DesktopRelease r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("platform", r.getPlatform());
        map.put("architecture", r.getArchitecture());
        map.put("version", r.getVersion());
        map.put("semanticVersion", r.getSemanticVersion());
        map.put("channel", r.getChannel());
        map.put("status", r.getStatus());
        map.put("fileName", r.getFileName());
        map.put("downloadUrl", r.getDownloadUrl());
        map.put("fileSizeBytes", r.getFileSizeBytes());
        map.put("sha256", r.getSha256());
        map.put("minimumOs", r.getMinimumOs());
        map.put("mandatoryUpdate", r.isMandatoryUpdate());
        map.put("publishedAt", r.getPublishedAt());
        map.put("releaseNotesEn", r.getReleaseNotesEn());
        map.put("releaseNotesFr", r.getReleaseNotesFr());
        map.put("releaseNotesAr", r.getReleaseNotesAr());
        map.put("createdBy", r.getCreatedBy());
        map.put("createdAt", r.getCreatedAt());
        return map;
    }
}
