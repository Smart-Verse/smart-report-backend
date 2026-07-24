package com.smartverse.smartreportbackend.config.interceptor;

import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.services.apikey.ApiKeyBusinessService;
import com.smartverse.smartreportbackend.services.plan.PlanBusinessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IntegrationAuthenticationFlow {
    private final ApiKeyBusinessService apiKeyService;
    private final DBMigration dbMigration;
    private final PlanBusinessService planService;

    public IntegrationAuthenticationFlow(ApiKeyBusinessService apiKeyService, DBMigration dbMigration,
                                         PlanBusinessService planService) {
        this.apiKeyService = apiKeyService;
        this.dbMigration = dbMigration;
        this.planService = planService;
    }

    public boolean hasCredentials(HttpServletRequest request) {
        var apiKey = request.getHeader(ApiKeyBusinessService.HEADER_NAME);
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean authenticate(HttpServletRequest request) {
        if (!isGenerateReportRequest(request)) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "API key scope only allows report generation");
        }

        var authenticatedKey = apiKeyService.authenticate(request.getHeader(ApiKeyBusinessService.HEADER_NAME));
        planService.consumeApiRequest(authenticatedKey.tenant());
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
