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
 * Production bootstrap/repair for the SUPER_ADMIN account named by
 * BOOTSTRAP_SUPERADMIN_EMAIL.
 *
 * <p>Entirely opt-in via BOOTSTRAP_SUPERADMIN_* environment variables, so no
 * plaintext credential ever needs to be inserted into the database by hand.
 * Idempotency is keyed on the bootstrap email specifically, not on "does any
 * SUPER_ADMIN exist" — a pre-existing, unrelated SUPER_ADMIN (e.g. from the
 * legacy demo bootstrap) must never cause this account to be silently
 * skipped. If the account already exists, its password/role/status are
 * repaired on every restart while BOOTSTRAP_SUPERADMIN_ENABLED=true; it is
 * never duplicated.
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
        log.info("BOOTSTRAP_SUPERADMIN_ENABLED={}", enabled);
        if (!enabled) {
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

        User user = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail).orElse(null);
        boolean existed = user != null;

        if (!existed) {
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

            String[] splitName = splitName(name);
            user = User.builder()
                    .email(normalizedEmail)
                    .tenant(systemTenant)
                    .firstName(splitName[0])
                    .lastName(splitName[1])
                    .failedLoginAttempts(0)
                    .build();
        }

        // Repair path applies on both create and update — a pre-existing account
        // with a stale password, wrong role, or locked/disabled state is fixed
        // rather than left broken.
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.SUPER_ADMIN);
        user.setAccountEnabled(true);
        user.setEmailVerified(true);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);

        User saved = userRepository.save(user);
        boolean matches = passwordEncoder.matches(password, saved.getPassword());

        log.info("Bootstrap SUPER_ADMIN {}: email={} role={} accountEnabled={} emailVerified={} locked={} passwordEncoderMatches={}",
                existed ? "updated" : "created",
                normalizedEmail, saved.getRole(), saved.getAccountEnabled(), saved.getEmailVerified(),
                saved.isLocked(), matches);

        if (!matches) {
            log.error("Bootstrap SUPER_ADMIN password verification failed immediately after save for email={}; "
                    + "login will not work until this is investigated.", normalizedEmail);
        }
    }

    private String[] splitName(String rawName) {
        String trimmedName = rawName == null ? "" : rawName.trim();
        if (trimmedName.isEmpty()) {
            return new String[] { null, null };
        }
        int spaceIndex = trimmedName.indexOf(' ');
        if (spaceIndex > 0) {
            return new String[] { trimmedName.substring(0, spaceIndex), trimmedName.substring(spaceIndex + 1).trim() };
        }
        return new String[] { trimmedName, null };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
