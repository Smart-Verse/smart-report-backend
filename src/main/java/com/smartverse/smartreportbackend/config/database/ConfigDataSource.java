package com.smartverse.smartreportbackend.config.database;
import com.potatotech.authorization.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class ConfigDataSource  implements CurrentTenantIdentifierResolver, HibernatePropertiesCustomizer {

    public static String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant() != null ?  TenantContext.getCurrentTenant() : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {}

}
