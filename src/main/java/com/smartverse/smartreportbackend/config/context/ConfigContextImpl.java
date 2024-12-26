package com.smartverse.smartreportbackend.config.context;

import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class ConfigContextImpl implements ConfigContext {

    @Override
    public String getUrl() {
        return "jdbc:postgresql://"+ this.getHost()+":"+ this.getDatabasePort() +"/"+this.getDatabase();
    }

    @Override
    public String getUsername() {
        return System.getenv(EnumConfigContext.DB_USERNAME.name());
    }

    @Override
    public String getPasswod() {
        return System.getenv(EnumConfigContext.DB_PASSWORD.name());
    }

    @Override
    public String getDatabase() {
        return System.getenv(EnumConfigContext.DATABASE_SCHEMA_NAME.name());
    }

    @Override
    public String getHost() {
        return  System.getenv(EnumConfigContext.SERVER_HOST.name()) == null ? "localhost" : System.getenv(EnumConfigContext.SERVER_HOST.name());
    }

    @Override
    public String getDatabasePort() {
        return System.getenv(EnumConfigContext.DB_PORT.name()) == null ? "5434" : System.getenv(EnumConfigContext.DB_PORT.name());
    }
}
