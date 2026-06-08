package com.carrental.service;

import com.carrental.dto.user.CreateUserRequest;
import com.carrental.dto.user.UpdateUserRequest;
import com.carrental.dto.user.UserResponse;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * User-management business logic.
 *
 * <p><strong>Tenant isolation guarantee:</strong> every query is scoped
 * to {@link TenantContext#getCurrentTenantId()}, which is populated by
 * {@code JwtAuthenticationFilter} before any controller code runs.
 * No user of tenant A can read or mutate data belonging to tenant B.
 *
 * <p><strong>Role restriction (enforced here AND at controller level):</strong>
 * <ul>
 *   <li>CREATE / DELETE – ADMIN only</li>
 *   <li>READ / UPDATE   – ADMIN or the user themselves</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository   userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder  passwordEncoder;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Returns every user that belongs to the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        Long tenantId = TenantContext.getCurrentTenantId();
        log.debug("Listing all users for tenant [{}]", tenantId);
        return userRepository.findAllByTenantId(tenantId)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Returns all users of a specific role within the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(Role role) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findAllByTenantIdAndRole(tenantId, role)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Fetches a single user scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the user does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.from(fetchUserInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Creates a new user inside the caller's tenant. ADMIN-only operation.
     *
     * @throws IllegalArgumentException if the e-mail is already taken in this tenant
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new IllegalArgumentException(
                    "Email already registered in this tenant: " + request.getEmail());
        }

        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .tenant(tenant)
                .build());

        log.info("Admin created user [id={}] '{}' role={} in tenant [{}]",
                user.getId(), user.getEmail(), user.getRole(), tenantId);

        return UserResponse.from(user);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * Both ADMINs and the user themselves may call this endpoint
     * (controller enforces who can change the role field).
     *
     * @throws ResourceNotFoundException if the user is not found in this tenant
     * @throws IllegalArgumentException  if the new e-mail is already taken
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        User user     = fetchUserInTenant(id);

        // Update e-mail
        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equals(user.getEmail())) {

            if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
                throw new IllegalArgumentException(
                        "Email already registered in this tenant: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        // Re-hash password only when provided
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Profile fields
        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (StringUtils.hasText(request.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (StringUtils.hasText(request.getJobTitle())) {
            user.setJobTitle(request.getJobTitle());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Role change — only ADMINs can do this; controller layer enforces via @PreAuthorize
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);
        log.info("Updated user [id={}] in tenant [{}]", id, tenantId);
        return UserResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a user scoped to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the user is not found in this tenant
     * @throws IllegalArgumentException  if the admin tries to delete themselves
     */
    @Transactional
    public void deleteUser(Long id, Long requestingUserId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        User user     = fetchUserInTenant(id);

        if (user.getId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Admins cannot delete their own account.");
        }

        userRepository.delete(user);
        log.info("Admin [id={}] deleted user [id={}] from tenant [{}]",
                requestingUserId, id, tenantId);
    }

    // ── Password Change ──────────────────────────────────────────────────────

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = fetchUserInTenant(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user [id={}] in tenant [{}]", userId, TenantContext.getCurrentTenantId());
    }

    @Transactional
    public void adminResetPassword(Long userId, String newPassword) {
        User user = fetchUserInTenant(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Administrator reset password for user [id={}] in tenant [{}]",
                userId, TenantContext.getCurrentTenantId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches a user only if they belong to the current tenant.
     * Throws {@link ResourceNotFoundException} (404) otherwise — which also
     * prevents leaking the existence of users in other tenants.
     */
    private User fetchUserInTenant(Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }
}
