package com.carrental.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionInterceptorTest {

    private final RolePermissionInterceptor interceptor =
            new RolePermissionInterceptor(null, null);

    @Test
    void contractMutationsUseDistinctPermissions() {
        assertThat(interceptor.resolvePermission("POST", "/api/contracts"))
                .isEqualTo("CREATE_CONTRACT");
        assertThat(interceptor.resolvePermission("PUT", "/api/contracts/42"))
                .isEqualTo("EDIT_CONTRACT");
        assertThat(interceptor.resolvePermission("DELETE", "/api/contracts/42"))
                .isEqualTo("DELETE_CONTRACT");
    }

    @Test
    void financeAndFleetAdministrationHaveDedicatedPermissions() {
        assertThat(interceptor.resolvePermission("GET", "/api/deposits"))
                .isEqualTo("VIEW_DEPOSITS");
        assertThat(interceptor.resolvePermission("POST", "/api/deposits"))
                .isEqualTo("MANAGE_DEPOSITS");
        assertThat(interceptor.resolvePermission("POST", "/api/gps/settings"))
                .isEqualTo("MANAGE_GPS");
        assertThat(interceptor.resolvePermission("POST", "/api/branches"))
                .isEqualTo("MANAGE_BRANCHES");
    }
}
