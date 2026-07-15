package com.carrental.bootstrap;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * One-time production bootstrap for the very first SUPER_ADMIN account.
 *
 * <p>Entirely opt-in via BOOTSTRAP_SUPERADMIN_* environment variables, so no
 * plaintext credential ever needs to be inserted into the database by hand.
 * The account is created at most once: if any SUPER_ADMIN already exists —
 * whether created by this runner or any other way — every later run is a
 * no-op even if the environment variables are left in place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrapRunner implements ApplicationRunner {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** At least 12 characters, one lowercase, one uppercase, one digit, one symbol. */
    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$");

    private static final String SYSTEM_TENANT_EMAIL = "system@innovax.tech";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.superadmin.enabled:false}")
    private boolean enabled;

    @Value("${app.bootstrap.superadmin.email:}")
    private String email;

    @Value("${app.bootstrap.superadmin.password:}")
    private String password;

    @Value("${app.bootstrap.superadmin.name:}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("BOOTSTRAP_SUPERADMIN_ENABLED is set but a SUPER_ADMIN already exists; skipping bootstrap.");
            return;
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (!hasText(normalizedEmail) || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_SUPERADMIN_ENABLED=true requires a valid BOOTSTRAP_SUPERADMIN_EMAIL.");
        }

        if (!hasText(password) || !STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_SUPERADMIN_ENABLED=true requires BOOTSTRAP_SUPERADMIN_PASSWORD to be at least "
                            + "12 characters and include an uppercase letter, a lowercase letter, a digit and a symbol.");
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("BOOTSTRAP_SUPERADMIN_ENABLED is set but a user with email {} already exists; skipping bootstrap.",
                    normalizedEmail);
            return;
        }

        Tenant systemTenant = tenantRepository.findByEmail(SYSTEM_TENANT_EMAIL)
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Innovax Technologies")
                        .email(SYSTEM_TENANT_EMAIL)
                        .subscriptionActive(true)
                        .subscriptionEndDate(LocalDate.now().plusYears(100))
                        .status("ACTIVE")
                        .planName("Enterprise")
                        .maxVehicles(9999)
                        .maxEmployees(9999)
                        .maxGpsDevices(9999)
                        .maxReservations(99999)
                        .storageLimitMb(1048576)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));

        String firstName = null;
        String lastName = null;
        String trimmedName = name == null ? "" : name.trim();
        if (!trimmedName.isEmpty()) {
            int spaceIndex = trimmedName.indexOf(' ');
            if (spaceIndex > 0) {
                firstName = trimmedName.substring(0, spaceIndex);
                lastName = trimmedName.substring(spaceIndex + 1).trim();
            } else {
                firstName = trimmedName;
            }
        }

        userRepository.save(User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .role(Role.SUPER_ADMIN)
                .tenant(systemTenant)
                .accountEnabled(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .firstName(firstName)
                .lastName(lastName)
                .build());

        log.info("Bootstrap SUPER_ADMIN account created: email={}", normalizedEmail);
        log.warn("SECURITY: remove BOOTSTRAP_SUPERADMIN_PASSWORD and set BOOTSTRAP_SUPERADMIN_ENABLED=false "
                + "now that the initial Super Admin account has been created.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
