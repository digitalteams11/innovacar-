package com.carrental.controller;

import com.carrental.entity.User;
import com.carrental.security.TenantContext;
import com.carrental.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Long userId = currentUserId();
        log.info("SSE subscription request from tenant {} user {}", tenantId, userId);
        return sseService.subscribe(tenantId, userId);
    }

    /**
     * Safe operational metrics — no secrets, no user personal data (Part 9).
     * Super Admin only.
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SseService.SseMetrics metrics() {
        return sseService.metrics();
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getId();
        throw new IllegalStateException("SSE subscribe requires an authenticated user");
    }
}
