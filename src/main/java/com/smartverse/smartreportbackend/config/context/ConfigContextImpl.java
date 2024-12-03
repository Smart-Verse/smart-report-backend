package com.smartverse.smartreportbackend.config.context;

import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class ConfigContextImpl implements ConfigContext {

    @Override
    public String getUrl() {
        return switch (System.getenv(EnumConfigContext.DB_NAME.name())){
            case "POSTGRES" -> "jdbc:postgresql://localhost:5434/"+this.getDatabase();
            case "MYSQL" -> "jdbc:postgresql://localhost:5432/"+this.getDatabase();
            case "SQLSERVER" -> "jdbc:postgresql://localhost:5432/"+this.getDatabase();
            default -> "";

        };
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
}
