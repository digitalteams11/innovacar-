package com.carrental.controller;

import com.carrental.dto.contract.CreateContractRequest;
import com.carrental.dto.contract.UpdateContractRequest;
import com.carrental.dto.contract.ContractResponse;
import com.carrental.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contract-management REST controller.
 *
 * <pre>
 * GET    /api/contracts              – list all contracts          [authenticated]
 * GET    /api/contracts/{id}         – get contract by id          [authenticated]
 * POST   /api/contracts              – create contract             [ADMIN]
 * PUT    /api/contracts/{id}         – partial update              [ADMIN]
 * DELETE /api/contracts/{id}         – delete contract             [ADMIN]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    // ── GET /api/contracts ───────────────────────────────────────────────────

    /**
     * Returns all contracts in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<List<ContractResponse>> listContracts() {
        return ResponseEntity.ok(contractService.getAllContracts());
    }

    // ── GET /api/contracts/{id} ──────────────────────────────────────────────

    /**
     * Fetches a single contract. Returns 404 for contracts belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
    }

    // ── POST /api/contracts ──────────────────────────────────────────────────

    /**
     * Registers a new contract in the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractResponse> createContract(
            @Valid @RequestBody CreateContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(request));
    }

    // ── PUT /api/contracts/{id} ──────────────────────────────────────────────

    /**
     * Partially updates a contract. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractResponse> updateContract(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContractRequest request) {
        return ResponseEntity.ok(contractService.updateContract(id, request));
    }

    // ── DELETE /api/contracts/{id} ───────────────────────────────────────────

    /**
     * Hard-deletes a contract. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ResponseEntity.noContent().build();
    }
}
