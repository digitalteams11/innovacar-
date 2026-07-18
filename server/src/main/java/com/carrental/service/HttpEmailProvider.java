package com.carrental.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.regex.Pattern;

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

    // ZeptoMail's console shows the send token pre-prefixed as "Zoho-enczapikey <secret>".
    // Operators frequently paste that whole string into ZEPTOMAIL_API_TOKEN, which would
    // otherwise double the prefix on the wire ("Zoho-enczapikey Zoho-enczapikey <secret>")
    // and ZeptoMail responds to that with an opaque 5xx rather than a clear 401. Strip the
    // prefix (case-insensitive) if present so the header is correct either way.
    private static final Pattern TOKEN_PREFIX_PATTERN = Pattern.compile("^zoho-enczapikey\\s+", Pattern.CASE_INSENSITIVE);

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

    /** The exact endpoint every send POSTs to — safe to log/expose. */
    public String endpoint() {
        return baseUrl() + "/v1.1/email";
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

    /** True if the configured token had an embedded "Zoho-enczapikey" prefix that was stripped. */
    public boolean tokenPrefixWasDuplicated() {
        return StringUtils.hasText(apiToken) && TOKEN_PREFIX_PATTERN.matcher(apiToken.strip()).find();
    }

    /** The bare secret, with any accidental "Zoho-enczapikey" prefix/whitespace removed — never logged. */
    private String normalizedToken() {
        String trimmed = apiToken == null ? "" : apiToken.strip();
        return TOKEN_PREFIX_PATTERN.matcher(trimmed).replaceFirst("");
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

        String url = endpoint();
        String recipientDomain = domainOf(message.to());
        try {
            String requestBody = buildZeptoMailRequestBody(message);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // ZeptoMail's own auth scheme — the token itself is never logged.
                    .header("Authorization", "Zoho-enczapikey " + normalizedToken())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 200 and 201 both indicate ZeptoMail accepted the message.
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("[EMAIL_API] method=POST url={} status={} sender={} recipientDomain={} subject={}",
                        url, response.statusCode(), fromEmail, recipientDomain, message.subject());
                return SmtpMailService.SmtpResult.success(label());
            }

            ZeptoMailError parsed = parseError(response.body());
            String errorCode = classifyError(response.statusCode(), parsed);
            log.warn("[EMAIL_API] method=POST url={} status={} errorCode={} zeptoCode={} zeptoMessage={} requestId={} sender={} recipientDomain={}",
                    url, response.statusCode(), errorCode, parsed.code(), parsed.message(), parsed.requestId(),
                    fromEmail, recipientDomain);
            String resultMessage = StringUtils.hasText(parsed.message())
                    ? parsed.message()
                    : "Email API returned HTTP " + response.statusCode();
            return SmtpMailService.SmtpResult.failure(label(), resultMessage, errorCode);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[EMAIL_API] method=POST url={} sender={} recipientDomain={} timed out", url, fromEmail, recipientDomain);
            return SmtpMailService.SmtpResult.failure(label(), "Email API request timed out.", "EMAIL_API_TIMEOUT");
        } catch (java.net.UnknownHostException | java.net.ConnectException | javax.net.ssl.SSLException e) {
            // DNS resolution failure, connection refused, or TLS handshake failure —
            // a network-layer problem, not a ZeptoMail application-layer response.
            log.warn("[EMAIL_API] method=POST url={} sender={} recipientDomain={} network error: exceptionClass={} message={}",
                    url, fromEmail, recipientDomain, e.getClass().getName(), e.getMessage());
            return SmtpMailService.SmtpResult.failure(label(), "Could not reach the email provider (network error).", "EMAIL_API_NETWORK_ERROR", e.getClass().getName());
        } catch (Exception e) {
            log.warn("[EMAIL_API] method=POST url={} sender={} recipientDomain={} failed: exceptionClass={} message={}",
                    url, fromEmail, recipientDomain, e.getClass().getName(), e.getMessage());
            return SmtpMailService.SmtpResult.failure(label(), e.getMessage(), "EMAIL_API_SEND_FAILED", e.getClass().getName());
        }
    }

    /** Safe-to-log slice of ZeptoMail's structured error response — never includes the token. */
    private record ZeptoMailError(String code, String message, String requestId) {
        static ZeptoMailError empty() {
            return new ZeptoMailError(null, null, null);
        }
    }

    /**
     * ZeptoMail error bodies look like:
     * {"error": {"code": "TM_3301", "message": "...", "details": [...] }, "message": "...", "request_id": "..."}
     * Some responses put the code/message at the top level instead of nested under "error" —
     * both shapes are handled defensively since the exact shape isn't guaranteed per status.
     */
    private ZeptoMailError parseError(String body) {
        if (!StringUtils.hasText(body)) return ZeptoMailError.empty();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorNode = root.path("error");
            String code = errorNode.path("code").asText(root.path("code").asText(null));
            String message = errorNode.path("message").asText(root.path("message").asText(null));
            if (!StringUtils.hasText(message) && errorNode.path("details").isArray() && !errorNode.path("details").isEmpty()) {
                message = errorNode.path("details").get(0).path("message").asText(null);
            }
            String requestId = root.path("request_id").asText(null);
            return new ZeptoMailError(code, message, requestId);
        } catch (Exception e) {
            // Not JSON, or an unexpected shape — fall back to a generic classification by status only.
            return ZeptoMailError.empty();
        }
    }

    private String classifyError(int status, ZeptoMailError error) {
        if (status == 400 || status == 403) {
            // Both a rejected sender/domain and a rejected token can come back as 400/403 —
            // the status code alone can't tell them apart, only the body's code/message can.
            if (isSenderRejection(error)) return "EMAIL_SENDER_NOT_VERIFIED";
        }
        if (status == 401 || status == 403) return "EMAIL_API_UNAUTHORIZED";
        if (status == 404) return "EMAIL_API_ENDPOINT_INVALID";
        if (status == 429) return "EMAIL_API_RATE_LIMITED";
        if (status >= 500) return "EMAIL_API_PROVIDER_UNAVAILABLE";
        if (status == 400) return "EMAIL_API_INVALID_PAYLOAD";
        return "EMAIL_API_REQUEST_REJECTED";
    }

    private boolean isSenderRejection(ZeptoMailError error) {
        String text = ((error.code() != null ? error.code() : "") + " " + (error.message() != null ? error.message() : "")).toLowerCase();
        return text.contains("sender") || text.contains("from address") || text.contains("mail_agent")
                || text.contains("domain") || text.contains("not verified") || text.contains("unverified");
    }

    /** The part of the recipient address after '@' — safe to log, never the full address. */
    private String domainOf(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at >= 0 && at < email.length() - 1 ? email.substring(at + 1) : null;
    }

    private String buildZeptoMailRequestBody(EmailMessage message) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode from = root.putObject("from");
        from.put("address", fromEmail);
        if (StringUtils.hasText(fromName)) from.put("name", fromName);

        ArrayNode toArray = root.putArray("to");
        ObjectNode toEntry = toArray.addObject();
        toEntry.putObject("email_address").put("address", message.to());

        // ZeptoMail rejects a blank subject as a malformed payload — never send "".
        root.put("subject", StringUtils.hasText(message.subject()) ? message.subject() : "Notification from Innovacar");
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
