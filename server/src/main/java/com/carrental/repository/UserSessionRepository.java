package com.carrental.repository;

import com.carrental.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, LocalDateTime now);

    List<UserSession> findByRevokedFalseAndExpiresAtAfter(LocalDateTime now);

    List<UserSession> findByRevokedFalse();

    Optional<UserSession> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime date);

    long countByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, LocalDateTime now);

    boolean existsByIdAndUserIdAndRevokedFalseAndExpiresAtAfter(
            Long id, Long userId, LocalDateTime now);

    /**
     * Most recent session among a set of user ids — pushed down to the database
     * (ORDER BY + LIMIT 1) instead of loading every session row into memory just
     * to compute a max() in application code. Used by CustomerSuccessService's
     * daily risk scan, which previously loaded the entire sessions table once
     * per run regardless of tenant count.
     */
    Optional<UserSession> findTopByUserIdInOrderByCreatedAtDesc(java.util.Collection<Long> userIds);
}
