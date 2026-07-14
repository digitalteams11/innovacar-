package com.carrental.legal.controller;

import com.carrental.entity.User;
import com.carrental.legal.dto.AcceptDocumentRequest;
import com.carrental.legal.dto.LegalAcceptanceDto;
import com.carrental.legal.dto.PendingAcceptanceDto;
import com.carrental.legal.service.LegalAcceptanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated document-acceptance flow: what's still pending for me, and
 * recording my acceptance of a specific published version. Any authenticated
 * role may use this — it's a personal consent action, not a business
 * permission — so there's no @PreAuthorize permission gate here.
 */
@RestController
@RequestMapping("/api/legal/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final LegalAcceptanceService legalAcceptanceService;

    @GetMapping("/pending")
    public List<PendingAcceptanceDto> getPending(Authentication authentication) {
        return legalAcceptanceService.getPendingAcceptances(currentUser(authentication));
    }

    @PostMapping("/accept")
    public LegalAcceptanceDto accept(
            Authentication authentication,
            @RequestBody AcceptDocumentRequest request,
            HttpServletRequest httpRequest) {
        User user = currentUser(authentication);
        return legalAcceptanceService.accept(user, request, resolveClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
    }

    @GetMapping("/history")
    public List<LegalAcceptanceDto> getHistory(Authentication authentication) {
        return legalAcceptanceService.getHistory(currentUser(authentication).getId());
    }

    private User currentUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("Authenticated user not found");
        }
        return user;
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
