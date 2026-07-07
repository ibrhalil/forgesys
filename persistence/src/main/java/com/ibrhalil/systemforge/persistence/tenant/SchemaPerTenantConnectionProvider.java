package com.ibrhalil.systemforge.persistence.tenant;

import com.ibrhalil.systemforge.common.exception.TenantNotFoundException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

public class SchemaPerTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(SchemaPerTenantConnectionProvider.class);
    private static final Pattern SCHEMA_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private static final String DEFAULT_SCHEMA = "public";

    private final DataSource dataSource;

    public SchemaPerTenantConnectionProvider(DataSource dataSource) {
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
        Connection connection = getAnyConnection();
        try {
            setSchemaOnConnection(connection, tenantIdentifier);
            log.debug("Connection search_path set to: {}", tenantIdentifier);
            return connection;
        } catch (SQLException e) {
            releaseAnyConnection(connection);
            throw new TenantNotFoundException("Tenant schema not accessible: " + tenantIdentifier);
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + DEFAULT_SCHEMA);
        } catch (SQLException e) {
            log.warn("Failed to reset search_path on connection release", e);
        }
        releaseAnyConnection(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new org.hibernate.HibernateException("Cannot unwrap " + unwrapType);
    }

    private void setSchemaOnConnection(Connection connection, String tenantIdentifier) throws SQLException {
        if (!isValidSchemaName(tenantIdentifier)) {
            throw new TenantNotFoundException("Invalid tenant schema name: " + tenantIdentifier);
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format("SET search_path TO %s, %s", tenantIdentifier, DEFAULT_SCHEMA));
        }
    }

    private boolean isValidSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return false;
        }
        return SCHEMA_NAME_PATTERN.matcher(schemaName).matches();
    }
}
