package com.carrental.controller;

import com.carrental.dto.client.CreateClientRequest;
import com.carrental.dto.client.UpdateClientRequest;
import com.carrental.dto.client.UpdateClientEmailRequest;
import com.carrental.dto.client.ClientResponse;
import com.carrental.dto.ApiResponse;
import com.carrental.service.ClientService;
import com.carrental.service.PlanLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Client-management REST controller.
 *
 * <pre>
 * GET    /api/clients              – list all clients            [authenticated]
 * GET    /api/clients/{id}         – get client by id            [authenticated]
 * POST   /api/clients              – create client               [ADMIN]
 * PUT    /api/clients/{id}         – partial update              [ADMIN]
 * PATCH  /api/clients/{id}/email   – add/fix client email         [ADMIN]
 * DELETE /api/clients/{id}         – delete client               [ADMIN]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService    clientService;
    private final PlanLimitService planLimitService;

    // ── GET /api/clients ─────────────────────────────────────────────────────

    /**
     * Returns all clients in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientResponse>>> listClients() {
        List<ClientResponse> clients = clientService.getAllClients();
        String message = clients.isEmpty() ? "No clients found" : "Clients loaded successfully";
        return ResponseEntity.ok(ApiResponse.success(clients, message));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> checkClientDuplicate(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String cin,
            @RequestParam(required = false) String passportNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                clientService.checkExistingClient(email, phone, cin, passportNumber),
                "Client duplicate check completed"));
    }

    // ── GET /api/clients/{id} ────────────────────────────────────────────────

    /**
     * Fetches a single client. Returns 404 for clients belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClient(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    // ── POST /api/clients ────────────────────────────────────────────────────

    /**
     * Registers a new client in the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("@rolePermissionService.has('CREATE_CLIENT')")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        planLimitService.assertClientLimit();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(clientService.createClient(request), "Client created successfully"));
    }

    // ── PUT /api/clients/{id} ────────────────────────────────────────────────

    /**
     * Partially updates a client. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('EDIT_CLIENT')")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    // ── PATCH /api/clients/{id}/email ────────────────────────────────────────

    /**
     * Adds or fixes a client's email address — the dedicated action behind
     * the "Add client email" recovery flow on Contract Details when a
     * client has none on file. Same authorization as the general update
     * (EDIT_CLIENT), but with a required, always-set email field instead of
     * the general endpoint's "null means leave alone, empty string means
     * clear" partial-update semantics.
     */
    @PatchMapping("/{id}/email")
    @PreAuthorize("@rolePermissionService.has('EDIT_CLIENT')")
    public ResponseEntity<ClientResponse> updateClientEmail(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClientEmailRequest request) {
        return ResponseEntity.ok(clientService.updateClientEmail(id, request.getEmail()));
    }

    // ── DELETE /api/clients/{id} ─────────────────────────────────────────────

    /**
     * Soft-deletes a client (archives it — see {@link ClientService#deleteClient}).
     * ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('DELETE_CLIENT')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("id", id, "deleted", true),
                "Client deleted successfully."));
    }

    // ── GET /api/clients/deleted ─────────────────────────────────────────────

    /**
     * Lists archived (soft-deleted) clients only. ADMIN-only.
     */
    @GetMapping("/deleted")
    @PreAuthorize("@rolePermissionService.has('DELETE_CLIENT')")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> listDeletedClients() {
        return ResponseEntity.ok(ApiResponse.success(
                clientService.getDeletedClients(), "Deleted clients loaded successfully"));
    }

    // ── POST /api/clients/{id}/restore ───────────────────────────────────────

    /**
     * Restores a previously soft-deleted client. 409 if an active client in
     * the same tenant already holds the same phone/CIN/passport/email.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("@rolePermissionService.has('DELETE_CLIENT')")
    public ResponseEntity<ApiResponse<ClientResponse>> restoreClient(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                clientService.restoreClient(id), "Client restored successfully."));
    }

    /**
     * Returns client balance summary: total rentals, total paid, remaining balance,
     * open invoices count, active contracts count.
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<java.util.Map<String, Object>> getClientBalance(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientBalance(id));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<java.util.Map<String, Object>> getClientProfile(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientProfile(id));
    }
}
