package com.example.DPMHC_backend.config.database.health;

import com.example.DPMHC_backend.config.database.DatabaseProperties;
import com.example.DPMHC_backend.config.database.DatabaseType;
import com.example.DPMHC_backend.config.database.routing.DatabaseLoadBalancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for monitoring database health and managing failover
 * Only enabled for development profile (master-slave setup)
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthMonitor {
    
    private final DatabaseLoadBalancer loadBalancer;
    private final DatabaseProperties databaseProperties;
    
    private Map<DatabaseType, DataSource> dataSources;
    private final Map<DatabaseType, AtomicInteger> failureCount = new ConcurrentHashMap<>();
    private final Map<DatabaseType, AtomicInteger> recoveryCount = new ConcurrentHashMap<>();
    private final Map<DatabaseType, Long> lastHealthCheckTime = new ConcurrentHashMap<>();
    
    /**
     * Initialize health monitoring for all databases
     */
    public void initializeHealthMonitoring(Map<DatabaseType, DataSource> dataSources) {
        this.dataSources = dataSources;
        
        for (DatabaseType type : DatabaseType.values()) {
            failureCount.put(type, new AtomicInteger(0));
            recoveryCount.put(type, new AtomicInteger(0));
            lastHealthCheckTime.put(type, System.currentTimeMillis());
        }
        
        log.info("Database health monitoring initialized for {} databases", dataSources.size());
    }
    
    /**
     * Scheduled health check for all databases
     */
    @Scheduled(fixedDelayString = "#{@databaseProperties.healthCheck.intervalMs}")
    public void performHealthChecks() {
        if (dataSources == null || !databaseProperties.getHealthCheck().isEnabled()) {
            return;
        }
        
        log.debug("Performing scheduled health checks");
        
        for (Map.Entry<DatabaseType, DataSource> entry : dataSources.entrySet()) {
            DatabaseType type = entry.getKey();
            DataSource dataSource = entry.getValue();
            
            boolean isHealthy = checkDatabaseHealth(type, dataSource);
            updateHealthStatus(type, isHealthy);
        }
    }
    
    /**
     * Check health of a specific database
     */
    public boolean checkDatabaseHealth(DatabaseType type, DataSource dataSource) {
        if (dataSource == null) {
            log.warn("DataSource is null for database: {}", type);
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            if (connection == null || connection.isClosed()) {
                log.warn("Unable to establish connection to database: {}", type);
                return false;
            }
            
            // Execute health check query
            String testQuery = databaseProperties.getConnectionPool().getConnectionTestQuery();
            try (PreparedStatement statement = connection.prepareStatement(testQuery)) {
                statement.setQueryTimeout(databaseProperties.getHealthCheck().getTimeoutMs() / 1000);
                statement.executeQuery();
            }
            
            long responseTime = System.currentTimeMillis() - startTime;
            log.debug("Database {} health check passed in {}ms", type, responseTime);
            
            return true;
            
        } catch (SQLException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("Database {} health check failed in {}ms: {}", type, responseTime, e.getMessage());
            return false;
        }
    }
    
    /**
     * Update health status based on check result
     */
    private void updateHealthStatus(DatabaseType type, boolean isHealthy) {
        boolean currentStatus = loadBalancer.isHealthy(type);
        lastHealthCheckTime.put(type, System.currentTimeMillis());
        
        if (isHealthy) {
            // Reset failure count and increment recovery count
            failureCount.get(type).set(0);
            int recoveries = recoveryCount.get(type).incrementAndGet();
            
            // Mark as healthy if not already or if recovery threshold is met
            if (!currentStatus) {
                if (recoveries >= databaseProperties.getHealthCheck().getRecoveryThreshold()) {
                    loadBalancer.updateHealthStatus(type, true);
                    recoveryCount.get(type).set(0);
                    log.info("Database {} marked as healthy after {} recovery checks", type, recoveries);
                }
            } else {
                // Already healthy, reset recovery count
                recoveryCount.get(type).set(0);
            }
        } else {
            // Reset recovery count and increment failure count
            recoveryCount.get(type).set(0);
            int failures = failureCount.get(type).incrementAndGet();
            
            // Mark as unhealthy if failure threshold is met
            if (currentStatus && failures >= databaseProperties.getHealthCheck().getFailureThreshold()) {
                loadBalancer.updateHealthStatus(type, false);
                failureCount.get(type).set(0);
                log.warn("Database {} marked as unhealthy after {} failed checks", type, failures);
                
                // Send alert for critical database failure
                sendHealthAlert(type, false);
            }
        }
    }
    
    /**
     * Force health check for a specific database
     */
    public boolean forceHealthCheck(DatabaseType type) {
        if (dataSources == null || !dataSources.containsKey(type)) {
            log.warn("Cannot perform health check for unknown database: {}", type);
            return false;
        }
        
        log.info("Forcing health check for database: {}", type);
        boolean isHealthy = checkDatabaseHealth(type, dataSources.get(type));
        updateHealthStatus(type, isHealthy);
        
        return isHealthy;
    }
    
    /**
     * Get health statistics for a database
     */
    public HealthStats getHealthStats(DatabaseType type) {
        return HealthStats.builder()
                .databaseType(type)
                .isHealthy(loadBalancer.isHealthy(type))
                .failureCount(failureCount.getOrDefault(type, new AtomicInteger(0)).get())
                .recoveryCount(recoveryCount.getOrDefault(type, new AtomicInteger(0)).get())
                .lastHealthCheckTime(lastHealthCheckTime.getOrDefault(type, 0L))
                .connectionCount(loadBalancer.getConnectionCount(type))
                .build();
    }
    
    /**
     * Get health statistics for all databases
     */
    public Map<DatabaseType, HealthStats> getAllHealthStats() {
        Map<DatabaseType, HealthStats> stats = new ConcurrentHashMap<>();
        
        for (DatabaseType type : DatabaseType.values()) {
            stats.put(type, getHealthStats(type));
        }
        
        return stats;
    }
    
    /**
     * Manually mark a database as healthy or unhealthy
     */
    public void setDatabaseHealth(DatabaseType type, boolean healthy) {
        log.info("Manually setting database {} health to: {}", type, healthy);
        loadBalancer.updateHealthStatus(type, healthy);
        
        if (healthy) {
            failureCount.get(type).set(0);
            recoveryCount.get(type).set(0);
        }
    }
    
    /**
     * Send health alert (implement based on your notification system)
     */
    private void sendHealthAlert(DatabaseType type, boolean isHealthy) {
        // This could integrate with your notification system
        String status = isHealthy ? "RECOVERED" : "FAILED";
        String message = String.format("Database %s health status: %s", type, status);
        
        log.warn("HEALTH ALERT: {}", message);
        
        // TODO: Integrate with email, Slack, or other notification systems
        // notificationService.sendAlert(message);
    }
    
    /**
     * Health statistics data class
     */
    @lombok.Builder
    @lombok.Data
    public static class HealthStats {
        private DatabaseType databaseType;
        private boolean isHealthy;
        private int failureCount;
        private int recoveryCount;
        private long lastHealthCheckTime;
        private int connectionCount;
        private long responseTimeMs;
    }
}