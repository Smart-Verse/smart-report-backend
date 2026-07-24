package com.smartverse.smartreportbackend.config.interceptor;

import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import com.smartverse.smartreportbackend_gen.authorization.security.Authenticate;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantConfiguration;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend_gen.enums.EnumConfigContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApplicationAuthenticationFlow extends Authenticate {
    private static final String ADMIN_TENANT = "admin";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TENANT_HEADER = "Xtenant";

    private final DBMigration dbMigration;

    public ApplicationAuthenticationFlow(DBMigration dbMigration) {
        this.dbMigration = dbMigration;
    }

    public boolean authenticate(HttpServletRequest request, Object handler) {
        var uri = request.getRequestURI();
        if (allowPublicApplicationRoute(uri)) return true;

        var anonymous = new TenantConfiguration().validAnonymous(handler);
        String tenant;
        if (anonymous) {
            tenant = request.getHeader(TENANT_HEADER);
            if (tenant == null || tenant.isBlank()) {
                throw new ServiceException(HttpStatus.FORBIDDEN, "tenant is required");
            }
        } else {
            tenant = isAuthenticated(request.getHeader(AUTHORIZATION_HEADER)).getTenant();
        }

        activateTenant(tenant);
        return true;
    }

    private boolean allowPublicApplicationRoute(String uri) {
        var servicePath = "/" + System.getenv(EnumConfigContext.SERVICE_NAME.name());
        if (uri.startsWith(servicePath + "/swagger-ui/")
                || uri.startsWith(servicePath + "/v3/")
                || uri.startsWith(servicePath + "/error")) {
            return true;
        }

        if (uri.startsWith(servicePath + "/authenticate")
                || uri.startsWith(servicePath + "/register")
                || uri.startsWith(servicePath + "/verifyURL")
                || uri.startsWith(servicePath + "/resendConfirmation")) {
            activateTenant(ADMIN_TENANT);
            return true;
        }
        return false;
    }

    private void activateTenant(String tenant) {
        TenantContext.setCurrentTenant(tenant);
        dbMigration.loadMigrateTenants(tenant);
    }
}
