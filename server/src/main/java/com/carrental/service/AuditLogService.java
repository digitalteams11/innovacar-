package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.User;
import com.carrental.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes explicit, descriptive audit entries for critical Super Admin
 * actions. The generic {@link com.carrental.security.AuditLoggingInterceptor}
 * already logs every mutating request, but its description is just the raw
 * HTTP method + path; this service lets services record a human-readable
 * "what changed" line for changes that matter (platform settings, security
 * policy, maintenance mode, feature flags, etc).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void logSuperAdminAction(String action, String entityType, Long entityId, String description) {
        try {
            User user = currentUser();
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .performedBy(user != null ? user.getEmail() : "SYSTEM")
                    .performedById(user != null ? user.getId() : null)
                    .isSuccess(true)
                    .build());
        } catch (Exception exception) {
            log.error("Unable to persist audit record for {} {} #{}", action, entityType, entityId, exception);
        }
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}
