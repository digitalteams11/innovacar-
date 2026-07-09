package com.carrental.service;

import com.carrental.entity.AuditLog;
import com.carrental.entity.Role;
import com.carrental.entity.SuperAdminRole;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.SuperAdminRoleRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Innovax platform staff accounts — {@link User} rows with
 * {@code role == SUPER_ADMIN}, attached to the single system tenant, each
 * optionally assigned a {@link SuperAdminRole} (see {@link SuperAdminPermissionService}).
 */
@Service
@RequiredArgsConstructor
public class SuperAdminStaffService {

    private static final String SYSTEM_TENANT_EMAIL = "system@innovax.tech";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final SuperAdminRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<User> listStaff() {
        return userRepository.findAllByRole(Role.SUPER_ADMIN);
    }

    @Transactional
    public User createStaff(String email, String password, String firstName, String lastName, Long superAdminRoleId) {
        Tenant systemTenant = tenantRepository.findByEmail(SYSTEM_TENANT_EMAIL)
                .orElseThrow(() -> new IllegalStateException("System tenant not found"));

        if (userRepository.existsByEmailAndTenantId(email, systemTenant.getId())) {
            throw new IllegalArgumentException("A staff account with this email already exists");
        }

        SuperAdminRole role = superAdminRoleId != null
                ? roleRepository.findById(superAdminRoleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"))
                : null;

        User staff = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.SUPER_ADMIN)
                .tenant(systemTenant)
                .firstName(firstName)
                .lastName(lastName)
                .superAdminRole(role)
                .accountEnabled(true)
                .build();
        staff = userRepository.save(staff);
        audit("STAFF_CREATED", staff, "Created staff account with role " + (role != null ? role.getCode() : "UNRESTRICTED"));
        return staff;
    }

    @Transactional
    public User updateStaff(Long id, String firstName, String lastName, Long superAdminRoleId) {
        User staff = fetchStaff(id);

        if (superAdminRoleId != null) {
            SuperAdminRole newRole = roleRepository.findById(superAdminRoleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
            guardLastSuperOwner(staff, newRole.getCode());
            staff.setSuperAdminRole(newRole);
        }
        if (firstName != null) staff.setFirstName(firstName);
        if (lastName != null) staff.setLastName(lastName);

        staff = userRepository.save(staff);
        audit("STAFF_UPDATED", staff, "Updated staff account");
        return staff;
    }

    @Transactional
    public User setStaffStatus(Long id, boolean enabled) {
        User staff = fetchStaff(id);
        if (!enabled) {
            guardLastSuperOwner(staff, null);
        }
        staff.setAccountEnabled(enabled);
        staff = userRepository.save(staff);
        audit(enabled ? "STAFF_ACTIVATED" : "STAFF_SUSPENDED", staff, null);
        return staff;
    }

    /**
     * Blocks an action (role change away from SUPER_OWNER, or suspension)
     * that would leave zero active SUPER_OWNER staff.
     *
     * @param newRoleCode the role the staff member would have afterward, or
     *                     {@code null} when the action is a suspension (role unchanged)
     */
    private void guardLastSuperOwner(User staff, String newRoleCode) {
        boolean currentlyOwner = staff.getSuperAdminRole() != null
                && SuperAdminPermissionService.SUPER_OWNER.equals(staff.getSuperAdminRole().getCode())
                && Boolean.TRUE.equals(staff.getAccountEnabled());
        if (!currentlyOwner) return;
        boolean staysOwner = SuperAdminPermissionService.SUPER_OWNER.equals(newRoleCode);
        if (staysOwner) return;

        long activeOwners = userRepository.countByRoleAndSuperAdminRole_CodeAndAccountEnabledTrue(
                Role.SUPER_ADMIN, SuperAdminPermissionService.SUPER_OWNER);
        if (activeOwners <= 1) {
            throw new IllegalArgumentException("Cannot remove or suspend the last active Super Owner.");
        }
    }

    private User fetchStaff(Long id) {
        User staff = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        if (staff.getRole() != Role.SUPER_ADMIN) {
            throw new ResourceNotFoundException("Staff member not found");
        }
        return staff;
    }

    private void audit(String action, User target, String description) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String actorEmail = principal instanceof User user ? user.getEmail() : "system";
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType("SUPER_ADMIN_STAFF")
                .entityId(target.getId())
                .description(description)
                .performedBy(actorEmail)
                .isSuccess(true)
                .build());
    }
}
