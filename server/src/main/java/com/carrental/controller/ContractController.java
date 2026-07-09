package com.carrental.controller;

import com.carrental.dto.contract.*;
import com.carrental.dto.ApiResponse;
import com.carrental.service.ContractPurgeService;
import com.carrental.service.ContractService;
import com.carrental.service.PdfService;
import com.carrental.service.PlanLimitService;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contract-management REST controller.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService      contractService;
    private final ContractPurgeService contractPurgeService;
    private final PdfService           pdfService;
    private final VehicleRepository    vehicleRepository;
    private final ContractRepository   contractRepository;
    private final PlanLimitService     planLimitService;
    private final com.carrental.service.PlatformEmailService platformEmailService;
    private final com.carrental.repository.EmailLogRepository emailLogRepository;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @GetMapping("/contracts")
    public ResponseEntity<List<ContractResponse>> listContracts() {
        return ResponseEntity.ok(contractService.getAllContracts());
    }

    @GetMapping("/contracts/active")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<Map<String, Object>> listActiveContracts() {
        List<ContractResponse> all = contractService.getAllContracts();
        List<ContractResponse> active = all.stream()
                .filter(c -> c.getStatus() != null && isActiveStatus(c.getStatus().name()))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", active, "total", active.size()));
    }

    private static boolean isActiveStatus(String status) {
        return switch (status) {
            case "WAITING_SIGNATURE", "WAITING_CLIENT_SIGNATURE", "PENDING_SIGNATURE",
                 "PARTIALLY_SIGNED", "SIGNED", "ACTIVE", "PAID" -> true;
            default -> false;
        };
    }

    @GetMapping("/contracts/{id}")
    public ResponseEntity<ContractResponse> getContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
    }

    @PostMapping("/contracts")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<ContractResponse> createContract(
            @Valid @RequestBody CreateContractRequest request) {
        planLimitService.assertContractLimit();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(request));
    }

    @PostMapping("/contracts/direct-create")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> directCreateContract(
            @Valid @RequestBody CreateContractRequest request) {
        planLimitService.assertContractLimit();
        Map<String, Object> result = contractService.directCreateContract(request);
        Object data = result.get("data");
        boolean isNew = !(data instanceof Map<?, ?> dataMap) || !Boolean.FALSE.equals(dataMap.get("isNew"));
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @PostMapping("/contracts/from-reservation/{reservationId}")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<ContractResponse> createContractFromReservation(
            @PathVariable Long reservationId) {
        // Idempotent: returns the existing contract with 200 if this reservation
        // was already converted, or the newly created contract with 200 otherwise.
        return ResponseEntity.ok(contractService.createFromReservation(reservationId));
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("@rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<ContractResponse> updateContract(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContractRequest request) {
        return ResponseEntity.ok(contractService.updateContract(id, request));
    }

    @DeleteMapping("/contracts/{id}")
    @PreAuthorize("@rolePermissionService.has('DELETE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> deleteContract(@PathVariable Long id) {
        Map<String, Object> result;
        try {
            result = contractService.deleteContract(id);
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "Contract not found or already deleted.");
            body.put("errorCode", "CONTRACT_NOT_FOUND");
            body.put("data", Map.of("contractId", id));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        } catch (IllegalStateException ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", ex.getMessage());
            body.put("errorCode", "CONTRACT_ALREADY_COMPLETED");
            body.put("data", Map.of("contractId", id));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        boolean vehicleMissing = Boolean.TRUE.equals(result.get("vehicleMissing"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", vehicleMissing
                ? "Contract deleted successfully. Linked vehicle was missing, but contract data was cleaned safely."
                : "Contract deleted successfully.");
        body.put("data", result);
        return ResponseEntity.ok(body);
    }

    // ── Trash / Restore / Purge ──────────────────────────────────────────────

    @GetMapping("/contracts/trash")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<Map<String, Object>> listTrashedContracts() {
        List<Map<String, Object>> trashed = contractService.getTrashedContracts();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Trashed contracts loaded.",
                "data", trashed
        ));
    }

    @PostMapping("/contracts/{id}/restore")
    @PreAuthorize("@rolePermissionService.has('DELETE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> restoreContract(
            @PathVariable Long id,
            @RequestBody(required = false) RestoreContractRequest body) {
        String rawMode = body != null ? body.getMode() : null;
        String mode = (rawMode != null && !rawMode.isBlank()) ? rawMode.trim().toUpperCase() : "NORMAL";
        log.info("[CONTRACT_RESTORE_REQUEST] contractId={} hasBody={} rawMode={} resolvedMode={}",
                id, body != null, rawMode, mode);
        if (!"NORMAL".equals(mode) && !"DRAFT_ONLY".equals(mode)) {
            return ResponseEntity.badRequest().body(errorBody(
                    "Invalid restore mode. Must be NORMAL or DRAFT_ONLY.",
                    "INVALID_RESTORE_MODE", Map.of("receivedMode", rawMode)));
        }
        try {
            Map<String, Object> result = contractService.restoreContract(id, mode);
            boolean asDraft = Boolean.TRUE.equals(result.get("restoredAsDraft"));
            String successMsg = asDraft
                    ? "Contract restored as draft. Vehicle availability is not affected."
                    : "Contract restored successfully.";
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", successMsg,
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Trashed contract not found.", "CONTRACT_NOT_FOUND", Map.of("contractId", id)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "RESTORE_WINDOW_EXPIRED", Map.of("contractId", id)));
        } catch (com.carrental.exception.VehicleConflictException ex) {
            Map<String, Object> conflictData = new LinkedHashMap<>();
            conflictData.put("contractId", id);
            conflictData.put("vehicleId", ex.getVehicleId());
            conflictData.put("requestedStartDate", ex.getRequestedStartDate());
            conflictData.put("requestedEndDate", ex.getRequestedEndDate());
            conflictData.put("conflictSource", ex.getConflictSource());
            conflictData.put("conflictId", ex.getConflictId());
            conflictData.put("conflictNumber", ex.getConflictNumber());
            conflictData.put("conflictStatus", ex.getConflictStatus());
            conflictData.put("conflictStartDate", ex.getConflictStartDate());
            conflictData.put("conflictEndDate", ex.getConflictEndDate());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), ex.getErrorCode(), conflictData));
        }
    }

    @PostMapping("/contracts/{id}/cancel")
    @PreAuthorize("@rolePermissionService.has('DELETE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> cancelContract(@PathVariable Long id) {
        try {
            Map<String, Object> result = contractService.cancelContract(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Contract cancelled.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Contract not found.", "CONTRACT_NOT_FOUND", Map.of("contractId", id)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "CONTRACT_CANCEL_CONFLICT", Map.of("contractId", id)));
        }
    }

    @DeleteMapping("/contracts/{id}/purge")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('AGENCY_OWNER')")
    public ResponseEntity<Map<String, Object>> purgeContract(@PathVariable Long id) {
        try {
            Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
            Map<String, Object> result = contractPurgeService.purgeContract(id, tenantId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Contract permanently deleted.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Trashed contract not found.", "CONTRACT_NOT_FOUND", Map.of("contractId", id)));
        } catch (DataIntegrityViolationException ex) {
            // Safety-net: if a FK still blocks the delete despite the purge logic,
            // return the exact constraint so the caller knows what is blocking.
            String constraint = extractConstraintName(ex);
            String blockingTable = guessBlockingTable(constraint);
            log.error("[CONTRACT_PURGE_DEBUG] contractId={} FK violation after purge constraint={} blockingTable={}",
                    id, constraint, blockingTable, ex);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("contractId", id);
            if (constraint    != null) data.put("constraint",    constraint);
            if (blockingTable != null) data.put("blockingTable", blockingTable);
            data.put("hint", "Delete linked records before deleting contract.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    "Contract cannot be permanently deleted because linked records still exist.",
                    "CONTRACT_PURGE_BLOCKED", data));
        }
    }

    private String extractConstraintName(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String guessBlockingTable(String constraintName) {
        if (constraintName == null) return null;
        String lower = constraintName.toLowerCase();
        if (lower.contains("payment"))           return "payments";
        if (lower.contains("deposit"))           return "deposits";
        if (lower.contains("inspection_media"))  return "inspection_media";
        if (lower.contains("inspection"))        return "inspections";
        if (lower.contains("audit"))             return "contract_audit_logs";
        if (lower.contains("document"))          return "contract_documents";
        if (lower.contains("condition"))         return "contract_vehicle_conditions";
        if (lower.contains("driver"))            return "contract_additional_drivers";
        return null;
    }

    @GetMapping("/contracts/debug-vehicle/{vehicleId}")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<Map<String, Object>> debugVehicle(@PathVariable Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        List<Contract> contracts = contractRepository.findAllByVehicleId(vehicleId);

        Map<String, Object> vehicleData = new LinkedHashMap<>();
        if (vehicle == null) {
            vehicleData.put("status", "NOT_FOUND");
        } else {
            vehicleData.put("id", vehicle.getId());
            vehicleData.put("status", vehicle.getStatut());
            vehicleData.put("brand", vehicle.getMarque());
            vehicleData.put("plate", vehicle.getPlate());
        }

        List<Map<String, Object>> contractData = contracts.stream()
                .map(contract -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", contract.getId());
                    item.put("status", contract.getStatus());
                    item.put("start", contract.getStartDate());
                    item.put("end", contract.getEndDate());
                    item.put("number", contract.getContractNumber());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle debug data loaded.",
                "data", Map.of("vehicle", vehicleData, "allContracts", contractData)
        ));
    }

    @PutMapping("/admin/reset-vehicle/{vehicleId}")
    @PreAuthorize("@rolePermissionService.has('EDIT_VEHICLE')")
    public ResponseEntity<Map<String, Object>> forceResetVehicle(@PathVariable Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
        vehicle.setStatut(VehicleStatus.AVAILABLE);
        vehicleRepository.save(vehicle);

        List<Contract> stuck = contractRepository.findAllByVehicleIdAndStatus(vehicleId, ContractStatus.WAITING_SIGNATURE);
        stuck.forEach(contract -> contract.setStatus(ContractStatus.CANCELLED));
        contractRepository.saveAll(stuck);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Vehicle " + vehicleId + " reset to AVAILABLE.",
                "data", Map.of("vehicleId", vehicleId, "cancelledContracts", stuck.size())
        ));
    }
    // -- Auto Generation ──────────────────────────────────────────────────────

    @GetMapping("/contracts/generate-number")
    @PreAuthorize("@rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<Map<String, String>> generateContractNumber() {
        String number = contractService.generateContractNumber();
        return ResponseEntity.ok(Map.of("contractNumber", number));
    }

    // ── Signature Workflow ───────────────────────────────────────────────────

    @PostMapping("/contracts/{id}/sign")
    @PreAuthorize("@rolePermissionService.has('SIGN_CONTRACT')")
    public ResponseEntity<Map<String, Object>> signContract(
            @PathVariable Long id,
            @Valid @RequestBody ContractSignatureRequest request) {
        try {
            ContractSignatureRequest.SignerType signerType = request.getResolvedSignerType();
            boolean agencySignature = signerType == ContractSignatureRequest.SignerType.OWNER;
            boolean alreadySigned = contractService.isSignerAlreadySigned(id, signerType);
            ContractResponse contract = contractService.signContract(id, request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", contract.getId());
            data.put("contractId", contract.getId());
            data.put("contractNumber", contract.getContractNumber());
            data.put("status", contract.getStatus());
            data.put("agencySigned", Boolean.TRUE.equals(contract.getOwnerSigned()));
            data.put("clientSigned", Boolean.TRUE.equals(contract.getClientSigned()));
            data.put("employeeSigned", Boolean.TRUE.equals(contract.getEmployeeSigned()));
            data.put("contract", contract);
            String message = alreadySigned && agencySignature
                    ? "Agency signature already applied"
                    : "Contract signed successfully";
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", message,
                    "data", data
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBody(
                    ex.getMessage(),
                    "SIGNATURE_REQUIRED",
                    null
            ));
        }
    }

    @GetMapping("/contracts/{id}/qr")
    @PreAuthorize("@rolePermissionService.has('SIGN_CONTRACT')")
    public ResponseEntity<Map<String, Object>> generateQrTokenGet(
            @PathVariable Long id,
            @RequestParam(required = false) String frontendUrl) {
        return generateQrResponse(id, frontendUrl);
    }

    @PostMapping("/contracts/{id}/qr")
    @PreAuthorize("@rolePermissionService.has('SIGN_CONTRACT')")
    public ResponseEntity<Map<String, Object>> generateQrToken(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String frontendUrl = body != null ? body.get("frontendUrl") : null;
        return generateQrResponse(id, frontendUrl);
    }

    private ResponseEntity<Map<String, Object>> generateQrResponse(Long id, String frontendUrl) {
        try {
            boolean existingQr = contractService.hasQrCode(id);
            String token = contractService.generateQrToken(id, frontendUrl);
            ContractResponse updated = contractService.getContractById(id);
            String publicSigningUrl = buildPublicSigningUrl(id, token, frontendUrl, updated.getPublicSigningUrl());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("contractId", updated.getId());
            data.put("contractNumber", updated.getContractNumber());
            data.put("qrToken", token);
            data.put("qrUrl", publicSigningUrl);
            data.put("publicSigningUrl", publicSigningUrl);
            data.put("signingUrl", publicSigningUrl);
            data.put("qrCodeUrl", publicSigningUrl);
            data.put("expiresAt", null);
            data.put("tokenExpiresAt", null);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", existingQr ? "Existing QR code loaded" : "QR code generated successfully",
                    "qrToken", token,
                    "publicSigningUrl", publicSigningUrl,
                    "data", data
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(errorBody(
                    ex.getMessage(),
                    "AGENCY_SIGNATURE_REQUIRED",
                    List.of()
            ));
        }
    }

    private String buildPublicSigningUrl(Long contractId, String token, String frontendUrl, String fallbackUrl) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return fallbackUrl != null ? fallbackUrl : "";
        }
        String base = frontendUrl;
        base = base.replaceAll("/+$", "");
        return base + "/contract-sign/" + contractId + "/" + token;
    }

    private Map<String, Object> errorBody(String message, String errorCode, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errorCode", errorCode);
        body.put("data", data);
        return body;
    }

    @PostMapping("/contracts/{id}/return-inspection")
    @PreAuthorize("@rolePermissionService.has('COMPLETE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> returnInspection(
            @PathVariable Long id,
            @RequestBody com.carrental.dto.contract.ReturnInspectionRequest request) {
        try {
            Map<String, Object> result = contractService.processReturnInspection(id, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vehicle returned and contract completed successfully.",
                    "data", result
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Contract not found.", "CONTRACT_NOT_FOUND", Map.of("contractId", id)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    ex.getMessage(), "CONTRACT_INVALID_STATE", Map.of("contractId", id)));
        }
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

    @PutMapping("/contracts/{id}/template")
    @PreAuthorize("@rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<ContractResponse> updateContractTemplate(@PathVariable Long id,
                                                                    @RequestBody Map<String, Object> body) {
        Object templateId = body == null ? null : body.get("templateId");
        return ResponseEntity.ok(contractService.updateSelectedTemplate(id, templateId));
    }

    @GetMapping("/contracts/{id}/pdf")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<?> downloadContractPdf(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "agency") String template) {
        try {
            ContractResponse contract = contractService.getContractById(id);
            byte[] pdf = contractService.generateContractPdf(id, !"system".equalsIgnoreCase(template));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"contract-" + contract.getContractNumber() + ".pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(pdf);
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Contract not found",
                    "CONTRACT_NOT_FOUND",
                    null
            ));
        } catch (com.carrental.exception.TemplatePlanRequiredException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(
                    ex.getMessage(),
                    "TEMPLATE_PLAN_REQUIRED",
                    Map.of("requiredPlan", ex.getRequiredPlan())
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                    "Unable to generate contract PDF",
                    "CONTRACT_TEMPLATE_PDF_FAILED",
                    null
            ));
        }
    }

    @GetMapping("/contracts/{id}/pdf/preview")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<?> previewContractPdf(@PathVariable Long id,
                                                @RequestParam(defaultValue = "agency") String template) {
        try {
            ContractResponse contract = contractService.getContractById(id);
            byte[] pdf = contractService.generateContractPdf(id, !"system".equalsIgnoreCase(template));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"contract-preview-" + contract.getContractNumber() + ".pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(pdf);
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Contract not found",
                    "CONTRACT_NOT_FOUND",
                    null
            ));
        } catch (com.carrental.exception.TemplatePlanRequiredException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(
                    ex.getMessage(),
                    "TEMPLATE_PLAN_REQUIRED",
                    Map.of("requiredPlan", ex.getRequiredPlan())
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                    "Unable to generate contract PDF",
                    "CONTRACT_TEMPLATE_PDF_FAILED",
                    null
            ));
        }
    }

    /**
     * Forces a fresh PDF render from the agency's current settings (logo, name,
     * address, terms, etc.) and overwrites the stored file — even for an already
     * signed contract whose PDF was frozen at signing time. Use this after
     * updating agency branding so Preview/Download stop serving the old file.
     */
    @PostMapping("/contracts/{id}/pdf/regenerate")
    @PreAuthorize("@rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> regenerateContractPdf(@PathVariable Long id) {
        try {
            ContractResponse updated = contractService.regenerateContractPdf(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "PDF regenerated from current agency settings.",
                    "data", updated
            ));
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "Contract not found", "CONTRACT_NOT_FOUND", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                    "Unable to regenerate contract PDF", "CONTRACT_TEMPLATE_PDF_FAILED", null));
        }
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
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
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

    // ── Public PDF download (no auth, scoped by qrToken) ────────────────────

    @GetMapping("/public/contracts/{contractId}/{qrToken}/pdf")
    public ResponseEntity<?> downloadPublicContractPdf(
            @PathVariable Long contractId,
            @PathVariable String qrToken) {
        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Contract not found"));
            if (contract.getQrToken() == null || !contract.getQrToken().equals(qrToken)) {
                return ResponseEntity.status(404).body(errorBody("Contract not found", "CONTRACT_NOT_FOUND", null));
            }
            byte[] pdf = contractService.generateContractPdf(contractId, true);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"contract-" + contract.getContractNumber() + ".pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .body(pdf);
        } catch (com.carrental.exception.ResourceNotFoundException ex) {
            return ResponseEntity.status(404).body(errorBody("Contract not found", "CONTRACT_NOT_FOUND", null));
        } catch (Exception ex) {
            log.error("Public PDF download failed contractId={}", contractId, ex);
            return ResponseEntity.status(500).body(errorBody("PDF generation failed", "CONTRACT_TEMPLATE_PDF_FAILED", null));
        }
    }

    // ── Agency Admin: send / resend contract email ───────────────────────────

    @PostMapping("/contracts/{id}/send-email")
    @PreAuthorize("@rolePermissionService.has('EDIT_CONTRACT') or @rolePermissionService.has('CREATE_CONTRACT')")
    public ResponseEntity<Map<String, Object>> sendContractEmail(@PathVariable Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Contract not found"));
        // Verify tenant ownership
        Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
        if (!contract.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(403).body(errorBody("Access denied", "ACCESS_DENIED", null));
        }
        if (!org.springframework.util.StringUtils.hasText(contract.getClientEmail())) {
            return ResponseEntity.ok(errorBody("Client has no email address", "EMAIL_TO_ADDRESS_MISSING", null));
        }
        boolean sent = platformEmailService.resendContractEmail(id);
        if (sent) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Contract email sent to " + contract.getClientEmail()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Email could not be sent. Check SMTP settings in Email Center.",
                "errorCode", "EMAIL_SEND_FAILED"
        ));
    }

    @GetMapping("/contracts/{id}/email-status")
    @PreAuthorize("@rolePermissionService.has('VIEW_CONTRACTS')")
    public ResponseEntity<Map<String, Object>> getContractEmailStatus(@PathVariable Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Contract not found"));
        Long tenantId = com.carrental.security.TenantContext.getCurrentTenantId();
        if (!contract.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.status(403).body(errorBody("Access denied", "ACCESS_DENIED", null));
        }
        java.util.List<com.carrental.entity.EmailLog> logs =
                emailLogRepository.findAllByContractIdOrderByCreatedAtDesc(id);
        com.carrental.entity.EmailLog last = logs.isEmpty() ? null : logs.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", id);
        result.put("clientEmail", contract.getClientEmail());
        result.put("hasClientEmail", org.springframework.util.StringUtils.hasText(contract.getClientEmail()));
        result.put("lastStatus",    last != null ? last.getStatus()    : null);
        result.put("lastErrorCode", last != null ? last.getErrorCode() : null);
        result.put("lastSentAt",    last != null ? last.getCreatedAt() : null);
        result.put("totalAttempts", logs.size());
        return ResponseEntity.ok(result);
    }
}

