package com.carrental.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every new branded Thymeleaf email template must at least parse and render
 * without throwing — this catches a broken th:replace/fragment expression or
 * an unresolved variable reference before it ever reaches a real send.
 * Deliberately builds its own minimal TemplateEngine (no full Spring context)
 * so this stays fast and independent of the rest of the app's wiring.
 */
class EmailTemplateRendererTest {

    private static EmailTemplateRenderer renderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new EmailTemplateRenderer(engine);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "welcome", "verify-email", "reset-password",
            "trial-started", "trial-expiring", "trial-expired",
            "subscription-success", "payment-success", "payment-failed",
            "monthly-report", "yearly-report",
            "contract-signed", "reservation-confirmation", "vehicle-returned",
            "maintenance-alert",
    })
    void rendersEveryTemplateWithoutError(String templateName) {
        String html = renderer().render(templateName, Map.ofEntries(
                entry("firstName", "Amine"),
                entry("tenantName", "Atlas Rentals"),
                entry("clientName", "Amine"),
                entry("vehicleName", "Dacia Sandero"),
                entry("code", "482913"),
                entry("expiresInMinutes", 15),
                entry("planName", "Business"),
                entry("amount", "199.00 MAD"),
                entry("reason", "Card declined"),
                entry("invoiceNumber", "INV-1001"),
                entry("paidDate", "2026-07-30"),
                entry("reservationNumber", "RES-2001"),
                entry("contractNumber", "CTR-3001"),
                entry("agencyName", "Atlas Rentals"),
                entry("maintenanceType", "Oil change"),
                entry("dueDate", "2026-08-10"),
                entry("startDate", "2026-08-01"),
                entry("endDate", "2026-08-05"),
                entry("returnDate", "2026-08-05"),
                entry("daysRemaining", 3),
                entry("trialEndDate", "2026-08-15"),
                entry("periodLabel", "July 2026"),
                entry("dashboardUrl", "https://app.innovacar.app/dashboard"),
                entry("verificationUrl", "https://app.innovacar.app/verify?token=abc"),
                entry("upgradeUrl", "https://app.innovacar.app/subscription"),
                entry("retryUrl", "https://app.innovacar.app/billing"),
                entry("invoiceUrl", "https://app.innovacar.app/invoices/1"),
                entry("downloadUrl", "https://app.innovacar.app/contracts/1.pdf"),
                entry("nextBillingDate", "2026-08-30")
        ));

        assertThat(html).contains("Innovacar");
        assertThat(html).contains("Powered by Innovax Technologies");
        assertThat(html).doesNotContain("RentCar");
        assertThat(html).doesNotContain("${"); // no unresolved expression leaked into output
    }
}
