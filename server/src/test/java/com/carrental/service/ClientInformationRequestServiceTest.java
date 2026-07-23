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
import static org.mockito.Mockito.lenient;
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
    @Mock private EmailService emailService;
    @Mock private WhatsAppMessagingService whatsAppMessagingService;

    private ClientInformationRequestService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ClientInformationRequestService(
                requestRepository, clientRepository, identityDocumentRepository,
                contractRepository, tenantRepository, notificationService, objectMapper,
                emailService, whatsAppMessagingService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://innovacar.app");

        lenient().when(emailService.sendClientInformationRequestEmail(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null));
        lenient().when(whatsAppMessagingService.send(any(), any()))
                .thenReturn(new WhatsAppMessagingService.WhatsAppResult(true, true, "WhatsApp Cloud API", null, null));
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
        // Saved once on creation, once more after recording the delivery outcome.
        verify(requestRepository, times(2)).save(captor.capture());
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

    // ── Delivery scenarios ───────────────────────────────────────────────────

    @Test
    void moroccanPhoneNumbersAreNormalizedToE164() {
        assertThat(com.carrental.service.ClientInformationRequestService.normalizeMoroccanPhone("0658742744")).isEqualTo("+212658742744");
        assertThat(com.carrental.service.ClientInformationRequestService.normalizeMoroccanPhone("212658742744")).isEqualTo("+212658742744");
        assertThat(com.carrental.service.ClientInformationRequestService.normalizeMoroccanPhone("+212658742744")).isEqualTo("+212658742744");
        assertThat(com.carrental.service.ClientInformationRequestService.normalizeMoroccanPhone("12345")).isNull();
    }

    @Test
    void createRejectsAnInvalidPhoneNumber() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("123");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("INVALID_PHONE");
    }

    @Test
    void createRejectsAnInvalidEmail() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setEmail("not-an-email");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("INVALID_EMAIL");
    }

    @Test
    void createRequiresAtLeastOneDeliveryDestination() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("NO_CHANNEL_AVAILABLE");
    }

    @Test
    void createSendsBothEmailAndWhatsappWhenBothDestinationsArePresent() {
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("0658742744");
        req.setEmail("sara@example.com");
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ClientInformationRequest r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });

        ClientInformationRequestResponse response = service.create(req);

        assertThat(response.getEmailResult().isSent()).isTrue();
        assertThat(response.getWhatsappResult().isSent()).isTrue();
        verify(emailService).sendClientInformationRequestEmail(eq("sara@example.com"), any(), any(), any(), any(), any());
        verify(whatsAppMessagingService).send(eq("+212658742744"), any());
    }

    @Test
    void createReportsPartialSuccessWhenWhatsappFailsButEmailSucceeds() {
        when(whatsAppMessagingService.send(any(), any()))
                .thenReturn(new WhatsAppMessagingService.WhatsAppResult(false, true, "WhatsApp Cloud API", "boom", "WHATSAPP_API_SEND_FAILED"));
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("0658742744");
        req.setEmail("sara@example.com");
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ClientInformationRequest r = inv.getArgument(0);
            r.setId(21L);
            return r;
        });

        ClientInformationRequestResponse response = service.create(req);

        assertThat(response.getEmailResult().isSent()).isTrue();
        assertThat(response.getWhatsappResult().isSent()).isFalse();
        // Partial failure never rolls back the request itself.
        verify(requestRepository, times(2)).save(any());
    }

    @Test
    void whatsappNotConfiguredIsReportedDistinctlyFromAFailure() {
        when(whatsAppMessagingService.send(any(), any()))
                .thenReturn(WhatsAppMessagingService.WhatsAppResult.notConfigured("WhatsApp Cloud API", "not set up"));
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setTemporaryName("Sara");
        req.setPhone("0658742744");
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ClientInformationRequest r = inv.getArgument(0);
            r.setId(22L);
            return r;
        });

        ClientInformationRequestResponse response = service.create(req);

        assertThat(response.getWhatsappResult().getStatus()).isEqualTo(com.carrental.entity.DeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void createBlocksCrossTenantClientAccess() {
        when(clientRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());
        CreateClientInformationRequestRequest req = new CreateClientInformationRequestRequest();
        req.setClientId(999L);
        req.setTemporaryName("Sara");
        req.setPhone("0658742744");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("TENANT_ACCESS_DENIED");
    }

    @Test
    void resendRetriesOnlyTheRequestedChannel() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(30L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .phone("+212658742744").email("sara@example.com")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(r));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClientInformationRequestResponse response = service.resend(30L, List.of("WHATSAPP"));

        assertThat(response.getWhatsappResult().isSent()).isTrue();
        assertThat(response.getEmailResult().isAttempted()).isFalse();
        verify(emailService, never()).sendClientInformationRequestEmail(any(), any(), any(), any(), any(), any());
        verify(whatsAppMessagingService).send(eq("+212658742744"), any());
    }

    @Test
    void expiredRequestIsRejectedOnResend() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(31L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .phone("+212658742744")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        when(requestRepository.findByIdAndTenantId(31L, 1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.resend(31L, null))
                .isInstanceOf(ClientInfoRequestException.class)
                .extracting(e -> ((ClientInfoRequestException) e).getCode())
                .isEqualTo("REQUEST_EXPIRED");
    }
}
