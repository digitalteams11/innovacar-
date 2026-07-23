package com.carrental.service;

import com.carrental.dto.clientinfo.ApproveClientInformationRequest;
import com.carrental.dto.clientinfo.ClientInformationRequestResponse;
import com.carrental.dto.clientinfo.ClientInformationSubmitRequest;
import com.carrental.dto.clientinfo.CreateClientInformationRequestRequest;
import com.carrental.dto.clientinfo.PublicClientInformationView;
import com.carrental.entity.Client;
import com.carrental.entity.ClientIdentityDocument;
import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import com.carrental.entity.Contract;
import com.carrental.entity.Notification;
import com.carrental.entity.Tenant;
import com.carrental.exception.ClientInfoRequestException;
import com.carrental.repository.ClientIdentityDocumentRepository;
import com.carrental.repository.ClientInformationRequestRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Client self-fill information" workflow — MVP slice (contract entry point
 * only, no reservation flow, no file upload, no correction round-trip, no
 * automated reminders yet). See ClientInformationRequest.java and
 * ClientInfoRequestStatus.java for what's deliberately deferred.
 *
 * Critical rule enforced throughout: a client's submission is unverified
 * input. It is never used to create/overwrite a real Client record until an
 * admin explicitly approves it (see {@link #approve}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInformationRequestService {

    private static final int DEFAULT_EXPIRY_HOURS = 48;
    private static final int TOKEN_BYTES = 32;

    private final ClientInformationRequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final ClientIdentityDocumentRepository identityDocumentRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── Admin: create / list / detail ───────────────────────────────────────

    @Transactional
    public ClientInformationRequestResponse create(CreateClientInformationRequestRequest req) {
        Long tenantId = TenantContext.getCurrentTenantId();

        if (req.getContractId() != null) {
            contractRepository.findByIdAndTenantId(req.getContractId(), tenantId)
                    .orElseThrow(() -> new ClientInfoRequestException(
                            "CLIENT_INFO_RELATED_RECORD_LOCKED", HttpStatus.BAD_REQUEST,
                            "The related contract could not be found."));
        }

        String rawToken = generateRawToken();
        int hours = req.getExpiresInHours() != null && req.getExpiresInHours() > 0 ? req.getExpiresInHours() : DEFAULT_EXPIRY_HOURS;

        ClientInformationRequest entity = ClientInformationRequest.builder()
                .tenantId(tenantId)
                .tokenHash(hash(rawToken))
                .temporaryName(req.getTemporaryName())
                .phone(req.getPhone())
                .email(req.getEmail())
                .preferredLanguage(StringUtils.hasText(req.getPreferredLanguage()) ? req.getPreferredLanguage() : "fr")
                .status(ClientInfoRequestStatus.SENT)
                .expiresAt(LocalDateTime.now().plusHours(hours))
                .contractId(req.getContractId())
                .build();

        ClientInformationRequest saved = requestRepository.save(entity);
        log.info("[CLIENT_INFO] request created id={} tenantId={} contractId={}", saved.getId(), tenantId, saved.getContractId());

        ClientInformationRequestResponse response = ClientInformationRequestResponse.from(saved);
        response.setSecureLink(buildSecureLink(rawToken));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ClientInformationRequestResponse> list() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return requestRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDetailResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientInformationRequestResponse getDetail(Long id) {
        ClientInformationRequest r = fetchInTenant(id);
        return toDetailResponse(r);
    }

    private ClientInformationRequestResponse toDetailResponse(ClientInformationRequest r) {
        ClientInformationRequestResponse dto = ClientInformationRequestResponse.from(r);
        if (r.getSubmissionPayload() != null) {
            dto.setSubmission(deserializeSubmission(r.getSubmissionPayload()));
            dto.setPotentialDuplicates(findPotentialDuplicates(r.getTenantId(), dto.getSubmission()));
        }
        return dto;
    }

    // ── Admin: revoke ────────────────────────────────────────────────────────

    @Transactional
    public void revoke(Long id) {
        ClientInformationRequest r = fetchInTenant(id);
        if (r.getStatus() == ClientInfoRequestStatus.APPROVED) {
            throw new ClientInfoRequestException("CLIENT_INFO_ALREADY_APPROVED", HttpStatus.CONFLICT,
                    "This request has already been approved and cannot be revoked.");
        }
        r.setStatus(ClientInfoRequestStatus.REVOKED);
        r.setRevokedAt(LocalDateTime.now());
        requestRepository.save(r);
        log.info("[CLIENT_INFO] request revoked id={} tenantId={}", id, r.getTenantId());
    }

    // ── Admin: approve (transactional — spec section 15) ────────────────────

    @Transactional
    public ClientInformationRequestResponse approve(Long id, ApproveClientInformationRequest req) {
        ClientInformationRequest r = fetchInTenant(id);
        if (r.getStatus() == ClientInfoRequestStatus.APPROVED) {
            throw new ClientInfoRequestException("CLIENT_INFO_ALREADY_APPROVED", HttpStatus.CONFLICT,
                    "This request has already been approved.");
        }
        if (r.getStatus() != ClientInfoRequestStatus.SUBMITTED) {
            throw new ClientInfoRequestException("CLIENT_INFO_CORRECTION_REQUIRED", HttpStatus.CONFLICT,
                    "This request has no submission to approve yet.");
        }
        ClientInformationSubmitRequest submission = deserializeSubmission(r.getSubmissionPayload());

        Client client;
        if (req.getAction() == ApproveClientInformationRequest.Action.LINK_EXISTING) {
            if (req.getExistingClientId() == null) {
                throw new ClientInfoRequestException("CLIENT_INFO_ACCESS_DENIED", HttpStatus.BAD_REQUEST,
                        "existingClientId is required when linking to an existing client.");
            }
            client = clientRepository.findByIdAndTenantId(req.getExistingClientId(), r.getTenantId())
                    .orElseThrow(() -> new ClientInfoRequestException("CLIENT_INFO_ACCESS_DENIED", HttpStatus.NOT_FOUND,
                            "The selected existing client could not be found."));
            // Deliberately NOT overwriting the existing client's fields here —
            // spec section 14 (field-by-field review) is out of scope for this
            // MVP slice; linking only, no silent overwrite of verified data.
        } else {
            client = createClientFromSubmission(r.getTenantId(), submission);
            attachIdentityDocument(r.getTenantId(), client.getId(), submission);
        }

        if (r.getContractId() != null) {
            linkClientToContract(r.getTenantId(), r.getContractId(), client, submission);
        }

        r.setStatus(ClientInfoRequestStatus.APPROVED);
        r.setApprovedAt(LocalDateTime.now());
        r.setApprovedClientId(client.getId());
        ClientInformationRequest saved = requestRepository.save(r);

        log.info("[CLIENT_INFO] request approved id={} tenantId={} clientId={} action={}",
                id, r.getTenantId(), client.getId(), req.getAction());

        return toDetailResponse(saved);
    }

    private Client createClientFromSubmission(Long tenantId, ClientInformationSubmitRequest s) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ClientInfoRequestException("CLIENT_INFO_RELATED_RECORD_LOCKED", HttpStatus.BAD_REQUEST, "Tenant not found."));
        Client client = Client.builder()
                .tenant(tenant)
                .name(s.getFullName())
                .phone(s.getPhone())
                .secondaryPhone(s.getSecondaryPhone())
                .email(s.getEmail())
                .gender(s.getGender())
                .birthDate(s.getBirthDate())
                .nationality(s.getNationality())
                .address(s.getAddress())
                .city(s.getCity())
                .postalCode(s.getPostalCode())
                .country(s.getCountry())
                .drivingLicense(s.getDriverLicenseNumber())
                .drivingLicenseCategory(s.getDriverLicenseCategory())
                .drivingLicenseIssue(s.getDriverLicenseIssueDate())
                .drivingLicenseExpiry(s.getDriverLicenseExpiryDate())
                .drivingLicenseCountry(s.getDriverLicenseCountry())
                .companyName(s.getCompanyName())
                .notes(s.getNotes())
                .build();
        if (s.getDocumentType() == com.carrental.entity.DocumentType.CIN) {
            client.setCin(s.getDocumentNumber());
        } else if (s.getDocumentType() == com.carrental.entity.DocumentType.PASSPORT) {
            client.setPassportNumber(s.getDocumentNumber());
        }
        return clientRepository.save(client);
    }

    private void attachIdentityDocument(Long tenantId, Long clientId, ClientInformationSubmitRequest s) {
        if (s.getDocumentType() == null || !StringUtils.hasText(s.getDocumentNumber())) return;
        ClientIdentityDocument doc = ClientIdentityDocument.builder()
                .tenantId(tenantId)
                .clientId(clientId)
                .documentType(s.getDocumentType())
                .documentNumber(s.getDocumentNumber())
                .issuingCountry(s.getDocumentIssuingCountry())
                .issueDate(s.getDocumentIssueDate())
                .expiryDate(s.getDocumentExpiryDate())
                .isPrimary(true)
                .build();
        identityDocumentRepository.save(doc);
    }

    /** Denormalized snapshot fields on Contract (spec section 16/17) — additive only, never touches price/vehicle/status/payment. */
    private void linkClientToContract(Long tenantId, Long contractId, Client client, ClientInformationSubmitRequest s) {
        Contract contract = contractRepository.findByIdAndTenantId(contractId, tenantId).orElse(null);
        if (contract == null) {
            log.warn("[CLIENT_INFO] contract {} not found in tenant {} at approval time — skipping contract link", contractId, tenantId);
            return;
        }
        contract.setClient(client);
        contract.setClientFullName(s.getFullName());
        contract.setClientPhone(s.getPhone());
        contract.setClientSecondaryPhone(s.getSecondaryPhone());
        contract.setClientEmail(s.getEmail());
        contract.setClientGender(s.getGender());
        contract.setClientBirthDate(s.getBirthDate());
        contract.setClientNationality(s.getNationality());
        contract.setClientAddress(s.getAddress());
        contract.setClientCity(s.getCity());
        contract.setClientCountry(s.getCountry());
        contract.setClientPostalCode(s.getPostalCode());
        contract.setClientDriverLicense(s.getDriverLicenseNumber());
        contract.setClientDriverLicenseIssue(s.getDriverLicenseIssueDate());
        contract.setClientDriverLicenseExpiry(s.getDriverLicenseExpiryDate());
        if (s.getDocumentType() == com.carrental.entity.DocumentType.CIN) {
            contract.setClientCin(s.getDocumentNumber());
        } else if (s.getDocumentType() == com.carrental.entity.DocumentType.PASSPORT) {
            contract.setClientPassportNumber(s.getDocumentNumber());
        }
        contractRepository.save(contract);
    }

    /** Spec section 13 — normalized phone/email/document-number matching, admin decides, never auto-merged. */
    private List<ClientInformationRequestResponse.ClientMatchSummary> findPotentialDuplicates(Long tenantId, ClientInformationSubmitRequest s) {
        List<ClientInformationRequestResponse.ClientMatchSummary> matches = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();

        if (StringUtils.hasText(s.getPhone())) {
            clientRepository.findFirstByTenantIdAndPhoneIgnoreCaseAndDeletedFalse(tenantId, s.getPhone().trim())
                    .ifPresent(c -> addMatch(matches, seen, c, "phone"));
        }
        if (StringUtils.hasText(s.getEmail())) {
            clientRepository.findFirstByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, s.getEmail().trim())
                    .ifPresent(c -> addMatch(matches, seen, c, "email"));
        }
        if (StringUtils.hasText(s.getDocumentNumber())) {
            identityDocumentRepository.findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(tenantId, s.getDocumentNumber().trim())
                    .ifPresent(doc -> clientRepository.findByIdAndTenantId(doc.getClientId(), tenantId)
                            .ifPresent(c -> addMatch(matches, seen, c, "document")));
        }
        return matches;
    }

    private void addMatch(List<ClientInformationRequestResponse.ClientMatchSummary> matches, Set<Long> seen, Client c, String matchedOn) {
        if (!seen.add(c.getId())) return;
        matches.add(ClientInformationRequestResponse.ClientMatchSummary.builder()
                .clientId(c.getId()).name(c.getName()).phone(c.getPhone()).email(c.getEmail())
                .matchedOn(matchedOn)
                .build());
    }

    // ── Public: get / submit ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicClientInformationView getPublic(String rawToken) {
        ClientInformationRequest r = findValidByRawToken(rawToken, false);
        Tenant tenant = tenantRepository.findById(r.getTenantId()).orElse(null);
        return PublicClientInformationView.builder()
                .temporaryName(r.getTemporaryName())
                .preferredLanguage(r.getPreferredLanguage())
                .agencyName(tenant != null ? tenant.getName() : null)
                .agencyLogo(tenant != null ? tenant.getLogoUrl() : null)
                .expiresAt(r.getExpiresAt())
                .alreadySubmitted(r.getStatus() == ClientInfoRequestStatus.SUBMITTED || r.getStatus() == ClientInfoRequestStatus.APPROVED)
                .build();
    }

    @Transactional
    public void submit(String rawToken, ClientInformationSubmitRequest submission) {
        if (submission.getPrivacyAccepted() == null || !submission.getPrivacyAccepted()) {
            throw new ClientInfoRequestException("CLIENT_INFO_LINK_INVALID", HttpStatus.BAD_REQUEST,
                    "You must accept the privacy notice before submitting.");
        }
        ClientInformationRequest r = findValidByRawToken(rawToken, true);

        r.setSubmissionPayload(serializeSubmission(submission));
        r.setSubmittedAt(LocalDateTime.now());
        r.setPrivacyAcceptedAt(LocalDateTime.now());
        r.setStatus(ClientInfoRequestStatus.SUBMITTED);
        requestRepository.save(r);

        notificationService.createNotification(
                "Client information submitted",
                (StringUtils.hasText(r.getTemporaryName()) ? r.getTemporaryName() : "A client") + " submitted their information for review.",
                Notification.NotificationType.INFORMATION,
                r.getContractId(), r.getTenantId());

        log.info("[CLIENT_INFO] submission received tenantId={} tokenPrefix={}...", r.getTenantId(), maskToken(rawToken));
    }

    /**
     * @param forSubmission when true, additionally rejects an already-SUBMITTED/APPROVED
     *                      request (one submission only — spec section 11 "prevent
     *                      duplicate rapid submissions" / "no silent resubmission").
     */
    private ClientInformationRequest findValidByRawToken(String rawToken, boolean forSubmission) {
        if (!StringUtils.hasText(rawToken)) {
            throw new ClientInfoRequestException("CLIENT_INFO_LINK_INVALID", HttpStatus.NOT_FOUND, "This link is invalid.");
        }
        ClientInformationRequest r = requestRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ClientInfoRequestException("CLIENT_INFO_LINK_INVALID", HttpStatus.NOT_FOUND, "This link is invalid."));

        if (r.getStatus() == ClientInfoRequestStatus.REVOKED) {
            throw new ClientInfoRequestException("CLIENT_INFO_LINK_REVOKED", HttpStatus.GONE, "This link is no longer active.");
        }
        if (r.getStatus() == ClientInfoRequestStatus.APPROVED) {
            throw new ClientInfoRequestException("CLIENT_INFO_ALREADY_APPROVED", HttpStatus.GONE, "This request has already been approved.");
        }
        if (r.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ClientInfoRequestException("CLIENT_INFO_LINK_EXPIRED", HttpStatus.GONE, "This link has expired.");
        }
        if (forSubmission && r.getStatus() == ClientInfoRequestStatus.SUBMITTED) {
            throw new ClientInfoRequestException("CLIENT_INFO_ALREADY_SUBMITTED", HttpStatus.CONFLICT,
                    "This information has already been submitted and is awaiting review.");
        }
        return r;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientInformationRequest fetchInTenant(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ClientInfoRequestException("CLIENT_INFO_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Client information request not found."));
    }

    private String buildSecureLink(String rawToken) {
        String base = frontendUrl.replaceAll("/+$", "");
        return base + "/#/client-info/" + rawToken;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 6) return "***";
        return token.substring(0, 6);
    }

    private String serializeSubmission(ClientInformationSubmitRequest s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new ClientInfoRequestException("CLIENT_INFO_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process the submission.");
        }
    }

    private ClientInformationSubmitRequest deserializeSubmission(String json) {
        try {
            return objectMapper.readValue(json, ClientInformationSubmitRequest.class);
        } catch (Exception e) {
            throw new ClientInfoRequestException("CLIENT_INFO_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the submission.");
        }
    }
}
