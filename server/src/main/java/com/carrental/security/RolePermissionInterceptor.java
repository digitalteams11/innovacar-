package com.carrental.security;

import com.carrental.service.RolePermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RolePermissionInterceptor implements HandlerInterceptor {
    private final RolePermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String permission = resolvePermission(request.getMethod(), request.getRequestURI());
        if (permission == null || permissionService.has(permission)) return true;

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "PERMISSION_DENIED");
        body.put("message", "Your role does not allow this action.");
        body.put("permission", permission);
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }

    String resolvePermission(String method, String path) {
        if (path.startsWith("/api/vehicles")) return action(method, "VEHICLE_VIEW", "VEHICLE_CREATE", "VEHICLE_UPDATE", "VEHICLE_DELETE");
        if (path.startsWith("/api/clients")) return action(method, "CLIENT_VIEW", "CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE");
        if (path.startsWith("/api/reservations")) return action(method, "RESERVATION_VIEW", "RESERVATION_CREATE", "RESERVATION_UPDATE", "RESERVATION_CANCEL");
        if (path.startsWith("/api/contracts")) {
            if (path.endsWith("/sign") || path.endsWith("/qr")) return "CONTRACT_QR_SIGNATURE";
            if (path.endsWith("/complete") || path.endsWith("/finalize")) return "CONTRACT_UPDATE";
            return action(method, "CONTRACT_VIEW", "CONTRACT_CREATE", "CONTRACT_UPDATE", "CONTRACT_DELETE");
        }
        if (path.startsWith("/api/payments")) return "GET".equals(method) ? "PAYMENT_VIEW" : "PAYMENT_CREATE";
        if (path.startsWith("/api/invoices")) return "GET".equals(method) ? "INVOICE_VIEW" : "INVOICE_EXPORT";
        if (path.startsWith("/api/deposits")) return "GET".equals(method) ? "VIEW_DEPOSITS" : "MANAGE_DEPOSITS";
        if (path.startsWith("/api/reports")) return "REPORT_VIEW";
        if (path.startsWith("/api/gps/settings") || path.startsWith("/api/gps-settings")) {
            return "GET".equals(method) ? "GPS_VIEW" : "GPS_SETTINGS";
        }
        if (path.startsWith("/api/gps")) return "GPS_VIEW";
        if (path.startsWith("/api/maintenance")) return "GET".equals(method) ? "VEHICLE_VIEW" : "VEHICLE_MAINTENANCE_MANAGE";
        if (path.startsWith("/api/employees")) return action(method, "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_UPDATE", "EMPLOYEE_DELETE");
        if (path.startsWith("/api/white-label")) return "AGENCY_SETTINGS_UPDATE";
        if (path.startsWith("/api/agency")) return "GET".equals(method) ? "AGENCY_SETTINGS_VIEW" : "AGENCY_SETTINGS_UPDATE";
        return null;
    }

    private String action(String method, String read, String create, String update, String delete) {
        return switch (method) {
            case "POST" -> create;
            case "PUT", "PATCH" -> update;
            case "DELETE" -> delete;
            default -> read;
        };
    }
}
