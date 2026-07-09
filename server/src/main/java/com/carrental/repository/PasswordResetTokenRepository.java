package com.carrental.repository;

import com.carrental.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // ── Legacy link-based lookup ─────────────────────────────────────────────

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // ── Code-based flow lookups ──────────────────────────────────────────────

    /** Most-recent PENDING token for a user — used when verifying the 6-digit code. */
    Optional<PasswordResetToken> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    /** Find by reset-session token hash — used when setting the new password. */
    Optional<PasswordResetToken> findByResetSessionTokenHash(String hash);

    /** All PENDING tokens for a user — used to invalidate previous codes. */
    List<PasswordResetToken> findAllByUserIdAndStatus(Long userId, String status);

    // ── Cleanup ──────────────────────────────────────────────────────────────

    void deleteByUserId(Long userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.status = 'EXPIRED' WHERE t.expiresAt < :cutoff AND t.status = 'PENDING'")
    int expireOldTokens(@Param("cutoff") LocalDateTime cutoff);

    void deleteByExpiresAtBefore(LocalDateTime date);
}
