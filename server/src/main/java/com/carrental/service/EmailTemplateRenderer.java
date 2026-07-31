package com.carrental.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Renders the static Thymeleaf email templates under
 * {@code classpath:/templates/email/*.html} (welcome, verify-email,
 * reset-password, trial-started/expiring/expired, subscription-success,
 * payment-success/failed, monthly/yearly-report, contract-signed,
 * reservation-confirmation, vehicle-returned, maintenance-alert) — all
 * sharing the branded header/footer/button fragments under
 * {@code templates/email/fragments/}.
 *
 * <p><b>Naming note:</b> this is deliberately a separate class from the
 * existing {@link EmailTemplateService}, which is a different system: a
 * DB-backed, per-tenant-customizable template registry (seeded rows,
 * editable via the Super Admin Email Center) used for support tickets,
 * contact-form replies, and a handful of other business events. Merging the
 * two would mean either dropping DB-driven customization or bolting
 * Thymeleaf files onto DB rows — both bigger, riskier changes than this
 * task calls for. This class only renders the new static file templates;
 * {@code EmailTemplateService} is untouched and keeps its own responsibility.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    private final TemplateEngine templateEngine;

    /**
     * @param templateName file name without extension, e.g. {@code "welcome"} for
     *                      {@code templates/email/welcome.html}
     * @param variables    values exposed to the template via {@code ${...}}
     * @return the fully rendered HTML document
     */
    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.ENGLISH);
        if (variables != null) context.setVariables(variables);
        return templateEngine.process("email/" + templateName, context);
    }
}
