package com.carrental.controller;

import com.carrental.dto.contract.AdditionalDriverDto;
import com.carrental.dto.contract.AdditionalDriverRequest;
import com.carrental.dto.contract.AdditionalDriverSignatureLinkResponse;
import com.carrental.dto.contract.AdditionalDriverSignatureView;
import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractAuditLog;
import com.carrental.entity.SignatureStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.AdditionalDriverSigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Authenticated CRUD + link-management endpoints for additional drivers.
 * The independent signing/decline workflow itself is public/no-login — see
 * {@link PublicAdditionalDriverController}.
 */
@Slf4j
@RestController
@RequestMapping("/api/contracts/{contractId}/additional-drivers")
@RequiredArgsConstructor
public class AdditionalDriverController {

    /** No existing per-contract collection-limit mechanism was found to reuse (unlike vehicle/user plan limits) — simple constant. */
    private static final int MAX_ADDITIONAL_DRIVERS = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AdditionalDriverRepository additionalDriverRepository;
    private final ContractRepository contractRepository;
    private final ContractAuditLogRepository contractAuditLogRepository;
    private final AdditionalDriverSigningService signingService;

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_VIEW')")
    public ResponseEntity<List<AdditionalDriverDto>> list(@PathVariable Long contractId) {
        Contract contract = fetchContract(contractId);
        List<AdditionalDriverDto> drivers = additionalDriverRepository.findAllByContractId(contract.getId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(drivers);
    }

    @GetMapping("/{driverId}/signature")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_VIEW')")
    public ResponseEntity<AdditionalDriverSignatureView> viewSignature(@PathVariable Long contractId, @PathVariable Long driverId) {
        AdditionalDriver driver = fetchDriver(contractId, driverId);
        return ResponseEntity.ok(AdditionalDriverSignatureView.builder()
                .id(driver.getId())
                .fullName(driver.getFullName())
                .signatureStatus(driver.getSignatureStatus())
                .signatureData(driver.getSignatureData())
                .signedAt(driver.getSignedAt())
                .signedIp(driver.getSignedIp())
                .signedUserAgent(driver.getSignedUserAgent())
                .declarationVersion(driver.getDeclarationVersion())
                .declarationsAccepted(driver.getDeclarationsAccepted())
                .declinedAt(driver.getDeclinedAt())
                .declineReason(driver.getDeclineReason())
                .build());
    }

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_CREATE')")
    public ResponseEntity<AdditionalDriverDto> create(@PathVariable Long contractId, @RequestBody AdditionalDriverRequest request) {
        Contract contract = fetchContract(contractId);
        List<AdditionalDriver> existing = additionalDriverRepository.findAllByContractId(contract.getId());
        if (existing.size() >= MAX_ADDITIONAL_DRIVERS) {
            throw new IllegalArgumentException("This contract already has the maximum of " + MAX_ADDITIONAL_DRIVERS + " additional drivers.");
        }
        validateIdentity(request);
        validateNoDuplicates(contract, existing, request, null);

        AdditionalDriver driver = AdditionalDriver.builder()
                .fullName(request.getFullName().trim())
                .cin(request.getCin())
                .passportNumber(request.getPassportNumber())
                .driverLicenseNumber(request.getDriverLicenseNumber())
                .nationality(request.getNationality())
                .address(request.getAddress())
                .phone(normalizePhone(request.getPhone()))
                .birthDate(request.getBirthDate())
                .email(normalizeEmail(request.getEmail()))
                .signatureRequired(request.getSignatureRequired() == null || request.getSignatureRequired())
                .contract(contract)
                .build();
        AdditionalDriver saved = additionalDriverRepository.save(driver);
        logAudit(contract, "ADDITIONAL_DRIVER_CREATED", "Added driver id=" + saved.getId() + " (" + saved.getFullName() + ")");
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{driverId}")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_UPDATE')")
    public ResponseEntity<AdditionalDriverDto> update(@PathVariable Long contractId, @PathVariable Long driverId,
                                                       @RequestBody AdditionalDriverRequest request) {
        Contract contract = fetchContract(contractId);
        AdditionalDriver driver = fetchDriver(contractId, driverId);
        validateIdentity(request);

        if (driver.getSignatureStatus() == SignatureStatus.SIGNED) {
            boolean identityChanged =
                    !safeEquals(driver.getFullName(), request.getFullName()) ||
                    !safeEquals(driver.getDriverLicenseNumber(), request.getDriverLicenseNumber()) ||
                    !safeEquals(driver.getCin(), request.getCin()) ||
                    !safeEquals(driver.getPassportNumber(), request.getPassportNumber());
            if (identityChanged) {
                throw new IllegalStateException(
                        "This driver has already signed. Revoke the signature first before editing identity fields.");
            }
        }

        List<AdditionalDriver> siblings = additionalDriverRepository.findAllByContractId(contract.getId());
        validateNoDuplicates(contract, siblings, request, driverId);

        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedEmail = normalizeEmail(request.getEmail());
        boolean contactOnlyChange =
                (!safeEquals(driver.getPhone(), normalizedPhone) || !safeEquals(driver.getEmail(), normalizedEmail))
                && safeEquals(driver.getFullName(), request.getFullName())
                && safeEquals(driver.getDriverLicenseNumber(), request.getDriverLicenseNumber())
                && safeEquals(driver.getCin(), request.getCin())
                && safeEquals(driver.getPassportNumber(), request.getPassportNumber());

        driver.setFullName(request.getFullName().trim());
        driver.setCin(request.getCin());
        driver.setPassportNumber(request.getPassportNumber());
        driver.setDriverLicenseNumber(request.getDriverLicenseNumber());
        driver.setNationality(request.getNationality());
        driver.setAddress(request.getAddress());
        driver.setPhone(normalizedPhone);
        driver.setBirthDate(request.getBirthDate());
        driver.setEmail(normalizedEmail);
        if (request.getSignatureRequired() != null) {
            driver.setSignatureRequired(request.getSignatureRequired());
        }
        AdditionalDriver saved = additionalDriverRepository.save(driver);
        if (contactOnlyChange) {
            logAudit(contract, "ADDITIONAL_DRIVER_CONTACT_UPDATED",
                    "Updated contact details for driver id=" + saved.getId() + " (" + saved.getFullName() + ")");
        } else {
            logAudit(contract, "ADDITIONAL_DRIVER_UPDATED", "Updated driver id=" + saved.getId() + " (" + saved.getFullName() + ")");
        }
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{driverId}")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long contractId, @PathVariable Long driverId,
                                        @RequestParam(required = false, defaultValue = "false") boolean confirm) {
        Contract contract = fetchContract(contractId);
        AdditionalDriver driver = fetchDriver(contractId, driverId);
        if (driver.getSignatureStatus() == SignatureStatus.SIGNED && !confirm) {
            throw new IllegalStateException("This driver has already signed. Pass confirm=true to remove them anyway.");
        }
        additionalDriverRepository.delete(driver);
        logAudit(contract, "ADDITIONAL_DRIVER_REMOVED", "Removed driver id=" + driverId + " (" + driver.getFullName() + ")");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{driverId}/signature-link")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_SEND')")
    public ResponseEntity<AdditionalDriverSignatureLinkResponse> generateLink(@PathVariable Long contractId, @PathVariable Long driverId) {
        return ResponseEntity.ok(signingService.generateLink(contractId, driverId));
    }

    @PostMapping("/{driverId}/signature-link/resend")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_SEND')")
    public ResponseEntity<AdditionalDriverSignatureLinkResponse> resendLink(@PathVariable Long contractId, @PathVariable Long driverId) {
        return ResponseEntity.ok(signingService.resendLink(contractId, driverId));
    }

    @PostMapping("/{driverId}/signature-link/revoke")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_REVOKE')")
    public ResponseEntity<Void> revokeLink(@PathVariable Long contractId, @PathVariable Long driverId) {
        signingService.revokeLink(contractId, driverId);
        return ResponseEntity.noContent().build();
    }

    /** For WhatsApp / copy-link — returns a fresh signing URL without sending an email. Same permission as sending, since it exposes the private link. */
    @PostMapping("/{driverId}/signature-link/for-share")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_SEND')")
    public ResponseEntity<AdditionalDriverSignatureLinkResponse> issueLinkForShare(@PathVariable Long contractId, @PathVariable Long driverId) {
        return ResponseEntity.ok(signingService.issueLinkForShare(contractId, driverId));
    }

    @PostMapping("/{driverId}/signature-link/copied")
    @PreAuthorize("@rolePermissionService.has('ADDITIONAL_DRIVER_SIGNATURE_SEND')")
    public ResponseEntity<Void> recordLinkCopied(@PathVariable Long contractId, @PathVariable Long driverId) {
        signingService.recordLinkCopied(contractId, driverId);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void validateIdentity(AdditionalDriverRequest request) {
        if (!StringUtils.hasText(request.getFullName())) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (!StringUtils.hasText(request.getDriverLicenseNumber())) {
            throw new IllegalArgumentException("Driver license number is required.");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        if (StringUtils.hasText(request.getEmail()) && !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("This email address is not valid.");
        }
    }

    /** Blank → null (never stored as a real value), email trimmed+lowercased, phone trimmed. */
    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    private void validateNoDuplicates(Contract contract, List<AdditionalDriver> siblings, AdditionalDriverRequest request, Long excludeDriverId) {
        String license = request.getDriverLicenseNumber() != null ? request.getDriverLicenseNumber().trim() : null;
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;

        boolean duplicateWithinContract = siblings.stream()
                .filter(d -> !d.getId().equals(excludeDriverId))
                .anyMatch(d -> (StringUtils.hasText(license) && license.equalsIgnoreCase(d.getDriverLicenseNumber()))
                        || (StringUtils.hasText(phone) && phone.equalsIgnoreCase(d.getPhone())));
        if (duplicateWithinContract) {
            throw new IllegalArgumentException("Another additional driver on this contract already has this license or phone number.");
        }

        boolean duplicatesMainClient = (StringUtils.hasText(license) && license.equalsIgnoreCase(contract.getClientDriverLicense()))
                || (StringUtils.hasText(phone) && phone.equalsIgnoreCase(contract.getClientPhone()));
        if (duplicatesMainClient && !Boolean.TRUE.equals(request.getAllowDuplicateOfMainClient())) {
            throw new IllegalArgumentException(
                    "This license/phone matches the main client. Pass allowDuplicateOfMainClient=true to add them anyway.");
        }
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private AdditionalDriverDto toDto(AdditionalDriver d) {
        return AdditionalDriverDto.builder()
                .id(d.getId())
                .fullName(d.getFullName())
                .cin(d.getCin())
                .passportNumber(d.getPassportNumber())
                .driverLicenseNumber(d.getDriverLicenseNumber())
                .nationality(d.getNationality())
                .address(d.getAddress())
                .phone(d.getPhone())
                .birthDate(d.getBirthDate())
                .email(d.getEmail())
                .signatureRequired(d.isSignatureRequired())
                .signatureStatus(d.getSignatureStatus())
                .linkSentAt(d.getLinkSentAt())
                .openedAt(d.getOpenedAt())
                .signedAt(d.getSignedAt())
                .declinedAt(d.getDeclinedAt())
                .deliveryStatus(d.getDeliveryStatus())
                .lastDeliveryChannel(d.getLastDeliveryChannel())
                .lastSentAt(d.getLastSentAt())
                .deliveryFailureMessageSafe(d.getDeliveryFailureMessageSafe())
                .deliveryAttemptCount(d.getDeliveryAttemptCount())
                .build();
    }

    private Contract fetchContract(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
    }

    private AdditionalDriver fetchDriver(Long contractId, Long driverId) {
        fetchContract(contractId);
        return additionalDriverRepository.findByIdAndContractId(driverId, contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Additional driver not found on this contract"));
    }

    private void logAudit(Contract contract, String action, String description) {
        try {
            ContractAuditLog log = ContractAuditLog.builder()
                    .action(action)
                    .description(description)
                    .performedBy(currentUser())
                    .contract(contract)
                    .build();
            contractAuditLogRepository.save(log);
        } catch (Exception e) {
            log.warn("[ADDITIONAL_DRIVER] audit log failed for contractId={} action={}", contract.getId(), action, e);
        }
    }

    private String currentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
