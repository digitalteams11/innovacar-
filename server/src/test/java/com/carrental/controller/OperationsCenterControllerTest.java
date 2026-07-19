package com.carrental.controller;

import com.carrental.entity.SupportTicket;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.NotificationService;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the "API server unavailable" incident on ticket creation:
 * createTicket() previously sent two synchronous ZeptoMail calls (up to ~10s HTTP
 * timeout each) at the tail of its own @Transactional method, holding the request's
 * DB connection the whole time and colliding with the frontend's 20s axios timeout.
 * The ticket must be persisted and the response returned without waiting on either
 * email send, and the (lazily-loaded) tenant must not blow up once the ticket entity
 * is handed to a different thread for email dispatch.
 */
class OperationsCenterControllerTest {

    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final KnowledgeArticleRepository articleRepository = mock(KnowledgeArticleRepository.class);
    private final LegalDocumentRepository legalDocumentRepository = mock(LegalDocumentRepository.class);
    private final LegalAcceptanceRepository acceptanceRepository = mock(LegalAcceptanceRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
    private final GpsSettingsRepository gpsSettingsRepository = mock(GpsSettingsRepository.class);
    private final TenantSettingsRepository tenantSettingsRepository = mock(TenantSettingsRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SupportMessageRepository messageRepository = mock(SupportMessageRepository.class);
    private final TrustedDeviceRepository trustedDeviceRepository = mock(TrustedDeviceRepository.class);
    private final LoginAttemptRepository loginAttemptRepository = mock(LoginAttemptRepository.class);
    private final SupportRoutingService supportRoutingService = mock(SupportRoutingService.class);
    private final PlatformEmailService platformEmailService = mock(PlatformEmailService.class);

    private User user;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("Acme Rentals").build();
        user = User.builder().id(10L).email("owner@acme.test").firstName("Owner").lastName("Person").tenant(tenant).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        TenantContext.setCurrentTenantId(1L);

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(supportRoutingService.resolveDestinationEmail(any(), any())).thenReturn("support@innovacar.app");
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
            SupportTicket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(99L);
            if (t.getTicketNumber() == null) t.setTicketNumber("RC-TEST0001");
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private OperationsCenterController controller(Executor executor) {
        return new OperationsCenterController(
                ticketRepository, articleRepository, legalDocumentRepository, acceptanceRepository,
                auditLogRepository, sessionRepository, gpsSettingsRepository, tenantSettingsRepository,
                tenantRepository, notificationService, messageRepository, trustedDeviceRepository,
                loginAttemptRepository, supportRoutingService, platformEmailService, executor);
    }

    @Test
    void createTicket_persistsAndReturnsWithoutWaitingOnEmailExecutor() {
        // A tracking executor that never actually runs the submitted task — if
        // createTicket() still returns successfully with the ticket persisted, the
        // response cannot possibly be waiting on the email sends to complete.
        Executor trackingExecutor = mock(Executor.class);
        OperationsCenterController controller = controller(trackingExecutor);

        ResponseEntity<Map<String, Object>> response = controller.createTicket(Map.of(
                "subject", "Cannot access my dashboard",
                "description", "I get a blank page after logging in.",
                "category", "ACCOUNT",
                "priority", "MEDIUM",
                "channel", "SUPPORT",
                "kind", "SUPPORT"
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat((Boolean) response.getBody().get("success")).isTrue();
        verify(ticketRepository).save(any(SupportTicket.class));
        verify(trackingExecutor).execute(any(Runnable.class));
        // The tracking executor never ran the task, so neither email call happened —
        // proving the response didn't depend on them.
        verifyNoInteractions(platformEmailService);
    }

    @Test
    void createTicket_emailDispatch_actuallyRunsBothSendsWhenExecutorExecutesInline() {
        OperationsCenterController controller = controller(Runnable::run);

        controller.createTicket(Map.of(
                "subject", "Billing question",
                "description", "My invoice total looks wrong this month.",
                "category", "BILLING",
                "priority", "HIGH",
                "channel", "BILLING",
                "kind", "SUPPORT"
        ));

        ArgumentCaptor<SupportTicket> ticketCaptor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(platformEmailService).sendSupportTicketCreatedInternal(ticketCaptor.capture());
        verify(platformEmailService).sendSupportTicketConfirmation(any(SupportTicket.class));
        assertThat(ticketCaptor.getValue().getTenant().getName()).isEqualTo("Acme Rentals");
    }

    @Test
    void createTicket_emailDispatchThrows_doesNotPropagateToCaller() {
        doThrow(new RuntimeException("ZeptoMail unreachable"))
                .when(platformEmailService).sendSupportTicketCreatedInternal(any());
        OperationsCenterController controller = controller(Runnable::run);

        ResponseEntity<Map<String, Object>> response = controller.createTicket(Map.of(
                "subject", "GPS not updating",
                "description", "Vehicle position hasn't refreshed in two days.",
                "category", "GPS",
                "priority", "HIGH",
                "channel", "TECHNICAL",
                "kind", "SUPPORT"
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(ticketRepository).save(any(SupportTicket.class));
    }

    @Test
    void createTicket_executorSaturated_stillPersistsTicketAndReturnsSuccess() {
        Executor rejecting = mock(Executor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException("queue full"))
                .when(rejecting).execute(any());
        OperationsCenterController controller = controller(rejecting);

        ResponseEntity<Map<String, Object>> response = controller.createTicket(Map.of(
                "subject", "Security concern",
                "description", "I noticed a login from an unfamiliar location.",
                "category", "SECURITY",
                "priority", "CRITICAL",
                "channel", "SECURITY",
                "kind", "SUPPORT"
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(ticketRepository).save(any(SupportTicket.class));
    }

    @Test
    void createTicket_unknownCategoryOrPriority_fallsBackToUnknownRatherThanThrowing() {
        OperationsCenterController controller = controller(Runnable::run);

        ResponseEntity<Map<String, Object>> response = controller.createTicket(Map.of(
                "subject", "Something odd happened",
                "description", "Not sure how to categorize this one.",
                "category", "NOT_A_REAL_CATEGORY",
                "priority", "NOT_A_REAL_PRIORITY",
                "channel", "SUPPORT",
                "kind", "SUPPORT"
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(SupportTicket.Category.UNKNOWN);
        assertThat(captor.getValue().getPriority()).isEqualTo(SupportTicket.Priority.UNKNOWN);
    }
}
