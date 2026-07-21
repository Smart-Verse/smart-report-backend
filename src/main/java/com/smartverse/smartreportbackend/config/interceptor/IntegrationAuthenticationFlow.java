package com.smartverse.smartreportbackend.config.interceptor;

import com.potatotech.authorization.exception.ServiceException;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.services.apikey.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IntegrationAuthenticationFlow {
    private final ApiKeyService apiKeyService;
    private final DBMigration dbMigration;

    public IntegrationAuthenticationFlow(ApiKeyService apiKeyService, DBMigration dbMigration) {
        this.apiKeyService = apiKeyService;
        this.dbMigration = dbMigration;
    }

    public boolean hasCredentials(HttpServletRequest request) {
        var apiKey = request.getHeader(ApiKeyService.HEADER_NAME);
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean authenticate(HttpServletRequest request) {
        if (!isGenerateReportRequest(request)) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "API key scope only allows report generation");
        }

        var authenticatedKey = apiKeyService.authenticate(request.getHeader(ApiKeyService.HEADER_NAME));
        activateTenant(authenticatedKey.tenant());
        return true;
    }

    private boolean isGenerateReportRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/generateReport");
    }

    private void activateTenant(String tenant) {
        TenantContext.setCurrentTenant(tenant);
        dbMigration.loadMigrateTenants(tenant);
    }
}
