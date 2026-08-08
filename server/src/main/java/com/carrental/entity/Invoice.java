package com.carrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an invoice within a tenant's business.
 */
@Entity
@Table(
    name = "invoices",
    indexes = {
        @Index(name = "idx_invoice_tenant", columnList = "tenant_id"),
        @Index(name = "idx_invoice_tenant_status", columnList = "tenant_id, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    // ── PDF / contract-link additions ────────────────────────────────────────

    /** Optional link to the rental contract this invoice bills — null for
     *  manual/ad-hoc invoices (the only kind that existed before this link
     *  was added). When set, the individual PDF renders the full rental
     *  context + fee breakdown sourced from the contract itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "currency", length = 10)
    private String currency;

    /** CONTRACT_LINKED (derived from a Contract, kept in sync) vs MANUAL (freely edited). */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private InvoiceSourceType sourceType = InvoiceSourceType.MANUAL;

    /** Sum of {@link #lines}' totals before discount/tax — kept for display; {@link #amount} remains the total. */
    @Column(name = "subtotal_amount", precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    /** Itemized breakdown — see InvoiceLine javadoc. Always in {@code sortOrder}. */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<InvoiceLine> lines = new ArrayList<>();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Cancellation audit ───────────────────────────────────────────────────
    // Cancellation never deletes financial history — these columns record the
    // reason for the cancellation itself (see InvoiceService#cancelInvoice).

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "pdf_generated_at")
    private LocalDateTime pdfGeneratedAt;

    @Column(name = "pdf_language", length = 5)
    private String pdfLanguage;

    @Column(name = "pdf_outdated", nullable = false)
    private boolean pdfOutdated;

    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @Column(name = "emailed_to", length = 150)
    private String emailedTo;

    // ── Payment reminders (PaymentReminderJob) — each fires at most once per
    // invoice, tracked here so a slow-paying client is never spammed daily. ──

    @Column(name = "due_soon_reminder_sent_at")
    private LocalDateTime dueSoonReminderSentAt;

    @Column(name = "overdue_reminder_sent_at")
    private LocalDateTime overdueReminderSentAt;

    // ── Multi-tenancy link ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
