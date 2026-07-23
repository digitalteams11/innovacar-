package com.carrental.service.export;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles list-report rows (clients, contracts, payments) for the PDF
 * exporters. Each method is tenant-scoped via TenantContext — never trusts
 * a tenantId supplied by the caller — and caps row counts the same way
 * VehicleExportService does, since OpenPDF table rendering is fully
 * memory-resident regardless of streaming.
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    public static final int MAX_ROWS = 5000;

    public static class ExportNoDataException extends RuntimeException {
        public ExportNoDataException(String message) { super(message); }
    }

    public static class ExportTooLargeException extends RuntimeException {
        public ExportTooLargeException(String message) { super(message); }
    }

    private final ClientRepository clientRepository;
    private final ClientIdentityDocumentRepository identityDocumentRepository;
    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Clients ──────────────────────────────────────────────────────────

    public static final String[] CLIENT_HEADERS = {
            "Name", "Phone", "Email", "Document Type", "Document Number", "City", "Rentals"
    };

    @Transactional(readOnly = true)
    public List<String[]> clientRows(String search) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Client> clients = clientRepository.findAllByTenantId(tenantId);
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase(Locale.ROOT);
            clients = clients.stream()
                    .filter(c -> containsIgnoreCase(c.getName(), q) || containsIgnoreCase(c.getPhone(), q) || containsIgnoreCase(c.getEmail(), q))
                    .toList();
        }
        if (clients.isEmpty()) throw new ExportNoDataException("No clients match the selected filters.");
        if (clients.size() > MAX_ROWS) throw new ExportTooLargeException("Too many clients for a single PDF export. Narrow your filters.");

        Map<Long, ClientIdentityDocument> docsByClient = identityDocumentRepository.findAllByTenantIdAndIsPrimaryTrue(tenantId)
                .stream().collect(Collectors.toMap(ClientIdentityDocument::getClientId, d -> d, (a, b) -> a));
        Map<Long, Long> rentalCounts = contractRepository.findAllByTenantId(tenantId).stream()
                .filter(c -> c.getClient() != null)
                .collect(Collectors.groupingBy(c -> c.getClient().getId(), Collectors.counting()));

        return clients.stream().map(c -> {
            ClientIdentityDocument doc = docsByClient.get(c.getId());
            String docType = doc != null ? String.valueOf(doc.getDocumentType()) : (c.getCin() != null ? "CIN" : c.getPassportNumber() != null ? "PASSPORT" : "");
            String docNumber = doc != null ? doc.getDocumentNumber() : (c.getCin() != null ? c.getCin() : c.getPassportNumber());
            return new String[]{
                    nullToEmpty(c.getName()),
                    nullToEmpty(c.getPhone()),
                    nullToEmpty(c.getEmail()),
                    docType,
                    ClientService.maskDocumentNumber(docNumber),
                    nullToEmpty(c.getCity()),
                    String.valueOf(rentalCounts.getOrDefault(c.getId(), 0L)),
            };
        }).toList();
    }

    // ── Contracts ────────────────────────────────────────────────────────

    public static final String[] CONTRACT_HEADERS = {
            "Contract #", "Client", "Vehicle", "Start", "End", "Status", "Total", "Signature"
    };

    @Transactional(readOnly = true)
    public List<String[]> contractRows(ContractStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Contract> contracts = contractRepository.findAllByTenantId(tenantId);
        if (status != null) {
            contracts = contracts.stream().filter(c -> c.getStatus() == status).toList();
        }
        if (contracts.isEmpty()) throw new ExportNoDataException("No contracts match the selected filters.");
        if (contracts.size() > MAX_ROWS) throw new ExportTooLargeException("Too many contracts for a single PDF export. Narrow your filters.");

        return contracts.stream().map(c -> new String[]{
                nullToEmpty(c.getContractNumber()),
                c.getClient() != null ? nullToEmpty(c.getClient().getName()) : "",
                c.getVehicle() != null ? nullToEmpty(c.getVehicle().getMarque()) : "",
                c.getStartDate() != null ? c.getStartDate().format(DATE_FMT) : "",
                c.getEndDate() != null ? c.getEndDate().format(DATE_FMT) : "",
                c.getStatus() != null ? c.getStatus().name() : "",
                c.getTotalPrice() != null ? c.getTotalPrice().toPlainString() + " MAD" : "",
                c.getClientSignedAt() != null ? "Signed" : "Pending",
        }).toList();
    }

    // ── Payments ─────────────────────────────────────────────────────────

    public static final String[] PAYMENT_HEADERS = {
            "Reference", "Client", "Contract/Reservation", "Amount", "Method", "Status", "Date"
    };

    @Transactional(readOnly = true)
    public List<String[]> paymentRows(PaymentStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Payment> payments = paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(tenantId);
        if (status != null) {
            payments = payments.stream().filter(p -> p.getStatus() == status).toList();
        }
        if (payments.isEmpty()) throw new ExportNoDataException("No payments match the selected filters.");
        if (payments.size() > MAX_ROWS) throw new ExportTooLargeException("Too many payments for a single PDF export. Narrow your filters.");

        return payments.stream().map(p -> {
            String reference = p.getContract() != null ? p.getContract().getContractNumber()
                    : p.getReservation() != null ? "RES-" + p.getReservation().getId() : "";
            return new String[]{
                    nullToEmpty(p.getPaymentNumber()),
                    p.getClient() != null ? nullToEmpty(p.getClient().getName()) : "",
                    reference,
                    p.getAmount() != null ? p.getAmount().toPlainString() + " MAD" : BigDecimal.ZERO + " MAD",
                    p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "",
                    p.getStatus() != null ? p.getStatus().name() : "",
                    p.getPaymentDate() != null ? p.getPaymentDate().format(DATETIME_FMT) : "",
            };
        }).toList();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
