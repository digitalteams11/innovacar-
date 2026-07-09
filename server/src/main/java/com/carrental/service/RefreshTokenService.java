package com.carrental.service;

import com.carrental.entity.RefreshToken;
import com.carrental.entity.User;
import com.carrental.repository.RefreshTokenRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Refresh token management service.
 * Handles creation, validation, and revocation of refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public RefreshToken saveRefreshToken(Long userId, String rawToken) {
        return saveRefreshToken(userId, rawToken, null, null);
    }

    @Transactional
    public RefreshToken saveRefreshToken(Long userId, String rawToken, String ipAddress, String userAgent) {
        String tokenHash = hashToken(rawToken);

        // Revoke any existing refresh tokens for this user (optional: allow multiple)
        // For now, we allow up to 5 concurrent refresh tokens per user
        long activeCount = refreshTokenRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(
                userId, LocalDateTime.now());
        if (activeCount >= 5) {
            var tokensToRevoke = refreshTokenRepository
                    .findByUserIdAndRevokedFalseOrderByCreatedAtAsc(userId).stream()
                    .limit(activeCount - 4)
                    .toList();
            tokensToRevoke.forEach(token -> token.setRevoked(true));
            refreshTokenRepository.saveAll(tokensToRevoke);
            log.info("Pruned old refresh tokens for user {}", userId);
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshExpirationMs() / 1000))
                .revoked(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public Optional<User> validateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken refreshToken = tokenOpt.get();

        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.isExpired()) {
            return Optional.empty();
        }

        // Also verify the JWT signature and claims
        if (!jwtTokenProvider.validateRefreshToken(rawToken)) {
            return Optional.empty();
        }

        Long tenantId = jwtTokenProvider.getTenantId(rawToken);
        String email = jwtTokenProvider.getEmail(rawToken);

        return userRepository.findByEmailAndTenantId(email, tenantId);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void revokeAllUserRefreshTokens(Long userId) {
        // Since we don't have a findByUserId method that returns all tokens,
        // we'll use a custom query approach. For now, we can delete by userId.
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Cleaned up expired refresh tokens");
    }

    /**
     * Hashes a token using SHA-256 for secure storage.
     * Raw tokens are never stored in the database.
     */
    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
