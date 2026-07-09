package com.carrental.service;

import com.carrental.entity.AiAuditLog;
import com.carrental.entity.User;
import com.carrental.repository.AiAuditLogRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes one {@link AiAuditLog} row per AI call. Deliberately takes only a
 * safe category string, never the raw prompt/response — see the entity
 * javadoc for why.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditService {

    private final AiAuditLogRepository aiAuditLogRepository;

    public void log(String feature, String promptCategory, String model,
                     Integer inputTokensEstimate, Integer outputTokensEstimate,
                     String status, String errorCode) {
        try {
            User user = currentUser();
            aiAuditLogRepository.save(AiAuditLog.builder()
                    .userId(user != null ? user.getId() : null)
                    .agencyId(TenantContext.getCurrentTenantId())
                    .role(user != null && user.getRole() != null ? user.getRole().name() : null)
                    .feature(feature)
                    .promptCategory(promptCategory)
                    .model(model)
                    .inputTokensEstimate(inputTokensEstimate)
                    .outputTokensEstimate(outputTokensEstimate)
                    .status(status)
                    .errorCode(errorCode)
                    .build());
        } catch (Exception exception) {
            log.error("Unable to persist AI audit record for feature={}", feature, exception);
        }
    }

    public Page<AiAuditLog> listAll(Pageable pageable) {
        return aiAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<AiAuditLog> listForAgency(Long agencyId, Pageable pageable) {
        return aiAuditLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(agencyId, pageable);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteById(Long id) {
        if (!aiAuditLogRepository.existsById(id)) {
            throw new com.carrental.exception.ResourceNotFoundException("AI audit log not found: " + id);
        }
        aiAuditLogRepository.deleteById(id);
        log.info("Deleted AI audit log id={}", id);
    }

    @org.springframework.transaction.annotation.Transactional
    public long clearAll() {
        long count = aiAuditLogRepository.count();
        aiAuditLogRepository.deleteAll();
        log.info("Cleared all AI audit logs ({} rows)", count);
        return count;
    }

    /** Rough request-count check for per-user/per-agency rate limiting. */
    public long countSince(Long userId, java.time.LocalDateTime since) {
        return aiAuditLogRepository.countByUserIdAndCreatedAtAfter(userId, since);
    }

    public long countForAgencySince(Long agencyId, java.time.LocalDateTime since) {
        return aiAuditLogRepository.countByAgencyIdAndCreatedAtAfter(agencyId, since);
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}
