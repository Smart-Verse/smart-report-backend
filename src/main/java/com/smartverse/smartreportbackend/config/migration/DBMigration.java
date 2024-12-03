package com.smartverse.smartreportbackend.config.migration;

import com.smartverse.smartreportbackend.config.context.ConfigContextImpl;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DBMigration {

    @Autowired
    ConfigContextImpl configContext;

    private static List<String> tenants = new ArrayList<>();

    @Bean
    public void initializeFlyway(){
        var fly = Flyway.configure()
                .locations("classpath:db/migration")
                .dataSource(configContext.getUrl(),configContext.getUsername(),configContext.getPasswod())
                .createSchemas(true)
                .load();
        fly.migrate();

    }


    public void loadMigrateTenants(String tenant){

        if(!tenants.contains(tenant)){

            var fly = Flyway.configure()
                    .locations("classpath:db/migration")
                    .dataSource(configContext.getUrl(),configContext.getUsername(),configContext.getPasswod())
                    .createSchemas(true)
                    .schemas(configContext.getDatabase().toUpperCase().concat("_").concat(tenant.toUpperCase()))
                    .load();
            fly.migrate();

            tenants.add(tenant);
        }
    }
}
