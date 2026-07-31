package com.carrental.service;

import com.carrental.dto.rbac.CreateCustomRoleRequest;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock private RolePermissionService rolePermissionService;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CustomRoleRepository customRoleRepository;
    @Mock private CustomRolePermissionRepository customRolePermissionRepository;
    @Mock private PermissionDefinitionRepository definitionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RoleAuditLogService roleAuditLogService;
    @Mock private PermissionResolutionService permissionResolutionService;
    @Mock private HttpServletRequest httpRequest;

    private RoleManagementService roleManagementService;

    @BeforeEach
    void setUp() {
        roleManagementService = new RoleManagementService(
                rolePermissionService, rolePermissionRepository, customRoleRepository,
                customRolePermissionRepository, definitionRepository, userRepository,
                overrideRepository, tenantRepository, roleAuditLogService, permissionResolutionService);
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCustomRole_rejectsADuplicateCodeWithinTheSameTenant() {
        when(customRoleRepository.existsByTenantIdAndCode(1L, "FRONT_DESK")).thenReturn(true);
        CreateCustomRoleRequest request = new CreateCustomRoleRequest();
        request.setCode("FRONT_DESK");
        request.setName("Front Desk");

        assertThatThrownBy(() -> roleManagementService.createCustomRole(request, httpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(customRoleRepository, never()).save(any());
    }

    @Test
    void deleteCustomRole_isBlockedWhileAnyUserIsStillAssigned() {
        CustomRole customRole = CustomRole.builder().id(7L).code("FRONT_DESK").build();
        when(customRoleRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(customRole));
        when(userRepository.countByTenantIdAndCustomRoleId(1L, 7L)).thenReturn(3L);

        assertThatThrownBy(() -> roleManagementService.deleteCustomRole("CUSTOM:7", httpRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 user");

        verify(customRoleRepository, never()).delete(any());
    }

    @Test
    void deleteCustomRole_succeedsAndAuditsWhenUnused() {
        CustomRole customRole = CustomRole.builder().id(8L).code("FRONT_DESK").build();
        when(customRoleRepository.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.of(customRole));
        when(userRepository.countByTenantIdAndCustomRoleId(1L, 8L)).thenReturn(0L);

        roleManagementService.deleteCustomRole("CUSTOM:8", httpRequest);

        verify(customRolePermissionRepository).deleteAllByCustomRoleId(8L);
        verify(customRoleRepository).delete(customRole);
        verify(roleAuditLogService).logRoleDeleted(eq("CUSTOM_ROLE"), eq("FRONT_DESK"), eq(8L), any(), eq(httpRequest));
    }

    @Test
    void getRole_rejectsACustomRoleIdBelongingToAnotherTenant() {
        when(customRoleRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.getRole("CUSTOM:99"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listRoles_includesSystemRolesAndTenantCustomRolesWithUserCounts() {
        when(rolePermissionService.configurableRoles()).thenReturn(List.of(Role.MANAGER));
        when(userRepository.findAllByTenantIdAndRole(1L, Role.MANAGER))
                .thenReturn(List.of(User.builder().id(1L).build(), User.builder().id(2L).build()));
        CustomRole customRole = CustomRole.builder().id(3L).code("FRONT_DESK").name("Front Desk").build();
        when(customRoleRepository.findAllByTenantId(1L)).thenReturn(List.of(customRole));
        when(userRepository.countByTenantIdAndCustomRoleId(1L, 3L)).thenReturn(1L);

        var roles = roleManagementService.listRoles();

        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).getRoleId()).isEqualTo("SYSTEM:MANAGER");
        assertThat(roles.get(0).getUserCount()).isEqualTo(2);
        assertThat(roles.get(1).getRoleId()).isEqualTo("CUSTOM:3");
        assertThat(roles.get(1).getUserCount()).isEqualTo(1);
        assertThat(roles.get(1).isDeletable()).isFalse();
    }
}
