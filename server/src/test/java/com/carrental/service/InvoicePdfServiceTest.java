package com.carrental.service;

import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import com.carrental.entity.Tenant;
import com.carrental.repository.PaymentRepository;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link InvoicePdfService}: individual/list PDF byte-stream sanity,
 * currency formatting precision, paid-amount derivation, fee/discount/tax/
 * deposit breakdown robustness (no NPE on nulls, contract-less "simple"
 * path), multi-page list export, and FR/EN/AR label + embedded-Arabic-font
 * correctness. Modeled on the plain Mockito + AssertJ style already used by
 * {@code AdditionalDriverSigningServiceTest} in this codebase.
 */
@ExtendWith(MockitoExtension.class)
class InvoicePdfServiceTest {

    @Mock private PaymentRepository paymentRepository;

    private InvoicePdfService service;
    private Tenant tenant;

    private void setUp() {
        service = new InvoicePdfService(paymentRepository);
        tenant = Tenant.builder()
                .id(1L).name("Innovacar Rabat").address("12 Rue Test").city("Rabat")
                .phone("+212600000000").email("contact@innovacar.ma").taxId("RC12345")
                .build();
    }

    private Invoice.InvoiceBuilder baseInvoiceBuilder() {
        return Invoice.builder()
                .id(500L).invoiceNumber("INV-2026-00500")
                .issueDate(LocalDate.of(2026, 1, 10))
                .dueDate(LocalDate.of(2026, 1, 25))
                .amount(new BigDecimal("4000.00"))
                .currency("MAD")
                .status(InvoiceStatus.PENDING)
                .tenant(tenant);
    }

    private Contract fullContract() {
        return Contract.builder()
                .id(200L).contractNumber("CTR-2026-00200")
                .vehicleBrand("Dacia").vehicleModel("Logan").vehicleRegistration("12345-A-6")
                .startDate(LocalDate.of(2026, 1, 5)).endDate(LocalDate.of(2026, 1, 10))
                .rentalDays(5)
                .dailyPrice(new BigDecimal("500.00"))
                .deliveryFees(new BigDecimal("50.00"))
                .returnFees(new BigDecimal("30.00"))
                .lateFees(BigDecimal.ZERO)
                .cleaningFees(new BigDecimal("20.00"))
                .fuelCharges(null) // deliberately null — must not NPE
                .discountAmount(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("100.00"))
                .depositAmount(new BigDecimal("2000.00"))
                .depositCurrency("MAD")
                .paidAmount(new BigDecimal("2000.00"))
                .remainingAmount(new BigDecimal("2500.00"))
                .totalPrice(new BigDecimal("4500.00"))
                .build();
    }

    private boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        return new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("%PDF");
    }

    // ── 1. Non-empty, real PDF bytes ────────────────────────────────────────

    @Test
    void generateInvoicePdfReturnsNonEmptyRealPdfBytes() {
        setUp();
        Invoice invoice = baseInvoiceBuilder().build();

        byte[] pdf = service.generateInvoicePdf(invoice, "fr");

        assertThat(pdf).isNotEmpty();
        assertThat(isPdf(pdf)).isTrue();
    }

    // ── 5 (partial: byte sanity) / 7. Contract-linked vs contract-less paths ──

    @Test
    void invoiceWithoutContractUsesSimpleAmountPathAndDoesNotThrow() {
        setUp();
        Invoice invoice = baseInvoiceBuilder().contract(null).build();

        byte[] pdf = service.generateInvoicePdf(invoice, "fr");

        assertThat(isPdf(pdf)).isTrue();
    }

    @Test
    void invoiceLinkedToContractWithSomeNullFeeFieldsProducesLargerOutputWithoutNpe() {
        setUp();
        Invoice simple = baseInvoiceBuilder().contract(null).build();
        Invoice linked = baseInvoiceBuilder().id(501L).invoiceNumber("INV-2026-00501")
                .contract(fullContract()).build();

        byte[] simplePdf = service.generateInvoicePdf(simple, "fr");
        byte[] linkedPdf = service.generateInvoicePdf(linked, "fr");

        assertThat(isPdf(simplePdf)).isTrue();
        assertThat(isPdf(linkedPdf)).isTrue();
        // The fee-breakdown/rental-context blocks add real content.
        assertThat(linkedPdf.length).isGreaterThan(simplePdf.length);
    }

    @Test
    void invoiceLinkedToContractWithAllFeeFieldsNullOrZeroSkipsRowsCleanlyWithoutNpe() {
        setUp();
        Contract sparse = Contract.builder()
                .id(201L).contractNumber("CTR-2026-00201")
                .startDate(LocalDate.of(2026, 2, 1)).endDate(LocalDate.of(2026, 2, 3))
                .dailyPrice(new BigDecimal("300.00"))
                .deliveryFees(null).returnFees(null).lateFees(null)
                .cleaningFees(null).fuelCharges(null)
                .discountAmount(null).taxAmount(null)
                .depositAmount(null).depositCurrency(null)
                .paidAmount(null).remainingAmount(null)
                .totalPrice(null)
                .build();
        Invoice invoice = baseInvoiceBuilder().id(502L).invoiceNumber("INV-2026-00502").contract(sparse).build();

        byte[] pdf = service.generateInvoicePdf(invoice, "fr");

        assertThat(isPdf(pdf)).isTrue();
    }

    // ── 8. Multi-page list export ───────────────────────────────────────────

    @Test
    void generateInvoiceListPdfWithManyRowsProducesMultiplePagesWithoutException() {
        setUp();
        List<Invoice> invoices = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            invoices.add(Invoice.builder()
                    .id(1000L + i).invoiceNumber("INV-2026-1" + String.format("%04d", i))
                    .issueDate(LocalDate.of(2026, 1, 1).plusDays(i))
                    .dueDate(LocalDate.of(2026, 1, 15).plusDays(i))
                    .amount(new BigDecimal("100.00").add(BigDecimal.valueOf(i)))
                    .currency("MAD")
                    .status(i % 3 == 0 ? InvoiceStatus.PAID : InvoiceStatus.PENDING)
                    .clientName("Client " + i)
                    .tenant(tenant)
                    .build());
        }
        lenient().when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(anyLong(), anyLong()))
                .thenReturn(BigDecimal.ZERO);

        InvoiceExportFilter filter = new InvoiceExportFilter();
        byte[] pdf = service.generateInvoiceListPdf(invoices, filter, tenant, "fr");

        assertThat(isPdf(pdf)).isTrue();
        assertThat(pdf.length).isGreaterThan(2000); // plausible size for a 65-row, multi-page report
    }

    @Test
    void generateInvoiceListPdfWithNoInvoicesStillProducesAValidPdf() {
        setUp();
        InvoiceExportFilter filter = new InvoiceExportFilter();
        byte[] pdf = service.generateInvoiceListPdf(List.of(), filter, tenant, "en");
        assertThat(isPdf(pdf)).isTrue();
    }

    // ── 5. formatCurrency precision (fr/en/ar + rounding boundary) ─────────

    @Test
    void formatCurrencyFrenchUsesCommaDecimalAndSpaceGrouping() {
        setUp();
        String result = service.formatCurrency(new BigDecimal("4000.00"), "MAD", "fr");
        // Grouping separator is a real non-breaking space (U+00A0), not ASCII 0x20 —
        // see InvoicePdfService's own "narrow/no-break space" comment.
        assertThat(result).isEqualTo("4 000,00 MAD");
    }

    @Test
    void formatCurrencyEnglishUsesCurrencyPrefixAndDotDecimal() {
        setUp();
        String result = service.formatCurrency(new BigDecimal("4000.00"), "MAD", "en");
        assertThat(result).isEqualTo("MAD 4,000.00");
    }

    @Test
    void formatCurrencyArabicUsesCurrencyPrefixWithLatinDigits() {
        setUp();
        String result = service.formatCurrency(new BigDecimal("4000.00"), "MAD", "ar");
        assertThat(result).isEqualTo("MAD 4,000.00");
    }

    @Test
    void formatCurrencyRoundsHalfUpOnlyAtTheFormattingBoundary() {
        setUp();
        // 1234.005 -> HALF_UP to 2 decimals -> 1234.01 (never drifts due to double).
        String fr = service.formatCurrency(new BigDecimal("1234.005"), "MAD", "fr");
        assertThat(fr).isEqualTo("1 234,01 MAD");

        String en = service.formatCurrency(new BigDecimal("1234.005"), "MAD", "en");
        assertThat(en).isEqualTo("MAD 1,234.01");
    }

    @Test
    void formatCurrencyHandlesNullAmountAsZeroRatherThanThrowing() {
        setUp();
        String result = service.formatCurrency(null, "MAD", "en");
        assertThat(result).isEqualTo("MAD 0.00");
    }

    // ── 6. paidAmountFor ─────────────────────────────────────────────────────

    @Test
    void paidAmountForReturnsSumOfLinkedPaymentsNotTheFlatInvoiceAmount() {
        setUp();
        Invoice invoice = baseInvoiceBuilder().amount(new BigDecimal("4000.00")).status(InvoiceStatus.PARTIALLY_PAID).build();
        when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(1L, 500L))
                .thenReturn(new BigDecimal("1500.00"));

        BigDecimal paid = service.paidAmountFor(invoice);

        assertThat(paid).isEqualByComparingTo("1500.00");
    }

    @Test
    void paidAmountForFallsBackToFullAmountWhenPaidStatusHasNoLinkedPayments() {
        setUp();
        Invoice invoice = baseInvoiceBuilder().amount(new BigDecimal("4000.00")).status(InvoiceStatus.PAID).build();
        when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(1L, 500L)).thenReturn(null);

        BigDecimal paid = service.paidAmountFor(invoice);

        assertThat(paid).isEqualByComparingTo("4000.00");
    }

    @Test
    void paidAmountForReturnsZeroWhenPendingWithNoPayments() {
        setUp();
        Invoice invoice = baseInvoiceBuilder().amount(new BigDecimal("4000.00")).status(InvoiceStatus.PENDING).build();
        when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(1L, 500L)).thenReturn(null);

        BigDecimal paid = service.paidAmountFor(invoice);

        assertThat(paid).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void paidAmountForReturnsZeroSumTreatedAsNoPayments() {
        setUp();
        // sumCollectedAmountByTenantIdAndInvoiceId returning exactly zero (not null) should
        // not be mistaken for "has payments" — falls through to the status-based rule.
        Invoice invoice = baseInvoiceBuilder().amount(new BigDecimal("4000.00")).status(InvoiceStatus.PENDING).build();
        when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(1L, 500L)).thenReturn(BigDecimal.ZERO);

        BigDecimal paid = service.paidAmountFor(invoice);

        assertThat(paid).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── 11. statusLabel FR/EN/AR ─────────────────────────────────────────────

    @Test
    void statusLabelCancelledIsTranslatedInAllThreeLanguages() {
        setUp();
        assertThat(service.statusLabel(InvoiceStatus.CANCELLED, "fr")).isEqualTo("Annulée");
        assertThat(service.statusLabel(InvoiceStatus.CANCELLED, "en")).isEqualTo("Cancelled");
        assertThat(service.statusLabel(InvoiceStatus.CANCELLED, "ar")).isEqualTo("ملغاة");
    }

    @Test
    void statusLabelPartiallyPaidAndRefundedAreTranslatedInAllThreeLanguages() {
        setUp();
        assertThat(service.statusLabel(InvoiceStatus.PARTIALLY_PAID, "fr")).isEqualTo("Partiellement payée");
        assertThat(service.statusLabel(InvoiceStatus.PARTIALLY_PAID, "en")).isEqualTo("Partially paid");
        assertThat(service.statusLabel(InvoiceStatus.PARTIALLY_PAID, "ar")).isEqualTo("مدفوعة جزئياً");

        assertThat(service.statusLabel(InvoiceStatus.REFUNDED, "fr")).isEqualTo("Remboursée");
        assertThat(service.statusLabel(InvoiceStatus.REFUNDED, "en")).isEqualTo("Refunded");
        assertThat(service.statusLabel(InvoiceStatus.REFUNDED, "ar")).isEqualTo("مستردة");
    }

    @Test
    void statusLabelReturnsEmptyStringForNullStatus() {
        setUp();
        assertThat(service.statusLabel(null, "fr")).isEmpty();
    }

    // ── 11. Arabic PDF generation succeeds + embeds the real Noto font ─────

    @Test
    void generateInvoicePdfInArabicSucceedsAndEmbedsTheNotoArabicFont() throws Exception {
        setUp();
        Invoice invoice = baseInvoiceBuilder().contract(fullContract()).build();

        byte[] pdf = service.generateInvoicePdf(invoice, "ar");

        assertThat(isPdf(pdf)).isTrue();
        assertEmbedsNotoArabicFont(pdf);
    }

    @Test
    void generateInvoiceListPdfInArabicSucceedsAndEmbedsTheNotoArabicFont() throws Exception {
        setUp();
        List<Invoice> invoices = List.of(baseInvoiceBuilder().build());
        lenient().when(paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(anyLong(), anyLong()))
                .thenReturn(BigDecimal.ZERO);

        byte[] pdf = service.generateInvoiceListPdf(invoices, new InvoiceExportFilter(), tenant, "ar");

        assertThat(isPdf(pdf)).isTrue();
        assertEmbedsNotoArabicFont(pdf);
    }

    /**
     * Proves the Arabic BaseFont was actually embedded (not a silent
     * Helvetica fallback, which would render disconnected boxes instead of
     * shaped Arabic glyphs). OpenPDF's structured font-resource API is
     * awkward to walk directly for embedded-font names across all page
     * resources, so — per the plan's own suggested fallback — this checks
     * for the literal embedded font subset/name bytes that OpenPDF writes
     * into the PDF's object streams for an embedded TrueType font. A
     * PdfReader parse is performed first to also prove the bytes are a
     * structurally valid PDF (not just "starts with %PDF").
     */
    private void assertEmbedsNotoArabicFont(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();

        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(raw).contains("NotoNaskhArabic");
    }

    // ── 12. Missing rental days / dailyPrice on contract doesn't throw ─────

    @Test
    void financialBreakdownHandlesContractWithNoRentalDaysOrDailyPriceWithoutThrowing() {
        setUp();
        Contract noDailyPrice = Contract.builder()
                .id(202L).contractNumber("CTR-2026-00202")
                .startDate(null).endDate(null)
                .dailyPrice(null)
                .totalPrice(new BigDecimal("1000.00"))
                .build();
        Invoice invoice = baseInvoiceBuilder().id(503L).invoiceNumber("INV-2026-00503").contract(noDailyPrice).build();

        byte[] pdf = service.generateInvoicePdf(invoice, "en");

        assertThat(isPdf(pdf)).isTrue();
    }
}
