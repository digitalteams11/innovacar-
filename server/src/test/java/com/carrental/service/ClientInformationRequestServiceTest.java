package com.carrental.service;

import com.carrental.dto.clientinfo.ApproveClientInformationRequest;
import com.carrental.dto.clientinfo.ClientInformationRequestResponse;
import com.carrental.dto.clientinfo.ClientInformationSubmitRequest;
import com.carrental.dto.clientinfo.CreateClientInformationRequestRequest;
import com.carrental.dto.clientinfo.PublicClientInformationView;
import com.carrental.entity.Client;
import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import com.carrental.entity.Contract;
import com.carrental.entity.DocumentType;
import com.carrental.entity.Tenant;
import com.carrental.exception.ClientInfoRequestException;
import com.carrental.repository.ClientIdentityDocumentRepository;
import com.carrental.repository.ClientInformationRequestRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientInformationRequestServiceTest {

    @Mock private ClientInformationRequestRepository requestRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientIdentityDocumentRepository identityDocumentRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private NotificationService notificationService;

    private ClientInformationRequestService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ClientInformationRequestService(
                requestRepository, clientRepository, identityDocumentRepository,
                contractRepository, tenantRepository, notificationService, objectMapper);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://innovacar.app");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ClientInformationSubmitRequest validSubmission() {
        ClientInformationSubmitRequest s = new ClientInformationSubmitRequest();
        s.setFullName("Sara Client");
        s.setPhone("+212600000000");
        s.setEmail("sara@example.com");
        s.setDocumentType(DocumentType.CIN);
        s.setDocumentNumber("AB123456");
        s.setPrivacyAccepted(true);
        return s;
    }

    @Test
    void createGeneratesASecureLinkOnlyReturnedOnce() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("+212600000000");
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ClientInformationRequest r = inv.getArgument(0);
            r.setId(5L);
            return r;
        });

        ClientInformationRequestResponse response = service.create(req);

        assertThat(response.getSecureLink()).contains("/#/client-info/");
        assertThat(response.getStatus()).isEqualTo(ClientInfoRequestStatus.SENT);

        ArgumentCaptor<ClientInformationRequest> captor = ArgumentCaptor.forClass(ClientInformationRequest.class);
        verify(requestRepository).save(captor.capture());
        // Only the hash is ever persisted — never the raw token.
        assertThat(captor.getValue().getTokenHash()).hasSize(64).doesNotContain("/#/client-info/");
    }

    @Test
    void publicUrlContainsNoInternalId() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("+212600000000");
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ClientInformationRequest r = inv.getArgument(0);
            r.setId(999L);
            return r;
        });

        ClientInformationRequestResponse response = service.create(req);

        assertThat(response.getSecureLink()).doesNotContain("999");
    }

    @Test
    void invalidTokenIsRejected() {
        when(requestRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublic("not-a-real-token"))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_LINK_INVALID");
    }

    @Test
    void expiredTokenIsRejected() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(1L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        when(requestRepository.findByTokenHash(any())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getPublic("some-token"))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_LINK_EXPIRED");
    }

    @Test
    void revokedTokenIsRejected() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(1L).tenantId(1L).status(ClientInfoRequestStatus.REVOKED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByTokenHash(any())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getPublic("some-token"))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_LINK_REVOKED");
    }

    @Test
    void clientCanSubmitOnceButNotTwice() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(1L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByTokenHash(any())).thenReturn(Optional.of(r));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit("some-token", validSubmission());
        assertThat(r.getStatus()).isEqualTo(ClientInfoRequestStatus.SUBMITTED);
        assertThat(r.getSubmissionPayload()).contains("Sara Client");

        // Second submission attempt against the now-SUBMITTED request must be rejected.
        assertThatThrownBy(() -> service.submit("some-token", validSubmission()))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_ALREADY_SUBMITTED");
    }

    @Test
    void submissionWithoutPrivacyConsentIsRejected() {
        ClientInformationSubmitRequest s = validSubmission();
        s.setPrivacyAccepted(false);

        assertThatThrownBy(() -> service.submit("some-token", s))
                .isInstanceOf(ClientInfoRequestException.class);
        verify(requestRepository, never()).findByTokenHash(any());
    }

    @Test
    void approvingWithCreateNewCreatesClientAndLinksContractWithoutTouchingPriceOrVehicle() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(10L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .contractId(50L)
                .submissionPayload("""
                        {"fullName":"Sara Client","phone":"+212600000000","email":"sara@example.com",
                         "documentType":"CIN","documentNumber":"AB123456","privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(r));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clientRepository.save(any())).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(77L);
            return c;
        });
        Contract contract = Contract.builder().id(50L).tenant(tenant)
                .dailyPrice(new java.math.BigDecimal("300.00")).totalPrice(new java.math.BigDecimal("900.00"))
                .build();
        when(contractRepository.findByIdAndTenantId(50L, 1L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApproveClientInformationRequest approveReq = new ApproveClientInformationRequest();
        approveReq.setAction(ApproveClientInformationRequest.Action.CREATE_NEW);

        ClientInformationRequestResponse response = service.approve(10L, approveReq);

        assertThat(response.getStatus()).isEqualTo(ClientInfoRequestStatus.APPROVED);
        assertThat(response.getApprovedClientId()).isEqualTo(77L);
        assertThat(contract.getClient()).isNotNull();
        assertThat(contract.getClientFullName()).isEqualTo("Sara Client");
        // Untouched — client-owned submission must never affect price/vehicle (spec section 17).
        assertThat(contract.getDailyPrice()).isEqualByComparingTo("300.00");
        assertThat(contract.getTotalPrice()).isEqualByComparingTo("900.00");
        verify(identityDocumentRepository).save(any());
    }

    @Test
    void approvingWithLinkExistingDoesNotCreateANewClient() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(11L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .submissionPayload("""
                        {"fullName":"Sara Client","phone":"+212600000000","documentType":"CIN",
                         "documentNumber":"AB123456","privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.of(r));
        Client existing = Client.builder().id(42L).tenant(tenant).name("Sara Client").build();
        when(clientRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(existing));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApproveClientInformationRequest approveReq = new ApproveClientInformationRequest();
        approveReq.setAction(ApproveClientInformationRequest.Action.LINK_EXISTING);
        approveReq.setExistingClientId(42L);

        ClientInformationRequestResponse response = service.approve(11L, approveReq);

        assertThat(response.getApprovedClientId()).isEqualTo(42L);
        verify(clientRepository, never()).save(any());
        verify(identityDocumentRepository, never()).save(any());
    }

    @Test
    void potentialDuplicatesAreSurfacedButNeverAutoMerged() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(12L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .submissionPayload("""
                        {"fullName":"Sara Client","phone":"+212600000000","email":"sara@example.com",
                         "privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(12L, 1L)).thenReturn(Optional.of(r));
        Client existing = Client.builder().id(88L).tenant(tenant).name("Sara C.").phone("+212600000000").build();
        when(clientRepository.findFirstByTenantIdAndPhoneIgnoreCaseAndDeletedFalse(1L, "+212600000000"))
                .thenReturn(Optional.of(existing));

        ClientInformationRequestResponse response = service.getDetail(12L);

        assertThat(response.getPotentialDuplicates()).hasSize(1);
        assertThat(response.getPotentialDuplicates().get(0).getClientId()).isEqualTo(88L);
        assertThat(response.getPotentialDuplicates().get(0).getMatchedOn()).isEqualTo("phone");
        // Surfaced only — no client was created or modified just by viewing the detail.
        verify(clientRepository, never()).save(any());
    }

    @Test
    void tenantIsolationIsEnforced() {
        when(requestRepository.findByIdAndTenantId(eq(99L), eq(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_REQUEST_NOT_FOUND");
    }

    @Test
    void alreadyApprovedRequestCannotBeApprovedAgain() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(13L).tenantId(1L).status(ClientInfoRequestStatus.APPROVED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByIdAndTenantId(13L, 1L)).thenReturn(Optional.of(r));

        ApproveClientInformationRequest approveReq = new ApproveClientInformationRequest();
        approveReq.setAction(ApproveClientInformationRequest.Action.CREATE_NEW);

        assertThatThrownBy(() -> service.approve(13L, approveReq))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("CLIENT_INFO_ALREADY_APPROVED");
    }
}
