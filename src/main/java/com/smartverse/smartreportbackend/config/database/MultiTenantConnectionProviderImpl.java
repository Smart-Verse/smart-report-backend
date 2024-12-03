package com.smartverse.smartreportbackend.config.database;

import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.context.ConfigContextImpl;
import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class MultiTenantConnectionProviderImpl implements MultiTenantConnectionProvider {

    @Autowired
    ConfigContextImpl configContext;
    private final DataSource dataSource;

    public MultiTenantConnectionProviderImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        final Connection connection = getAnyConnection();
        try {
            connection.createStatement().execute(String.format("SET SCHEMA '%s_%s'",configContext.getDatabase().toUpperCase(), TenantContext.getCurrentTenant().toUpperCase()));
        } catch (SQLException e) {
            throw new HibernateException("Não foi possivel alterar para o schema [" + tenantIdentifier + "]", e);
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (connection) {
            connection.createStatement().execute(String.format("SET SCHEMA '%s'" ,ConfigDataSource.DEFAULT_TENANT));
        } catch (SQLException e) {
            throw new HibernateException("Não foi se conectar ao schema padrão", e);
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class clazz) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        return null;
    }
}
