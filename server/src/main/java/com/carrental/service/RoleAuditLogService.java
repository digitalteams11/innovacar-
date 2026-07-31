package com.carrental.service;

import com.carrental.entity.RoleAuditLog;
import com.carrental.entity.User;
import com.carrental.repository.RoleAuditLogRepository;
import com.carrental.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes one {@link RoleAuditLog} row per role/permission change (spec:
 * every create/update/delete of a role, every user role reassignment, every
 * per-user override add/remove must be traceable to who did it, when, and
 * from what value to what value). Follows this codebase's existing
 * per-domain-audit-table convention ({@link AiAuditService}) — an audit
 * write must never fail the calling transaction, so every persistence
 * failure here is caught and logged, never rethrown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAuditLogService {

    private final RoleAuditLogRepository roleAuditLogRepository;

    public void logRoleCreated(String roleType, String roleCode, Long customRoleId, String reason, HttpServletRequest request) {
        write("ROLE_CREATED", roleType, roleCode, customRoleId, null, null, null, null, reason, request);
    }

    public void logRoleUpdated(String roleType, String roleCode, Long customRoleId, String permissionCode,
                                boolean oldValue, boolean newValue, HttpServletRequest request) {
        write("ROLE_PERMISSION_CHANGED", roleType, roleCode, customRoleId, null, permissionCode,
                String.valueOf(oldValue), String.valueOf(newValue), null, request);
    }

    public void logRoleDeleted(String roleType, String roleCode, Long customRoleId, String reason, HttpServletRequest request) {
        write("ROLE_DELETED", roleType, roleCode, customRoleId, null, null, null, null, reason, request);
    }

    public void logUserRoleChanged(Long targetUserId, String oldRole, String newRole, HttpServletRequest request) {
        write("USER_ROLE_CHANGED", null, null, null, targetUserId, null, oldRole, newRole, null, request);
    }

    public void logUserOverrideAdded(Long targetUserId, String permissionCode, String overrideType, String reason, HttpServletRequest request) {
        write("USER_OVERRIDE_ADDED", null, null, null, targetUserId, permissionCode, null, overrideType, reason, request);
    }

    public void logUserOverrideRemoved(Long targetUserId, String permissionCode, HttpServletRequest request) {
        write("USER_OVERRIDE_REMOVED", null, null, null, targetUserId, permissionCode, null, null, null, request);
    }

    public Page<RoleAuditLog> listForTenant(Long tenantId, Pageable pageable) {
        return roleAuditLogRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    public Page<RoleAuditLog> listForUser(Long targetUserId, Pageable pageable) {
        return roleAuditLogRepository.findAllByTargetUserIdOrderByCreatedAtDesc(targetUserId, pageable);
    }

    private void write(String action, String roleType, String roleCode, Long customRoleId, Long targetUserId,
                        String permissionCode, String oldValue, String newValue, String reason, HttpServletRequest request) {
        try {
            User actor = currentUser();
            roleAuditLogRepository.save(RoleAuditLog.builder()
                    .tenantId(TenantContext.getCurrentTenantId())
                    .action(action)
                    .roleType(roleType)
                    .roleCode(roleCode)
                    .customRoleId(customRoleId)
                    .targetUserId(targetUserId)
                    .changedByUserId(actor != null ? actor.getId() : null)
                    .permissionCode(permissionCode)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .reason(reason)
                    .ipAddress(request != null ? request.getRemoteAddr() : null)
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .build());
        } catch (Exception ex) {
            log.error("Unable to persist role audit log entry for action={}", action, ex);
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
