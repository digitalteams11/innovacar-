package com.carrental.controller;

import com.carrental.dto.deposit.*;
import com.carrental.service.DepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Security deposit (caution) REST controller.
 */
@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('VIEW_DEPOSITS')")
    public ResponseEntity<List<DepositResponse>> listDeposits() {
        return ResponseEntity.ok(depositService.getAllDeposits());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('VIEW_DEPOSITS')")
    public ResponseEntity<DepositResponse> getDeposit(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.getDepositById(id));
    }

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("@rolePermissionService.has('VIEW_DEPOSITS')")
    public ResponseEntity<DepositResponse> getDepositByContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(depositService.getDepositByContractId(contractId));
    }

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<DepositResponse> createDeposit(@Valid @RequestBody CreateDepositRequest request) {
        return ResponseEntity.ok(depositService.createDeposit(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<DepositResponse> updateDeposit(@PathVariable Long id, @Valid @RequestBody UpdateDepositRequest request) {
        return ResponseEntity.ok(depositService.updateDeposit(id, request));
    }

    @PostMapping("/{id}/received")
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<DepositResponse> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.markReceived(id));
    }

    @PostMapping("/{id}/held")
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<DepositResponse> markHeld(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.markHeld(id));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<DepositResponse> processReturn(@PathVariable Long id, @Valid @RequestBody DepositReturnRequest request) {
        return ResponseEntity.ok(depositService.processReturn(id, request));
    }

    @PostMapping("/{id}/accept-conditions")
    public ResponseEntity<DepositResponse> acceptConditions(@PathVariable Long id) {
        return ResponseEntity.ok(depositService.markConditionsAccepted(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_DEPOSITS')")
    public ResponseEntity<Void> deleteDeposit(@PathVariable Long id) {
        depositService.deleteDeposit(id);
        return ResponseEntity.ok().build();
    }

    // ── Client & Dashboard endpoints ─────────────────────────────────────────

    @GetMapping("/client/{clientId}/summary")
    @PreAuthorize("@rolePermissionService.has('VIEW_DEPOSITS')")
    public ResponseEntity<Map<String, Object>> getClientDepositSummary(@PathVariable Long clientId) {
        return ResponseEntity.ok(depositService.getClientDepositSummary(clientId));
    }

    @GetMapping("/stats")
    @PreAuthorize("@rolePermissionService.has('VIEW_DEPOSITS')")
    public ResponseEntity<Map<String, Object>> getDepositStats() {
        return ResponseEntity.ok(depositService.getDepositStats());
    }
}
