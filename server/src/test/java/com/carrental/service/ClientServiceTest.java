package com.carrental.service;

import com.carrental.dto.client.ClientResponse;
import com.carrental.dto.client.CreateClientRequest;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ClientDuplicateException;
import com.carrental.exception.ClientValidationException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.entity.ClientIdentityDocument;
import com.carrental.entity.DocumentType;
import com.carrental.repository.ClientIdentityDocumentRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientServiceTest {

    private static final long TENANT_ID = 7L;

    @Mock private ClientRepository clientRepository;
    @Mock private ClientIdentityDocumentRepository identityDocumentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RolePermissionService rolePermissionService;
    @Mock private DepositService depositService;

    @InjectMocks
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        when(identityDocumentRepository.findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(identityDocumentRepository.findFirstByClientIdAndTenantIdAndIsPrimaryTrue(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(identityDocumentRepository.save(any(ClientIdentityDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllClients_emptyDatabase_returnsEmptyList() {
        when(clientRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThat(clientService.getAllClients()).isEmpty();
    }

    @Test
    void createClient_withoutOptionalEmail_savesNormalizedTenantClient() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).name("Agency").email("agency@test.com").build();
        CreateClientRequest request = validRequest();
        request.setEmail("");
        request.setSecondaryPhone("");
        request.setDrivingLicense("");
        request.setNotes("");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            client.setId(11L);
            return client;
        });

        ClientResponse response = clientService.createClient(request);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getFullName()).isEqualTo("mohamed Amddah");
        assertThat(response.getEmail()).isNull();
        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
        verify(clientRepository).save(argThat(client ->
                client.getTenant() == tenant
                        && client.getEmail() == null
                        && client.getSecondaryPhone() == null
                        && client.getDrivingLicense() == null));
    }

    @Test
    void createClient_duplicatePhone_throwsCleanConflict() {
        CreateClientRequest request = validRequest();
        Client existing = Client.builder().id(99L).phone(request.getPhone()).build();
        when(clientRepository.findFirstByTenantIdAndPhoneIgnoreCase(TENANT_ID, request.getPhone()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ClientDuplicateException.class)
                .hasMessage("A client with this phone number already exists in this agency.");
        verify(clientRepository, never()).save(any());
    }

    @Test
    void createClient_withoutTenant_throwsTenantRequired() {
        TenantContext.clear();

        assertThatThrownBy(() -> clientService.createClient(validRequest()))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Current user is not linked to an agency.");
    }

    @Test
    void updateClientEmail_authorizedTenantClient_persistsNormalizedEmail() {
        Client client = Client.builder().id(5L).name("Jane Doe").email(null).build();
        when(clientRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientResponse response = clientService.updateClientEmail(5L, "  Jane.Doe@Example.COM  ");

        assertThat(response.getEmail()).isEqualTo("jane.doe@example.com");
        verify(clientRepository).save(argThat(c -> "jane.doe@example.com".equals(c.getEmail())));
    }

    @Test
    void updateClientEmail_crossTenantClient_throwsNotFound() {
        when(clientRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateClientEmail(5L, "jane@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void updateClientEmail_missingClient_throwsNotFound() {
        when(clientRepository.findByIdAndTenantId(999L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateClientEmail(999L, "jane@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateClientEmail_updatesExistingRecord_doesNotCreateDuplicate() {
        Client client = Client.builder().id(5L).name("Jane Doe").email("old@example.com").build();
        when(clientRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        clientService.updateClientEmail(5L, "new@example.com");

        verify(clientRepository, never()).save(argThat(c -> !c.getId().equals(5L)));
        verify(clientRepository, times(1)).save(any(Client.class));
    }

    @Test
    void createClient_missingDocumentNumber_throwsValidation() {
        CreateClientRequest request = validRequest();
        request.setCin(null);
        request.setDocumentType(null);
        request.setDocumentNumber(null);

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Identity document type is required.");
    }

    @Test
    void createClient_duplicateDocumentNumberInTenant_throwsConflict() {
        CreateClientRequest request = validRequest();
        request.setCin(null);
        request.setDocumentType(DocumentType.CIN);
        request.setDocumentNumber("AB123456");
        ClientIdentityDocument existing = ClientIdentityDocument.builder()
                .id(1L).tenantId(TENANT_ID).clientId(99L).documentType(DocumentType.CIN)
                .documentNumber("AB123456").isPrimary(true).build();
        when(identityDocumentRepository.findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(TENANT_ID, "AB123456"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ClientDuplicateException.class);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void createClient_documentExpiryBeforeIssue_throwsValidation() {
        CreateClientRequest request = validRequest();
        request.setCin(null);
        request.setDocumentType(DocumentType.PASSPORT);
        request.setDocumentNumber("MA1234567");
        request.setDocumentIssueDate(LocalDate.of(2025, 1, 1));
        request.setDocumentExpiryDate(LocalDate.of(2024, 1, 1));

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Document expiry date cannot be before the issue date.");
    }

    @Test
    void createClient_birthDateInFuture_throwsValidation() {
        CreateClientRequest request = validRequest();
        request.setBirthDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Date of birth cannot be in the future.");
    }

    @Test
    void createClient_legacyCinOnly_resolvesToCinDocumentType() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).name("Agency").build();
        CreateClientRequest request = validRequest();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            client.setId(21L);
            return client;
        });

        ClientResponse response = clientService.createClient(request);

        assertThat(response.getDocumentType()).isEqualTo(DocumentType.CIN);
        verify(identityDocumentRepository).save(argThat(doc ->
                doc.getDocumentType() == DocumentType.CIN && "jy45452".equals(doc.getDocumentNumber())));
    }

    @Test
    void getClientDetails_validClient_returns200ShapeWithSummaryAndHistory() {
        Client client = Client.builder().id(5L).name("Jane Doe").email("jane@example.com").build();
        when(clientRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(client));

        Reservation activeReservation = Reservation.builder().id(1L).status(ReservationStatus.CONFIRMED)
                .dateEnd(LocalDate.of(2026, 1, 10)).build();
        Reservation completedReservation = Reservation.builder().id(2L).status(ReservationStatus.COMPLETED)
                .dateEnd(LocalDate.of(2026, 3, 1)).build();
        Reservation cancelledReservation = Reservation.builder().id(3L).status(ReservationStatus.CANCELLED)
                .dateEnd(LocalDate.of(2025, 12, 1)).build();
        when(reservationRepository.findAllByTenantIdAndClientId(TENANT_ID, 5L))
                .thenReturn(List.of(activeReservation, completedReservation, cancelledReservation));

        Contract activeContract = Contract.builder().id(10L).status(ContractStatus.ACTIVE).build();
        Contract draftContract = Contract.builder().id(11L).status(ContractStatus.DRAFT).build();
        when(contractRepository.findAllByTenantIdAndClientId(TENANT_ID, 5L))
                .thenReturn(List.of(activeContract, draftContract));

        Invoice invoice = Invoice.builder().id(20L).amount(new BigDecimal("1000")).status(InvoiceStatus.ISSUED).build();
        when(invoiceRepository.findAllByTenantIdAndClientId(TENANT_ID, 5L)).thenReturn(List.of(invoice));

        Payment paidPayment = Payment.builder().id(30L).amount(new BigDecimal("400")).status(PaymentStatus.PAID).build();
        when(paymentRepository.findAllByTenantIdAndClientIdOrderByPaymentDateDesc(TENANT_ID, 5L))
                .thenReturn(List.of(paidPayment));

        when(depositService.getClientDepositSummary(5L))
                .thenReturn(Map.of("activeDeposits", new BigDecimal("500")));

        Map<String, Object> result = clientService.getClientDetails(5L);

        assertThat(result).containsKeys("client", "summary", "reservationHistory", "contractHistory", "paymentHistory");
        ClientResponse clientResponse = (ClientResponse) result.get("client");
        assertThat(clientResponse.getId()).isEqualTo(5L);
        assertThat(clientResponse.getFullName()).isEqualTo("Jane Doe");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("totalReservations")).isEqualTo(3);
        assertThat(summary.get("activeReservations")).isEqualTo(1L);
        assertThat(summary.get("completedRentals")).isEqualTo(1L);
        assertThat(summary.get("cancelledReservations")).isEqualTo(1L);
        assertThat(summary.get("totalContracts")).isEqualTo(2);
        assertThat(summary.get("activeContracts")).isEqualTo(1L);
        assertThat(summary.get("totalInvoiced")).isEqualTo(new BigDecimal("1000"));
        assertThat(summary.get("totalPaid")).isEqualTo(new BigDecimal("400"));
        assertThat(summary.get("outstandingBalance")).isEqualTo(new BigDecimal("600"));
        assertThat(summary.get("activeDeposits")).isEqualTo(new BigDecimal("500"));
        assertThat(summary.get("lastRentalDate")).isEqualTo(LocalDate.of(2026, 3, 1));

        assertThat((List<?>) result.get("reservationHistory")).hasSize(3);
        assertThat((List<?>) result.get("contractHistory")).hasSize(2);
        assertThat((List<?>) result.get("paymentHistory")).hasSize(1);
    }

    @Test
    void getClientDetails_missingClient_throwsNotFound() {
        when(clientRepository.findByIdAndTenantId(999L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientDetails(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getClientDetails_crossTenantClient_throwsNotFoundNotAnotherAgencysData() {
        // fetchClientInTenant scopes the lookup to TENANT_ID — a client that belongs to a
        // different tenant simply isn't found by findByIdAndTenantId, so this never leaks
        // another agency's client the way an id-only lookup would.
        when(clientRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientDetails(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getClientDetails_noOptionalIdentityFields_doesNotThrow() {
        // A client with no CIN/passport/license/birth date/nationality/emergency contact on
        // file must still resolve to a 200 with null fields, never a 500.
        Client bareClient = Client.builder().id(6L).name("No Extras").build();
        when(clientRepository.findByIdAndTenantId(6L, TENANT_ID)).thenReturn(Optional.of(bareClient));
        when(reservationRepository.findAllByTenantIdAndClientId(TENANT_ID, 6L)).thenReturn(List.of());
        when(contractRepository.findAllByTenantIdAndClientId(TENANT_ID, 6L)).thenReturn(List.of());
        when(invoiceRepository.findAllByTenantIdAndClientId(TENANT_ID, 6L)).thenReturn(List.of());
        when(paymentRepository.findAllByTenantIdAndClientIdOrderByPaymentDateDesc(TENANT_ID, 6L)).thenReturn(List.of());
        when(depositService.getClientDepositSummary(6L)).thenReturn(Map.of());

        Map<String, Object> result = clientService.getClientDetails(6L);

        ClientResponse clientResponse = (ClientResponse) result.get("client");
        assertThat(clientResponse.getBirthDate()).isNull();
        assertThat(clientResponse.getDocumentNumber()).isNull();
        assertThat(clientResponse.getDrivingLicense()).isNull();
        assertThat(clientResponse.getEmergencyContactName()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("lastRentalDate")).isNull();
        assertThat(summary.get("activeDeposits")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void maskDocumentNumber_showsFirstTwoAndLastTwoOnly() {
        assertThat(ClientService.maskDocumentNumber("AB123456")).isEqualTo("AB••••56");
        assertThat(ClientService.maskDocumentNumber("MA1234567")).isEqualTo("MA•••••67");
        assertThat(ClientService.maskDocumentNumber("AB")).isEqualTo("A•");
    }

    private CreateClientRequest validRequest() {
        CreateClientRequest request = new CreateClientRequest();
        request.setFullName("mohamed Amddah");
        request.setEmail("mohamed21amddah@gmail.com");
        request.setPhone("0658742744");
        request.setCin("jy45452");
        request.setAddress("rue patrice mumumba hassan");
        request.setCity("rabat");
        request.setCountry("Morocco");
        request.setNationality("Morocco");
        request.setBirthDate(LocalDate.of(2004, 1, 15));
        return request;
    }
}
