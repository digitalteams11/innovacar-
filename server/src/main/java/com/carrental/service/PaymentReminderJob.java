package com.carrental.service;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Notification;
import com.carrental.repository.InvoiceRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Real-database-state payment reminders — never fabricated. Each reminder
 * fires at most once per invoice (tracked via
 * {@code dueSoonReminderSentAt}/{@code overdueReminderSentAt}), same
 * once-only pattern as {@link ClientInfoRequestReminderJob}. Cross-tenant
 * sweep with {@link TenantContext} set per row, also matching that job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReminderJob {

    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    private static final List<InvoiceStatus> OWING_STATUSES = List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);

    /** Once a day: refresh any invoice whose due date has silently passed into OVERDUE —
     *  status only ever changes on a payment/contract-sync event otherwise, so an untouched
     *  invoice would never transition on its own just because time passed. */
    @Scheduled(cron = "${app.payment-reminder.status-sweep.cron:0 0 8 * * *}")
    @Transactional
    public void refreshOverdueStatuses() {
        List<Invoice> candidates = invoiceRepository.findAllByStatusInAndDueDateBefore(OWING_STATUSES, LocalDate.now());
        int flipped = 0;
        for (Invoice invoice : candidates) {
            try {
                TenantContext.setCurrentTenantId(invoice.getTenant().getId());
                paymentService.updateInvoiceStatus(invoice);
                if (invoice.getStatus() == InvoiceStatus.OVERDUE) flipped++;
            } catch (Exception e) {
                log.warn("[PAYMENT_REMINDER] status refresh failed invoiceId={} reason={}", invoice.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (flipped > 0) {
            log.info("[PAYMENT_REMINDER] status sweep flipped {} invoice(s) to OVERDUE", flipped);
        }
    }

    /** Once a day: "Paiement à venir" — invoices still owing, due within 3 days. */
    @Scheduled(cron = "${app.payment-reminder.due-soon.cron:0 15 8 * * *}")
    @Transactional
    public void remindDueSoon() {
        LocalDate today = LocalDate.now();
        List<Invoice> candidates = invoiceRepository.findAllByStatusInAndDueDateBetweenAndDueSoonReminderSentAtIsNull(
                OWING_STATUSES, today, today.plusDays(3));
        for (Invoice invoice : candidates) {
            try {
                TenantContext.setCurrentTenantId(invoice.getTenant().getId());
                BigDecimal remaining = remainingFor(invoice);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                notificationService.createNotification(
                        "Paiement à venir",
                        remaining.stripTrailingZeros().toPlainString() + " MAD restant pour le contrat " + contractLabel(invoice) + ".",
                        Notification.NotificationType.CLIENT_BALANCE_DUE,
                        invoice.getContract() != null ? invoice.getContract().getId() : null,
                        invoice.getTenant().getId());
                invoice.setDueSoonReminderSentAt(LocalDateTime.now());
                invoiceRepository.save(invoice);
            } catch (Exception e) {
                log.warn("[PAYMENT_REMINDER] due-soon reminder failed invoiceId={} reason={}", invoice.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Once a day: "Facture en retard de paiement" — invoices already OVERDUE, never reminded. */
    @Scheduled(cron = "${app.payment-reminder.overdue.cron:0 30 8 * * *}")
    @Transactional
    public void remindOverdue() {
        List<Invoice> candidates = invoiceRepository.findAllByStatusAndOverdueReminderSentAtIsNull(InvoiceStatus.OVERDUE);
        for (Invoice invoice : candidates) {
            try {
                TenantContext.setCurrentTenantId(invoice.getTenant().getId());
                notificationService.createNotification(
                        "Facture en retard",
                        "Facture en retard de paiement pour le contrat " + contractLabel(invoice) + ".",
                        Notification.NotificationType.INVOICE_OVERDUE,
                        invoice.getContract() != null ? invoice.getContract().getId() : null,
                        invoice.getTenant().getId());
                invoice.setOverdueReminderSentAt(LocalDateTime.now());
                invoiceRepository.save(invoice);
            } catch (Exception e) {
                log.warn("[PAYMENT_REMINDER] overdue reminder failed invoiceId={} reason={}", invoice.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private BigDecimal remainingFor(Invoice invoice) {
        BigDecimal collected = paymentService.collectedAmountFor(invoice);
        BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        return amount.subtract(collected).max(BigDecimal.ZERO);
    }

    private String contractLabel(Invoice invoice) {
        return invoice.getContract() != null ? invoice.getContract().getContractNumber() : invoice.getInvoiceNumber();
    }
}
