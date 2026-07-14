package com.carrental.legal.controller;

import com.carrental.entity.User;
import com.carrental.legal.dto.PrivacyRequestCreateRequest;
import com.carrental.legal.dto.PrivacyRequestDto;
import com.carrental.legal.service.PrivacyRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Self-service data-subject rights requests (access, rectification,
 * deletion, export, objection, restriction). Any authenticated user may
 * submit and view their own requests; admin triage lives under
 * SuperAdminLegalController.
 */
@RestController
@RequestMapping("/api/legal/privacy-requests")
@RequiredArgsConstructor
public class PrivacyRequestController {

    private final PrivacyRequestService privacyRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrivacyRequestDto create(Authentication authentication, @Valid @RequestBody PrivacyRequestCreateRequest request) {
        return privacyRequestService.create(currentUser(authentication), request);
    }

    @GetMapping("/me")
    public List<PrivacyRequestDto> getMine(Authentication authentication) {
        return privacyRequestService.getMine(currentUser(authentication).getId());
    }

    private User currentUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalStateException("Authenticated user not found");
        }
        return user;
    }
}
