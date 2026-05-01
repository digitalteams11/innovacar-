package com.carrental.controller;

import com.carrental.dto.user.CreateUserRequest;
import com.carrental.dto.user.UpdateUserRequest;
import com.carrental.dto.user.UserResponse;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User-management REST controller.
 *
 * <pre>
 * GET    /api/users            – list all users in tenant        [ADMIN]
 * GET    /api/users?role=X     – filter by role                  [ADMIN]
 * GET    /api/users/{id}       – get user by id                  [ADMIN | self]
 * POST   /api/users            – create user                     [ADMIN]
 * PUT    /api/users/{id}       – update user                     [ADMIN | self*]
 * DELETE /api/users/{id}       – delete user                     [ADMIN]
 * </pre>
 *
 * * Non-admin users may update their own email/password but NOT their role.
 *   Role changes are guarded by a separate {@code @PreAuthorize} on the
 *   {@link #updateUser} method and enforced in {@link UserService#updateUser}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── GET /api/users ───────────────────────────────────────────────────────

    /**
     * List all users in the caller's tenant.
     * Optional {@code role} query-param filters by role.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers(
            @RequestParam(required = false) Role role) {

        List<UserResponse> users = (role != null)
                ? userService.getUsersByRole(role)
                : userService.getAllUsers();

        return ResponseEntity.ok(users);
    }

    // ── GET /api/users/{id} ──────────────────────────────────────────────────

    /**
     * Fetch one user.
     * ADMINs can fetch any user in their tenant; employees can fetch only themselves.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ── POST /api/users ──────────────────────────────────────────────────────

    /**
     * Create a new user inside the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/users/{id} ──────────────────────────────────────────────────

    /**
     * Update a user.
     * ADMINs can update any field (including role) for any tenant user.
     * Non-admins can update only their own email/password; role changes are
     * silently ignored by the service when not performed by an ADMIN.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User caller) {

        // Non-admins cannot change roles — strip the field before forwarding
        if (caller.getRole() != Role.ADMIN) {
            request.setRole(null);
        }

        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    // ── DELETE /api/users/{id} ───────────────────────────────────────────────

    /**
     * Hard-delete a user. ADMIN-only. Admins cannot delete themselves.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller) {

        userService.deleteUser(id, caller.getId());
        return ResponseEntity.noContent().build();
    }
}
