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
}
