package com.se2026.ai03.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.se2026.ai03.auth.AuthService;
import com.se2026.ai03.auth.AuthUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final AuthService authService;
    private final List<String> allowedOrigins;

    public WebConfig(
            AuthService authService,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.authService = authService;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Audit-Event-Id", "X-Request-Id");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HandlerInterceptor authInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                AuthUser user = authService.authenticate(request.getHeader("Authorization"));
                request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
                return true;
            }
        };
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/health");
    }
}
