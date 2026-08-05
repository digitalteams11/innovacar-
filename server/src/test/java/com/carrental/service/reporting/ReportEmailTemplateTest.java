package com.carrental.service.reporting;

import com.carrental.service.EmailActionUrlBuilder;
import com.carrental.service.EmailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the reported bug: the monthly/yearly report
 * email's "Open dashboard" button rendered (or, for the fixed case, its
 * successor "View report" button renders) as a REAL absolute HTTPS anchor
 * pointing at an actual hash-router route — not silently omitted (the
 * previous bug: dashboardUrl was never added to the template variable map,
 * so th:if="${dashboardUrl}" always evaluated false and the whole button
 * div never rendered at all) and not a relative/non-hash URL that the
 * deployed HashRouter SPA can't resolve.
 *
 * <p>Uses a real Thymeleaf engine over the actual template files (like
 * EmailServiceTest) — the point is to catch a genuinely broken render, which
 * a mocked renderer can't.
 */
class ReportEmailTemplateTest {

    private EmailTemplateRenderer renderer() {
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
    @ValueSource(strings = {"monthly-report", "yearly-report"})
    void reportEmail_rendersARealAbsoluteHashRouterAnchor_notASilentlyOmittedButton(String templateName) {
        EmailActionUrlBuilder urlBuilder = new EmailActionUrlBuilder("https://innovacar.app");
        String html = renderer().render(templateName, Map.of(
                "tenantName", "Acme Rental",
                "periodLabel", "2026-01-01 - 2026-01-31",
                "reportUrl", urlBuilder.reportArchiveUrl(500L),
                "buttonLabel", ReportLabels.get("button.viewReport", "en")));

        assertThat(html).contains("<a ");
        assertThat(html).contains("href=\"https://innovacar.app/#/report-archive?reportId=500\"");
        assertThat(html).contains(">View report<");
        // The label must not still claim "Open dashboard" while pointing somewhere else.
        assertThat(html).doesNotContain(">Open dashboard<");
        // No JS-based interaction, no relative/hash-less URL, no dev host.
        assertThat(html).doesNotContain("onclick=");
        assertThat(html).doesNotContain("javascript:");
        assertThat(html).doesNotContain("localhost");
        // Visible fallback URL (spec: "if the button doesn't work, copy this link").
        assertThat(html).contains("copy and paste this link");
    }

    @Test
    void reportEmail_omitsTheButtonEntirely_whenNoReportUrlIsSupplied() {
        String html = renderer().render("monthly-report", Map.of(
                "tenantName", "Acme Rental",
                "periodLabel", "2026-01-01 - 2026-01-31"));

        // Fail-closed, not fail-broken: no reportUrl means no CTA at all, never a
        // dead href="" button (spec section 17: "fail email generation explicitly
        // when a required action URL is missing... do not send a broken CTA email").
        // (The email footer's mailto/innovacar.app links are unrelated and expected.)
        assertThat(html).doesNotContain("href=\"\"");
        assertThat(html).doesNotContain("copy and paste this link");
    }

    @Test
    void reportEmail_frenchLabelIsTranslated_notHardcodedEnglish() {
        EmailActionUrlBuilder urlBuilder = new EmailActionUrlBuilder("https://innovacar.app");
        String html = renderer().render("monthly-report", Map.of(
                "tenantName", "Agence Acme",
                "periodLabel", "2026-01-01 - 2026-01-31",
                "reportUrl", urlBuilder.reportArchiveUrl(500L),
                "buttonLabel", ReportLabels.get("button.viewReport", "fr")));

        assertThat(html).contains(">Voir le rapport<");
    }

    @Test
    void reportEmail_arabicLabelIsTranslated() {
        EmailActionUrlBuilder urlBuilder = new EmailActionUrlBuilder("https://innovacar.app");
        String html = renderer().render("monthly-report", Map.of(
                "tenantName", "وكالة",
                "periodLabel", "2026-01-01 - 2026-01-31",
                "reportUrl", urlBuilder.reportArchiveUrl(500L),
                "buttonLabel", ReportLabels.get("button.viewReport", "ar")));

        assertThat(html).contains("عرض التقرير");
    }
}
