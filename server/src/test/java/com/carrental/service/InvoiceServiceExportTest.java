package com.carrental.service;

import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.entity.Client;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link InvoiceService#exportFilteredInvoices} and
 * {@link InvoiceService#getInvoiceEntityById}: tenant scoping always comes
 * from {@link TenantContext} regardless of the filter DTO (item 3, item 9),
 * status-string parsing (including "all"/garbage -> no filter, item 9),
 * sort-string parsing, and the {@code CURRENT_PAGE}/{@code pageInvoiceIds}
 * narrowing never resurrecting another tenant's invoice even if its id
 * happens to appear in the list (item 3's export-specific case). Modeled on
 * this codebase's plain Mockito + AssertJ service-test convention.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceExportTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractExtensionRepository contractExtensionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private NumberGeneratorService numberGeneratorService;
    @Mock private InvoiceConflictRecoveryService invoiceConflictRecoveryService;

    private InvoiceService service;
    private Tenant tenantA;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, tenantRepository, clientRepository, contractRepository,
                contractExtensionRepository, paymentRepository, paymentService, numberGeneratorService,
                invoiceConflictRecoveryService);
        tenantA = Tenant.builder().id(1L).name("Tenant A").build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice invoiceFor(Tenant tenant, long id, InvoiceStatus status) {
        return Invoice.builder()
                .id(id).invoiceNumber("INV-" + id)
                .issueDate(LocalDate.of(2026, 1, 1)).dueDate(LocalDate.of(2026, 1, 15))
                .amount(java.math.BigDecimal.TEN).currency("MAD")
                .status(status).tenant(tenant)
                .build();
    }

    // ── 3. Tenant isolation: repository always scoped by TenantContext ─────

    @Test
    void exportAlwaysScopesTheRepositoryQueryToTheCurrentTenantContextRegardlessOfFilter() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setClientId(999L); // attacker-controlled content — must never override tenant scoping

        service.exportFilteredInvoices(filter);

        verify(invoiceRepository).findAllForExport(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(Sort.class));
    }

    @Test
    void exportForTenantBNeverIncludesTenantAsInvoiceEvenIfIdIsInPageInvoiceIds() {
        TenantContext.setCurrentTenantId(2L);
        Tenant tenantB = Tenant.builder().id(2L).name("Tenant B").build();
        // The repository is tenant-scoped by definition — it only ever returns tenant B's rows,
        // regardless of what pageInvoiceIds the request claims.
        Invoice tenantBInvoice = invoiceFor(tenantB, 501L, InvoiceStatus.PENDING);
        when(invoiceRepository.findAllForExport(eq(2L), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of(tenantBInvoice));

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setScope("CURRENT_PAGE");
        // Tenant A's invoice id (500L) is smuggled into the request alongside tenant B's own (501L).
        filter.setPageInvoiceIds(List.of(500L, 501L));

        List<Invoice> results = service.exportFilteredInvoices(filter);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(501L);
        assertThat(results).noneMatch(inv -> inv.getId().equals(500L));
    }

    @Test
    void getInvoiceEntityByIdIs404NotAnExceptionLeakingCrossTenantExistence() {
        TenantContext.setCurrentTenantId(2L);
        when(invoiceRepository.findByIdAndTenantId(500L, 2L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getInvoiceEntityById(500L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── 9. Status filter fidelity ───────────────────────────────────────────

    @Test
    void statusFilterIsParsedFromStringAndPassedToTheRepositoryQuery() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setStatus("CANCELLED");
        service.exportFilteredInvoices(filter);

        verify(invoiceRepository).findAllForExport(eq(1L), isNull(), eq(InvoiceStatus.CANCELLED),
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Sort.class));
    }

    @Test
    void statusFilterOfAllIsTreatedAsNoStatusFilter() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setStatus("all");
        service.exportFilteredInvoices(filter);

        verify(invoiceRepository).findAllForExport(eq(1L), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Sort.class));
    }

    @Test
    void unknownStatusValueIsTreatedAsNoStatusFilterRatherThanFailingTheExport() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setStatus("NOT_A_REAL_STATUS");

        service.exportFilteredInvoices(filter);

        verify(invoiceRepository).findAllForExport(eq(1L), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Sort.class));
    }

    // ── 9. Date range / client / contract / vehicle filters passed through ─

    @Test
    void dateRangeClientContractAndVehicleFiltersArePassedThroughToTheRepositoryQuery() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setSearch("  INV-2026  ");
        filter.setDateFrom(LocalDate.of(2026, 1, 1));
        filter.setDateTo(LocalDate.of(2026, 1, 31));
        filter.setClientId(10L);
        filter.setContractId(20L);
        filter.setVehicleId(30L);

        service.exportFilteredInvoices(filter);

        verify(invoiceRepository).findAllForExport(
                eq(1L), eq("INV-2026"), isNull(),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31)),
                eq(10L), eq(20L), eq(30L), any(Sort.class));
    }

    @Test
    void sortParameterIsParsedIntoDirectionAndProperty() {
        TenantContext.setCurrentTenantId(1L);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), sortCaptor.capture()))
                .thenReturn(List.of());

        InvoiceExportFilter filter = new InvoiceExportFilter();
        filter.setSort("dueDate,asc");
        service.exportFilteredInvoices(filter);

        Sort.Order order = sortCaptor.getValue().getOrderFor("dueDate");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void defaultSortIsIssueDateDescendingWhenNoSortProvided() {
        TenantContext.setCurrentTenantId(1L);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), sortCaptor.capture()))
                .thenReturn(List.of());

        service.exportFilteredInvoices(new InvoiceExportFilter());

        Sort.Order order = sortCaptor.getValue().getOrderFor("issueDate");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void nullFilterExportsWithNoNarrowingAndDefaultSort() {
        TenantContext.setCurrentTenantId(1L);
        when(invoiceRepository.findAllForExport(any(), any(), any(), any(), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        service.exportFilteredInvoices(null);

        verify(invoiceRepository).findAllForExport(eq(1L), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Sort.class));
    }
}
