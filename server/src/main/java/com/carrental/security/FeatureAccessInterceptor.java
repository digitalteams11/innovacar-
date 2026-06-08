package com.carrental.security;

import com.carrental.service.FeatureAccessService;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeatureAccessInterceptor implements HandlerInterceptor {

    private final FeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> PATH_FEATURES = new LinkedHashMap<>();

    static {
        PATH_FEATURES.put("/api/vehicles", "VEHICLE_MANAGEMENT");
        PATH_FEATURES.put("/api/clients", "CLIENT_MANAGEMENT");
        PATH_FEATURES.put("/api/reservations", "RESERVATION_MANAGEMENT");
        PATH_FEATURES.put("/api/contracts", "CONTRACT_MANAGEMENT");
        PATH_FEATURES.put("/api/invoices", "INVOICE_GENERATION");
        PATH_FEATURES.put("/api/employees", "MULTI_EMPLOYEE");
        PATH_FEATURES.put("/api/reports", "REPORTS_BASIC");
        PATH_FEATURES.put("/api/gps", "GPS_TRACKING");
        PATH_FEATURES.put("/api/white-label", "WHITE_LABEL");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isSuperAdmin(authentication)) {
            return true;
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

        Map<String, Object> feature = featureAccessService.checkCurrentTenantFeature(featureCode);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "FEATURE_NOT_AVAILABLE",
                "message", feature.get("name") + " is not included in the current subscription plan",
                "feature", feature
        ));
        return false;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
