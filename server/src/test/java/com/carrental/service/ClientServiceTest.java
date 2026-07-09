package com.carrental.service;

import com.carrental.dto.client.ClientResponse;
import com.carrental.dto.client.CreateClientRequest;
import com.carrental.entity.Client;
import com.carrental.entity.Tenant;
import com.carrental.exception.ClientDuplicateException;
import com.carrental.exception.ClientValidationException;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final long TENANT_ID = 7L;

    @Mock private ClientRepository clientRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
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
