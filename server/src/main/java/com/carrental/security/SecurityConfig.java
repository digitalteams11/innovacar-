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

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174,http://192.168.*.*:5173,http://192.168.*.*:5174,http://192.168.194.1:5174}")
    private String allowedOrigins;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

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
        warnIfProductionOriginsLookLocal();

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
                "X-Device-Id"));
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
     * Logs (never throws — a CORS misconfiguration breaks the frontend, not the
     * server) a clear warning if this looks like a production/Railway deploy
     * whose CORS_ALLOWED_ORIGINS still defaults to the LAN-dev list, or doesn't
     * include the configured app.frontend-url host. This is the same signal a
     * browser console CORS error would show, just surfaced in server logs first.
     */
    private void warnIfProductionOriginsLookLocal() {
        boolean onRailway = System.getenv("RAILWAY_ENVIRONMENT_NAME") != null
                || System.getenv("RAILWAY_PROJECT_ID") != null
                || System.getenv("RAILWAY_SERVICE_ID") != null;
        boolean prodProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!onRailway && !prodProfile) {
            return;
        }

        String frontendHost = null;
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            try {
                frontendHost = java.net.URI.create(frontendUrl).getHost();
            } catch (Exception ignored) {
                // leave frontendHost null — handled below
            }
        }

        boolean originsLookLocal = allowedOrigins.toLowerCase(java.util.Locale.ROOT).contains("localhost")
                || allowedOrigins.contains("192.168.")
                || allowedOrigins.contains("127.0.0.1");
        boolean containsFrontendHost = frontendHost != null && allowedOrigins.contains(frontendHost);

        if (originsLookLocal || !containsFrontendHost) {
            log.warn("[CORS_CONFIG_WARNING] onRailway={} prodProfile={} app.frontend-url={} — "
                    + "app.cors.allowed-origins does not look production-ready: '{}'. "
                    + "Set CORS_ALLOWED_ORIGINS to the exact production origin(s), comma-separated, "
                    + "no wildcards — e.g. https://innovacar.app,https://www.innovacar.app",
                    onRailway, prodProfile, frontendUrl, allowedOrigins);
        }
    }
}
