package com.carrental.service.export;

import com.carrental.entity.*;
import com.carrental.repository.ClientIdentityDocumentRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock private ClientRepository clientRepository;
    @Mock private ClientIdentityDocumentRepository identityDocumentRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private PaymentRepository paymentRepository;

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        service = new ReportExportService(clientRepository, identityDocumentRepository, contractRepository, paymentRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Clients ──────────────────────────────────────────────────────────

    @Test
    void clientRows_masksDocumentNumber_andIncludesRentalCount() {
        Client client = Client.builder().id(1L).name("Jane Doe").phone("0600000000").email("jane@test.com").city("Rabat").build();
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(client));
        ClientIdentityDocument doc = ClientIdentityDocument.builder()
                .clientId(1L).documentType(DocumentType.CIN).documentNumber("AB123456").isPrimary(true).build();
        when(identityDocumentRepository.findAllByTenantIdAndIsPrimaryTrue(TENANT_ID)).thenReturn(List.of(doc));
        Contract contract = Contract.builder().client(client).build();
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(contract, contract));

        List<String[]> rows = service.clientRows(null);

        assertThat(rows).hasSize(1);
        String[] row = rows.get(0);
        assertThat(row[0]).isEqualTo("Jane Doe");
        assertThat(row[4]).isEqualTo("AB••••56"); // masked, never the raw document number
        assertThat(row[4]).doesNotContain("AB123456");
        assertThat(row[6]).isEqualTo("2"); // rental count from batched contract lookup
    }

    @Test
    void clientRows_noMatches_throwsExportNoData() {
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.clientRows(null))
                .isInstanceOf(ReportExportService.ExportNoDataException.class);
    }

    @Test
    void clientRows_searchFilters_byNamePhoneEmail() {
        Client match = Client.builder().id(1L).name("Jane Doe").phone("0600000000").email("jane@test.com").build();
        Client other = Client.builder().id(2L).name("John Smith").phone("0611111111").email("john@test.com").build();
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(match, other));
        when(identityDocumentRepository.findAllByTenantIdAndIsPrimaryTrue(TENANT_ID)).thenReturn(List.of());
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());

        List<String[]> rows = service.clientRows("jane");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("Jane Doe");
    }

    // ── Contracts ────────────────────────────────────────────────────────

    @Test
    void contractRows_filtersByStatus() {
        Contract active = Contract.builder().id(1L).contractNumber("CTR-1").status(ContractStatus.ACTIVE).totalPrice(new BigDecimal("500.00")).build();
        Contract cancelled = Contract.builder().id(2L).contractNumber("CTR-2").status(ContractStatus.CANCELLED).build();
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(active, cancelled));

        List<String[]> rows = service.contractRows(ContractStatus.ACTIVE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("CTR-1");
        assertThat(rows.get(0)[6]).isEqualTo("500.00 MAD");
        assertThat(rows.get(0)[7]).isEqualTo("Pending"); // no clientSignedAt set
    }

    @Test
    void contractRows_signedContract_showsSignedStatus() {
        Contract signed = Contract.builder().id(1L).contractNumber("CTR-1").status(ContractStatus.ACTIVE)
                .clientSignedAt(LocalDateTime.now()).build();
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(signed));

        List<String[]> rows = service.contractRows(null);

        assertThat(rows.get(0)[7]).isEqualTo("Signed");
    }

    @Test
    void contractRows_noMatches_throwsExportNoData() {
        when(contractRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.contractRows(null))
                .isInstanceOf(ReportExportService.ExportNoDataException.class);
    }

    // ── Payments ─────────────────────────────────────────────────────────

    @Test
    void paymentRows_filtersByStatus_andFormatsAmount() {
        Payment paid = Payment.builder().id(1L).paymentNumber("PAY-1").status(PaymentStatus.PAID)
                .amount(new BigDecimal("199.00")).paymentMethod(PaymentMethod.CASH).paymentDate(LocalDateTime.now()).build();
        Payment pending = Payment.builder().id(2L).paymentNumber("PAY-2").status(PaymentStatus.PENDING).build();
        when(paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(TENANT_ID)).thenReturn(List.of(paid, pending));

        List<String[]> rows = service.paymentRows(PaymentStatus.PAID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("PAY-1");
        assertThat(rows.get(0)[3]).isEqualTo("199.00 MAD");
    }

    @Test
    void paymentRows_noMatches_throwsExportNoData() {
        when(paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.paymentRows(null))
                .isInstanceOf(ReportExportService.ExportNoDataException.class);
    }
}
