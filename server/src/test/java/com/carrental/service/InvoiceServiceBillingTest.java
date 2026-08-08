package com.carrental.service;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceFinancialPreviewResponse;
import com.carrental.dto.invoice.InvoiceLineDto;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.entity.*;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractExtensionRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the billing redesign's core invariants (see spec cases 1-11):
 * a contract-linked invoice's lines always sum to the contract total, an
 * existing active invoice is reused rather than duplicated, a cancelled
 * contract cannot get a new normal invoice, a manual invoice's total is
 * always computed server-side from its lines, and the financial preview
 * never double-counts money already paid.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceBillingTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractExtensionRepository contractExtensionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private NumberGeneratorService numberGeneratorService;

    private InvoiceService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, tenantRepository, clientRepository, contractRepository,
                contractExtensionRepository, paymentRepository, paymentService, numberGeneratorService);
        tenant = Tenant.builder().id(1L).name("Tenant A").build();
        TenantContext.setCurrentTenantId(1L);
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentService.collectedAmountFor(any(Invoice.class))).thenReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Contract.ContractBuilder baseContract() {
        return Contract.builder()
                .id(10L)
                .tenant(tenant)
                .contractNumber("CTR-2026-00008")
                .startDate(LocalDate.of(2026, 8, 3))
                .endDate(LocalDate.of(2026, 8, 8))
                .rentalDays(5)
                .dailyPrice(new BigDecimal("600"))
                .totalPrice(new BigDecimal("3000"))
                .paidAmount(new BigDecimal("2000"))
                .status(ContractStatus.ACTIVE);
    }

    // ── syncInvoiceForContract: lines always sum to the contract total ──────

    @Test
    void syncInvoiceForContractBuildsLinesSummingExactlyToContractTotal() {
        Contract contract = baseContract().build();
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of());
        when(contractExtensionRepository.findAllByTenantIdAndContractIdOrderByCreatedAtAsc(1L, 10L)).thenReturn(List.of());
        when(numberGeneratorService.generateInvoiceNumber(1L)).thenReturn("FAC-2026-000001");

        Invoice invoice = service.syncInvoiceForContract(contract);

        assertThat(invoice.getAmount()).isEqualByComparingTo("3000");
        BigDecimal lineSum = invoice.getLines().stream().map(InvoiceLine::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineSum).isEqualByComparingTo(invoice.getAmount());
        assertThat(invoice.getSourceType()).isEqualTo(InvoiceSourceType.CONTRACT_LINKED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("FAC-2026-000001");
    }

    @Test
    void syncInvoiceForContractIncludesFeesAndDiscountAsSeparateLinesStillSummingToTotal() {
        Contract contract = baseContract()
                .totalPrice(new BigDecimal("3150")) // 3000 base + 200 delivery + 100 fuel - 150 discount
                .deliveryFees(new BigDecimal("200"))
                .fuelCharges(new BigDecimal("100"))
                .discountAmount(new BigDecimal("150"))
                .build();
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of());
        when(contractExtensionRepository.findAllByTenantIdAndContractIdOrderByCreatedAtAsc(1L, 10L)).thenReturn(List.of());
        when(numberGeneratorService.generateInvoiceNumber(1L)).thenReturn("FAC-2026-000002");

        Invoice invoice = service.syncInvoiceForContract(contract);

        BigDecimal lineSum = invoice.getLines().stream().map(InvoiceLine::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(lineSum).isEqualByComparingTo("3150");
        assertThat(invoice.getLines()).extracting(InvoiceLine::getType)
                .contains(InvoiceLineType.DELIVERY, InvoiceLineType.FUEL, InvoiceLineType.DISCOUNT, InvoiceLineType.RENTAL);
    }

    // ── Idempotency: never a second active invoice for the same contract ────

    @Test
    void syncInvoiceForContractReusesExistingActiveInvoiceInsteadOfCreatingASecondOne() {
        Contract contract = baseContract().totalPrice(new BigDecimal("3500")).build();
        Invoice existing = Invoice.builder()
                .id(99L).invoiceNumber("FAC-2026-000001").tenant(tenant).contract(contract)
                .status(InvoiceStatus.ISSUED).sourceType(InvoiceSourceType.CONTRACT_LINKED)
                .amount(new BigDecimal("3000")).issueDate(LocalDate.now()).dueDate(LocalDate.now())
                .lines(new java.util.ArrayList<>())
                .build();
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of(existing));
        when(contractExtensionRepository.findAllByTenantIdAndContractIdOrderByCreatedAtAsc(1L, 10L)).thenReturn(List.of());

        Invoice result = service.syncInvoiceForContract(contract);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getAmount()).isEqualByComparingTo("3500");
        verify(numberGeneratorService, org.mockito.Mockito.never()).generateInvoiceNumber(anyLong());
    }

    @Test
    void syncInvoiceForContractLeavesACancelledInvoiceUntouchedAndCreatesANewOneInstead() {
        Contract contract = baseContract().totalPrice(new BigDecimal("3000")).build();
        Invoice cancelled = Invoice.builder()
                .id(98L).invoiceNumber("FAC-2026-000000").tenant(tenant).contract(contract)
                .status(InvoiceStatus.CANCELLED).sourceType(InvoiceSourceType.CONTRACT_LINKED)
                .amount(new BigDecimal("1000")).issueDate(LocalDate.now()).dueDate(LocalDate.now())
                .lines(new java.util.ArrayList<>())
                .build();
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of(cancelled));
        when(contractExtensionRepository.findAllByTenantIdAndContractIdOrderByCreatedAtAsc(1L, 10L)).thenReturn(List.of());
        when(numberGeneratorService.generateInvoiceNumber(1L)).thenReturn("FAC-2026-000003");

        Invoice result = service.syncInvoiceForContract(contract);

        assertThat(result).isNotSameAs(cancelled);
        assertThat(result.getInvoiceNumber()).isEqualTo("FAC-2026-000003");
        assertThat(cancelled.getAmount()).isEqualByComparingTo("1000"); // untouched
    }

    // ── createInvoice: cancelled contract cannot get a new normal invoice ───

    @Test
    void createInvoiceRefusesAContractLinkedInvoiceForACancelledContract() {
        Contract contract = baseContract().status(ContractStatus.CANCELLED).build();
        when(contractRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(contract));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setContractId(10L);

        assertThatThrownBy(() -> service.createInvoice(request)).isInstanceOf(IllegalStateException.class);
    }

    // ── Manual invoice: total is always computed server-side from lines ─────

    @Test
    void manualInvoiceTotalIsComputedFromLinesNeverTrustedFromClient() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(numberGeneratorService.generateInvoiceNumber(1L)).thenReturn("FAC-2026-000010");

        InvoiceLineDto line1 = new InvoiceLineDto();
        line1.setType("OTHER");
        line1.setDescription("Service A");
        line1.setQuantity(new BigDecimal("2"));
        line1.setUnitPrice(new BigDecimal("100"));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setIssueDate(LocalDate.now());
        request.setDueDate(LocalDate.now().plusDays(15));
        request.setLines(List.of(line1));
        request.setDiscountAmount(new BigDecimal("20"));
        request.setTaxAmount(new BigDecimal("0"));

        InvoiceResponse response = service.createInvoice(request);

        // 2 * 100 = 200 subtotal, -20 discount => 180, regardless of any amount the client might send
        assertThat(response.getSubtotalAmount()).isEqualByComparingTo("200");
        assertThat(response.getAmount()).isEqualByComparingTo("180");
        assertThat(response.getSourceType()).isEqualTo(InvoiceSourceType.MANUAL);
    }

    @Test
    void manualInvoiceRequiresEitherLinesOrAFlatAmount() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setIssueDate(LocalDate.now());
        request.setDueDate(LocalDate.now().plusDays(15));

        assertThatThrownBy(() -> service.createInvoice(request)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Financial preview: never double-counts money already paid ───────────

    @Test
    void financialPreviewComputesOutstandingFromContractPaidAmountNeverDoubleCounting() {
        Contract contract = baseContract().build(); // total=3000, paid=2000
        when(contractRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(contract));
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of());

        InvoiceFinancialPreviewResponse preview = service.getFinancialPreviewForContract(10L);

        assertThat(preview.getContractTotal()).isEqualByComparingTo("3000");
        assertThat(preview.getAlreadyPaid()).isEqualByComparingTo("2000");
        assertThat(preview.getOutstandingBalance()).isEqualByComparingTo("1000");
        assertThat(preview.isFullyPaid()).isFalse();
        assertThat(preview.isHasActiveInvoice()).isFalse();
    }

    @Test
    void financialPreviewFlagsFullyPaidWhenNothingRemains() {
        Contract contract = baseContract().paidAmount(new BigDecimal("3000")).build();
        when(contractRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(contract));
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 10L)).thenReturn(List.of());

        InvoiceFinancialPreviewResponse preview = service.getFinancialPreviewForContract(10L);

        assertThat(preview.getOutstandingBalance()).isEqualByComparingTo("0");
        assertThat(preview.isFullyPaid()).isTrue();
    }
}
