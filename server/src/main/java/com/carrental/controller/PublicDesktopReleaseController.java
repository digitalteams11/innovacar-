package com.carrental.controller;

import com.carrental.entity.DesktopDownloadEvent;
import com.carrental.entity.DesktopRelease;
import com.carrental.entity.User;
import com.carrental.repository.DesktopDownloadEventRepository;
import com.carrental.repository.DesktopReleaseRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only, unauthenticated release metadata + download-event recording for
 * the public landing page, the public {@code /desktop} marketing page, and
 * the authenticated Desktop App page (all three call this same endpoint so
 * release metadata is never duplicated — see PublicDesktopReleaseController
 * usage from useDesktopRelease() on the frontend). Never exposes anything
 * beyond a PUBLISHED release; DRAFT/DEPRECATED/WITHDRAWN rows are invisible
 * here — see SuperAdminDesktopReleaseController for the management side.
 */
@RestController
@RequestMapping("/api/public/desktop")
@RequiredArgsConstructor
public class PublicDesktopReleaseController {

    private final DesktopReleaseRepository desktopReleaseRepository;
    private final DesktopDownloadEventRepository desktopDownloadEventRepository;

    @GetMapping("/releases/latest")
    public ResponseEntity<Map<String, Object>> latest(
            @RequestParam(defaultValue = "WINDOWS") String platform,
            @RequestParam(defaultValue = "X64") String arch,
            @RequestParam(defaultValue = "STABLE") String channel) {

        DesktopRelease.Platform platformEnum = parseEnum(DesktopRelease.Platform.class, platform);
        DesktopRelease.Architecture archEnum = parseEnum(DesktopRelease.Architecture.class, arch);
        DesktopRelease.Channel channelEnum = parseEnum(DesktopRelease.Channel.class, channel);

        if (platformEnum == null || archEnum == null || channelEnum == null) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        Optional<DesktopRelease> latest = desktopReleaseRepository
                .findLatestPublished(platformEnum, archEnum, channelEnum);

        if (latest.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        return ResponseEntity.ok(toResponse(latest.get()));
    }

    /**
     * Records that a download was started (or failed to start). Deliberately
     * lightweight and privacy-safe — no IP address, no device fingerprint.
     * Works both for anonymous (public landing/desktop page) and
     * authenticated (in-app) requests: the agency ID is only attached when a
     * real authenticated principal is present.
     */
    @PostMapping("/downloads")
    public ResponseEntity<Map<String, Object>> recordDownload(@RequestBody Map<String, Object> body) {
        Long releaseId = body.get("releaseId") == null ? null : Long.valueOf(body.get("releaseId").toString());
        String source = String.valueOf(body.getOrDefault("source", "LANDING"));
        String status = String.valueOf(body.getOrDefault("status", "STARTED"));

        DesktopDownloadEvent.Source sourceEnum = parseEnum(DesktopDownloadEvent.Source.class, source);
        DesktopDownloadEvent.Status statusEnum = parseEnum(DesktopDownloadEvent.Status.class, status);
        if (sourceEnum == null || statusEnum == null) {
            throw new IllegalArgumentException("Invalid source or status");
        }

        DesktopRelease release = releaseId == null ? null : desktopReleaseRepository.findById(releaseId).orElse(null);

        DesktopDownloadEvent event = DesktopDownloadEvent.builder()
                .releaseId(release == null ? null : release.getId())
                .version(release == null ? null : release.getVersion())
                .platform(release == null ? null : release.getPlatform().name())
                .architecture(release == null ? null : release.getArchitecture().name())
                .agencyId(currentTenantId())
                .source(sourceEnum)
                .status(statusEnum)
                .build();
        desktopDownloadEventRepository.save(event);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private Long currentTenantId() {
        try {
            Long tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) return tenantId;
            Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                    : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return principal instanceof User user && user.getTenant() != null ? user.getTenant().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toResponse(DesktopRelease r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("available", true);
        map.put("releaseId", r.getId());
        map.put("platform", r.getPlatform().name());
        map.put("architecture", r.getArchitecture().name());
        map.put("version", r.getVersion());
        map.put("channel", r.getChannel().name());
        map.put("releaseDate", r.getPublishedAt());
        map.put("fileName", r.getFileName());
        map.put("downloadUrl", r.getDownloadUrl());
        map.put("fileSizeBytes", r.getFileSizeBytes());
        map.put("sha256", r.getSha256());
        map.put("minimumOs", r.getMinimumOs());
        map.put("mandatoryUpdate", r.isMandatoryUpdate());
        map.put("releaseNotes", Map.of(
                "en", splitLines(r.getReleaseNotesEn()),
                "fr", splitLines(r.getReleaseNotesFr()),
                "ar", splitLines(r.getReleaseNotesAr())));
        return map;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }
}
