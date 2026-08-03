package com.carrental.service;

import com.carrental.dto.contract.AdditionalDriverDeclineRequest;
import com.carrental.dto.contract.AdditionalDriverSignatureLinkResponse;
import com.carrental.dto.contract.AdditionalDriverSignatureSubmitRequest;
import com.carrental.dto.contract.PublicAdditionalDriverSigningResponse;
import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractAuditLog;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.SignatureStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.PublicSigningException;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers AdditionalDriverSigningService's independent signing workflow:
 * distinct per-driver tokens, cross-driver isolation, expired/revoked/reused
 * token rejection, submission validation, successful signing side effects,
 * and cancelled-contract rejection. Modeled on ClientInformationRequestServiceTest
 * (same hashed-token / lazy-expiry-on-read architecture).
 */
@ExtendWith(MockitoExtension.class)
class AdditionalDriverSigningServiceTest {

    @Mock private AdditionalDriverRepository additionalDriverRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractAuditLogRepository contractAuditLogRepository;
    @Mock private DepositRepository depositRepository;
    @Mock private NotificationService notificationService;
    @Mock private ContractService contractService;
    @Mock private PdfService pdfService;

    private AdditionalDriverSigningService service;
    private Tenant tenant;
    private Contract contract;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AdditionalDriverSigningService(
                additionalDriverRepository, contractRepository, contractAuditLogRepository,
                depositRepository, notificationService, contractService, pdfService, objectMapper);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://innovacar.app");

        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
        contract = Contract.builder()
                .id(100L).contractNumber("CTR-2026-00100").tenant(tenant)
                .status(ContractStatus.PARTIALLY_SIGNED)
                .build();

        lenient().when(additionalDriverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contractAuditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AdditionalDriver driver(Long id, SignatureStatus status) {
        return AdditionalDriver.builder()
                .id(id).fullName("Driver " + id).driverLicenseNumber("DL-" + id)
                .signatureRequired(true).signatureStatus(status)
                .contract(contract)
                .build();
    }

    private String validSignatureDataUrl() {
        byte[] bytes = new byte[200];
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private AdditionalDriverSignatureSubmitRequest validSubmitRequest() {
        AdditionalDriverSignatureSubmitRequest req = new AdditionalDriverSignatureSubmitRequest();
        req.setDeclarationsAccepted(Map.of(
                "identityCorrect", true,
                "licenseValid", true,
                "responsibilityAccepted", true,
                "termsRead", true,
                "dataConsent", true));
        req.setSignatureData(validSignatureDataUrl());
        req.setDeviceInfo("JUnit-Agent");
        return req;
    }

    // ── 1. Distinct tokens per driver ───────────────────────────────────────

    @Test
    void generatingLinksForMultipleDriversProducesDistinctTokenHashes() {
        AdditionalDriver driverA = driver(1L, SignatureStatus.PENDING);
        AdditionalDriver driverB = driver(2L, SignatureStatus.PENDING);
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        when(additionalDriverRepository.findByIdAndContractId(1L, 100L)).thenReturn(Optional.of(driverA));
        when(additionalDriverRepository.findByIdAndContractId(2L, 100L)).thenReturn(Optional.of(driverB));

        service.generateLink(100L, 1L);
        service.generateLink(100L, 2L);

        assertThat(driverA.getTokenHash()).isNotNull();
        assertThat(driverB.getTokenHash()).isNotNull();
        assertThat(driverA.getTokenHash()).isNotEqualTo(driverB.getTokenHash());
    }

    // ── 2. Cross-driver token rejection ─────────────────────────────────────

    @Test
    void aTokenGeneratedForOneDriverNeverResolvesToAnotherDriversRecord() {
        AdditionalDriver driverA = driver(1L, SignatureStatus.PENDING);
        AdditionalDriver driverB = driver(2L, SignatureStatus.PENDING);
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        when(additionalDriverRepository.findByIdAndContractId(1L, 100L)).thenReturn(Optional.of(driverA));
        when(additionalDriverRepository.findByIdAndContractId(2L, 100L)).thenReturn(Optional.of(driverB));

        AdditionalDriverSignatureLinkResponse linkA = service.generateLink(100L, 1L);
        AdditionalDriverSignatureLinkResponse linkB = service.generateLink(100L, 2L);
        String rawTokenA = extractToken(linkA.getSigningUrl());
        String rawTokenB = extractToken(linkB.getSigningUrl());

        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken(rawTokenA)))
                .thenReturn(Optional.of(driverA));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken(rawTokenB)))
                .thenReturn(Optional.of(driverB));

        PublicAdditionalDriverSigningResponse viewA = service.getPublicView(rawTokenA);
        PublicAdditionalDriverSigningResponse viewB = service.getPublicView(rawTokenB);

        assertThat(viewA.getDriverName()).isEqualTo("Driver 1");
        assertThat(viewB.getDriverName()).isEqualTo("Driver 2");
        assertThat(viewA.getDriverName()).isNotEqualTo(viewB.getDriverName());
    }

    private String extractToken(String signingUrl) {
        return signingUrl.substring(signingUrl.lastIndexOf('/') + 1);
    }

    // ── 3. Expired token ─────────────────────────────────────────────────────

    @Test
    void expiredTokenIsRejectedOnSignAndFlipsStatusToExpired() {
        AdditionalDriver expiredDriver = driver(1L, SignatureStatus.LINK_SENT);
        expiredDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        expiredDriver.setTokenExpiresAt(LocalDateTime.now().minusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(expiredDriver));

        assertThatThrownBy(() -> service.submitSignature("raw-token", validSubmitRequest(), "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.EXPIRED);

        assertThat(expiredDriver.getSignatureStatus()).isEqualTo(SignatureStatus.EXPIRED);
        verify(additionalDriverRepository).save(expiredDriver);
        ArgumentCaptor<ContractAuditLog> auditCaptor = ArgumentCaptor.forClass(ContractAuditLog.class);
        verify(contractAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ADDITIONAL_DRIVER_SIGNATURE_EXPIRED");
    }

    // ── 4. Revoked token ─────────────────────────────────────────────────────

    @Test
    void revokedTokenIsRejectedOnSign() {
        AdditionalDriver revokedDriver = driver(1L, SignatureStatus.REVOKED);
        revokedDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        revokedDriver.setTokenRevokedAt(LocalDateTime.now().minusMinutes(5));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(revokedDriver));

        assertThatThrownBy(() -> service.submitSignature("raw-token", validSubmitRequest(), "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.REVOKED);
    }

    // ── 5. Reused token after successful signature ──────────────────────────

    @Test
    void aTokenCannotBeReusedAfterASuccessfulSignature() {
        AdditionalDriver signedDriver = driver(1L, SignatureStatus.SIGNED);
        signedDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(signedDriver));

        assertThatThrownBy(() -> service.submitSignature("raw-token", validSubmitRequest(), "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.ALREADY_SIGNED);

        AdditionalDriverDeclineRequest declineReq = new AdditionalDriverDeclineRequest();
        assertThatThrownBy(() -> service.declineSignature("raw-token", declineReq, "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.ALREADY_SIGNED);
    }

    // ── 6. Empty/too-small signature rejected, no mutation ──────────────────

    @Test
    void emptySignatureDataIsRejectedAndDoesNotMutateStatus() {
        AdditionalDriver pendingDriver = driver(1L, SignatureStatus.LINK_SENT);
        pendingDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        pendingDriver.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(pendingDriver));

        AdditionalDriverSignatureSubmitRequest req = validSubmitRequest();
        req.setSignatureData("");

        assertThatThrownBy(() -> service.submitSignature("raw-token", req, "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.VALIDATION);

        assertThat(pendingDriver.getSignatureStatus()).isEqualTo(SignatureStatus.LINK_SENT);
        assertThat(pendingDriver.getSignedAt()).isNull();
        verify(additionalDriverRepository, never()).save(any());
    }

    @Test
    void tooSmallSignatureDataIsRejected() {
        AdditionalDriver pendingDriver = driver(1L, SignatureStatus.LINK_SENT);
        pendingDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        pendingDriver.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(pendingDriver));

        AdditionalDriverSignatureSubmitRequest req = validSubmitRequest();
        // Only a handful of decoded bytes — under MIN_SIGNATURE_BYTES (100).
        req.setSignatureData("data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[5]));

        assertThatThrownBy(() -> service.submitSignature("raw-token", req, "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.VALIDATION);

        assertThat(pendingDriver.getSignatureStatus()).isEqualTo(SignatureStatus.LINK_SENT);
        verify(additionalDriverRepository, never()).save(any());
    }

    // ── 7. Missing declarations rejected, no persist ────────────────────────

    @Test
    void missingRequiredDeclarationsAreRejectedAndNothingIsPersisted() {
        AdditionalDriver pendingDriver = driver(1L, SignatureStatus.LINK_SENT);
        pendingDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        pendingDriver.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(pendingDriver));

        AdditionalDriverSignatureSubmitRequest req = validSubmitRequest();
        req.setDeclarationsAccepted(Map.of(
                "identityCorrect", true,
                "licenseValid", true,
                "responsibilityAccepted", false, // missing/false
                "termsRead", true,
                "dataConsent", true));

        assertThatThrownBy(() -> service.submitSignature("raw-token", req, "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.VALIDATION);

        assertThat(pendingDriver.getSignatureStatus()).isEqualTo(SignatureStatus.LINK_SENT);
        assertThat(pendingDriver.getSignatureData()).isNull();
        verify(additionalDriverRepository, never()).save(any());
    }

    // ── 8. Fully valid submission persists and audits ───────────────────────

    @Test
    void aFullyValidSubmissionPersistsSignatureAndAuditsWithoutLeakingSecrets() {
        AdditionalDriver pendingDriver = driver(1L, SignatureStatus.LINK_SENT);
        pendingDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        pendingDriver.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(pendingDriver));
        when(contractService.recheckSignatureCompletion(contract)).thenReturn(false);
        when(depositRepository.findByContractId(100L)).thenReturn(Optional.empty());
        when(pdfService.generateContractPdf(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(pdfService.saveContractPdf(any(), any())).thenReturn("/pdfs/x.pdf");

        AdditionalDriverSignatureSubmitRequest req = validSubmitRequest();
        service.submitSignature("raw-token", req, "9.9.9.9", "SomeAgent/1.0");

        assertThat(pendingDriver.getSignatureData()).isEqualTo(req.getSignatureData());
        assertThat(pendingDriver.getSignedAt()).isNotNull();
        assertThat(pendingDriver.getSignedIp()).isEqualTo("9.9.9.9");
        assertThat(pendingDriver.getSignedUserAgent()).isEqualTo("JUnit-Agent"); // deviceInfo takes precedence over UA header
        assertThat(pendingDriver.getDeclarationVersion()).isEqualTo("v1");
        assertThat(pendingDriver.getDeclarationsAccepted()).contains("identityCorrect");
        assertThat(pendingDriver.getSignatureStatus()).isEqualTo(SignatureStatus.SIGNED);

        ArgumentCaptor<ContractAuditLog> auditCaptor = ArgumentCaptor.forClass(ContractAuditLog.class);
        verify(contractAuditLogRepository).save(auditCaptor.capture());
        ContractAuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo("ADDITIONAL_DRIVER_SIGNED");
        assertThat(audit.getDescription()).doesNotContain("raw-token");
        assertThat(audit.getDescription()).doesNotContain(req.getSignatureData());
    }

    // ── 9. Cancelled contract rejects signing ───────────────────────────────

    @Test
    void signingOnACancelledContractIsRejected() {
        contract.setStatus(ContractStatus.CANCELLED);
        AdditionalDriver pendingDriver = driver(1L, SignatureStatus.LINK_SENT);
        pendingDriver.setTokenHash(RefreshTokenService.hashToken("raw-token"));
        pendingDriver.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(additionalDriverRepository.findByTokenHash(RefreshTokenService.hashToken("raw-token")))
                .thenReturn(Optional.of(pendingDriver));

        assertThatThrownBy(() -> service.submitSignature("raw-token", validSubmitRequest(), "1.2.3.4", "UA"))
                .isInstanceOf(PublicSigningException.class)
                .extracting(e -> ((PublicSigningException) e).getReason())
                .isEqualTo(PublicSigningException.Reason.CANCELLED);
    }
}
