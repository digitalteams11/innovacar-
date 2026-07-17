package com.carrental.controller;

import com.carrental.entity.EmailLog;
import com.carrental.entity.EmailTemplate;
import com.carrental.entity.OnboardingProgress;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import com.carrental.entity.PlatformSettings;
import com.carrental.entity.SupportMessage;
import com.carrental.entity.SupportTicket;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.AuditLogRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.EmailLogRepository;
import com.carrental.repository.EmailTemplateRepository;
import com.carrental.repository.EmployeeRepository;
import com.carrental.repository.GpsAlertRepository;
import com.carrental.repository.GpsSettingsRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.LoginAttemptRepository;
import com.carrental.repository.OnboardingProgressRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.PlatformSettingsRepository;
import com.carrental.repository.PromoCodeRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.SubscriptionPlanRepository;
import com.carrental.repository.SupportMessageRepository;
import com.carrental.repository.SupportTicketRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.TenantSettingsRepository;
import com.carrental.repository.TrustedDeviceRepository;
import com.carrental.repository.UserRepository;
import com.carrental.repository.UserSessionRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.service.PlatformSettingsService;
import com.carrental.service.SystemHealthService;
import com.carrental.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private GpsSettingsRepository gpsSettingsRepository;
    @Mock private GpsAlertRepository gpsAlertRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SupportTicketRepository ticketRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PlatformSettingsRepository platformSettingsRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private EmailTemplateRepository emailTemplateRepository;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private TenantSettingsRepository tenantSettingsRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private TrustedDeviceRepository trustedDeviceRepository;
    @Mock private OnboardingProgressRepository onboardingProgressRepository;
    @Mock private SystemHealthService systemHealthService;
    @Mock private PlatformSettingsService platformSettingsService;
    @Mock private EncryptionUtil encryptionUtil;
    @Mock private com.carrental.service.SmtpMailService smtpMailService;

    @InjectMocks private SuperAdminController superAdminController;

    @BeforeEach
    void authenticateAsSuperAdmin() {
        User superAdmin = User.builder().id(1L).email("superadmin@test.com").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(superAdmin, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSupportAnalyticsCalculatesResolutionTimeFromTickets() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 13, 10, 0);
        when(ticketRepository.findAll()).thenReturn(List.of(
                ticket("OPEN", "CRITICAL", now.minusHours(2), null),
                ticket("RESOLVED", "HIGH", now.minusHours(5), now.minusHours(1)),
                ticket("CLOSED", "LOW", now.minusHours(4), now.minusHours(1))
        ));

        Map<String, Object> analytics = superAdminController.getSupportAnalytics().getBody();

        assertThat(analytics)
                .containsEntry("totalTickets", 3)
                .containsEntry("openTickets", 1L)
                .containsEntry("resolvedTickets", 2L)
                .containsEntry("criticalTickets", 1L)
                .containsEntry("avgResolutionHours", 3.5);
    }

    @Test
    void getTicketNotesReturnsOnlyInternalNotes() {
        SupportTicket ticket = ticket("OPEN", "MEDIUM", LocalDateTime.now(), null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                message(ticket, "SUPPORT", "Visible in conversation"),
                message(ticket, "INTERNAL_NOTE", "Call agency owner tomorrow")
        ));

        List<Map<String, Object>> notes = superAdminController.getTicketNotes(10L).getBody();

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0))
                .containsEntry("content", "Call agency owner tomorrow")
                .containsEntry("createdBy", "Innovax Support");
    }

    @Test
    void addTicketNotePersistsInternalSupportMessage() {
        SupportTicket ticket = ticket("OPEN", "HIGH", LocalDateTime.now(), null);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenAnswer(invocation -> {
            SupportMessage message = invocation.getArgument(0);
            message.setId(99L);
            message.setCreatedAt(LocalDateTime.of(2026, 6, 13, 11, 0));
            return message;
        });

        Map<String, Object> response = superAdminController
                .addTicketNote(10L, Map.of("content", " Verify payment history before next reply "))
                .getBody();

        ArgumentCaptor<SupportMessage> messageCaptor = ArgumentCaptor.forClass(SupportMessage.class);
        verify(supportMessageRepository).save(messageCaptor.capture());
        SupportMessage saved = messageCaptor.getValue();
        assertThat(saved.getTicket()).isSameAs(ticket);
        assertThat(saved.getSenderType()).isEqualTo("INTERNAL_NOTE");
        assertThat(saved.getMessage()).isEqualTo("Verify payment history before next reply");
        verify(ticketRepository).save(ticket);
        assertThat(response).containsEntry("success", true);
        assertThat((Map<String, Object>) response.get("item"))
                .containsEntry("id", 99L)
                .containsEntry("content", "Verify payment history before next reply");
    }

    @Test
    void sendTestEmailRejectsMissingSmtpHost() {
        when(platformSettingsRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformSettings.builder().build()));

        Map<String, Object> response = superAdminController
                .sendTestEmail(Map.of("to", " admin@example.com "))
                .getBody();

        assertThat(response)
                .containsEntry("success", false)
                .containsEntry("errorCode", "SMTP_HOST_MISSING");
    }

    @Test
    void deleteEmailTemplateRequiresExistingTemplate() {
        EmailTemplate template = EmailTemplate.builder()
                .id(7L)
                .name("Welcome")
                .build();
        when(emailTemplateRepository.findById(7L)).thenReturn(Optional.of(template));

        Map<String, Object> response = superAdminController.deleteEmailTemplate(7L).getBody();

        verify(emailTemplateRepository).delete(template);
        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("message", "Template deleted successfully");
    }

    @Test
    void deleteEmailTemplateRejectsMissingTemplate() {
        when(emailTemplateRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> superAdminController.deleteEmailTemplate(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Template not found");
    }

    @Test
    void getMarketingConversionUsesRealTenantAndOnboardingData() {
        LocalDateTime now = LocalDateTime.now();
        Tenant recentPaid = tenant(1L, now.minusDays(5), true);
        Tenant oldTrial = tenant(2L, now.minusDays(45), false);
        Tenant recentTrial = tenant(3L, now.minusDays(10), false);
        when(tenantRepository.findAll()).thenReturn(List.of(recentPaid, oldTrial, recentTrial));
        when(onboardingProgressRepository.findAll()).thenReturn(List.of(
                OnboardingProgress.builder()
                        .tenant(recentPaid)
                        .completed(true)
                        .completedAt(recentPaid.getCreatedAt().plusMinutes(30))
                        .build(),
                OnboardingProgress.builder()
                        .tenant(oldTrial)
                        .completed(true)
                        .completedAt(oldTrial.getCreatedAt().plusMinutes(90))
                        .build(),
                OnboardingProgress.builder()
                        .tenant(recentTrial)
                        .completed(false)
                        .build()
        ));

        Map<String, Object> conversion = superAdminController.getMarketingConversion().getBody();

        assertThat(conversion)
                .containsEntry("websiteVisits", 0)
                .containsEntry("trafficTrackingEnabled", false)
                .containsEntry("signupsStarted", 2L)
                .containsEntry("trialsCreated", 2L)
                .containsEntry("totalTrials", 3)
                .containsEntry("trialsCompleted", 2L)
                .containsEntry("paidConversion", 1L)
                .containsEntry("trialToPaidRate", 33.33)
                .containsEntry("avgOnboardingMinutes", 60.0)
                .containsEntry("landingCTR", 0);
    }

    @Test
    void getRevenueReportReturnsRowsAndCsvForSelectedDateRange() {
        Tenant agency = tenant(1L, LocalDateTime.of(2026, 1, 1, 9, 0), true);
        when(paymentRepository.findAll()).thenReturn(List.of(
                payment("PAY-001", agency, "1250.00", LocalDateTime.of(2026, 6, 10, 12, 0)),
                payment("PAY-OLD", agency, "900.00", LocalDateTime.of(2026, 5, 31, 12, 0))
        ));

        Map<String, Object> report = superAdminController
                .getReport("revenue", "2026-06-01", "2026-06-30")
                .getBody();

        assertThat(report)
                .containsEntry("type", "revenue")
                .containsEntry("rowCount", 1)
                .containsEntry("filename", "rentcar-revenue-2026-06-01-to-2026-06-30.csv");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("paymentNumber", "PAY-001")
                .containsEntry("agency", "Agency 1")
                .containsEntry("status", PaymentStatus.PAID)
                .containsEntry("type", PaymentType.SUBSCRIPTION);
        assertThat((String) report.get("csv"))
                .contains("\"paymentNumber\",\"agency\",\"amount\",\"status\",\"type\",\"paymentDate\"")
                .contains("\"PAY-001\",\"Agency 1\",\"1250.00\",\"PAID\",\"SUBSCRIPTION\",\"2026-06-10T12:00\"")
                .doesNotContain("PAY-OLD")
                .doesNotContain("/api/reports/revenue.csv");
    }

    @Test
    void getMarketingOnboardingMergesStoredSettingsWithDefaults() throws Exception {
        PlatformSettings settings = PlatformSettings.builder()
                .marketingOnboardingJson("{\"step1Title\":\"Custom Welcome\",\"ctaText\":\"Book Demo\"}")
                .build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(settings);
        when(objectMapper.readValue(settings.getMarketingOnboardingJson(), Map.class))
                .thenReturn(Map.of("step1Title", "Custom Welcome", "ctaText", "Book Demo"));

        Map<String, Object> data = superAdminController.getMarketingOnboarding().getBody();

        assertThat(data)
                .containsEntry("step1Title", "Custom Welcome")
                .containsEntry("ctaText", "Book Demo")
                .containsEntry("step2Title", "Configure Your Fleet")
                .containsEntry("features", "GPS Tracking\nDigital Contracts\nRevenue Analytics");
    }

    @Test
    void updateMarketingOnboardingPersistsSettingsJson() throws Exception {
        PlatformSettings settings = PlatformSettings.builder().build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(settings);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"step1Title\":\"New Flow\"}");

        Map<String, Object> response = superAdminController
                .updateMarketingOnboarding(Map.of("step1Title", "New Flow"))
                .getBody();

        verify(platformSettingsRepository).save(settings);
        assertThat(settings.getMarketingOnboardingJson()).isEqualTo("{\"step1Title\":\"New Flow\"}");
        assertThat(response).containsEntry("success", true);
        assertThat((Map<String, Object>) response.get("data"))
                .containsEntry("step1Title", "New Flow")
                .containsEntry("step2Title", "Configure Your Fleet");
    }

    // ── SMTP settings persistence/validation ─────────────────────────────────

    @Test
    void savingSmtpSettingsThenReloadingReturnsTheSamePersistedValues() {
        PlatformSettings ps = PlatformSettings.builder().id(1L).build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);
        when(encryptionUtil.encrypt("app-password")).thenReturn("ENC(app-password)");

        Map<String, Object> saveResponse = superAdminController.updateSmtpSettings(Map.of(
                "smtpHost", "smtp.zoho.com",
                "smtpPort", 587,
                "smtpUsername", "contact@innovacar.app",
                "smtpPassword", "app-password",
                "fromEmail", "contact@innovacar.app",
                "smtpEnabled", true
        )).getBody();

        assertThat(saveResponse)
                .containsEntry("smtpHost", "smtp.zoho.com")
                .containsEntry("smtpPort", 587)
                .containsEntry("smtpUsername", "contact@innovacar.app")
                .containsEntry("hasPassword", true)
                .containsEntry("smtpEnabled", true);

        // Reload — the row must still report the same values (proves the singleton fix:
        // save and read no longer bind to different platform_settings rows).
        Map<String, Object> reloadResponse = superAdminController.getSmtpSettings().getBody();
        assertThat(reloadResponse)
                .containsEntry("smtpHost", "smtp.zoho.com")
                .containsEntry("smtpPort", 587)
                .containsEntry("smtpUsername", "contact@innovacar.app")
                .containsEntry("hasPassword", true);
    }

    @Test
    void blankPasswordOnUpdatePreservesExistingEncryptedPassword() {
        PlatformSettings ps = PlatformSettings.builder()
                .id(1L)
                .smtpHost("smtp.zoho.com")
                .smtpUsername("contact@innovacar.app")
                .smtpPasswordEncrypted("ENC(existing-password)")
                .build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);

        superAdminController.updateSmtpSettings(Map.of(
                "smtpHost", "smtp.zoho.com",
                "smtpPassword", ""
        ));

        assertThat(ps.getSmtpPasswordEncrypted()).isEqualTo("ENC(existing-password)");
    }

    @Test
    void getSmtpSettingsNeverReturnsThePasswordValue() {
        PlatformSettings ps = PlatformSettings.builder()
                .id(1L)
                .smtpPasswordEncrypted("ENC(super-secret)")
                .build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);

        Map<String, Object> response = superAdminController.getSmtpSettings().getBody();

        assertThat(response).doesNotContainKey("smtpPassword");
        assertThat(response.values()).noneMatch(v -> "ENC(super-secret)".equals(v));
        assertThat(response).containsEntry("hasPassword", true);
    }

    @Test
    void updateSmtpSettingsRejectsEnabledConfigWithoutHost() {
        PlatformSettings ps = PlatformSettings.builder().id(1L).build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);

        org.springframework.http.ResponseEntity<Map<String, Object>> response = superAdminController.updateSmtpSettings(Map.of(
                "smtpEnabled", true,
                "smtpUsername", "contact@innovacar.app"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("errorCode", "SMTP_HOST_MISSING");
        assertThat(ps.getSmtpHost()).isNull();
    }

    @Test
    void updateSmtpSettingsRejectsTlsAndSslEnabledTogether() {
        PlatformSettings ps = PlatformSettings.builder().id(1L).build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);

        org.springframework.http.ResponseEntity<Map<String, Object>> response = superAdminController.updateSmtpSettings(Map.of(
                "smtpUseTls", true,
                "smtpSslEnabled", true
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("errorCode", "SMTP_TLS_SSL_CONFLICT");
    }

    @Test
    void updatePlatformSettingsNoLongerWritesSmtpFields() {
        PlatformSettings ps = PlatformSettings.builder()
                .id(1L)
                .smtpHost("smtp.zoho.com")
                .smtpUsername("contact@innovacar.app")
                .build();
        when(platformSettingsService.getOrCreateSingleton()).thenReturn(ps);

        superAdminController.updatePlatformSettings(Map.of(
                "smtpHost", "attacker-controlled.example.com",
                "smtpUsername", "attacker@example.com",
                "platformName", "Innovacar"
        ));

        // The generic /settings endpoint must never change SMTP fields — those are owned
        // exclusively by /email/settings (updateSmtpSettings).
        assertThat(ps.getSmtpHost()).isEqualTo("smtp.zoho.com");
        assertThat(ps.getSmtpUsername()).isEqualTo("contact@innovacar.app");
        assertThat(ps.getPlatformName()).isEqualTo("Innovacar");
    }

    private SupportTicket ticket(String status, String priority, LocalDateTime createdAt, LocalDateTime resolvedAt) {
        return SupportTicket.builder()
                .subject("Support request")
                .status(status)
                .priority(priority)
                .createdAt(createdAt)
                .resolvedAt(resolvedAt)
                .build();
    }

    private SupportMessage message(SupportTicket ticket, String senderType, String content) {
        return SupportMessage.builder()
                .id(1L)
                .ticket(ticket)
                .senderName("Innovax Support")
                .senderType(senderType)
                .message(content)
                .createdAt(LocalDateTime.of(2026, 6, 13, 10, 0))
                .build();
    }

    private Tenant tenant(Long id, LocalDateTime createdAt, boolean subscriptionActive) {
        return Tenant.builder()
                .id(id)
                .name("Agency " + id)
                .email("agency" + id + "@test.com")
                .createdAt(createdAt)
                .subscriptionActive(subscriptionActive)
                .build();
    }

    private Payment payment(String number, Tenant tenant, String amount, LocalDateTime paymentDate) {
        return Payment.builder()
                .paymentNumber(number)
                .tenant(tenant)
                .amount(new java.math.BigDecimal(amount))
                .paymentDate(paymentDate)
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.PAID)
                .type(PaymentType.SUBSCRIPTION)
                .build();
    }
}
