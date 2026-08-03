package com.carrental.controller;

import com.carrental.dto.contract.AdditionalDriverDeclineRequest;
import com.carrental.dto.contract.AdditionalDriverSignatureSubmitRequest;
import com.carrental.dto.contract.PublicAdditionalDriverSigningResponse;
import com.carrental.service.AdditionalDriverSigningService;
import com.carrental.util.MaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Public (unauthenticated) endpoints for the additional-driver independent
 * signing workflow. Never accepts/returns internal DB ids — only the opaque
 * secure token in the URL. Mirrors PublicClientInformationController's
 * conventions (no auth, masked tokens in every log line).
 */
@Slf4j
@RestController
@RequestMapping("/api/public/additional-driver-signatures")
@RequiredArgsConstructor
public class PublicAdditionalDriverController {

    private final AdditionalDriverSigningService signingService;

    @GetMapping("/{token}")
    public ResponseEntity<PublicAdditionalDriverSigningResponse> get(@PathVariable String token) {
        log.info("PUBLIC_ADDITIONAL_DRIVER_LOOKUP tokenPrefix={}", MaskingUtil.maskToken(token));
        return ResponseEntity.ok(signingService.getPublicView(token));
    }

    @PostMapping("/{token}/open")
    public ResponseEntity<Void> open(@PathVariable String token) {
        signingService.markOpened(token);
        log.info("PUBLIC_ADDITIONAL_DRIVER_OPEN tokenPrefix={}", MaskingUtil.maskToken(token));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/sign")
    public ResponseEntity<Void> sign(@PathVariable String token,
                                      @RequestBody AdditionalDriverSignatureSubmitRequest request,
                                      HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        signingService.submitSignature(token, request, ip, userAgent);
        log.info("PUBLIC_ADDITIONAL_DRIVER_SIGN tokenPrefix={}", MaskingUtil.maskToken(token));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/decline")
    public ResponseEntity<Void> decline(@PathVariable String token,
                                         @RequestBody(required = false) AdditionalDriverDeclineRequest request,
                                         HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        signingService.declineSignature(token, request, ip, userAgent);
        log.info("PUBLIC_ADDITIONAL_DRIVER_DECLINE tokenPrefix={}", MaskingUtil.maskToken(token));
        return ResponseEntity.noContent().build();
    }

    /** Same X-Forwarded-For-aware IP extraction convention as the rest of the public contract-signing endpoints. */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
