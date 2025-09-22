package com.example.DPMHC_backend.config.database.routing;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Custom routing data source that selects the appropriate database
 * based on the current thread context
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {
    
    /**
     * Determine which database to use based on the current context
     */
    @Override
    protected Object determineCurrentLookupKey() {
        DatabaseType currentType = DatabaseContextHolder.getDatabaseType();
        
        log.debug("Current database routing key: {}", currentType);
        
        return currentType;
    }
    
    /**
     * Override to add logging for connection acquisition
     */
    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        DatabaseType currentType = DatabaseContextHolder.getDatabaseType();
        log.debug("Acquiring connection for database: {}", currentType);
        
        try {
            java.sql.Connection connection = super.getConnection();
            log.debug("Successfully acquired connection for database: {}", currentType);
            return connection;
        } catch (java.sql.SQLException e) {
            log.error("Failed to acquire connection for database: {} - {}", currentType, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Override to add logging for connection acquisition with username/password
     */
    @Override
    public java.sql.Connection getConnection(String username, String password) throws java.sql.SQLException {
        DatabaseType currentType = DatabaseContextHolder.getDatabaseType();
        log.debug("Acquiring connection with credentials for database: {}", currentType);
        
        try {
            java.sql.Connection connection = super.getConnection(username, password);
            log.debug("Successfully acquired connection with credentials for database: {}", currentType);
            return connection;
        } catch (java.sql.SQLException e) {
            log.error("Failed to acquire connection with credentials for database: {} - {}", currentType, e.getMessage());
            throw e;
        }
    }
}