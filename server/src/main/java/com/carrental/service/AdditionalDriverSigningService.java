package com.carrental.service;

import com.carrental.dto.contract.AdditionalDriverDeclineRequest;
import com.carrental.dto.contract.AdditionalDriverSignatureLinkResponse;
import com.carrental.dto.contract.AdditionalDriverSignatureSubmitRequest;
import com.carrental.dto.contract.PublicAdditionalDriverSigningResponse;
import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractAuditLog;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Notification;
import com.carrental.entity.SignatureStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.PublicSigningException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.security.TenantContext;
import com.carrental.util.MaskingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Independent digital-signing workflow for additional drivers — modeled
 * directly on {@link ClientInformationRequestService} (hashed single-use
 * token, lazy expiry-on-read, precise-but-non-leaking public errors) but
 * completely separate from the main client/agency contract-signing flow in
 * {@link ContractService}. Signature fields live on {@link AdditionalDriver}
 * itself — no generic signature entity — per the approved plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdditionalDriverSigningService {

    private static final int TOKEN_BYTES = 32;
    // 72h default expiry. Could later become tenant-configurable (mirroring
    // ClientInformationRequestService's per-request expiresInHours) but no
    // settings infra exists for this specific duration yet — ships as a
    // constant per the plan's explicit scope decision.
    private static final int DEFAULT_EXPIRY_HOURS = 72;
    private static final String DECLARATION_VERSION = "v1";
    private static final long MIN_SIGNATURE_BYTES = 100;

    /** The 5 declarations the driver must accept before signing (spec-drafted, not legally certified — see plan). */
    private static final List<String> REQUIRED_DECLARATIONS = List.of(
            "identityCorrect", "licenseValid", "responsibilityAccepted", "termsRead", "dataConsent");

    private final AdditionalDriverRepository additionalDriverRepository;
    private final ContractRepository contractRepository;
    private final ContractAuditLogRepository contractAuditLogRepository;
    private final DepositRepository depositRepository;
    private final NotificationService notificationService;
    private final ContractService contractService;
    private final PdfService pdfService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── Admin: generate / resend / revoke ───────────────────────────────────

    @Transactional
    public AdditionalDriverSignatureLinkResponse generateLink(Long contractId, Long driverId) {
        AdditionalDriver driver = fetchDriverInContract(contractId, driverId);
        if (driver.getSignatureStatus() == SignatureStatus.SIGNED) {
            throw new IllegalStateException("This driver has already signed — revoke is not applicable to a completed signature.");
        }

        String rawToken = generateRawToken();
        driver.setTokenHash(hashToken(rawToken));
        driver.setTokenExpiresAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS));
        driver.setTokenRevokedAt(null);
        driver.setSignatureStatus(SignatureStatus.LINK_SENT);
        driver.setLinkSentAt(LocalDateTime.now());
        AdditionalDriver saved = additionalDriverRepository.save(driver);

        logAudit(saved.getContract(), "ADDITIONAL_DRIVER_SIGNATURE_LINK_SENT",
                "Signing link sent to driver id=" + saved.getId() + " (" + saved.getFullName() + ")");

        String url = buildSigningUrl(rawToken);
        log.info("[ADDITIONAL_DRIVER_SIGN] link generated driverId={} contractId={} tokenPrefix={}",
                saved.getId(), contractId, MaskingUtil.maskToken(rawToken));

        try {
            notificationService.createNotification(
                    "Additional driver link sent",
                    "A signing link was sent to " + saved.getFullName() + ".",
                    Notification.NotificationType.ADDITIONAL_DRIVER_LINK_SENT,
                    contractId, TenantContext.getCurrentTenantId());
        } catch (Exception e) {
            log.warn("[ADDITIONAL_DRIVER_SIGN] notification failed for link-sent driverId={}", saved.getId(), e);
        }

        return AdditionalDriverSignatureLinkResponse.builder()
                .signingUrl(url)
                .expiresAt(saved.getTokenExpiresAt())
                .build();
    }

    /** New token invalidates the old one implicitly (token_hash is overwritten) — still audited as "link sent". */
    @Transactional
    public AdditionalDriverSignatureLinkResponse resendLink(Long contractId, Long driverId) {
        return generateLink(contractId, driverId);
    }

    @Transactional
    public void revokeLink(Long contractId, Long driverId) {
        AdditionalDriver driver = fetchDriverInContract(contractId, driverId);
        if (driver.getSignatureStatus() == SignatureStatus.SIGNED) {
            throw new IllegalStateException("Cannot revoke a completed signature.");
        }
        driver.setTokenRevokedAt(LocalDateTime.now());
        driver.setSignatureStatus(SignatureStatus.REVOKED);
        AdditionalDriver saved = additionalDriverRepository.save(driver);
        logAudit(saved.getContract(), "ADDITIONAL_DRIVER_SIGNATURE_REVOKED",
                "Signing link revoked for driver id=" + saved.getId() + " (" + saved.getFullName() + ")");
        log.info("[ADDITIONAL_DRIVER_SIGN] link revoked driverId={} contractId={}", saved.getId(), contractId);
    }

    // ── Public: get / open / sign / decline ─────────────────────────────────

    @Transactional
    public PublicAdditionalDriverSigningResponse getPublicView(String rawToken) {
        AdditionalDriver driver = findValidByRawToken(rawToken, false);
        Contract contract = driver.getContract();
        Tenant tenant = contract.getTenant();

        boolean cancelled = contract.getStatus() == ContractStatus.CANCELLED;

        String vehicleSummary = buildVehicleSummary(contract);

        return PublicAdditionalDriverSigningResponse.builder()
                .driverName(driver.getFullName())
                .maskedLicense(MaskingUtil.maskLicense(driver.getDriverLicenseNumber()))
                .contractNumber(contract.getContractNumber())
                .agencyName(tenant != null ? tenant.getName() : null)
                .agencyLogo(tenant != null ? tenant.getLogoUrl() : null)
                .vehicleSummary(vehicleSummary)
                .rentalStartDate(contract.getStartDate())
                .rentalEndDate(contract.getEndDate())
                .mainClientName(contract.getClientFullName())
                .signatureStatus(driver.getSignatureStatus())
                .declarationVersion(driver.getDeclarationVersion() != null ? driver.getDeclarationVersion() : DECLARATION_VERSION)
                .contractCancelled(cancelled)
                .build();
    }

    /** Idempotent — never regresses SIGNED/DECLINED back to OPENED, and only notifies on the first open. */
    @Transactional
    public void markOpened(String rawToken) {
        AdditionalDriver driver = findValidByRawToken(rawToken, false);
        if (driver.getOpenedAt() != null) return;
        if (driver.getSignatureStatus() != SignatureStatus.PENDING && driver.getSignatureStatus() != SignatureStatus.LINK_SENT) {
            return;
        }
        driver.setOpenedAt(LocalDateTime.now());
        driver.setSignatureStatus(SignatureStatus.OPENED);
        AdditionalDriver saved = additionalDriverRepository.save(driver);
        logAudit(saved.getContract(), "ADDITIONAL_DRIVER_SIGNATURE_OPENED",
                "Driver id=" + saved.getId() + " (" + saved.getFullName() + ") opened the signing link");
        try {
            notificationService.createNotification(
                    "Additional driver opened signing link",
                    saved.getFullName() + " opened their signing link.",
                    Notification.NotificationType.ADDITIONAL_DRIVER_OPENED,
                    saved.getContract().getId(), saved.getContract().getTenant().getId());
        } catch (Exception e) {
            log.warn("[ADDITIONAL_DRIVER_SIGN] notification failed for opened driverId={}", saved.getId(), e);
        }
    }

    @Transactional
    public void submitSignature(String rawToken, AdditionalDriverSignatureSubmitRequest request, String ip, String userAgent) {
        AdditionalDriver driver = findValidByRawToken(rawToken, true);
        Contract contract = driver.getContract();

        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new PublicSigningException(PublicSigningException.Reason.CANCELLED, HttpStatus.CONFLICT,
                    "This contract has been cancelled and can no longer be signed.");
        }

        Map<String, Boolean> declarations = request.getDeclarationsAccepted();
        List<String> missing = REQUIRED_DECLARATIONS.stream()
                .filter(key -> declarations == null || !Boolean.TRUE.equals(declarations.get(key)))
                .toList();
        if (!missing.isEmpty()) {
            throw new PublicSigningException(PublicSigningException.Reason.VALIDATION, HttpStatus.BAD_REQUEST,
                    "The following declarations must be accepted before signing: " + String.join(", ", missing));
        }

        if (!StringUtils.hasText(request.getSignatureData()) || !isSignatureLargeEnough(request.getSignatureData())) {
            throw new PublicSigningException(PublicSigningException.Reason.VALIDATION, HttpStatus.BAD_REQUEST,
                    "A valid signature is required.");
        }

        driver.setSignatureData(request.getSignatureData());
        driver.setSignedAt(LocalDateTime.now());
        driver.setSignedIp(ip);
        driver.setSignedUserAgent(StringUtils.hasText(request.getDeviceInfo()) ? request.getDeviceInfo() : userAgent);
        driver.setDeclarationVersion(DECLARATION_VERSION);
        driver.setDeclarationsAccepted(serializeDeclarations(declarations));
        driver.setSignatureStatus(SignatureStatus.SIGNED);
        AdditionalDriver saved = additionalDriverRepository.save(driver);

        logAudit(contract, "ADDITIONAL_DRIVER_SIGNED",
                "Driver id=" + saved.getId() + " (" + saved.getFullName() + ", license " + MaskingUtil.maskLicense(saved.getDriverLicenseNumber()) + ") signed",
                ip);

        Long tenantId = contract.getTenant() != null ? contract.getTenant().getId() : null;
        try {
            notificationService.createNotification(
                    "Additional driver signed",
                    saved.getFullName() + " signed contract " + contract.getContractNumber() + ".",
                    Notification.NotificationType.ADDITIONAL_DRIVER_SIGNED,
                    contract.getId(), tenantId);
        } catch (Exception e) {
            log.warn("[ADDITIONAL_DRIVER_SIGN] notification failed for signed driverId={}", saved.getId(), e);
        }

        // Recompute the shared fully-signed policy — may flip the contract to ACTIVE
        // when this driver's signature was the last one required. recheckSignatureCompletion
        // only flips status/persists — it does NOT itself notify (that logic lives in the
        // main-client public-sign path in ContractService, which this driver-sign path
        // doesn't go through), so fire CONTRACT_FULLY_SIGNED here explicitly, and only
        // when this call is what actually completed it.
        boolean justCompleted = contractService.recheckSignatureCompletion(contract)
                && contract.getStatus() == ContractStatus.ACTIVE;
        if (justCompleted) {
            try {
                notificationService.createNotification(
                        "Contract Fully Signed",
                        saved.getFullName() + "'s signature completed all required signatures for contract "
                                + contract.getContractNumber() + ".",
                        Notification.NotificationType.CONTRACT_FULLY_SIGNED,
                        contract.getId(), tenantId);
            } catch (Exception e) {
                log.warn("[ADDITIONAL_DRIVER_SIGN] fully-signed notification failed for contractId={}", contract.getId(), e);
            }
        }
        regeneratePdf(contract);

        log.info("[ADDITIONAL_DRIVER_SIGN] driver signed driverId={} contractId={}", saved.getId(), contract.getId());
    }

    @Transactional
    public void declineSignature(String rawToken, AdditionalDriverDeclineRequest request, String ip, String userAgent) {
        AdditionalDriver driver = findValidByRawToken(rawToken, true);
        Contract contract = driver.getContract();

        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new PublicSigningException(PublicSigningException.Reason.CANCELLED, HttpStatus.CONFLICT,
                    "This contract has been cancelled.");
        }

        driver.setSignatureStatus(SignatureStatus.DECLINED);
        driver.setDeclinedAt(LocalDateTime.now());
        driver.setDeclineReason(request != null ? request.getReason() : null);
        AdditionalDriver saved = additionalDriverRepository.save(driver);

        logAudit(contract, "ADDITIONAL_DRIVER_DECLINED",
                "Driver id=" + saved.getId() + " (" + saved.getFullName() + ") declined to sign", ip);

        Long tenantId = contract.getTenant() != null ? contract.getTenant().getId() : null;
        try {
            notificationService.createNotification(
                    "Additional driver declined",
                    saved.getFullName() + " declined to sign contract " + contract.getContractNumber() + ".",
                    Notification.NotificationType.ADDITIONAL_DRIVER_DECLINED,
                    contract.getId(), tenantId);
        } catch (Exception e) {
            log.warn("[ADDITIONAL_DRIVER_SIGN] notification failed for declined driverId={}", saved.getId(), e);
        }
        log.info("[ADDITIONAL_DRIVER_SIGN] driver declined driverId={} contractId={}", saved.getId(), contract.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void regeneratePdf(Contract contract) {
        try {
            com.carrental.entity.Deposit deposit = depositRepository.findByContractId(contract.getId()).orElse(null);
            byte[] pdf = pdfService.generateContractPdf(contract, contract.getTenant(), deposit);
            String pdfUrl = pdfService.saveContractPdf(contract, pdf);
            contract.setPdfUrl(pdfUrl);
            contractRepository.save(contract);
            log.info("[ADDITIONAL_DRIVER_SIGN] PDF regenerated for contract [id={}] after driver signature", contract.getId());
        } catch (Exception e) {
            log.error("[ADDITIONAL_DRIVER_SIGN] Failed to regenerate PDF for contract [id={}]", contract.getId(), e);
        }
    }

    private String buildVehicleSummary(Contract contract) {
        if (contract.getVehicle() != null) {
            String marque = contract.getVehicle().getMarque();
            String plate = contract.getVehicle().getPlate();
            return StringUtils.hasText(plate) ? (marque + " — " + plate) : marque;
        }
        if (StringUtils.hasText(contract.getVehicleModel()) || StringUtils.hasText(contract.getVehicleRegistration())) {
            return (StringUtils.hasText(contract.getVehicleModel()) ? contract.getVehicleModel() : "") +
                    (StringUtils.hasText(contract.getVehicleRegistration()) ? " — " + contract.getVehicleRegistration() : "");
        }
        return null;
    }

    private boolean isSignatureLargeEnough(String dataUrl) {
        try {
            if (!dataUrl.contains(",")) return false;
            byte[] decoded = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(",") + 1));
            return decoded.length >= MIN_SIGNATURE_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    private String serializeDeclarations(Map<String, Boolean> declarations) {
        try {
            return objectMapper.writeValueAsString(declarations);
        } catch (Exception e) {
            return null;
        }
    }

    private AdditionalDriver fetchDriverInContract(Long contractId, Long driverId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        return additionalDriverRepository.findByIdAndContractId(driverId, contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Additional driver not found on this contract"));
    }

    /**
     * @param forMutation when true, additionally rejects a token whose driver is no
     *                    longer in a signable state (SIGNED/DECLINED/REVOKED/EXPIRED) —
     *                    mirrors ClientInformationRequestService's forSubmission guard.
     */
    private AdditionalDriver findValidByRawToken(String rawToken, boolean forMutation) {
        if (!StringUtils.hasText(rawToken)) {
            throw new PublicSigningException(PublicSigningException.Reason.INVALID, HttpStatus.NOT_FOUND, "This link is invalid.");
        }
        AdditionalDriver driver = additionalDriverRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new PublicSigningException(PublicSigningException.Reason.INVALID, HttpStatus.NOT_FOUND, "This link is invalid."));

        if (driver.getTokenRevokedAt() != null || driver.getSignatureStatus() == SignatureStatus.REVOKED) {
            throw new PublicSigningException(PublicSigningException.Reason.REVOKED, HttpStatus.GONE, "This link is no longer active.");
        }
        if (driver.getSignatureStatus() == SignatureStatus.SIGNED) {
            throw new PublicSigningException(PublicSigningException.Reason.ALREADY_SIGNED, HttpStatus.GONE, "This driver has already signed.");
        }
        if (driver.getTokenExpiresAt() != null && driver.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            if (driver.getSignatureStatus() != SignatureStatus.EXPIRED) {
                driver.setSignatureStatus(SignatureStatus.EXPIRED);
                AdditionalDriver saved = additionalDriverRepository.save(driver);
                logAudit(saved.getContract(), "ADDITIONAL_DRIVER_SIGNATURE_EXPIRED",
                        "Signing link expired for driver id=" + saved.getId() + " (" + saved.getFullName() + ")");
                try {
                    notificationService.createNotification(
                            "Additional driver link expired",
                            saved.getFullName() + "'s signing link expired.",
                            Notification.NotificationType.ADDITIONAL_DRIVER_LINK_EXPIRED,
                            saved.getContract().getId(), saved.getContract().getTenant() != null ? saved.getContract().getTenant().getId() : null);
                } catch (Exception e) {
                    log.warn("[ADDITIONAL_DRIVER_SIGN] notification failed for expired driverId={}", saved.getId(), e);
                }
            }
            throw new PublicSigningException(PublicSigningException.Reason.EXPIRED, HttpStatus.GONE, "This link has expired.");
        }
        if (forMutation && driver.getSignatureStatus() == SignatureStatus.DECLINED) {
            throw new PublicSigningException(PublicSigningException.Reason.INVALID, HttpStatus.CONFLICT, "This request has already been declined.");
        }
        return driver;
    }

    private void logAudit(Contract contract, String action, String description) {
        logAudit(contract, action, description, null);
    }

    /** Overload that records the signer's IP (spec §1's "IP address ... where legally appropriate") for sign/decline events. */
    private void logAudit(Contract contract, String action, String description, String ipAddress) {
        try {
            ContractAuditLog log = ContractAuditLog.builder()
                    .action(action)
                    .description(description)
                    .performedBy(getCurrentUser())
                    .ipAddress(ipAddress)
                    .contract(contract)
                    .build();
            contractAuditLogRepository.save(log);
        } catch (Exception e) {
            // Don't fail the main operation if audit logging fails
        }
    }

    private String getCurrentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private String buildSigningUrl(String rawToken) {
        String base = frontendUrl.replaceAll("/+$", "");
        return base + "/#/sign/additional-driver/" + rawToken;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        return com.carrental.service.RefreshTokenService.hashToken(rawToken);
    }
}
