package com.carrental.service;

import com.carrental.entity.UserSession;
import com.carrental.repository.UserSessionRepository;
import com.carrental.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User session management service.
 * Tracks active sessions and allows revocation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserSession createSession(Long userId, String tokenHash, String ipAddress, String userAgent, long expiryMinutes) {
        UserSession session = UserSession.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();
        return userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessions(Long userId) {
        return userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public long getActiveSessionCount(Long userId) {
        return userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(userId, LocalDateTime.now());
    }

    @Transactional
    public void revokeSession(Long sessionId) {
        Optional<UserSession> sessionOpt = userSessionRepository.findById(sessionId);
        sessionOpt.ifPresent(session -> {
            session.setRevoked(true);
            userSessionRepository.save(session);
            refreshTokenRepository.findByTokenHash(session.getTokenHash()).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
            log.info("Revoked session {} for user {}", sessionId, session.getUserId());
        });
    }

    @Transactional
    public void revokeSessionByTokenHash(String tokenHash) {
        Optional<UserSession> sessionOpt = userSessionRepository.findByTokenHash(tokenHash);
        sessionOpt.ifPresent(session -> {
            session.setRevoked(true);
            userSessionRepository.save(session);
            log.info("Revoked session by token hash for user {}", session.getUserId());
        });
    }

    @Transactional
    public void revokeAllUserSessions(Long userId) {
        List<UserSession> sessions = getActiveSessions(userId);
        for (UserSession session : sessions) {
            session.setRevoked(true);
        }
        userSessionRepository.saveAll(sessions);
        var refreshTokens = refreshTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(userId);
        refreshTokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(refreshTokens);
        log.info("Revoked all {} sessions for user {}", sessions.size(), userId);
    }

    @Transactional
    public void revokeAllUserSessionsExcept(Long userId, Long keepSessionId) {
        List<UserSession> sessions = getActiveSessions(userId);
        List<UserSession> toRevoke = sessions.stream()
                .filter(s -> !s.getId().equals(keepSessionId))
                .toList();
        for (UserSession session : toRevoke) {
            session.setRevoked(true);
        }
        userSessionRepository.saveAll(toRevoke);
        var refreshTokens = refreshTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(userId);
        // revoke refresh tokens not belonging to the kept session
        List<String> keepHashes = sessions.stream()
                .filter(s -> s.getId().equals(keepSessionId))
                .map(UserSession::getTokenHash)
                .toList();
        var tokensToRevoke = refreshTokens.stream()
                .filter(t -> !keepHashes.contains(t.getTokenHash()))
                .toList();
        tokensToRevoke.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokensToRevoke);
        log.info("Revoked {} sessions (kept session {}) for user {}", toRevoke.size(), keepSessionId, userId);
    }

    @Transactional
    public void cleanupExpiredSessions() {
        userSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Cleaned up expired sessions");
    }
}
