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

    private String resolvePermission(String method, String path) {
        if (path.startsWith("/api/vehicles")) return action(method, "VIEW_VEHICLES", "CREATE_VEHICLE", "EDIT_VEHICLE", "DELETE_VEHICLE");
        if (path.startsWith("/api/clients")) return action(method, "VIEW_CLIENTS", "CREATE_CLIENT", "EDIT_CLIENT", "DELETE_CLIENT");
        if (path.startsWith("/api/reservations")) return action(method, "VIEW_RESERVATIONS", "CREATE_RESERVATION", "EDIT_RESERVATION", "CANCEL_RESERVATION");
        if (path.startsWith("/api/contracts")) {
            if (path.endsWith("/sign") || path.endsWith("/qr")) return "SIGN_CONTRACT";
            if (path.endsWith("/complete") || path.endsWith("/finalize")) return "COMPLETE_CONTRACT";
            return action(method, "VIEW_CONTRACTS", "CREATE_CONTRACT", "CREATE_CONTRACT", "CREATE_CONTRACT");
        }
        if (path.startsWith("/api/payments")) return "GET".equals(method) ? "VIEW_PAYMENTS" : "RECORD_PAYMENT";
        if (path.startsWith("/api/invoices")) return "GET".equals(method) ? "VIEW_INVOICES" : "MANAGE_INVOICES";
        if (path.startsWith("/api/reports")) return "VIEW_REPORTS";
        if (path.startsWith("/api/gps")) return "GPS_ACCESS";
        if (path.startsWith("/api/maintenance")) return "GET".equals(method) ? "VIEW_MAINTENANCE" : "MANAGE_MAINTENANCE";
        if (path.startsWith("/api/employees")) return "MANAGE_EMPLOYEES";
        if (path.startsWith("/api/agency")) return "GET".equals(method) ? null : "MANAGE_SETTINGS";
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
