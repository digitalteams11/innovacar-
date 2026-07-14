package com.carrental.legal.controller;

import com.carrental.entity.User;
import com.carrental.legal.dto.CookiePreferenceRequest;
import com.carrental.legal.dto.CookiePreferenceResponse;
import com.carrental.legal.service.CookieConsentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Cookie-category preferences. Two path groups because consent must be
 * capturable *before* login (the banner shows on first visit):
 *  - /api/public/legal/cookies — anonymous, keyed by a client-generated UUID
 *    (permitAll via SecurityConfig's "/api/public/**" rule).
 *  - /api/legal/cookies — authenticated, keyed by the logged-in user, and
 *    merges in any prior anonymous choice if an anonymousId is supplied.
 */
@RestController
@RequiredArgsConstructor
public class CookiePreferenceController {

    private final CookieConsentService cookieConsentService;

    @GetMapping("/api/public/legal/cookies")
    public CookiePreferenceResponse getAnonymousPreferences(@RequestParam(required = false) String anonymousId) {
        return cookieConsentService.getPreferences(null, anonymousId);
    }

    @PostMapping("/api/public/legal/cookies")
    public CookiePreferenceResponse saveAnonymousPreferences(
            @RequestBody CookiePreferenceRequest request, HttpServletRequest httpRequest) {
        return cookieConsentService.savePreferences(null, null, request, resolveClientIp(httpRequest));
    }

    @GetMapping("/api/legal/cookies")
    public CookiePreferenceResponse getMyPreferences(
            Authentication authentication, @RequestParam(required = false) String anonymousId) {
        return cookieConsentService.getPreferences(currentUser(authentication).getId(), anonymousId);
    }

    @PostMapping("/api/legal/cookies")
    public CookiePreferenceResponse saveMyPreferences(
            Authentication authentication, @RequestBody CookiePreferenceRequest request, HttpServletRequest httpRequest) {
        User user = currentUser(authentication);
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;
        return cookieConsentService.savePreferences(user.getId(), tenantId, request, resolveClientIp(httpRequest));
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
