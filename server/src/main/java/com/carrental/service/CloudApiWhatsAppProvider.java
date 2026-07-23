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

/**
 * Sends WhatsApp messages via Meta's WhatsApp Cloud API
 * (https://developers.facebook.com/docs/whatsapp/cloud-api). Business-initiated
 * messages (i.e. not a reply within a customer's 24h service window) must go
 * through a pre-approved message template — free-form text is rejected by
 * Meta outside that window — so this always sends a template message with
 * {@code WHATSAPP_TEMPLATE_NAME} / {@code WHATSAPP_TEMPLATE_LANGUAGE}, with the
 * body text (already fully rendered, including the secure link) passed as a
 * single positional parameter. The approved template is expected to have one
 * {{1}} placeholder for that combined body — see WHATSAPP_TEMPLATE_NAME.
 *
 * <p>Non-throwing by design (mirrors {@link HttpEmailProvider}): a missing
 * config or a network/API failure is reported as a result, never an exception,
 * so it can never break the request-creation flow that triggers it.
 */
@Slf4j
@Component
public class CloudApiWhatsAppProvider implements WhatsAppProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_GRAPH_BASE_URL = "https://graph.facebook.com/v20.0";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.graph-base-url:}")
    private String graphBaseUrl;

    // Never logged.
    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.template-name:client_information_request}")
    private String templateName;

    @Value("${app.whatsapp.template-language:fr}")
    private String templateLanguage;

    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId);
    }

    @Override
    public String label() {
        return "WhatsApp Cloud API";
    }

    @Override
    public WhatsAppMessagingService.WhatsAppResult send(WhatsAppMessage message) {
        if (!enabled) {
            return WhatsAppMessagingService.WhatsAppResult.notConfigured(label(), "WhatsApp delivery is disabled (WHATSAPP_ENABLED=false).");
        }
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(phoneNumberId)) {
            return WhatsAppMessagingService.WhatsAppResult.notConfigured(label(), "WhatsApp credentials are not configured.");
        }
        if (!StringUtils.hasText(message.toE164Phone())) {
            return WhatsAppMessagingService.WhatsAppResult.failure(label(), "Recipient phone number is required.", "WHATSAPP_NO_RECIPIENT");
        }

        String url = baseUrl() + "/" + phoneNumberId + "/messages";
        try {
            String requestBody = buildRequestBody(message);
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken.strip())
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("[WHATSAPP_API] method=POST status={} recipientCountryPrefix={}",
                        response.statusCode(), countryPrefixOf(message.toE164Phone()));
                return WhatsAppMessagingService.WhatsAppResult.success(label());
            }

            String errorMessage = parseErrorMessage(response.body());
            String errorCode = classifyError(response.statusCode());
            log.warn("[WHATSAPP_API] method=POST status={} errorCode={} message={}", response.statusCode(), errorCode, errorMessage);
            return WhatsAppMessagingService.WhatsAppResult.failure(label(),
                    StringUtils.hasText(errorMessage) ? errorMessage : "WhatsApp API returned HTTP " + response.statusCode(), errorCode);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[WHATSAPP_API] request timed out");
            return WhatsAppMessagingService.WhatsAppResult.failure(label(), "WhatsApp API request timed out.", "WHATSAPP_API_TIMEOUT");
        } catch (java.net.UnknownHostException | java.net.ConnectException | javax.net.ssl.SSLException e) {
            log.warn("[WHATSAPP_API] network error: exceptionClass={} message={}", e.getClass().getName(), e.getMessage());
            return WhatsAppMessagingService.WhatsAppResult.failure(label(), "Could not reach the WhatsApp provider (network error).", "WHATSAPP_API_NETWORK_ERROR");
        } catch (Exception e) {
            log.warn("[WHATSAPP_API] failed: exceptionClass={} message={}", e.getClass().getName(), e.getMessage());
            return WhatsAppMessagingService.WhatsAppResult.failure(label(), e.getMessage(), "WHATSAPP_API_SEND_FAILED");
        }
    }

    private String baseUrl() {
        return StringUtils.hasText(graphBaseUrl) ? graphBaseUrl : DEFAULT_GRAPH_BASE_URL;
    }

    private String buildRequestBody(WhatsAppMessage message) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", message.toE164Phone().replace("+", ""));
        root.put("type", "template");

        ObjectNode template = root.putObject("template");
        template.put("name", templateName);
        template.putObject("language").put("code", templateLanguage);

        ArrayNode components = template.putArray("components");
        ObjectNode body = components.addObject();
        body.put("type", "body");
        ArrayNode parameters = body.putArray("parameters");
        ObjectNode param = parameters.addObject();
        param.put("type", "text");
        param.put("text", message.body());

        return objectMapper.writeValueAsString(root);
    }

    private String parseErrorMessage(String body) {
        if (!StringUtils.hasText(body)) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            String msg = error.path("error_user_msg").asText(error.path("message").asText(null));
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    private String classifyError(int status) {
        if (status == 401 || status == 403) return "WHATSAPP_API_UNAUTHORIZED";
        if (status == 404) return "WHATSAPP_API_ENDPOINT_INVALID";
        if (status == 429) return "WHATSAPP_API_RATE_LIMITED";
        if (status >= 500) return "WHATSAPP_API_PROVIDER_UNAVAILABLE";
        if (status == 400) return "WHATSAPP_API_INVALID_PAYLOAD";
        return "WHATSAPP_API_REQUEST_REJECTED";
    }

    /** Safe-to-log slice of the recipient — country calling code only, never the full number. */
    private String countryPrefixOf(String e164) {
        if (e164 == null || e164.length() < 4) return null;
        return e164.substring(0, 4);
    }
}
