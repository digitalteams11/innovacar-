package com.carrental.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcFeatureConfig implements WebMvcConfigurer {

    private final FeatureAccessInterceptor featureAccessInterceptor;
    private final RolePermissionInterceptor rolePermissionInterceptor;
    private final AuditLoggingInterceptor auditLoggingInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadsPath = Path.of("uploads").toAbsolutePath().normalize().toUri().toString();
        if (!uploadsPath.endsWith("/")) {
            uploadsPath = uploadsPath + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLoggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/client-errors"
                );
        registry.addInterceptor(featureAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/features/**",
                        "/api/subscriptions/**",
                        "/api/saas/**",
                        "/api/super-admin/**"
                );
        registry.addInterceptor(rolePermissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/permissions/**",
                        "/api/subscriptions/**",
                        "/api/saas/**",
                        "/api/super-admin/**"
                );
    }
}
