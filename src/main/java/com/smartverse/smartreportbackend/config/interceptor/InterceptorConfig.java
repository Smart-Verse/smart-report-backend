package com.smartverse.smartreportbackend.config.interceptor;

import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements HandlerInterceptor, WebMvcConfigurer {
    private final ApplicationAuthenticationFlow applicationAuthentication;
    private final IntegrationAuthenticationFlow integrationAuthentication;

    public InterceptorConfig(ApplicationAuthenticationFlow applicationAuthentication,
                             IntegrationAuthenticationFlow integrationAuthentication) {
        this.applicationAuthentication = applicationAuthentication;
        this.integrationAuthentication = integrationAuthentication;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (integrationAuthentication.hasCredentials(request)) {
            return integrationAuthentication.authenticate(request);
        }
        return applicationAuthentication.authenticate(request, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        TenantContext.setCurrentTenant(null);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }
}
