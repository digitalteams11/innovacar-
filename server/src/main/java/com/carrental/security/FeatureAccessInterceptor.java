package com.carrental.security;

import com.carrental.service.FeatureAccessService;
import com.carrental.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intercepts API requests and blocks access to features not enabled in the
 * tenant's current subscription plan.
 *
 * <p>More specific paths MUST appear before less specific ones in PATH_FEATURES
 * because matching uses startsWith() with findFirst().
 */
@Component
@RequiredArgsConstructor
public class FeatureAccessInterceptor implements HandlerInterceptor {

    private final FeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;

    // Ordered map: more-specific paths first so startsWith() matches correctly
    private static final Map<String, String> PATH_FEATURES = new LinkedHashMap<>();

    static {
        // ── GPS (specific before general) ─────────────────────────────────
        PATH_FEATURES.put("/api/gps/alerts",   "GPS_ALERTS");
        PATH_FEATURES.put("/api/gps",          "GPS_TRACKING");

        // ── AI (specific before general) ──────────────────────────────────
        PATH_FEATURES.put("/api/ai/reports",   "AI_REPORTS");
        PATH_FEATURES.put("/api/ai/translate", "AI_TRANSLATIONS");
        PATH_FEATURES.put("/api/ai",           "AI_ASSISTANT");

        // ── Core modules ──────────────────────────────────────────────────
        PATH_FEATURES.put("/api/vehicles",      "VEHICLE_MANAGEMENT");
        PATH_FEATURES.put("/api/clients",       "CLIENT_MANAGEMENT");
        PATH_FEATURES.put("/api/reservations",  "RESERVATION_MANAGEMENT");
        PATH_FEATURES.put("/api/contracts",     "CONTRACT_MANAGEMENT");
        PATH_FEATURES.put("/api/invoices",      "INVOICE_GENERATION");
        PATH_FEATURES.put("/api/payments",      "PAYMENTS");
        PATH_FEATURES.put("/api/employees",     "MULTI_EMPLOYEE");
        PATH_FEATURES.put("/api/reports",       "REPORTS_BASIC");
        PATH_FEATURES.put("/api/white-label",   "WHITE_LABEL");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isSuperAdmin(authentication)) {
            return true;
        }
        if (authentication.getPrincipal() instanceof User user && user.getTenant() == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "errorCode", "TENANT_REQUIRED",
                    "message", "Agency account is not linked to a tenant.",
                    "data", null
            ));
            return false;
        }

        String uri = request.getRequestURI();
        String featureCode = PATH_FEATURES.entrySet().stream()
                .filter(entry -> uri.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (featureCode == null || featureAccessService.isEnabledForCurrentTenant(featureCode)) {
            return true;
        }

        // Build error response matching the required format
        Map<String, Object> featureInfo = featureAccessService.checkCurrentTenantFeature(featureCode);

        @SuppressWarnings("unchecked")
        List<String> requiredPlans = featureInfo.get("requiredPlans") instanceof List<?>
                ? (List<String>) featureInfo.get("requiredPlans")
                : List.of("Standard", "Premium");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentPlan",   featureInfo.get("currentPlan"));
        data.put("feature",       featureCode);
        data.put("featureName",   featureInfo.get("name"));
        data.put("requiredPlans", requiredPlans);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success",   false,
                "errorCode", "FEATURE_NOT_INCLUDED_IN_PLAN",
                "message",   "This feature is not included in your current plan.",
                "data",      data
        ));
        return false;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
