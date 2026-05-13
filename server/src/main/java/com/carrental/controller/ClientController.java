package com.carrental.controller;

import com.carrental.dto.client.CreateClientRequest;
import com.carrental.dto.client.UpdateClientRequest;
import com.carrental.dto.client.ClientResponse;
import com.carrental.service.ClientService;
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

    private final ClientService clientService;

    // ── GET /api/clients ─────────────────────────────────────────────────────

    /**
     * Returns all clients in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<List<ClientResponse>> listClients() {
        return ResponseEntity.ok(clientService.getAllClients());
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientService.createClient(request));
    }

    // ── PUT /api/clients/{id} ────────────────────────────────────────────────

    /**
     * Partially updates a client. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    // ── DELETE /api/clients/{id} ─────────────────────────────────────────────

    /**
     * Hard-deletes a client. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}
