package com.carrental.controller;

import com.carrental.dto.contract.*;
import com.carrental.service.ContractService;
import com.carrental.service.PdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contract-management REST controller.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final PdfService pdfService;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @GetMapping("/contracts")
    public ResponseEntity<List<ContractResponse>> listContracts() {
        return ResponseEntity.ok(contractService.getAllContracts());
    }

    @GetMapping("/contracts/{id}")
    public ResponseEntity<ContractResponse> getContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
    }

    @PostMapping("/contracts")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<ContractResponse> createContract(
            @Valid @RequestBody CreateContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(request));
    }

    @PostMapping("/contracts/from-reservation/{reservationId}")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<ContractResponse> createContractFromReservation(
            @PathVariable Long reservationId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createFromReservation(reservationId));
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<ContractResponse> updateContract(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContractRequest request) {
        return ResponseEntity.ok(contractService.updateContract(id, request));
    }

    @DeleteMapping("/contracts/{id}")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<Void> deleteContract(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ResponseEntity.noContent().build();
    }

    // ── Auto Generation ──────────────────────────────────────────────────────

    @GetMapping("/contracts/generate-number")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<Map<String, String>> generateContractNumber() {
        String number = contractService.generateContractNumber();
        return ResponseEntity.ok(Map.of("contractNumber", number));
    }

    // ── Signature Workflow ───────────────────────────────────────────────────

    @PostMapping("/contracts/{id}/sign")
    @PreAuthorize("@rolePermissionService.has('SIGN_CONTRACT')")
    public ResponseEntity<ContractResponse> signContract(
            @PathVariable Long id,
            @Valid @RequestBody ContractSignatureRequest request) {
        return ResponseEntity.ok(contractService.signContract(id, request));
    }

    @PostMapping("/contracts/{id}/qr")
    @PreAuthorize("@rolePermissionService.has('SIGN_CONTRACT')")
    public ResponseEntity<Map<String, String>> generateQrToken(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String frontendUrl = body != null ? body.get("frontendUrl") : null;
        String token = contractService.generateQrToken(id, frontendUrl);
        // Fetch the updated contract to return the full signing URL
        ContractResponse updated = contractService.getContractById(id);
        return ResponseEntity.ok(Map.of(
                "qrToken", token,
                "publicSigningUrl", updated.getPublicSigningUrl() != null ? updated.getPublicSigningUrl() : ""
        ));
    }

    @PostMapping("/contracts/{id}/finalize")
    @PreAuthorize("@rolePermissionService.has('COMPLETE_CONTRACT')")
    public ResponseEntity<ContractResponse> finalizeContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.finalizeContract(id));
    }

    @PostMapping("/contracts/{id}/complete")
    @PreAuthorize("@rolePermissionService.has('COMPLETE_CONTRACT')")
    public ResponseEntity<ContractResponse> markCompleted(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.markCompleted(id));
    }

    @GetMapping(value = "/contracts/{id}/pdf", produces = "application/pdf")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<byte[]> downloadContractPdf(@PathVariable Long id) {
        byte[] pdf = contractService.generateContractPdf(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=contract-" + id + ".pdf")
                .body(pdf);
    }

    @GetMapping(value = "/contracts/{id}/pdf-file", produces = "application/pdf")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<?> getContractPdfFile(@PathVariable Long id) {
        ContractResponse contract = contractService.getContractById(id);
        byte[] pdf = pdfService.getContractPdfFile(id, contract.getContractNumber());
        if (pdf == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "PDF not found. Please regenerate it."));
        }
        ByteArrayResource resource = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=contract-" + contract.getContractNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(resource);
    }

    // ── Public Signing (no auth) ─────────────────────────────────────────────

    @GetMapping("/public/contracts/{qrToken}")
    public ResponseEntity<PublicContractResponse> getPublicContract(
            @PathVariable String qrToken) {
        return ResponseEntity.ok(contractService.getPublicContract(qrToken));
    }

    @PostMapping("/public/contracts/{qrToken}/sign")
    public ResponseEntity<PublicContractResponse> signPublicContract(
            @PathVariable String qrToken,
            @Valid @RequestBody ContractSignatureRequest request) {
        return ResponseEntity.ok(contractService.signPublicContract(qrToken, request));
    }

    // New secure endpoints with contractId + token validation
    @GetMapping("/public/contracts/{contractId}/{qrToken}")
    public ResponseEntity<PublicContractResponse> getPublicContractByIdAndToken(
            @PathVariable Long contractId,
            @PathVariable String qrToken) {
        return ResponseEntity.ok(contractService.getPublicContract(contractId, qrToken));
    }

    @PostMapping("/public/contracts/{contractId}/{qrToken}/sign")
    public ResponseEntity<PublicContractResponse> signPublicContractByIdAndToken(
            @PathVariable Long contractId,
            @PathVariable String qrToken,
            @Valid @RequestBody ContractSignatureRequest request) {
        return ResponseEntity.ok(contractService.signPublicContract(contractId, qrToken, request));
    }
}
