package com.carrental.repository;

import com.carrental.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
    java.util.List<RefreshToken> findByUserIdAndRevokedFalseOrderByCreatedAtAsc(Long userId);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(LocalDateTime date);

    long countByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, LocalDateTime now);
}
