package com.carrental.controller;

import com.carrental.entity.AuditLog;
import com.carrental.entity.User;
import com.carrental.repository.AuditLogRepository;
import com.carrental.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Best-effort sink for client-side failures. Logging a frontend error must
 * never itself fail the request — if the audit-log write throws for any
 * reason, swallow it and still answer 200 so the page that's already
 * recovering from one failure doesn't take a second hit from the reporter.
 */
@RestController
@RequestMapping("/api/client-errors")
@RequiredArgsConstructor
@Slf4j
public class ClientErrorController {
    private final AuditLogRepository auditLogRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> log(
            @RequestBody(required = false) Map<String, Object> payload,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        try {
            Map<String, Object> body = payload != null ? payload : Map.of();
            auditLogRepository.save(AuditLog.builder()
                    .action("CLIENT_API_ERROR")
                    .entityType("FRONTEND")
                    .description(limit(body.toString(), 1000))
                    .performedBy(user != null ? user.getEmail() : "unknown")
                    .performedById(user != null ? user.getId() : null)
                    .tenantId(TenantContext.getCurrentTenantId())
                    .ipAddress(request.getRemoteAddr())
                    .userAgent(limit(request.getHeader("User-Agent"), 500))
                    .isSuccess(false)
                    .errorMessage(limit(String.valueOf(body.getOrDefault("userMessage", "API request failed")), 500))
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to persist client error report: {}", ex.getMessage());
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Frontends that probe this endpoint with a GET (e.g. to confirm it's
     * reachable) get an empty list instead of a 405/404.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
