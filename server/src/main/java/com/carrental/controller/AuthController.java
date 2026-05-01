package com.carrental.controller;

import com.carrental.dto.AuthResponse;
import com.carrental.dto.LoginRequest;
import com.carrental.dto.SignupRequest;
import com.carrental.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints — no JWT required.
 *
 * <pre>
 * POST /api/auth/signup   – Create tenant + admin user → returns JWT
 * POST /api/auth/login    – Authenticate user          → returns JWT
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── POST /api/auth/signup ────────────────────────────────────────────────

    /**
     * Registers a new tenant together with its first administrator account.
     *
     * @param request tenant + admin user payload
     * @return 201 Created + JWT response
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────

    /**
     * Authenticates an existing user and issues a JWT containing the tenantId.
     *
     * @param request email + password
     * @return 200 OK + JWT response
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
