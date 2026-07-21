package com.carrental.service;

import com.carrental.dto.client.CreateClientRequest;
import com.carrental.dto.client.UpdateClientRequest;
import com.carrental.dto.client.ClientResponse;
import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.dto.contract.ContractResponse;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.exception.ClientDuplicateException;
import com.carrental.exception.ClientValidationException;
import com.carrental.exception.DuplicateClientException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for clients that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read clients. Only ADMIN users may
 * create, update, or delete them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all clients for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        Long tenantId = requireTenantId();
        log.debug("Listing clients for tenant [{}]", tenantId);

        return clientRepository.findAllByTenantId(tenantId)
                .stream()
                .map(ClientResponse::from)
                .toList();
    }

    /**
     * Fetches a single client scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the client does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public ClientResponse getClientById(Long id) {
        return ClientResponse.from(fetchClientInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new client to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        Long tenantId = requireTenantId();
        validateRequired(request);
        checkDuplicates(tenantId, request);

        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ClientValidationException(
                        "Agency account is not linked to a tenant.", "TENANT_REQUIRED"));

        Client client;
        try {
            client = clientRepository.save(Client.builder()
                    .name(required(request.getFullName()))
                    .email(optional(request.getEmail()))
                    .phone(required(request.getPhone()))
                    .secondaryPhone(optional(request.getSecondaryPhone()))
                    .address(required(request.getAddress()))
                    .city(required(request.getCity()))
                    .country(required(request.getCountry()))
                    .postalCode(optional(request.getPostalCode()))
                    .nationality(required(request.getNationality()))
                    .gender(normalizeGender(request.getGender()))
                    .birthDate(request.getBirthDate())
                    .cin(optional(request.getCin()))
                    .passportNumber(optional(request.getPassportNumber()))
                    .drivingLicense(optional(request.getDrivingLicense()))
                    .drivingLicenseIssue(request.getDrivingLicenseIssue())
                    .drivingLicenseExpiry(request.getDrivingLicenseExpiry())
                    .emergencyContactName(optional(request.getEmergencyContactName()))
                    .emergencyContactPhone(optional(request.getEmergencyContactPhone()))
                    .companyName(optional(request.getCompanyName()))
                    .notes(optional(request.getNotes()))
                    .tenant(tenant)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Race-condition fallback only: checkDuplicates() above already
            // rejects any match among active clients before we get here, so
            // this only fires if two requests insert the same value at once.
            // Map the DB-level partial unique index that fired back to a
            // field-specific message instead of a blanket "already exists".
            String field = fieldForConstraint(ex);
            if (field != null) {
                throw new DuplicateClientException(duplicateMessage(field), field);
            }
            throw new ClientDuplicateException("Client already exists", "CLIENT_ALREADY_EXISTS", null);
        }

        log.info("Created client [id={}] '{}' in tenant [{}]",
                client.getId(), client.getName(), tenantId);

        return ClientResponse.from(client);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkExistingClient(String email, String phone, String cin, String passportNumber) {
        Long tenantId = requireTenantId();
        Map<String, Object> result = new HashMap<>();
        result.put("exists", false);
        String normalizedEmail = optional(email);
        if (normalizedEmail != null) {
            Optional<Client> duplicate = clientRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail);
            if (duplicate.isPresent()) return duplicateCheck("email", duplicate.get());
        }
        String normalizedPhone = optional(phone);
        if (normalizedPhone != null) {
            Optional<Client> duplicate = clientRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, normalizedPhone);
            if (duplicate.isPresent()) return duplicateCheck("phone", duplicate.get());
        }
        String normalizedCin = optional(cin);
        if (normalizedCin != null) {
            Optional<Client> duplicate = clientRepository.findFirstByTenantIdAndCinIgnoreCase(tenantId, normalizedCin);
            if (duplicate.isPresent()) return duplicateCheck("cin", duplicate.get());
        }
        String normalizedPassport = optional(passportNumber);
        if (normalizedPassport != null) {
            Optional<Client> duplicate = clientRepository.findFirstByTenantIdAndPassportNumberIgnoreCase(tenantId, normalizedPassport);
            if (duplicate.isPresent()) return duplicateCheck("passportNumber", duplicate.get());
        }
        return result;
    }

    private Map<String, Object> duplicateCheck(String field, Client client) {
        Map<String, Object> result = new HashMap<>();
        result.put("exists", true);
        result.put("field", field);
        result.put("clientId", client.getId());
        result.put("message", duplicateMessage(field));
        return result;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public ClientResponse updateClient(Long id, UpdateClientRequest request) {
        Client client = fetchClientInTenant(id);

        if (StringUtils.hasText(request.getName())) {
            client.setName(request.getName());
        }
        if (request.getEmail() != null) {
            client.setEmail(request.getEmail().isEmpty() ? null : request.getEmail());
        }
        if (request.getPhone() != null) {
            client.setPhone(request.getPhone().isEmpty() ? null : request.getPhone());
        }
        if (request.getSecondaryPhone() != null) {
            client.setSecondaryPhone(request.getSecondaryPhone().isEmpty() ? null : request.getSecondaryPhone());
        }
        if (request.getAddress() != null) {
            client.setAddress(request.getAddress().isEmpty() ? null : request.getAddress());
        }
        if (request.getCity() != null) {
            client.setCity(request.getCity().isEmpty() ? null : request.getCity());
        }
        if (request.getCountry() != null) {
            client.setCountry(request.getCountry().isEmpty() ? null : request.getCountry());
        }
        if (request.getPostalCode() != null) {
            client.setPostalCode(request.getPostalCode().isEmpty() ? null : request.getPostalCode());
        }
        if (request.getNationality() != null) {
            client.setNationality(request.getNationality().isEmpty() ? null : request.getNationality());
        }
        if (request.getGender() != null) {
            client.setGender(request.getGender().isEmpty() ? null : request.getGender());
        }
        if (request.getBirthDate() != null) {
            client.setBirthDate(request.getBirthDate());
        }
        if (request.getCin() != null) {
            client.setCin(request.getCin().isEmpty() ? null : request.getCin());
        }
        if (request.getPassportNumber() != null) {
            client.setPassportNumber(request.getPassportNumber().isEmpty() ? null : request.getPassportNumber());
        }
        if (request.getDrivingLicense() != null) {
            client.setDrivingLicense(request.getDrivingLicense().isEmpty() ? null : request.getDrivingLicense());
        }
        if (request.getDrivingLicenseIssue() != null) {
            client.setDrivingLicenseIssue(request.getDrivingLicenseIssue());
        }
        if (request.getDrivingLicenseExpiry() != null) {
            client.setDrivingLicenseExpiry(request.getDrivingLicenseExpiry());
        }
        if (request.getEmergencyContactName() != null) {
            client.setEmergencyContactName(request.getEmergencyContactName().isEmpty() ? null : request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            client.setEmergencyContactPhone(request.getEmergencyContactPhone().isEmpty() ? null : request.getEmergencyContactPhone());
        }
        if (request.getCompanyName() != null) {
            client.setCompanyName(request.getCompanyName().isEmpty() ? null : request.getCompanyName());
        }
        if (request.getNotes() != null) {
            client.setNotes(request.getNotes().isEmpty() ? null : request.getNotes());
        }

        Client saved = clientRepository.save(client);
        log.info("Updated client [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return ClientResponse.from(saved);
    }

    /**
     * Dedicated "add/fix missing client email" action — backs the recovery
     * flow surfaced from Contract Details when a client has no email on
     * file. Deliberately does not enforce tenant-wide email uniqueness:
     * unlike {@link #createClient}'s duplicate-customer guard (which exists
     * to stop the same person being registered as two client records), this
     * only corrects a contact detail on an already-identified client, and
     * there is no DB-level uniqueness constraint on {@code clients.email} —
     * two clients (e.g. a shared family/company inbox) may legitimately use
     * the same address.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public ClientResponse updateClientEmail(Long id, String rawEmail) {
        Client client = fetchClientInTenant(id);
        String normalized = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        client.setEmail(normalized);
        Client saved = clientRepository.save(client);
        // Never log the raw email — this is intentionally logged as a bare
        // fact ("this client's email was changed"), not what it changed to.
        log.info("Updated client [id={}] email in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return ClientResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a client from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public void deleteClient(Long id) {
        Client client = fetchClientInTenant(id);
        client.setDeleted(true);
        client.setDeletedAt(LocalDateTime.now());
        client.setDeletedBy(currentUserEmail());
        clientRepository.save(client);
        log.info("Soft-deleted client [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
    }

    private String currentUserEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    /** Archived clients only, for the admin "view deleted" screen. */
    @Transactional(readOnly = true)
    public List<ClientResponse> getDeletedClients() {
        Long tenantId = requireTenantId();
        return clientRepository.findAllDeletedByTenantId(tenantId)
                .stream()
                .map(ClientResponse::from)
                .toList();
    }

    /**
     * Restores a soft-deleted client. Rejected with 409 if an active client
     * in the same tenant already holds the same phone/CIN/passport/email —
     * restoring must not silently create a new duplicate-among-active-rows.
     */
    @Transactional
    public ClientResponse restoreClient(Long id) {
        Long tenantId = requireTenantId();
        Client client = clientRepository.findDeletedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deleted client not found with id: " + id));

        if (StringUtils.hasText(client.getPhone())) {
            var conflict = clientRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, client.getPhone());
            if (conflict.isPresent()) throw duplicate("phone", conflict.get());
        }
        if (StringUtils.hasText(client.getEmail())) {
            var conflict = clientRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, client.getEmail());
            if (conflict.isPresent()) throw duplicate("email", conflict.get());
        }
        if (StringUtils.hasText(client.getCin())) {
            var conflict = clientRepository.findFirstByTenantIdAndCinIgnoreCase(tenantId, client.getCin());
            if (conflict.isPresent()) throw duplicate("cin", conflict.get());
        }
        if (StringUtils.hasText(client.getPassportNumber())) {
            var conflict = clientRepository.findFirstByTenantIdAndPassportNumberIgnoreCase(tenantId, client.getPassportNumber());
            if (conflict.isPresent()) throw duplicate("passportNumber", conflict.get());
        }

        client.setDeleted(false);
        client.setDeletedAt(null);
        client.setDeletedBy(null);
        Client restored = clientRepository.save(client);
        log.info("Restored client [id={}] in tenant [{}]", id, tenantId);
        return ClientResponse.from(restored);
    }

    // ── CLIENT BALANCE ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getClientBalance(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Client client = fetchClientInTenant(clientId);

        var reservations = reservationRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var contracts = contractRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var invoices = invoiceRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var payments = paymentRepository.findAllByTenantIdAndClientIdOrderByPaymentDateDesc(tenantId, clientId);

        int totalRentals = reservations.size();
        int totalContracts = contracts.size();
        int activeContracts = (int) contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .count();

        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PARTIALLY_PAID)
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInvoiced = invoices.stream()
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingBalance = totalInvoiced.subtract(totalPaid).max(BigDecimal.ZERO);

        long openInvoices = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID)
                .count();

        // Payment history
        List<PaymentResponse> paymentHistory = payments.stream()
                .map(PaymentResponse::from)
                .toList();

        String paymentStatus;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) <= 0 && totalInvoiced.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = "PAID";
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = "PARTIALLY_PAID";
        } else {
            paymentStatus = "UNPAID";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("clientId", clientId);
        result.put("clientName", client.getName());
        result.put("totalRentals", totalRentals);
        result.put("totalContracts", totalContracts);
        result.put("totalPaid", totalPaid);
        result.put("outstandingBalance", outstandingBalance);
        result.put("openInvoices", openInvoices);
        result.put("activeContracts", activeContracts);
        result.put("paymentStatus", paymentStatus);
        result.put("paymentHistory", paymentHistory);

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClientProfile(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Client client = fetchClientInTenant(clientId);
        var reservations = reservationRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var contracts = contractRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var invoices = invoiceRepository.findAllByTenantIdAndClientId(tenantId, clientId);
        var payments = paymentRepository.findAllByTenantIdAndClientIdOrderByPaymentDateDesc(tenantId, clientId);

        Map<String, Object> profile = new HashMap<>();
        profile.put("client", ClientResponse.from(client));
        profile.put("balance", getClientBalance(clientId));
        profile.put("activeReservations", reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING
                        || r.getStatus() == ReservationStatus.CONFIRMED
                        || r.getStatus() == ReservationStatus.ACTIVE)
                .map(ReservationResponse::from).toList());
        profile.put("reservationHistory", reservations.stream().map(ReservationResponse::from).toList());
        profile.put("activeContracts", contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.SIGNED
                        || c.getStatus() == ContractStatus.ACTIVE
                        || c.getStatus() == ContractStatus.PAID)
                .map(ContractResponse::from).toList());
        profile.put("contractHistory", contracts.stream().map(ContractResponse::from).toList());
        profile.put("paymentHistory", payments.stream().map(PaymentResponse::from).toList());
        profile.put("invoices", invoices.stream().map(InvoiceResponse::from).toList());
        profile.put("documents", contracts.stream()
                .flatMap(contract -> contract.getDocuments().stream())
                .map(document -> Map.of(
                        "id", document.getId(),
                        "contractId", document.getContract().getId(),
                        "type", document.getDocumentType() != null ? document.getDocumentType() : "",
                        "name", document.getDocumentName() != null ? document.getDocumentName() : "",
                        "url", document.getDocumentUrl() != null ? document.getDocumentUrl() : ""))
                .toList());
        profile.put("signatureHistory", contracts.stream()
                .filter(contract -> contract.getOwnerSignedAt() != null || contract.getClientSignedAt() != null)
                .map(contract -> {
                    Map<String, Object> signature = new HashMap<>();
                    signature.put("contractId", contract.getId());
                    signature.put("contractNumber", contract.getContractNumber());
                    signature.put("agencySignedAt", contract.getOwnerSignedAt());
                    signature.put("clientSignedAt", contract.getClientSignedAt());
                    signature.put("status", contract.getStatus().name());
                    return signature;
                }).toList());
        return profile;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped client lookup. Returns 404 for both missing and
     * cross-tenant clients so tenant B cannot discover tenant A's IDs.
     */
    public Client fetchClientInTenant(Long clientId) {
        Long tenantId = requireTenantId();
        return clientRepository.findByIdAndTenantId(clientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId));
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new ClientValidationException(
                    "Current user is not linked to an agency.", "TENANT_REQUIRED");
        }
        return tenantId;
    }

    private void validateRequired(CreateClientRequest request) {
        if (!StringUtils.hasText(request.getFullName())) {
            throw new ClientValidationException("Full name is required");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new ClientValidationException("Phone is required");
        }
        if (!StringUtils.hasText(request.getAddress())) {
            throw new ClientValidationException("Address is required");
        }
        if (!StringUtils.hasText(request.getCity())) {
            throw new ClientValidationException("City is required");
        }
        if (!StringUtils.hasText(request.getCountry())) {
            throw new ClientValidationException("Country is required");
        }
        if (!StringUtils.hasText(request.getNationality())) {
            throw new ClientValidationException("Nationality is required");
        }
        if (!StringUtils.hasText(request.getCin()) && !StringUtils.hasText(request.getPassportNumber())) {
            throw new ClientValidationException("CIN or passport number is required");
        }
    }

    private void checkDuplicates(Long tenantId, CreateClientRequest request) {
        String phone = required(request.getPhone());
        var byPhone = clientRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, phone);
        if (byPhone.isPresent()) {
            throw duplicate("phone", byPhone.get());
        }
        String email = optional(request.getEmail());
        var byEmail = email == null ? Optional.<Client>empty()
                : clientRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, email);
        if (byEmail.isPresent()) {
            throw duplicate("email", byEmail.get());
        }
        String cin = optional(request.getCin());
        var byCin = cin == null ? Optional.<Client>empty()
                : clientRepository.findFirstByTenantIdAndCinIgnoreCase(tenantId, cin);
        if (byCin.isPresent()) {
            throw duplicate("cin", byCin.get());
        }
        String passport = optional(request.getPassportNumber());
        var byPassport = passport == null ? Optional.<Client>empty()
                : clientRepository.findFirstByTenantIdAndPassportNumberIgnoreCase(tenantId, passport);
        if (byPassport.isPresent()) {
            throw duplicate("passportNumber", byPassport.get());
        }
    }

    private DuplicateClientException duplicate(String field, Client existing) {
        return new DuplicateClientException(duplicateMessage(field), field, existing.getId());
    }

    /** Maps a DB-level unique-index violation back to the offending field name. */
    private String fieldForConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        String constraintName = null;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                constraintName = cve.getConstraintName();
                break;
            }
            cause = cause.getCause();
        }
        if (constraintName == null) return null;
        String lower = constraintName.toLowerCase();
        if (lower.contains("email")) return "email";
        if (lower.contains("phone")) return "phone";
        if (lower.contains("passport")) return "passportNumber";
        if (lower.contains("cin")) return "cin";
        return null;
    }

    private String duplicateMessage(String field) {
        return switch (field) {
            case "email" -> "A client with this email already exists in this agency.";
            case "phone" -> "A client with this phone number already exists in this agency.";
            case "cin" -> "A client with this CIN already exists in this agency.";
            case "passportNumber" -> "A client with this passport number already exists in this agency.";
            default -> "A client with this information already exists in this agency.";
        };
    }

    private String normalizeGender(String value) {
        String gender = optional(value);
        return gender == null ? null : gender.toUpperCase();
    }

    private String required(String value) {
        return value == null ? null : value.trim();
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
