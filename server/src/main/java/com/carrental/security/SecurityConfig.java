package com.carrental.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

/**
 * Central Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless session (JWT-based)</li>
 *   <li>CSRF disabled (REST API)</li>
 *   <li>Public: {@code /api/auth/**}</li>
 *   <li>All other endpoints require authentication</li>
 *   <li>Method-level security enabled ({@code @PreAuthorize})</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SubscriptionFilter      subscriptionFilter;
    private final UserDetailsServiceImpl  userDetailsService;
    private final org.springframework.core.env.Environment environment;

    @Value("${app.cors.allowed-origins:https://innovacar.app,https://www.innovacar.app,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://192.168.*.*:5173,http://192.168.*.*:5174,http://192.168.194.1:5174}")
    private String allowedOrigins;

    // ── Password encoder ────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Authentication provider ─────────────────────────────────────────────

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ── AuthenticationManager ───────────────────────────────────────────────

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // ── HTTP security ────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS
            .cors(org.springframework.security.config.Customizer.withDefaults())
            
            // Disable CSRF (not needed for stateless REST)
            .csrf(AbstractHttpConfigurer::disable)

            // Response security headers. This API is pure JSON and never
            // meant to be framed/embedded, so the policy here is
            // deliberately restrictive — the real, permissive CSP that
            // reflects what the frontend actually loads (Google Fonts,
            // Google Identity, map tiles, etc.) lives in vercel.json for
            // the frontend origin, not here.
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(63072000))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()")))

            // Stateless session — no HTTP session
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"success\":false,\"error\":\"UNAUTHORIZED\",\"errorCode\":\"UNAUTHORIZED\","
                                    + "\"message\":\"Your session has expired. Please sign in again.\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(org.springframework.http.HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    boolean isSuperAdminRoute = request.getRequestURI().startsWith("/api/super-admin/");
                    String message = isSuperAdminRoute
                            ? "You do not have Super Admin permission."
                            : "You do not have permission to perform this action.";
                    response.getWriter().write(
                            "{\"success\":false,\"error\":\"FORBIDDEN\",\"errorCode\":\"PERMISSION_DENIED\","
                                    + "\"message\":\"" + message + "\",\"data\":null}");
                }))

            // Route-level access control
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/api/auth/**",
                        "/health",
                        "/api/health",
                        "/actuator/health",
                        "/public/branding",
                        "/error",
                        "/api/client-errors",
                        "/api/client-errors/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/webhooks/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/inspections/token/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/inspections/*/upload").permitAll()
                .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                // Everything else requires a valid JWT
                .anyRequest().authenticated())

            // Plug in the JWT filter before Spring's username/password filter
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class)
            // Ensure subscription validity after successful JWT authentication
            .addFilterAfter(subscriptionFilter,
                            JwtAuthenticationFilter.class)


            .authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        logCorsConfig();

        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .forEach(configuration::addAllowedOriginPattern);
        configuration.setAllowedMethods(java.util.List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-Auth-Transport",
                "X-Device-Id",
                "X-Tenant-ID",
                "X-Requested-By"));
        configuration.setExposedHeaders(java.util.List.of(
                "Token-Expired",
                "Content-Disposition",
                "Content-Type",
                "X-Content-Type-Options"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Logs the single canonical CORS configuration line on startup — never the
     * cookies/tokens the app also handles, just the resolved origin list, so a
     * misconfiguration (e.g. the dev-oriented default leaking into a prod
     * deploy) is visible from the very first log line instead of only
     * surfacing as an opaque browser CORS error later.
     */
    private void logCorsConfig() {
        String profile = String.join(",", environment.getActiveProfiles());
        java.util.List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        log.info("[CORS_CONFIG] profile={} allowedOrigins={}",
                profile.isBlank() ? "default" : profile, origins);

        boolean prodProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean originsLookLocal = origins.stream().anyMatch(o ->
                o.toLowerCase(java.util.Locale.ROOT).contains("localhost") || o.contains("192.168."));
        if (prodProfile && originsLookLocal) {
            log.warn("[CORS_CONFIG_WARNING] prod profile is active but allowedOrigins still contains a "
                    + "dev/local origin — set CORS_ALLOWED_ORIGINS to the exact production origins only.");
        }
    }
}
