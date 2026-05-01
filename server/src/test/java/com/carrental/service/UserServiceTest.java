package com.carrental.service;

import com.carrental.dto.user.CreateUserRequest;
import com.carrental.dto.user.UpdateUserRequest;
import com.carrental.dto.user.UserResponse;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 * Uses Mockito — no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository   userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder  passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final Long TENANT_ID = 1L;
    private static final Long ADMIN_ID  = 10L;
    private static final Long USER_ID   = 20L;

    private Tenant tenant;
    private User   adminUser;
    private User   regularUser;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);

        tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Test Corp")
                .email("billing@testcorp.com")
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build();

        adminUser = User.builder()
                .id(ADMIN_ID)
                .email("admin@testcorp.com")
                .password("hashed")
                .role(Role.ADMIN)
                .tenant(tenant)
                .build();

        regularUser = User.builder()
                .id(USER_ID)
                .email("emp@testcorp.com")
                .password("hashed")
                .role(Role.EMPLOYEE)
                .tenant(tenant)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsTenantScopedList() {
        when(userRepository.findAllByTenantId(TENANT_ID))
                .thenReturn(List.of(adminUser, regularUser));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("admin@testcorp.com", "emp@testcorp.com");
    }

    // ── getUserById ──────────────────────────────────────────────────────────

    @Test
    void getUserById_returnsUser_whenExists() {
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(regularUser));

        UserResponse response = userService.getUserById(USER_ID);

        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void getUserById_throws404_whenNotInTenant() {
        when(userRepository.findByIdAndTenantId(99L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── createUser ───────────────────────────────────────────────────────────

    @Test
    void createUser_persistsAndReturnsResponse() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("new@testcorp.com");
        req.setPassword("Password1!");
        req.setRole(Role.EMPLOYEE);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByEmailAndTenantId("new@testcorp.com", TENANT_ID)).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed_new");

        User saved = User.builder()
                .id(30L)
                .email("new@testcorp.com")
                .password("hashed_new")
                .role(Role.EMPLOYEE)
                .tenant(tenant)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = userService.createUser(req);

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getEmail()).isEqualTo("new@testcorp.com");
        verify(passwordEncoder).encode("Password1!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_throwsConflict_whenEmailAlreadyExists() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("emp@testcorp.com");
        req.setPassword("Password1!");
        req.setRole(Role.EMPLOYEE);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByEmailAndTenantId("emp@testcorp.com", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emp@testcorp.com");
    }

    // ── updateUser ───────────────────────────────────────────────────────────

    @Test
    void updateUser_changesEmailAndRole() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setEmail("updated@testcorp.com");
        req.setRole(Role.ADMIN);

        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(regularUser));
        when(userRepository.existsByEmailAndTenantId("updated@testcorp.com", TENANT_ID))
                .thenReturn(false);

        User updated = User.builder()
                .id(USER_ID).email("updated@testcorp.com")
                .password("hashed").role(Role.ADMIN).tenant(tenant).build();
        when(userRepository.save(any(User.class))).thenReturn(updated);

        UserResponse response = userService.updateUser(USER_ID, req);

        assertThat(response.getEmail()).isEqualTo("updated@testcorp.com");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
    }

    // ── deleteUser ───────────────────────────────────────────────────────────

    @Test
    void deleteUser_removesUser() {
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(regularUser));

        userService.deleteUser(USER_ID, ADMIN_ID);

        verify(userRepository).delete(regularUser);
    }

    @Test
    void deleteUser_throwsWhenAdminDeletesSelf() {
        when(userRepository.findByIdAndTenantId(ADMIN_ID, TENANT_ID))
                .thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.deleteUser(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own account");
    }

    // ── getUsersByRole ───────────────────────────────────────────────────────

    @Test
    void getUsersByRole_filtersCorrectly() {
        when(userRepository.findAllByTenantIdAndRole(TENANT_ID, Role.EMPLOYEE))
                .thenReturn(List.of(regularUser));

        List<UserResponse> result = userService.getUsersByRole(Role.EMPLOYEE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(Role.EMPLOYEE);
    }
}
