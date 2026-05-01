package com.carrental.security;

import lombok.RequiredArgsConstructor;
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
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SubscriptionFilter      subscriptionFilter;
    private final UserDetailsServiceImpl  userDetailsService;

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

            // Route-level access control
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // Actuator health (optional — safe to expose)
                .requestMatchers("/actuator/health").permitAll()
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
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.addAllowedOriginPattern("*"); // Allow all origins for the Electron client
        configuration.addAllowedMethod("*");        // Allow all HTTP methods
        configuration.addAllowedHeader("*");        // Allow all headers
        configuration.setAllowCredentials(true);    // Allow credentials
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
