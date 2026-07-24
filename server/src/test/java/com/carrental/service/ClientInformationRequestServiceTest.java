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
                emailService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://innovacar.app");

        lenient().when(emailService.sendClientInformationRequestEmail(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null));
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
    void submitNotifiesUsingTheRequestsOwnIdNotContractId() {
        // contractId is intentionally left null — a fresh client-info request
        // isn't linked to a contract yet, which is exactly what made the
        // legacy 5-arg notification call produce a dead (contractId-based)
        // link: entityId/actionUrl both ended up null. The fix must key off
        // the request's own id instead.
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(60L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .temporaryName("King").expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByTokenHash(any())).thenReturn(Optional.of(r));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit("some-token", validSubmission());

        verify(notificationService).notifyClientInformationSubmitted(
                eq("Client information submitted"),
                org.mockito.ArgumentMatchers.contains("King"),
                eq(60L), eq(1L));
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
    void createSendsEmailWhenAnEmailDestinationIsPresent() {
        // WhatsApp delivery is a manual, client-side wa.me share action now
        // (see SendClientInfoRequestModal.tsx) — the backend only ever
        // attempts email, so a phone-only request must not require it.
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
        verify(emailService).sendClientInformationRequestEmail(eq("sara@example.com"), any(), any(), any(), any(), any());
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
    void resendOnlyRetriesEmailWhenRequested() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(30L).tenantId(1L).status(ClientInfoRequestStatus.SENT)
                .phone("+212658742744").email("sara@example.com")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(requestRepository.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(r));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClientInformationRequestResponse response = service.resend(30L, List.of("EMAIL"));

        assertThat(response.getEmailResult().isSent()).isTrue();
        verify(emailService).sendClientInformationRequestEmail(eq("sara@example.com"), any(), any(), any(), any(), any());
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

    // ── Simplified form: no dates, single CIN/passport selector ────────────

    @Test
    void approvingWithPassportOnly_setsPassportNotCin() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(40L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .submissionPayload("""
                        {"fullName":"Karim Client","phone":"+212600000001",
                         "documentType":"PASSPORT","documentNumber":"PP998877","privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(40L, 1L)).thenReturn(Optional.of(r));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        when(clientRepository.save(clientCaptor.capture())).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(78L);
            return c;
        });
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApproveClientInformationRequest approveReq = new ApproveClientInformationRequest();
        approveReq.setAction(ApproveClientInformationRequest.Action.CREATE_NEW);
        service.approve(40L, approveReq);

        Client saved = clientCaptor.getValue();
        assertThat(saved.getPassportNumber()).isEqualTo("PP998877");
        assertThat(saved.getCin()).isNull();
    }

    @Test
    void approvedClientAndIdentityDocumentCarryNoDates() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(41L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .submissionPayload("""
                        {"fullName":"Karim Client","phone":"+212600000001",
                         "documentType":"CIN","documentNumber":"AB998877",
                         "driverLicenseNumber":"DL12345","privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(41L, 1L)).thenReturn(Optional.of(r));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        when(clientRepository.save(clientCaptor.capture())).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(79L);
            return c;
        });
        ArgumentCaptor<com.carrental.entity.ClientIdentityDocument> docCaptor =
                ArgumentCaptor.forClass(com.carrental.entity.ClientIdentityDocument.class);
        when(identityDocumentRepository.save(docCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApproveClientInformationRequest approveReq = new ApproveClientInformationRequest();
        approveReq.setAction(ApproveClientInformationRequest.Action.CREATE_NEW);
        service.approve(41L, approveReq);

        Client savedClient = clientCaptor.getValue();
        assertThat(savedClient.getDrivingLicense()).isEqualTo("DL12345");
        assertThat(savedClient.getDrivingLicenseIssue()).isNull();
        assertThat(savedClient.getDrivingLicenseExpiry()).isNull();
        assertThat(savedClient.getDrivingLicenseCategory()).isNull();
        assertThat(savedClient.getDrivingLicenseCountry()).isNull();

        com.carrental.entity.ClientIdentityDocument savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getDocumentNumber()).isEqualTo("AB998877");
        assertThat(savedDoc.getIssueDate()).isNull();
        assertThat(savedDoc.getExpiryDate()).isNull();
        assertThat(savedDoc.getIssuingCountry()).isNull();
    }

    @Test
    void duplicateDetectionByDocumentNumber() {
        ClientInformationRequest r = ClientInformationRequest.builder()
                .id(42L).tenantId(1L).status(ClientInfoRequestStatus.SUBMITTED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .submissionPayload("""
                        {"fullName":"Karim Client","phone":"+212600000099",
                         "documentType":"CIN","documentNumber":"AB998877","privacyAccepted":true}""")
                .build();
        when(requestRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(r));
        com.carrental.entity.ClientIdentityDocument existingDoc = com.carrental.entity.ClientIdentityDocument.builder()
                .id(5L).tenantId(1L).clientId(88L).documentNumber("AB998877").isPrimary(true).build();
        when(identityDocumentRepository.findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(1L, "AB998877"))
                .thenReturn(Optional.of(existingDoc));
        Client existing = Client.builder().id(88L).tenant(tenant).name("Karim C.").build();
        when(clientRepository.findByIdAndTenantId(88L, 1L)).thenReturn(Optional.of(existing));

        ClientInformationRequestResponse response = service.getDetail(42L);

        assertThat(response.getPotentialDuplicates()).hasSize(1);
        assertThat(response.getPotentialDuplicates().get(0).getClientId()).isEqualTo(88L);
        assertThat(response.getPotentialDuplicates().get(0).getMatchedOn()).isEqualTo("document");
    }
}
