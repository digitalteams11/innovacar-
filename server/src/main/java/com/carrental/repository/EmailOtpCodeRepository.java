package com.carrental.repository;

import com.carrental.entity.EmailOtpCode;
import com.carrental.entity.EmailOtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailOtpCodeRepository extends JpaRepository<EmailOtpCode, Long> {

    /** Latest active (not used, not expired, not exhausted) OTP for a given user + purpose. */
    @Query("""
        SELECT e FROM EmailOtpCode e
        WHERE e.userId = :userId
          AND e.purpose = :purpose
          AND e.usedAt IS NULL
          AND e.expiresAt > :now
          AND e.attempts < e.maxAttempts
        ORDER BY e.createdAt DESC
        """)
    Optional<EmailOtpCode> findActiveByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") EmailOtpPurpose purpose,
            @Param("now") LocalDateTime now);

    /** Most recently created OTP for rate-limit / resend-cooldown checks. */
    @Query("""
        SELECT e FROM EmailOtpCode e
        WHERE e.userId = :userId
          AND e.purpose = :purpose
        ORDER BY e.createdAt DESC
        """)
    List<EmailOtpCode> findLatestByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") EmailOtpPurpose purpose);

    /** Count how many codes were issued in the last N minutes (rate limiting). */
    @Query("""
        SELECT COUNT(e) FROM EmailOtpCode e
        WHERE e.userId = :userId
          AND e.purpose = :purpose
          AND e.createdAt >= :since
        """)
    long countRecentByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") EmailOtpPurpose purpose,
            @Param("since") LocalDateTime since);

    /** Invalidate all active (unused, unexpired) codes for a user+purpose before issuing new one. */
    @Modifying
    @Query("""
        UPDATE EmailOtpCode e
        SET e.usedAt = :now
        WHERE e.userId = :userId
          AND e.purpose = :purpose
          AND e.usedAt IS NULL
          AND e.expiresAt > :now
        """)
    int invalidateActiveByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") EmailOtpPurpose purpose,
            @Param("now") LocalDateTime now);

    /** Purge expired / used codes older than a given cutoff (for housekeeping). */
    @Modifying
    @Query("DELETE FROM EmailOtpCode e WHERE e.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
