package com.carrental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Sends email over HTTPS (port 443) via ZeptoMail's transactional email API
 * (https://www.zoho.com/zeptomail/help/api/email-sending.html) — the sole
 * email delivery mechanism. SMTP (ports 465/587) is never attempted: Railway
 * blocks outbound SMTP ports at the network level, so no SMTP configuration
 * can ever succeed there, only a different protocol can.
 *
 * <p>The API token is a static per-account credential (unlike Zoho Mail's own
 * send API, which requires a full OAuth 2.0 authorization-code flow with
 * refresh tokens) — that's what {@code apiToken} holds, read from
 * {@code ZEPTOMAIL_API_TOKEN} (falling back to the older {@code EMAIL_API_TOKEN}
 * name if that's what's set) and never logged.
 */
@Slf4j
@Component
public class HttpEmailProvider implements EmailProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_ZEPTOMAIL_BASE_URL = "https://api.zeptomail.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.email.api-base-url:}")
    private String apiBaseUrl;

    // Reads ZEPTOMAIL_API_TOKEN first; falls back to EMAIL_API_TOKEN (the
    // pre-existing name) so nothing breaks if that's what's actually set.
    @Value("${app.email.api-token:}")
    private String apiToken;

    @Value("${app.email.from-email:}")
    private String fromEmail;

    @Value("${app.email.from-name:RentCar}")
    private String fromName;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiToken) && StringUtils.hasText(fromEmail);
    }

    @Override
    public String label() {
        return "ZeptoMail API";
    }

    /** Base URL actually in effect (default or overridden) — safe to log/expose. */
    public String baseUrl() {
        return StringUtils.hasText(apiBaseUrl) ? apiBaseUrl : DEFAULT_ZEPTOMAIL_BASE_URL;
    }

    public String fromEmail() {
        return fromEmail;
    }

    public String fromName() {
        return fromName;
    }

    /** Whether an API token is set — never the token value itself. */
    public boolean tokenPresent() {
        return StringUtils.hasText(apiToken);
    }

    @Override
    public SmtpMailService.SmtpResult send(EmailMessage message) {
        if (!StringUtils.hasText(apiToken)) {
            return SmtpMailService.SmtpResult.failure(label(), "ZEPTOMAIL_API_TOKEN is not set.", "EMAIL_CONFIGURATION_MISSING");
        }
        if (!StringUtils.hasText(fromEmail)) {
            return SmtpMailService.SmtpResult.failure(label(), "EMAIL_FROM_EMAIL is not set.", "EMAIL_CONFIGURATION_MISSING");
        }
        if (!StringUtils.hasText(message.to())) {
            return SmtpMailService.SmtpResult.failure(label(), "Recipient email is required.", "EMAIL_NO_RECIPIENT");
        }

        try {
            String requestBody = buildZeptoMailRequestBody(message);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/v1.1/email"))
                    // ZeptoMail's own auth scheme — the token itself is never logged.
                    .header("Authorization", "Zoho-enczapikey " + apiToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EMAIL_API] Sent via {} to {} | Subject: {}", label(), message.to(), message.subject());
                return SmtpMailService.SmtpResult.success(label());
            }

            String errorCode = classifyHttpError(response.statusCode());
            log.warn("[EMAIL_API] Send via {} to {} failed status={} body={}",
                    label(), message.to(), response.statusCode(), safeBody(response.body()));
            return SmtpMailService.SmtpResult.failure(label(),
                    "Email API returned HTTP " + response.statusCode(), errorCode);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[EMAIL_API] Send via {} to {} timed out", label(), message.to());
            return SmtpMailService.SmtpResult.failure(label(), "Email API request timed out.", "EMAIL_API_TIMEOUT");
        } catch (Exception e) {
            log.warn("[EMAIL_API] Send via {} to {} failed: exceptionClass={} message={}",
                    label(), message.to(), e.getClass().getName(), e.getMessage());
            return SmtpMailService.SmtpResult.failure(label(), e.getMessage(), "EMAIL_API_SEND_FAILED", e.getClass().getName());
        }
    }

    private String classifyHttpError(int status) {
        if (status == 401 || status == 403) return "EMAIL_API_AUTH_FAILED";
        if (status == 429) return "EMAIL_API_RATE_LIMITED";
        if (status >= 500) return "EMAIL_API_PROVIDER_ERROR";
        return "EMAIL_API_REQUEST_REJECTED";
    }

    /** Truncates a response body before logging — never assume a provider error body is small or secret-free. */
    private String safeBody(String body) {
        if (body == null) return "";
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }

    private String buildZeptoMailRequestBody(EmailMessage message) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode from = root.putObject("from");
        from.put("address", fromEmail);
        if (StringUtils.hasText(fromName)) from.put("name", fromName);

        ArrayNode toArray = root.putArray("to");
        ObjectNode toEntry = toArray.addObject();
        toEntry.putObject("email_address").put("address", message.to());

        root.put("subject", message.subject() != null ? message.subject() : "");
        if (StringUtils.hasText(message.htmlBody())) {
            root.put("htmlbody", message.htmlBody());
        }
        if (StringUtils.hasText(message.plainBody())) {
            root.put("textbody", message.plainBody());
        }

        if (message.hasAttachment()) {
            ArrayNode attachments = root.putArray("attachments");
            ObjectNode attachment = attachments.addObject();
            attachment.put("content", Base64.getEncoder().encodeToString(message.attachmentBytes()));
            attachment.put("mime_type", StringUtils.hasText(message.attachmentContentType())
                    ? message.attachmentContentType() : "application/pdf");
            attachment.put("name", message.attachmentName());
        }

        return objectMapper.writeValueAsString(root);
    }
}
