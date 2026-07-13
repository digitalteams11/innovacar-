package com.carrental.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionInterceptorTest {

    private final RolePermissionInterceptor interceptor =
            new RolePermissionInterceptor(null, null);

    @Test
    void contractMutationsUseDistinctPermissions() {
        assertThat(interceptor.resolvePermission("POST", "/api/contracts"))
                .isEqualTo("CONTRACT_CREATE");
        assertThat(interceptor.resolvePermission("PUT", "/api/contracts/42"))
                .isEqualTo("CONTRACT_UPDATE");
        assertThat(interceptor.resolvePermission("DELETE", "/api/contracts/42"))
                .isEqualTo("CONTRACT_DELETE");
    }

    @Test
    void financeAndFleetAdministrationHaveDedicatedPermissions() {
        assertThat(interceptor.resolvePermission("GET", "/api/deposits"))
                .isEqualTo("VIEW_DEPOSITS");
        assertThat(interceptor.resolvePermission("POST", "/api/deposits"))
                .isEqualTo("MANAGE_DEPOSITS");
        assertThat(interceptor.resolvePermission("POST", "/api/gps/settings"))
                .isEqualTo("GPS_SETTINGS");
        assertThat(interceptor.resolvePermission("POST", "/api/branches"))
                .isNull();
    }
}
