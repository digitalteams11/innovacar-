package com.carrental.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcFeatureConfig implements WebMvcConfigurer {

    private final FeatureAccessInterceptor featureAccessInterceptor;
    private final RolePermissionInterceptor rolePermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
