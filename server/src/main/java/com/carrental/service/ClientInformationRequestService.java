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
import com.carrental.entity.AuditLog;
import com.carrental.entity.Tenant;
import com.carrental.exception.ClientInfoRequestException;
import com.carrental.repository.AuditLogRepository;
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
import java.util.Objects;
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
    private final AuditLogRepository auditLogRepository;

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
            applyClientEdits(client, submission);
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

    /**
     * Applies any field the client actually changed on the public form to the linked
     * existing client — spec section 14 (field-by-field review), previously deliberately
     * skipped in this MVP slice. Never a blind overwrite: the submission was already
     * merged with the client's own known values at submit time (see
     * {@link #mergeFromKnownClient}), so a field here differs from the client's current
     * value ONLY if the client genuinely edited it — a confirmed-as-is field submits
     * back the identical value and is a no-op. Runs at approval time (an admin's
     * explicit review/approve action), never immediately on submission, and writes one
     * audit entry per changed field so the change is traceable (spec section 4's
     * "preserve an audit trail" requirement) even though this project has no
     * field-level Client audit log of its own — reuses the existing generic
     * {@link AuditLog} table the same way {@link AuditLogService} already does for
     * other administrative actions.
     */
    private void applyClientEdits(Client client, ClientInformationSubmitRequest s) {
        List<String> changes = new ArrayList<>();
        changes.addAll(diffAndApply("fullName", client.getName(), s.getFullName(), client::setName));
        changes.addAll(diffAndApply("phone", client.getPhone(), s.getPhone(), client::setPhone));
        changes.addAll(diffAndApply("secondaryPhone", client.getSecondaryPhone(), s.getSecondaryPhone(), client::setSecondaryPhone));
        changes.addAll(diffAndApply("email", client.getEmail(), s.getEmail(), client::setEmail));
        changes.addAll(diffAndApply("gender", client.getGender(), s.getGender(), client::setGender));
        changes.addAll(diffAndApply("nationality", client.getNationality(), s.getNationality(), client::setNationality));
        changes.addAll(diffAndApply("address", client.getAddress(), s.getAddress(), client::setAddress));
        changes.addAll(diffAndApply("city", client.getCity(), s.getCity(), client::setCity));
        changes.addAll(diffAndApply("country", client.getCountry(), s.getCountry(), client::setCountry));
        changes.addAll(diffAndApply("drivingLicense", client.getDrivingLicense(), s.getDriverLicenseNumber(), client::setDrivingLicense));
        changes.addAll(diffAndApply("companyName", client.getCompanyName(), s.getCompanyName(), client::setCompanyName));
        if (!Objects.equals(client.getBirthDate(), s.getBirthDate()) && s.getBirthDate() != null) {
            changes.add("birthDate: " + client.getBirthDate() + " -> " + s.getBirthDate());
            client.setBirthDate(s.getBirthDate());
        }
        if (s.getDocumentType() == com.carrental.entity.DocumentType.CIN) {
            changes.addAll(diffAndApply("cin", client.getCin(), s.getDocumentNumber(), client::setCin));
        } else if (s.getDocumentType() == com.carrental.entity.DocumentType.PASSPORT) {
            changes.addAll(diffAndApply("passportNumber", client.getPassportNumber(), s.getDocumentNumber(), client::setPassportNumber));
        }

        if (changes.isEmpty()) return;
        clientRepository.save(client);
        writeClientAuditLog(client, changes);
    }

    /** Applies {@code newValue} onto the client via {@code setter} and returns a one-line change description, only if it actually differs and isn't blank. */
    private List<String> diffAndApply(String field, String oldValue, String newValue, java.util.function.Consumer<String> setter) {
        if (!StringUtils.hasText(newValue) || Objects.equals(oldValue, newValue)) return List.of();
        setter.accept(newValue);
        return List.of(field + ": '" + (oldValue == null ? "" : oldValue) + "' -> '" + newValue + "'");
    }

    private void writeClientAuditLog(Client client, List<String> changes) {
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            com.carrental.entity.User admin = authentication != null && authentication.getPrincipal() instanceof com.carrental.entity.User u
                    ? u : null;
            auditLogRepository.save(AuditLog.builder()
                    .action("UPDATE")
                    .entityType("CLIENT")
                    .entityId(client.getId())
                    .description("Client information updated via client self-fill approval: " + String.join("; ", changes))
                    .performedBy(admin != null ? admin.getEmail() : "SYSTEM")
                    .performedById(admin != null ? admin.getId() : null)
                    .tenantId(client.getTenant() != null ? client.getTenant().getId() : null)
                    .isSuccess(true)
                    .build());
        } catch (Exception e) {
            log.warn("[CLIENT_INFO] failed to write client audit log clientId={} reason={}", client.getId(), e.getMessage());
        }
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

    // ── Admin: completeness preview before sending ──────────────────────────

    /** Agency-side "what's already known" summary — see ClientCompletenessResponse javadoc. */
    @Transactional(readOnly = true)
    public com.carrental.dto.clientinfo.ClientCompletenessResponse getClientCompleteness(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Client client = clientRepository.findByIdAndTenantId(clientId, tenantId)
                .orElseThrow(() -> new ClientInfoRequestException("CLIENT_INFO_ACCESS_DENIED", HttpStatus.NOT_FOUND,
                        "Client not found."));
        List<String> missing = computeMissingFields(client);
        List<String> available = ALL_SUBMISSION_FIELDS.stream().filter(f -> !missing.contains(f)).toList();
        return com.carrental.dto.clientinfo.ClientCompletenessResponse.builder()
                .clientName(client.getName())
                .availableFields(available)
                .missingFields(missing)
                .build();
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

        PublicClientInformationView.PublicClientInformationViewBuilder view = PublicClientInformationView.builder()
                .temporaryName(r.getTemporaryName())
                .preferredLanguage(r.getPreferredLanguage())
                .agencyName(tenant != null ? tenant.getName() : null)
                .agencyLogo(tenant != null ? tenant.getLogoUrl() : null)
                .expiresAt(r.getExpiresAt())
                .alreadySubmitted(r.getStatus() == ClientInfoRequestStatus.SUBMITTED || r.getStatus() == ClientInfoRequestStatus.APPROVED);

        // Progressive disclosure: when this request is linked to an existing client,
        // show back what's already on file (confirmed, editable-on-request) and list
        // only the genuinely missing fields — never re-ask for what the agency already
        // has. The Client entity/id itself is never exposed — only its field values.
        Client client = r.getClientId() != null
                ? clientRepository.findByIdAndTenantId(r.getClientId(), r.getTenantId()).orElse(null)
                : null;
        if (client != null) {
            view.hasKnownClient(true)
                    .knownFullName(client.getName())
                    .knownPhone(client.getPhone())
                    .knownSecondaryPhone(client.getSecondaryPhone())
                    .knownEmail(client.getEmail())
                    .knownGender(client.getGender())
                    .knownBirthDate(client.getBirthDate())
                    .knownNationality(client.getNationality())
                    .knownAddress(client.getAddress())
                    .knownCity(client.getCity())
                    .knownCountry(client.getCountry())
                    .knownDriverLicenseNumber(client.getDrivingLicense())
                    .knownCompanyName(client.getCompanyName())
                    .missingFields(computeMissingFields(client));
            if (StringUtils.hasText(client.getCin())) {
                view.knownDocumentType("CIN").knownDocumentNumber(client.getCin());
            } else if (StringUtils.hasText(client.getPassportNumber())) {
                view.knownDocumentType("PASSPORT").knownDocumentNumber(client.getPassportNumber());
            }
        } else {
            view.missingFields(ALL_SUBMISSION_FIELDS);
        }

        return view.build();
    }

    private static final List<String> ALL_SUBMISSION_FIELDS = List.of(
            "fullName", "phone", "secondaryPhone", "email", "gender", "birthDate", "nationality",
            "address", "city", "country", "documentNumber", "driverLicenseNumber", "companyName");

    /** Field keys with no value on the given client — see PublicClientInformationView#missingFields javadoc. */
    private List<String> computeMissingFields(Client client) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(client.getName())) missing.add("fullName");
        if (!StringUtils.hasText(client.getPhone())) missing.add("phone");
        if (!StringUtils.hasText(client.getSecondaryPhone())) missing.add("secondaryPhone");
        if (!StringUtils.hasText(client.getEmail())) missing.add("email");
        if (!StringUtils.hasText(client.getGender())) missing.add("gender");
        if (client.getBirthDate() == null) missing.add("birthDate");
        if (!StringUtils.hasText(client.getNationality())) missing.add("nationality");
        if (!StringUtils.hasText(client.getAddress())) missing.add("address");
        if (!StringUtils.hasText(client.getCity())) missing.add("city");
        if (!StringUtils.hasText(client.getCountry())) missing.add("country");
        if (!StringUtils.hasText(client.getCin()) && !StringUtils.hasText(client.getPassportNumber())) missing.add("documentNumber");
        if (!StringUtils.hasText(client.getDrivingLicense())) missing.add("driverLicenseNumber");
        if (!StringUtils.hasText(client.getCompanyName())) missing.add("companyName");
        return missing;
    }

    @Transactional
    public void submit(String rawToken, ClientInformationSubmitRequest submission) {
        if (submission.getPrivacyAccepted() == null || !submission.getPrivacyAccepted()) {
            throw new ClientInfoRequestException("CLIENT_INFO_LINK_INVALID", HttpStatus.BAD_REQUEST,
                    "You must accept the privacy notice before submitting.");
        }
        ClientInformationRequest r = findValidByRawToken(rawToken, true);

        // Progressive disclosure: fill in anything the client left blank from the
        // already-known linked client (see getPublic) — the client is never forced to
        // retype information the agency already has, but every downstream consumer of
        // this submission (createClientFromSubmission, linkClientToContract, duplicate
        // matching) keeps reading a fully-populated ClientInformationSubmitRequest exactly
        // as before, so nothing else needed to change.
        Client linkedClient = r.getClientId() != null
                ? clientRepository.findByIdAndTenantId(r.getClientId(), r.getTenantId()).orElse(null)
                : null;
        if (linkedClient != null) {
            mergeFromKnownClient(submission, linkedClient);
        }
        validateRequiredFields(submission);

        r.setSubmissionPayload(serializeSubmission(submission));
        r.setSubmittedAt(LocalDateTime.now());
        r.setPrivacyAcceptedAt(LocalDateTime.now());
        r.setStatus(ClientInfoRequestStatus.SUBMITTED);
        requestRepository.save(r);

        notificationService.notifyClientInformationSubmitted(
                "Client information submitted",
                (StringUtils.hasText(r.getTemporaryName()) ? r.getTemporaryName() : "A client") + " submitted their information for review.",
                r.getId(), r.getTenantId());

        sendSubmissionConfirmation(r, submission);

        log.info("[CLIENT_INFO] submission received tenantId={} tokenPrefix={}...", r.getTenantId(), maskToken(rawToken));
    }

    /** Backfills every blank submission field from the linked client's existing value — see {@link #submit}. */
    private void mergeFromKnownClient(ClientInformationSubmitRequest s, Client client) {
        if (!StringUtils.hasText(s.getFullName())) s.setFullName(client.getName());
        if (!StringUtils.hasText(s.getPhone())) s.setPhone(client.getPhone());
        if (!StringUtils.hasText(s.getSecondaryPhone())) s.setSecondaryPhone(client.getSecondaryPhone());
        if (!StringUtils.hasText(s.getEmail())) s.setEmail(client.getEmail());
        if (!StringUtils.hasText(s.getGender())) s.setGender(client.getGender());
        if (s.getBirthDate() == null) s.setBirthDate(client.getBirthDate());
        if (!StringUtils.hasText(s.getNationality())) s.setNationality(client.getNationality());
        if (!StringUtils.hasText(s.getAddress())) s.setAddress(client.getAddress());
        if (!StringUtils.hasText(s.getCity())) s.setCity(client.getCity());
        if (!StringUtils.hasText(s.getCountry())) s.setCountry(client.getCountry());
        if (!StringUtils.hasText(s.getDriverLicenseNumber())) s.setDriverLicenseNumber(client.getDrivingLicense());
        if (!StringUtils.hasText(s.getCompanyName())) s.setCompanyName(client.getCompanyName());
        if (s.getDocumentType() == null || !StringUtils.hasText(s.getDocumentNumber())) {
            if (StringUtils.hasText(client.getCin())) {
                s.setDocumentType(com.carrental.entity.DocumentType.CIN);
                s.setDocumentNumber(client.getCin());
            } else if (StringUtils.hasText(client.getPassportNumber())) {
                s.setDocumentType(com.carrental.entity.DocumentType.PASSPORT);
                s.setDocumentNumber(client.getPassportNumber());
            }
        }
    }

    /** The fields a submission must have a value for — after merging with any known client — to be accepted. */
    private void validateRequiredFields(ClientInformationSubmitRequest s) {
        if (!StringUtils.hasText(s.getFullName()) || !StringUtils.hasText(s.getPhone())
                || s.getDocumentType() == null || !StringUtils.hasText(s.getDocumentNumber())) {
            throw new ClientInfoRequestException("CLIENT_INFO_REQUIRED_FIELDS_MISSING", HttpStatus.BAD_REQUEST,
                    "Please fill all required fields.");
        }
        if (StringUtils.hasText(s.getEmail()) && !EMAIL_PATTERN.matcher(s.getEmail().trim()).matches()) {
            throw new ClientInfoRequestException("INVALID_EMAIL", HttpStatus.BAD_REQUEST,
                    "This email address is not valid.");
        }
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
