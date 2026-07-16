package com.carrental.bootstrap;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SuperAdminBootstrapRunner}.
 * Uses Mockito — no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapRunnerTest {

    private static final String VALID_EMAIL = "founder@innovax.tech";
    private static final String VALID_PASSWORD = "Str0ng!Passw0rd";

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private SuperAdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        runner = new SuperAdminBootstrapRunner(userRepository, tenantRepository, passwordEncoder);
    }

    private void configure(boolean enabled, String email, String password, String name) {
        ReflectionTestUtils.setField(runner, "enabled", enabled);
        ReflectionTestUtils.setField(runner, "email", email);
        ReflectionTestUtils.setField(runner, "password", password);
        ReflectionTestUtils.setField(runner, "name", name);
    }

    @Test
    void createsSuperAdminWhenNoneExists() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, "Ada Lovelace");
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(VALID_EMAIL)).thenReturn(Optional.empty());
        Tenant systemTenant = Tenant.builder().id(1L).email("system@innovax.tech").name("Innovax Technologies").build();
        when(tenantRepository.findByEmail("system@innovax.tech")).thenReturn(Optional.of(systemTenant));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo(VALID_EMAIL);
        assertThat(saved.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getTenant()).isEqualTo(systemTenant);
        assertThat(saved.getAccountEnabled()).isTrue();
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getLockedUntil()).isNull();
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
    }

    @Test
    void repairsExistingAccountInsteadOfSkippingOrDuplicating() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        User existing = User.builder()
                .id(42L)
                .email(VALID_EMAIL)
                .password("old-stale-hash")
                .role(Role.ADMIN)
                .tenant(Tenant.builder().id(9L).build())
                .accountEnabled(false)
                .emailVerified(false)
                .lockedUntil(LocalDateTime.now().plusMinutes(30))
                .failedLoginAttempts(5)
                .build();
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(VALID_EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("new-bcrypt-hash");
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        // Never created a new tenant / new row — same id, same tenant, one save call.
        verify(tenantRepository, never()).save(any());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getPassword()).isEqualTo("new-bcrypt-hash");
        assertThat(saved.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(saved.getAccountEnabled()).isTrue();
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getLockedUntil()).isNull();
        assertThat(saved.getFailedLoginAttempts()).isZero();
    }

    @Test
    void aPreExistingUnrelatedSuperAdminDoesNotBlockTheBootstrapEmail() {
        // Regression: an old/legacy SUPER_ADMIN (e.g. from the demo bootstrap)
        // must never cause this runner to skip the BOOTSTRAP_SUPERADMIN_EMAIL account.
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(VALID_EMAIL)).thenReturn(Optional.empty());
        when(tenantRepository.findByEmail("system@innovax.tech"))
                .thenReturn(Optional.of(Tenant.builder().id(1L).build()));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void passwordIsBCryptEncodedNeverStoredInPlainText() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(VALID_EMAIL)).thenReturn(Optional.empty());
        when(tenantRepository.findByEmail("system@innovax.tech"))
                .thenReturn(Optional.of(Tenant.builder().id(1L).build()));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("bcrypt-hash").isNotEqualTo(VALID_PASSWORD);
        verify(passwordEncoder).encode(VALID_PASSWORD);
    }

    @Test
    void disabledBootstrapCreatesNothing() {
        configure(false, VALID_EMAIL, VALID_PASSWORD, null);

        runner.run(null);

        verify(userRepository, never()).findFirstByEmailIgnoreCaseOrderByIdAsc(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsWeakPasswordWithoutCrashingStartup() {
        configure(true, VALID_EMAIL, "password", null);

        // run() is a Spring ApplicationRunner invoked after Tomcat is already up —
        // it must never let a misconfigured env var propagate and crash the whole
        // application context (see the "Deliberately NOT rethrown" comment on
        // SuperAdminBootstrapRunner.run()). A weak/invalid password is misconfiguration,
        // not a reason to fail the Railway healthcheck.
        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidEmailWithoutCrashingStartup() {
        configure(true, "not-an-email", VALID_PASSWORD, null);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }
}
