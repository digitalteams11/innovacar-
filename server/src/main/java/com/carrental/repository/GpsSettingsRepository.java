package com.carrental.repository;

import com.carrental.entity.GpsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GpsSettingsRepository extends JpaRepository<GpsSettings, Long> {

    Optional<GpsSettings> findByTenantId(Long tenantId);

    boolean existsByTenantId(Long tenantId);

    /**
     * All tenants with GPS actively enabled — used by the background scheduler.
     * Fetches the tenant eagerly in the same query so the per-tenant loop in
     * {@code GpsSchedulerService} doesn't trigger one extra lazy-load query
     * per row (N+1).
     */
    @Query("SELECT gs FROM GpsSettings gs JOIN FETCH gs.tenant WHERE gs.enabled = true")
    java.util.List<GpsSettings> findAllByEnabledTrue();

    /** Deletes the GPS credentials/settings row for a tenant — used by the Super Admin data-reset execute. */
    void deleteByTenantId(Long tenantId);
}
