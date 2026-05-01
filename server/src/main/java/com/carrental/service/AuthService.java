package com.carrental.service;

import com.carrental.dto.AuthResponse;
import com.carrental.dto.LoginRequest;
import com.carrental.dto.SignupRequest;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Business logic for tenant signup and user login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository    tenantRepository;
    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtTokenProvider    jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    // ── Signup ───────────────────────────────────────────────────────────────

    /**
     * Creates a new tenant and its first admin user in a single transaction.
     *
     * @param request signup payload
     * @return JWT + user metadata
     * @throws IllegalArgumentException if tenant name/email already taken
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // Guard: unique tenant name
        if (tenantRepository.existsByName(request.getTenantName())) {
            throw new IllegalArgumentException(
                "Tenant name already in use: " + request.getTenantName());
        }
        // Guard: unique tenant email
        if (tenantRepository.existsByEmail(request.getTenantEmail())) {
            throw new IllegalArgumentException(
                "Tenant email already in use: " + request.getTenantEmail());
        }

        // 1. Persist tenant
        LocalDate endDate = request.getSubscriptionEndDate() != null
                ? request.getSubscriptionEndDate()
                : LocalDate.now().plusYears(1);

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.getTenantName())
                .email(request.getTenantEmail())
                .subscriptionActive(true)
                .subscriptionEndDate(endDate)
                .build());

        log.info("Created tenant [id={}] '{}'", tenant.getId(), tenant.getName());

        // 2. Guard: admin email not already taken in this tenant
        if (userRepository.existsByEmailAndTenantId(request.getAdminEmail(), tenant.getId())) {
            throw new IllegalArgumentException("Admin email already registered for this tenant.");
        }

        // 3. Persist admin user
        User admin = userRepository.save(User.builder()
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .tenant(tenant)
                .build());

        log.info("Created admin user [id={}] '{}' for tenant [id={}]",
                 admin.getId(), admin.getEmail(), tenant.getId());

        // 4. Issue JWT
        String token = jwtTokenProvider.generateToken(admin);

        return buildResponse(token, admin);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by email + password and returns a fresh JWT.
     *
     * @param request login payload
     * @return JWT + user metadata
     */
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        User user  = (User) auth.getPrincipal();
        String token = jwtTokenProvider.generateToken(user);

        log.info("User [id={}] '{}' (tenant {}) logged in",
                 user.getId(), user.getEmail(), user.getTenant().getId());

        return buildResponse(token, user);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AuthResponse buildResponse(String token, User user) {
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenant().getId())
                .tenantName(user.getTenant().getName())
                .build();
    }
}
