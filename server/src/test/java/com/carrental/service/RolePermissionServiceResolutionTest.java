package com.carrental.service;

import com.carrental.entity.*;
import com.carrental.repository.CustomRolePermissionRepository;
import com.carrental.repository.PermissionDefinitionRepository;
import com.carrental.repository.RolePermissionRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserPermissionOverrideRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the evaluation order documented on
 * {@link RolePermissionService#has(String)}: explicit deny always wins over
 * a role grant, explicit grant works even when the role itself denies,
 * custom-role resolution takes over entirely for a user with a custom role,
 * and a deactivated permission is a hard deny regardless of any stored grant.
 */
@ExtendWith(MockitoExtension.class)
class RolePermissionServiceResolutionTest {

    @Mock private PermissionDefinitionRepository definitionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PermissionSyncService permissionSyncService;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private CustomRolePermissionRepository customRolePermissionRepository;

    private RolePermissionService rolePermissionService;

    @BeforeEach
    void setUp() {
        rolePermissionService = new RolePermissionService(
                definitionRepository, rolePermissionRepository, tenantRepository,
                permissionSyncService, overrideRepository, customRolePermissionRepository);
        ReflectionTestUtils.setField(rolePermissionService, "self", rolePermissionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void explicitDenyOverride_winsOverAnEnabledRoleGrant() {
        Tenant tenant = Tenant.builder().id(1L).build();
        User user = User.builder().id(10L).role(Role.MANAGER).tenant(tenant).build();
        authenticateAs(user);

        activeDefinition("RESERVATION_CANCEL");
        when(overrideRepository.findByUserIdAndPermissionCode(10L, "RESERVATION_CANCEL"))
                .thenReturn(Optional.of(UserPermissionOverride.builder()
                        .overrideType(PermissionOverrideType.DENY).build()));

        assertThat(rolePermissionService.has("RESERVATION_CANCEL")).isFalse();
    }

    @Test
    void explicitGrantOverride_grantsAccessEvenWithNoRolePermissionRowAtAll() {
        Tenant tenant = Tenant.builder().id(1L).build();
        User user = User.builder().id(11L).role(Role.VIEWER).tenant(tenant).build();
        authenticateAs(user);

        activeDefinition("VEHICLE_DELETE");
        when(overrideRepository.findByUserIdAndPermissionCode(11L, "VEHICLE_DELETE"))
                .thenReturn(Optional.of(UserPermissionOverride.builder()
                        .overrideType(PermissionOverrideType.GRANT).build()));

        assertThat(rolePermissionService.has("VEHICLE_DELETE")).isTrue();
    }

    @Test
    void customRoleUser_resolvesFromCustomRolePermissions_ignoringTheSystemRoleTable() {
        Tenant tenant = Tenant.builder().id(1L).build();
        CustomRole customRole = CustomRole.builder().id(5L).tenant(tenant).code("FRONT_DESK").build();
        User user = User.builder().id(12L).role(Role.CUSTOM).tenant(tenant).customRole(customRole).build();
        authenticateAs(user);

        activeDefinition("CLIENT_VIEW");
        when(overrideRepository.findByUserIdAndPermissionCode(anyLong(), anyString())).thenReturn(Optional.empty());
        when(customRolePermissionRepository.findByCustomRoleIdAndPermissionCode(5L, "CLIENT_VIEW"))
                .thenReturn(Optional.of(CustomRolePermission.builder().enabled(true).build()));

        assertThat(rolePermissionService.has("CLIENT_VIEW")).isTrue();
        // A custom-role user is never checked against role_permissions — only their custom role's own grants.
        org.mockito.Mockito.verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void customRoleUser_defaultsToDenyWhenThePermissionIsUnconfiguredForThatCustomRole() {
        Tenant tenant = Tenant.builder().id(1L).build();
        CustomRole customRole = CustomRole.builder().id(5L).tenant(tenant).code("FRONT_DESK").build();
        User user = User.builder().id(13L).role(Role.CUSTOM).tenant(tenant).customRole(customRole).build();
        authenticateAs(user);

        activeDefinition("VEHICLE_DELETE");
        when(overrideRepository.findByUserIdAndPermissionCode(anyLong(), anyString())).thenReturn(Optional.empty());
        when(customRolePermissionRepository.findByCustomRoleIdAndPermissionCode(5L, "VEHICLE_DELETE"))
                .thenReturn(Optional.empty());

        assertThat(rolePermissionService.has("VEHICLE_DELETE")).isFalse();
    }

    @Test
    void deactivatedPermission_isAHardDeny_evenWithAnExplicitGrantOverrideStored() {
        Tenant tenant = Tenant.builder().id(1L).build();
        User user = User.builder().id(14L).role(Role.MANAGER).tenant(tenant).build();
        authenticateAs(user);

        when(definitionRepository.findAllByCode("RETIRED_FEATURE_CODE"))
                .thenReturn(List.of(PermissionDefinition.builder().code("RETIRED_FEATURE_CODE").active(false).build()));

        assertThat(rolePermissionService.has("RETIRED_FEATURE_CODE")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(overrideRepository);
    }

    @Test
    void superAdminAndAgencyOwnerAndAdmin_alwaysBypassResolution() {
        for (Role role : List.of(Role.SUPER_ADMIN, Role.AGENCY_OWNER, Role.ADMIN)) {
            User user = User.builder().id(20L).role(role).build();
            authenticateAs(user);
            assertThat(rolePermissionService.has("ANY_UNKNOWN_CODE")).isTrue();
        }
    }

    private void activeDefinition(String code) {
        lenient().when(definitionRepository.findAllByCode(code))
                .thenReturn(List.of(PermissionDefinition.builder().code(code).active(true).build()));
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
