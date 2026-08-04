package com.carrental.controller;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.service.InvoicePdfService;
import com.carrental.service.InvoiceService;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SmtpMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link InvoiceController}'s authenticated PDF/export/email
 * endpoints: content-type/disposition headers (attachment vs inline),
 * tenant isolation (cross-tenant id -> 404, never a malformed PDF),
 * empty-filtered-export -> 422 structured body without ever calling the PDF
 * generator, and invalid-id -> precise 404. Modeled on
 * {@code AdditionalDriverControllerTest}'s plain-constructor + Mockito
 * style (no MockMvc/Spring context — {@code @PreAuthorize} is a proxy-level
 * concern covered separately by {@code InvoicePermissionEnforcementTest}).
 */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock private InvoiceService invoiceService;
    @Mock private InvoicePdfService invoicePdfService;
    @Mock private PlatformEmailService platformEmailService;

    private InvoiceController controller;
    private Tenant tenant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        controller = new InvoiceController(invoiceService, invoicePdfService, platformEmailService);
        tenant = Tenant.builder().id(1L).name("Innovacar Rabat").build();
        invoice = Invoice.builder()
                .id(500L).invoiceNumber("INV-2026-00500")
                .issueDate(LocalDate.of(2026, 1, 10)).dueDate(LocalDate.of(2026, 1, 25))
                .amount(new BigDecimal("4000.00")).currency("MAD")
                .status(InvoiceStatus.PENDING).tenant(tenant)
                .build();
    }

    // ── 2. Content-Type / Content-Disposition (attachment vs inline) ───────

    @Test
    void downloadingInvoicePdfInAttachmentModeSetsAttachmentDispositionWithDerivedFilename() {
        when(invoiceService.getInvoiceEntityById(500L)).thenReturn(invoice);
        when(invoicePdfService.generateInvoicePdf(invoice, "fr")).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = controller.downloadInvoicePdf(500L, "attachment", "fr");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).isEqualTo("attachment; filename=\"facture-INV-2026-00500.pdf\"");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        verify(invoiceService).markPdfGenerated(500L, "fr");
    }

    @Test
    void downloadingInvoicePdfInInlineModeSetsInlineDisposition() {
        when(invoiceService.getInvoiceEntityById(500L)).thenReturn(invoice);
        when(invoicePdfService.generateInvoicePdf(invoice, "en")).thenReturn(new byte[]{9});

        ResponseEntity<byte[]> response = controller.downloadInvoicePdf(500L, "inline", "en");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).isEqualTo("inline; filename=\"facture-INV-2026-00500.pdf\"");
    }

    @Test
    void downloadDefaultsToAttachmentModeWhenModeParamIsUnrecognized() {
        when(invoiceService.getInvoiceEntityById(500L)).thenReturn(invoice);
        when(invoicePdfService.generateInvoicePdf(invoice, "fr")).thenReturn(new byte[]{1});

        ResponseEntity<byte[]> response = controller.downloadInvoicePdf(500L, "bogus-mode", "fr");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment;");
    }

    // ── 3. Tenant isolation: cross-tenant id -> 404, no PDF produced ────────

    @Test
    void requestingAnotherTenantsInvoicePdfNeverProducesAPdfAnd404s() {
        when(invoiceService.getInvoiceEntityById(999L))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 999"));

        assertThatThrownBy(() -> controller.downloadInvoicePdf(999L, "attachment", "fr"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(invoicePdfService, never()).generateInvoicePdf(any(), anyString());
        verify(invoiceService, never()).markPdfGenerated(any(), any());
    }

    // ── 12. Invalid invoice id -> precise 404, not malformed PDF/500 ───────

    @Test
    void requestingPdfForANonExistentInvoiceIdIs404NotAMalformedPdf() {
        when(invoiceService.getInvoiceEntityById(424242L))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 424242"));

        assertThatThrownBy(() -> controller.downloadInvoicePdf(424242L, "attachment", "fr"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invoicePdfService, never()).generateInvoicePdf(any(), anyString());
    }

    @Test
    void emailingANonExistentInvoiceIs404() {
        when(invoiceService.getInvoiceEntityById(424242L))
                .thenThrow(new ResourceNotFoundException("Invoice not found with id: 424242"));

        assertThatThrownBy(() -> controller.emailInvoicePdf(424242L, Map.of(), "fr"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── 10. Empty-filtered export -> 422 structured body, no PDF bytes ──────

    @Test
    void exportingPdfWithZeroMatchingInvoicesReturns422WithStructuredCodeAndNeverCallsPdfGenerator() {
        when(invoiceService.exportFilteredInvoices(any())).thenReturn(List.of());

        ResponseEntity<?> response = controller.exportInvoicesPdf(null, "fr");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isEqualTo(Map.of("code", "NO_MATCHING_INVOICES"));
        verify(invoicePdfService, never()).generateInvoiceListPdf(any(), any(), any(), any());
    }

    @Test
    void exportingCsvWithZeroMatchingInvoicesReturns422WithStructuredCodeAndNeverBuildsPdf() {
        when(invoiceService.exportFilteredInvoices(any())).thenReturn(List.of());

        ResponseEntity<?> response = controller.exportInvoicesCsv(
                null, null, null, null, null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isEqualTo(Map.of("code", "NO_MATCHING_INVOICES"));
        verify(invoicePdfService, never()).generateInvoiceListPdf(any(), any(), any(), any());
    }

    @Test
    void exportingPdfWithMatchesReturnsAttachmentPdfDerivedFromInvoiceTenant() {
        List<Invoice> invoices = List.of(invoice);
        when(invoiceService.exportFilteredInvoices(any())).thenReturn(invoices);
        when(invoicePdfService.generateInvoiceListPdf(eq(invoices), any(), eq(tenant), eq("fr")))
                .thenReturn(new byte[]{7, 7});

        ResponseEntity<?> response = controller.exportInvoicesPdf(null, "fr");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getBody()).isEqualTo(new byte[]{7, 7});
    }

    // ── Email: missing recipient resolves to 422 without sending ───────────

    @Test
    void emailingAnInvoiceWithNoResolvableRecipientReturns422WithoutCallingEmailService() {
        Invoice noClient = Invoice.builder()
                .id(500L).invoiceNumber("INV-2026-00500")
                .issueDate(LocalDate.of(2026, 1, 10)).dueDate(LocalDate.of(2026, 1, 25))
                .amount(new BigDecimal("4000.00")).currency("MAD")
                .status(InvoiceStatus.PENDING).tenant(tenant).client(null)
                .build();
        when(invoiceService.getInvoiceEntityById(500L)).thenReturn(noClient);

        ResponseEntity<?> response = controller.emailInvoicePdf(500L, Map.of(), "fr");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        verify(platformEmailService, never()).sendInvoicePdfEmail(any(), any(), any());
    }

    @Test
    void emailingAnInvoiceWithAnExplicitToEmailSucceedsAndReportsIt() {
        when(invoiceService.getInvoiceEntityById(500L)).thenReturn(invoice);
        when(platformEmailService.sendInvoicePdfEmail(invoice, "client@example.com", "fr"))
                .thenReturn(SmtpMailService.SmtpResult.success(null));

        ResponseEntity<?> response = controller.emailInvoicePdf(500L, Map.of("toEmail", "client@example.com"), "fr");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(Map.of("success", true, "emailedTo", "client@example.com"));
    }
}
