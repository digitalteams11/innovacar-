package com.carrental.util;

import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Hibernate;

/**
 * Safely resolves a lazy JPA association whose target row is excluded by a
 * {@code @SQLRestriction} (e.g. a payment's contract/client/vehicle/invoice FK
 * pointing at a row that still exists but was soft-deleted). Hibernate only
 * detects this when the proxy is initialized, throwing EntityNotFoundException
 * instead of returning null. Callers that only need the FK id are unaffected,
 * since a proxy's id never requires initialization; this is only needed before
 * reading any other field off the proxy.
 */
public final class LazyEntityUtil {

    private LazyEntityUtil() {
    }

    public static <T> T resolve(T proxy) {
        if (proxy == null) {
            return null;
        }
        try {
            Hibernate.initialize(proxy);
            return proxy;
        } catch (EntityNotFoundException ex) {
            return null;
        }
    }
}
