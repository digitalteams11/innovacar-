package com.carrental.repository;

import com.carrental.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByUserIdAndFingerprintHash(Long userId, String fingerprintHash);

    List<TrustedDevice> findByUserIdOrderByLastSeenAtDesc(Long userId);

    List<TrustedDevice> findByTenantIdOrderByLastSeenAtDesc(Long tenantId);

    long countByBlockedTrue();

    /** Find a device that is explicitly trusted, not revoked, and not expired. */
    @Query("SELECT d FROM TrustedDevice d WHERE d.userId = :userId " +
           "AND d.fingerprintHash = :hash " +
           "AND d.revokedAt IS NULL " +
           "AND d.trusted = true " +
           "AND d.expiresAt IS NOT NULL " +
           "AND d.expiresAt > :now")
    Optional<TrustedDevice> findActiveTrusted(
            @Param("userId") Long userId,
            @Param("hash") String hash,
            @Param("now") LocalDateTime now);

    /** Revoke all active trusted records for a user (password change, logout-all, 2FA disable). */
    @Modifying
    @Query("UPDATE TrustedDevice d SET d.revokedAt = :now, d.trusted = false " +
           "WHERE d.userId = :userId AND d.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
