package com.carrental.security;

/**
 * Thread-local holder for the current tenant identifier.
 *
 * <p>Populated by {@link JwtAuthenticationFilter} from the JWT claims and
 * cleared after each request to prevent leakage across threads in the pool.
 *
 * <p>Service-layer code can read {@code TenantContext.getCurrentTenantId()}
 * to scope database queries without requiring explicit tenant parameters on
 * every method signature.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
