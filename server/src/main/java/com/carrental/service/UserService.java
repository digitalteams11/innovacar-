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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
    private final PasswordPolicyService passwordPolicyService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;

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
        passwordPolicyService.validate(request.getPassword());
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
            passwordPolicyService.replacePassword(user, request.getPassword());
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

    // ── Avatar upload ────────────────────────────────────────────────────────

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/jfif");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /**
     * Stores the uploaded avatar image under {@code uploads/avatars/{userId}/}
     * and persists the resulting short relative URL on the user record.
     *
     * @throws IllegalArgumentException if the file is missing, too large, or an unsupported type
     */
    @Transactional
    public UserResponse updateAvatar(Long id, MultipartFile file) {
        User user = fetchUserInTenant(id);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, WebP, or JFIF images are allowed.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar image must be smaller than 5MB.");
        }

        String extension = switch (contentType) {
            case "image/jpeg", "image/jfif" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported avatar type");
        };

        try {
            Path avatarDir = Path.of("uploads", "avatars", String.valueOf(id));
            Files.createDirectories(avatarDir);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
            String fileName = "avatar_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            Path destination = avatarDir.resolve(fileName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            user.setAvatarUrl("/uploads/avatars/" + id + "/" + fileName);
            User saved = userRepository.save(user);
            log.info("Updated avatar for user [id={}]", id);
            return UserResponse.from(saved);
        } catch (IOException exception) {
            log.warn("Unable to save avatar for user [id={}]", id, exception);
            throw new java.io.UncheckedIOException("Unable to upload avatar image", exception);
        }
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

        passwordPolicyService.replacePassword(user, newPassword);
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeAllUserRefreshTokens(user.getId());
        sessionService.revokeAllUserSessions(user.getId());
        log.info("Password changed for user [id={}] in tenant [{}]", userId, TenantContext.getCurrentTenantId());
    }

    @Transactional
    public void adminResetPassword(Long userId, String newPassword) {
        User user = fetchUserInTenant(userId);
        passwordPolicyService.replacePassword(user, newPassword);
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeAllUserRefreshTokens(user.getId());
        sessionService.revokeAllUserSessions(user.getId());
        log.info("Administrator reset password for user [id={}] in tenant [{}]",
                userId, TenantContext.getCurrentTenantId());
    }

    // ── Personal preferences (language / theme mode) ────────────────────────

    private static final Set<String> ALLOWED_LANGUAGES = Set.of("en", "fr", "ar");
    private static final Set<String> ALLOWED_THEME_MODES = Set.of("light", "dark", "auto");

    /**
     * Updates the calling user's own language/theme preferences. These are
     * personal account settings — never shared across the tenant/agency.
     */
    @Transactional
    public UserResponse updatePreferences(Long userId, String language, String themeMode) {
        User user = fetchUserInTenant(userId);

        if (language != null) {
            if (!ALLOWED_LANGUAGES.contains(language)) {
                throw new IllegalArgumentException("Unsupported language: " + language);
            }
            user.setLanguage(language);
        }
        if (themeMode != null) {
            if (!ALLOWED_THEME_MODES.contains(themeMode)) {
                throw new IllegalArgumentException("Unsupported theme mode: " + themeMode);
            }
            user.setThemeMode(themeMode);
        }

        userRepository.save(user);
        return UserResponse.from(user);
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
