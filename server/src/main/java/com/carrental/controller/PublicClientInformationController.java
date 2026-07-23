package com.carrental.controller;

import com.carrental.dto.clientinfo.ClientInformationSubmitRequest;
import com.carrental.dto.clientinfo.PublicClientInformationView;
import com.carrental.service.ClientInformationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (unauthenticated) endpoints for the client self-fill form. No
 * internal IDs are ever accepted or returned here — only the opaque secure
 * token in the URL (see ClientInformationRequestService for token handling).
 */
@Slf4j
@RestController
@RequestMapping("/api/public/client-information")
@RequiredArgsConstructor
public class PublicClientInformationController {

    private final ClientInformationRequestService service;

    @GetMapping("/{token}")
    public ResponseEntity<PublicClientInformationView> get(@PathVariable String token) {
        log.info("PUBLIC_CLIENT_INFO_LOOKUP tokenPrefix={}", maskToken(token));
        return ResponseEntity.ok(service.getPublic(token));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<Void> submit(@PathVariable String token, @Valid @RequestBody ClientInformationSubmitRequest submission) {
        service.submit(token, submission);
        log.info("PUBLIC_CLIENT_INFO_SUBMIT tokenPrefix={}", maskToken(token));
        return ResponseEntity.noContent().build();
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 6) return "***";
        return token.substring(0, 6) + "...";
    }
}
