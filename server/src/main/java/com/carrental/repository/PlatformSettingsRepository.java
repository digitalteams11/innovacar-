package com.carrental.repository;

import com.carrental.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
    // NOTE: platform_settings is a singleton table. Reads should use this ordered lookup
    // (never findAll().stream().findFirst(), which is nondeterministic if more than one row
    // ever exists). Creating the row if absent must go through the synchronized
    // PlatformSettingsService.getOrCreateSingleton() — synchronization requires a real
    // instance method, which an interface default method cannot provide.
    java.util.Optional<PlatformSettings> findTopByOrderByIdAsc();
}
