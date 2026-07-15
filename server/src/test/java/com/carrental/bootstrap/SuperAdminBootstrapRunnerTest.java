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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(VALID_EMAIL)).thenReturn(false);
        Tenant systemTenant = Tenant.builder().id(1L).email("system@innovax.tech").name("Innovax Technologies").build();
        when(tenantRepository.findByEmail("system@innovax.tech")).thenReturn(Optional.of(systemTenant));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");

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
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
    }

    @Test
    void doesNotDuplicateWhenSuperAdminAlreadyExists() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(true);

        runner.run(null);

        verify(userRepository, never()).save(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void doesNotDuplicateWhenEmailAlreadyExists() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(VALID_EMAIL)).thenReturn(true);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordIsBCryptEncodedNeverStoredInPlainText() {
        configure(true, VALID_EMAIL, VALID_PASSWORD, null);
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(VALID_EMAIL)).thenReturn(false);
        when(tenantRepository.findByEmail("system@innovax.tech"))
                .thenReturn(Optional.of(Tenant.builder().id(1L).build()));
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("bcrypt-hash");

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

        verify(userRepository, never()).existsByRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsWeakPassword() {
        configure(true, VALID_EMAIL, "password", null);
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_SUPERADMIN_PASSWORD");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidEmail() {
        configure(true, "not-an-email", VALID_PASSWORD, null);
        when(userRepository.existsByRole(Role.SUPER_ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_SUPERADMIN_EMAIL");
        verify(userRepository, never()).save(any());
    }
}
