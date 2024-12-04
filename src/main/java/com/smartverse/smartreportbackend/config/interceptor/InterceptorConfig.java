package com.smartverse.smartreportbackend.config.interceptor;


import com.potatotech.authorization.exception.ServiceException;
import com.potatotech.authorization.security.Authenticate;
import com.potatotech.authorization.tenant.TenantConfiguration;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.context.EnumConfigContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import feign.Request;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig extends Authenticate implements HandlerInterceptor, WebMvcConfigurer  {


    @Autowired
    DBMigration dbMigration;
    private static final String AUTHORIZATION = "Authorization";
    private static final String TENANT = "Xtenant";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        var tenantConfiguration = new TenantConfiguration();

        String uri = request.getRequestURI();

        if(validateDomainsAllowAccess(uri)){
            return true;
        }

        if(isOptions(request)){
            return true;
        }
        var auth = request.getHeader(AUTHORIZATION);
        var tenant = request.getHeader(TENANT);

        if(!tenantConfiguration.validAnonymous(handler)){
            var user = this.isAuthenticated(auth);
            TenantContext.setCurrentTenant(user.getTenant());
            tenant = user.getTenant();
        } else {
            if(tenant == null){
                throw new ServiceException(HttpStatus.FORBIDDEN,"tenant is required");
            }
            TenantContext.setCurrentTenant(tenant);
        }
        dbMigration.loadMigrateTenants(tenant);
        return true;
    }

    private boolean validateDomainsAllowAccess(String uri) {

        // valida swagger
        if(uri.startsWith("/"+System.getenv(EnumConfigContext.SERVICE_NAME.name())+"/swagger-ui/") || uri.startsWith("/"+System.getenv(EnumConfigContext.SERVICE_NAME.name())+"/v3/")) {
            return true;
        } // valida login e register
        else if(uri.startsWith("/"+System.getenv(EnumConfigContext.SERVICE_NAME.name())+"/authenticate") || uri.startsWith("/"+System.getenv(EnumConfigContext.SERVICE_NAME.name())+"/register")) {
            TenantContext.setCurrentTenant("admin");
            dbMigration.loadMigrateTenants("admin");
            return true;
        }
        else if(uri.startsWith("/church-lite/error")) {
            return true;
        }
        else {
            return false;
        }
    }


    private boolean isOptions(HttpServletRequest request){
        return Request.HttpMethod.OPTIONS.equals(request.getMethod());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(this);
    }
}
