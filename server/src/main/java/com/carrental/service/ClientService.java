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
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Long tenantId = TenantContext.getCurrentTenantId();
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
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        Client client = clientRepository.save(Client.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .secondaryPhone(request.getSecondaryPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .nationality(request.getNationality())
                .gender(request.getGender())
                .birthDate(request.getBirthDate())
                .cin(request.getCin())
                .passportNumber(request.getPassportNumber())
                .drivingLicense(request.getDrivingLicense())
                .drivingLicenseIssue(request.getDrivingLicenseIssue())
                .drivingLicenseExpiry(request.getDrivingLicenseExpiry())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .companyName(request.getCompanyName())
                .notes(request.getNotes())
                .tenant(tenant)
                .build());

        log.info("Created client [id={}] '{}' in tenant [{}]",
                client.getId(), client.getName(), tenantId);

        return ClientResponse.from(client);
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

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a client from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public void deleteClient(Long id) {
        Client client = fetchClientInTenant(id);
        clientRepository.delete(client);
        log.info("Deleted client [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
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
        Long tenantId = TenantContext.getCurrentTenantId();
        return clientRepository.findByIdAndTenantId(clientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId));
    }
}
