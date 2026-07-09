package com.carrental.security;

import com.carrental.entity.AuditLog;
import com.carrental.entity.User;
import com.carrental.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final Pattern NUMERIC_ID = Pattern.compile("/(\\d+)(?:/|$)");

    private final AuditLogRepository auditLogRepository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (!isMutation(request.getMethod())) return;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) return;

        try {
            String path = request.getRequestURI();
            boolean successful = exception == null && response.getStatus() < 400;
            auditLogRepository.save(AuditLog.builder()
                    .action(resolveAction(request.getMethod(), path))
                    .entityType(resolveEntityType(path))
                    .entityId(resolveEntityId(path))
                    .description(request.getMethod() + " " + path)
                    .performedBy(user.getEmail())
                    .performedById(user.getId())
                    .tenantId(user.getTenant().getId())
                    .ipAddress(resolveClientIp(request))
                    .userAgent(limit(request.getHeader("User-Agent"), 500))
                    .isSuccess(successful)
                    .errorMessage(successful ? null : failureSummary(response, exception))
                    .build());
        } catch (Exception auditFailure) {
            log.error("Unable to persist audit record for {} {}", request.getMethod(),
                    request.getRequestURI(), auditFailure);
        }
    }

    private boolean isMutation(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String resolveAction(String method, String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.contains("/sign")) return "SIGN";
        if (normalized.contains("/refund")) return "REFUND";
        if (normalized.contains("/verify")) return "VERIFY";
        if (normalized.contains("/restore")) return "RESTORE";
        if (normalized.contains("/revoke") || "DELETE".equals(method)
                && normalized.contains("/sessions/")) return "REVOKE";
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
    }

    private String resolveEntityType(String path) {
        String normalized = path.replaceFirst("^/api/", "");
        String firstSegment = normalized.split("/")[0];
        return firstSegment.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private Long resolveEntityId(String path) {
        Matcher matcher = NUMERIC_ID.matcher(path);
        Long last = null;
        while (matcher.find()) last = Long.valueOf(matcher.group(1));
        return last;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return limit(forwarded.split(",")[0].trim(), 64);
        }
        return limit(request.getRemoteAddr(), 64);
    }

    private String failureSummary(HttpServletResponse response, Exception exception) {
        if (exception != null) return limit(exception.getClass().getSimpleName(), 255);
        return "HTTP " + response.getStatus();
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}
