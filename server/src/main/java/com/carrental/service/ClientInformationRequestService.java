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
import com.carrental.entity.DeliveryStatus;
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
import java.util.regex.Pattern;

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
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ClientInformationRequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final ClientIdentityDocumentRepository identityDocumentRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

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

        String phone = req.getPhone();
        String email = req.getEmail();
        String temporaryName = req.getTemporaryName();

        if (req.getClientId() != null) {
            Client client = clientRepository.findByIdAndTenantId(req.getClientId(), tenantId)
                    .orElseThrow(() -> new ClientInfoRequestException("TENANT_ACCESS_DENIED", HttpStatus.FORBIDDEN,
                            "The selected client could not be found in this agency."));
            if (!StringUtils.hasText(phone)) phone = client.getPhone();
            if (!StringUtils.hasText(email)) email = client.getEmail();
            if (!StringUtils.hasText(temporaryName)) temporaryName = client.getName();
        }

        String normalizedPhone = null;
        if (StringUtils.hasText(phone)) {
            normalizedPhone = normalizeMoroccanPhone(phone);
            if (normalizedPhone == null) {
                throw new ClientInfoRequestException("INVALID_PHONE", HttpStatus.BAD_REQUEST,
                        "This phone number is not a valid Moroccan number.");
            }
        }
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ClientInfoRequestException("INVALID_EMAIL", HttpStatus.BAD_REQUEST,
                    "This email address is not valid.");
        }

        if (!StringUtils.hasText(normalizedPhone) && !StringUtils.hasText(email)) {
            throw new ClientInfoRequestException("NO_CHANNEL_AVAILABLE", HttpStatus.BAD_REQUEST,
                    "At least a valid email or phone number is required to send the form.");
        }
        // Email is the only channel the backend auto-sends; WhatsApp is a manual,
        // client-side share action (wa.me link) built by the frontend — no
        // provider, credentials or webhook required on this side.
        Set<String> channels = resolveDeliveryChannels(req.getDeliveryChannels(), email);

        String rawToken = generateRawToken();
        int hours = req.getExpiresInHours() != null && req.getExpiresInHours() > 0 ? req.getExpiresInHours() : DEFAULT_EXPIRY_HOURS;
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(hours);

        ClientInformationRequest entity = ClientInformationRequest.builder()
                .tenantId(tenantId)
                .tokenHash(hash(rawToken))
                .clientId(req.getClientId())
                .temporaryName(temporaryName)
                .phone(normalizedPhone)
                .email(email)
                .preferredLanguage(StringUtils.hasText(req.getPreferredLanguage()) ? req.getPreferredLanguage() : "fr")
                .status(ClientInfoRequestStatus.SENT)
                .expiresAt(expiresAt)
                .contractId(req.getContractId())
                .deliveryChannels(String.join(",", channels))
                .build();

        ClientInformationRequest saved = requestRepository.save(entity);
        log.info("[CLIENT_INFO] request created id={} tenantId={} contractId={} channels={}",
                saved.getId(), tenantId, saved.getContractId(), channels);

        String publicUrl = buildSecureLink(rawToken);
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String agencyName = tenant != null ? tenant.getName() : null;

        ClientInformationRequestResponse.DeliveryResult emailResult = deliverEmail(saved, channels, publicUrl, agencyName);

        saved.setEmailDeliveryStatus(emailResult.getStatus());
        requestRepository.save(saved);

        ClientInformationRequestResponse response = ClientInformationRequestResponse.from(saved);
        response.setSecureLink(publicUrl);
        response.setPublicUrl(publicUrl);
        response.setEmailResult(emailResult);
        return response;
    }

    /** Retries delivery on whichever channels are requested (typically just the one that failed). */
    @Transactional
    public ClientInformationRequestResponse resend(Long id, List<String> requestedChannels) {
        ClientInformationRequest r = fetchInTenant(id);
        if (r.getStatus() == ClientInfoRequestStatus.APPROVED || r.getStatus() == ClientInfoRequestStatus.REJECTED
                || r.getStatus() == ClientInfoRequestStatus.REVOKED) {
            throw new ClientInfoRequestException("CLIENT_INFO_ALREADY_APPROVED", HttpStatus.CONFLICT,
                    "This request is no longer active and cannot be resent.");
        }
        if (r.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ClientInfoRequestException("REQUEST_EXPIRED", HttpStatus.GONE, "This request has expired.");
        }

        if (!StringUtils.hasText(r.getPhone()) && !StringUtils.hasText(r.getEmail())) {
            throw new ClientInfoRequestException("NO_CHANNEL_AVAILABLE", HttpStatus.BAD_REQUEST,
                    "At least a valid email or phone number is required to resend the form.");
        }
        Set<String> channels = resolveDeliveryChannels(requestedChannels, r.getEmail());

        // A fresh raw token is generated on every resend — the old link is invalidated,
        // consistent with "single-purpose, unusable after ..." token rules.
        String rawToken = generateRawToken();
        r.setTokenHash(hash(rawToken));
        String publicUrl = buildSecureLink(rawToken);
        Tenant tenant = tenantRepository.findById(r.getTenantId()).orElse(null);
        String agencyName = tenant != null ? tenant.getName() : null;

        ClientInformationRequestResponse.DeliveryResult emailResult = channels.contains("EMAIL")
                ? deliverEmail(r, channels, publicUrl, agencyName)
                : ClientInformationRequestResponse.DeliveryResult.builder().attempted(false).sent(false).status(r.getEmailDeliveryStatus()).build();

        if (channels.contains("EMAIL")) r.setEmailDeliveryStatus(emailResult.getStatus());
        if (r.getStatus() == ClientInfoRequestStatus.EXPIRED) r.setStatus(ClientInfoRequestStatus.SENT);
        ClientInformationRequest saved = requestRepository.save(r);

        ClientInformationRequestResponse response = ClientInformationRequestResponse.from(saved);
        response.setSecureLink(publicUrl);
        response.setPublicUrl(publicUrl);
        response.setEmailResult(emailResult);
        log.info("[CLIENT_INFO] request resent id={} tenantId={} channels={}", id, r.getTenantId(), channels);
        return response;
    }

    @Transactional(readOnly = true)
    public ClientInformationRequestResponse deliveryStatus(Long id) {
        return toDetailResponse(fetchInTenant(id));
    }

    private ClientInformationRequestResponse.DeliveryResult deliverEmail(ClientInformationRequest r, Set<String> channels,
                                                                          String publicUrl, String agencyName) {
        if (!channels.contains("EMAIL")) {
            return ClientInformationRequestResponse.DeliveryResult.builder()
                    .attempted(false).sent(false).status(DeliveryStatus.NOT_REQUESTED).build();
        }
        r.setEmailLastAttemptAt(LocalDateTime.now());
        SmtpMailService.SmtpResult result = emailService.sendClientInformationRequestEmail(
                r.getEmail(), r.getTemporaryName(), agencyName, publicUrl, r.getExpiresAt(), r.getPreferredLanguage());
        if (result.sent()) {
            r.setEmailSentAt(LocalDateTime.now());
            r.setEmailLastError(null);
            return ClientInformationRequestResponse.DeliveryResult.builder()
                    .attempted(true).sent(true).status(DeliveryStatus.SENT).message("Email sent successfully").build();
        }
        boolean notConfigured = "EMAIL_CONFIGURATION_MISSING".equals(result.errorCode()) || "EMAIL_NOT_CONFIGURED".equals(result.errorCode());
        r.setEmailLastError(result.errorMessage());
        return ClientInformationRequestResponse.DeliveryResult.builder()
                .attempted(true).sent(false)
                .status(notConfigured ? DeliveryStatus.NOT_CONFIGURED : DeliveryStatus.FAILED)
                .message(result.errorMessage() != null ? result.errorMessage() : "Email delivery failed")
                .build();
    }

    /**
     * The only backend-attempted channel is email; WhatsApp is a manual,
     * client-side share action (see the frontend modal) and is never sent
     * from here. Defaults to EMAIL when the admin didn't explicitly pick a
     * channel and an email address is available.
     */
    private Set<String> resolveDeliveryChannels(List<String> requested, String email) {
        Set<String> available = new LinkedHashSet<>();
        if (StringUtils.hasText(email)) available.add("EMAIL");

        if (requested == null || requested.isEmpty()) return available;
        Set<String> result = new LinkedHashSet<>();
        for (String c : requested) {
            String upper = c == null ? "" : c.trim().toUpperCase();
            if (available.contains(upper)) result.add(upper);
        }
        return result;
    }

    /**
     * Normalizes a Moroccan phone number to E.164 (+212XXXXXXXXX):
     * 0658742744 / 212658742744 / +212658742744 all become +212658742744.
     * Returns null if it doesn't look like a valid 9-digit Moroccan subscriber number.
     */
    static String normalizeMoroccanPhone(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("212")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[5-7][0-9]{8}")) return null;
        return "+212" + digits;
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

    // ── Admin: reject ────────────────────────────────────────────────────────

    @Transactional
    public ClientInformationRequestResponse reject(Long id) {
        ClientInformationRequest r = fetchInTenant(id);
        if (r.getStatus() != ClientInfoRequestStatus.SUBMITTED) {
            throw new ClientInfoRequestException("CLIENT_INFO_CORRECTION_REQUIRED", HttpStatus.CONFLICT,
                    "This request has no submission to reject.");
        }
        r.setStatus(ClientInfoRequestStatus.REJECTED);
        r.setRejectedAt(LocalDateTime.now());
        ClientInformationRequest saved = requestRepository.save(r);
        log.info("[CLIENT_INFO] request rejected id={} tenantId={}", id, r.getTenantId());
        return toDetailResponse(saved);
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

    @Transactional
    public PublicClientInformationView getPublic(String rawToken) {
        ClientInformationRequest r = findValidByRawToken(rawToken, false);
        if (r.getOpenedAt() == null && (r.getStatus() == ClientInfoRequestStatus.SENT)) {
            r.setOpenedAt(LocalDateTime.now());
            r.setStatus(ClientInfoRequestStatus.OPENED);
            requestRepository.save(r);
        }
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

        sendSubmissionConfirmation(r, submission);

        log.info("[CLIENT_INFO] submission received tenantId={} tokenPrefix={}...", r.getTenantId(), maskToken(rawToken));
    }

    /** Best-effort confirmation to the client that their submission was received — never blocks the submit call. */
    private void sendSubmissionConfirmation(ClientInformationRequest r, ClientInformationSubmitRequest submission) {
        try {
            if (StringUtils.hasText(submission.getEmail())) {
                String lang = StringUtils.hasText(r.getPreferredLanguage()) ? r.getPreferredLanguage() : "fr";
                String subject = switch (lang) {
                    case "ar" -> "تم استلام معلوماتك";
                    case "en" -> "Your information was received";
                    default -> "Vos informations ont bien été reçues";
                };
                String body = switch (lang) {
                    case "ar" -> "شكراً " + submission.getFullName() + "، تم استلام معلوماتك بنجاح وهي الآن قيد المراجعة من طرف الوكالة.";
                    case "en" -> "Thank you " + submission.getFullName() + ", your information was received successfully and is now under review by the agency.";
                    default -> "Merci " + submission.getFullName() + ", vos informations ont bien été reçues et sont en cours de vérification par l'agence.";
                };
                emailService.sendCustomerSuccessEmail(submission.getEmail(), subject, body);
            }
        } catch (Exception e) {
            log.warn("[CLIENT_INFO] submission confirmation send failed tenantId={} reason={}", r.getTenantId(), e.getMessage());
        }
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
