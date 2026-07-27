package com.carrental.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of SecurityConfig: SecurityConfig's constructor depends (via
 * CustomOidcUserService -> GoogleOAuthService -> TwoFactorService) on
 * PasswordEncoder, so defining it as an instance @Bean method on
 * SecurityConfig itself created a circular reference (SecurityConfig needed
 * to finish constructing before it could produce the bean its own
 * constructor chain required). This class has no dependencies, so the
 * bean is available before any of that chain resolves.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
