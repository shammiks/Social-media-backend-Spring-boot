package com.example.DPMHC_backend.config.database.routing;

import com.example.DPMHC_backend.config.database.DatabaseType;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Load balancer for distributing read operations across slave databases
 * Only enabled for development profile (master-slave setup)
 */
@Component
@Profile("dev")
@Slf4j
public class DatabaseLoadBalancer {
    
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<String, Integer> userAffinityMap = new ConcurrentHashMap<>();
    private final Map<DatabaseType, Integer> connectionCounts = new ConcurrentHashMap<>();
    private final Map<DatabaseType, Boolean> healthStatus = new ConcurrentHashMap<>();
    
    // Initialize health status
    {
        healthStatus.put(DatabaseType.SLAVE_1, true);
        healthStatus.put(DatabaseType.SLAVE_2, true);
        connectionCounts.put(DatabaseType.SLAVE_1, 0);
        connectionCounts.put(DatabaseType.SLAVE_2, 0);
    }
    
    /**
     * Select appropriate slave database based on strategy
     */
    public DatabaseType selectSlave(ReadOnlyDB.LoadBalanceStrategy strategy, String userId) {
        // Check if any slaves are healthy
        if (!isAnySlaveHealthy()) {
            log.warn("No healthy slaves available, falling back to master");
            return DatabaseType.MASTER;
        }
        
        return switch (strategy) {
            case ROUND_ROBIN -> roundRobinSelection();
            case USER_SPECIFIC -> userSpecificSelection(userId);
            case LEAST_CONNECTIONS -> leastConnectionsSelection();
            case HEALTH_BASED -> healthBasedSelection();
        };
    }
    
    /**
     * Round robin selection between healthy slaves
     */
    private DatabaseType roundRobinSelection() {
        int attempts = 0;
        while (attempts < 2) { // Max 2 attempts to find healthy slave
            int index = roundRobinCounter.getAndIncrement() % 2;
            DatabaseType selectedType = DatabaseType.getSlaveType(index);
            
            if (isHealthy(selectedType)) {
                log.debug("Round robin selected: {}", selectedType);
                return selectedType;
            }
            attempts++;
        }
        
        log.warn("No healthy slaves found in round robin, falling back to master");
        return DatabaseType.MASTER;
    }
    
    /**
     * User-specific selection for better cache locality
     */
    private DatabaseType userSpecificSelection(String userId) {
        if (userId == null) {
            return roundRobinSelection();
        }
        
        // Get or create user affinity
        int slaveIndex = userAffinityMap.computeIfAbsent(userId, this::calculateUserAffinityIndex);
        
        DatabaseType selectedType = DatabaseType.getSlaveType(slaveIndex);
        
        if (isHealthy(selectedType)) {
            log.debug("User {} routed to: {}", userId, selectedType);
            return selectedType;
        }
        
        // If preferred slave is unhealthy, try the other one
        int alternativeIndex = (slaveIndex + 1) % 2;
        DatabaseType alternativeType = DatabaseType.getSlaveType(alternativeIndex);
        
        if (isHealthy(alternativeType)) {
            log.debug("User {} routed to alternative: {}", userId, alternativeType);
            return alternativeType;
        }
        
        log.warn("No healthy slaves for user {}, falling back to master", userId);
        return DatabaseType.MASTER;
    }
    
    /**
     * Select slave with least connections
     */
    private DatabaseType leastConnectionsSelection() {
        DatabaseType selected = null;
        int minConnections = Integer.MAX_VALUE;
        
        for (DatabaseType type : new DatabaseType[]{DatabaseType.SLAVE_1, DatabaseType.SLAVE_2}) {
            if (isHealthy(type)) {
                int connections = connectionCounts.getOrDefault(type, 0);
                if (connections < minConnections) {
                    minConnections = connections;
                    selected = type;
                }
            }
        }
        
        if (selected != null) {
            log.debug("Least connections selected: {} with {} connections", selected, minConnections);
            return selected;
        }
        
        log.warn("No healthy slaves found for least connections, falling back to master");
        return DatabaseType.MASTER;
    }
    
    /**
     * Health-based selection prioritizing healthier slaves
     */
    private DatabaseType healthBasedSelection() {
        // For now, just return any healthy slave
        // In production, you might want to implement more sophisticated health scoring
        for (DatabaseType type : new DatabaseType[]{DatabaseType.SLAVE_1, DatabaseType.SLAVE_2}) {
            if (isHealthy(type)) {
                log.debug("Health-based selected: {}", type);
                return type;
            }
        }
        
        log.warn("No healthy slaves found in health-based selection, falling back to master");
        return DatabaseType.MASTER;
    }
    
    /**
     * Check if any slave is healthy
     */
    private boolean isAnySlaveHealthy() {
        return isHealthy(DatabaseType.SLAVE_1) || isHealthy(DatabaseType.SLAVE_2);
    }
    
    /**
     * Check health status of a database
     */
    public boolean isHealthy(DatabaseType type) {
        return healthStatus.getOrDefault(type, false);
    }
    
    /**
     * Update health status of a database
     */
    public void updateHealthStatus(DatabaseType type, boolean healthy) {
        boolean previousStatus = healthStatus.getOrDefault(type, false);
        healthStatus.put(type, healthy);
        
        if (previousStatus != healthy) {
            log.info("Database {} health status changed: {} -> {}", type, previousStatus, healthy);
        }
    }
    
    /**
     * Increment connection count for a database
     */
    public void incrementConnectionCount(DatabaseType type) {
        connectionCounts.merge(type, 1, Integer::sum);
    }
    
    /**
     * Decrement connection count for a database
     */
    public void decrementConnectionCount(DatabaseType type) {
        connectionCounts.merge(type, -1, this::safeDecrementConnection);
    }
    
    /**
     * Get current connection count for a database
     */
    public int getConnectionCount(DatabaseType type) {
        return connectionCounts.getOrDefault(type, 0);
    }
    
    /**
     * Clear user affinity (useful for testing or cache cleanup)
     */
    public void clearUserAffinity(String userId) {
        userAffinityMap.remove(userId);
    }
    
    /**
     * Get all health statuses for monitoring
     */
    public Map<DatabaseType, Boolean> getAllHealthStatuses() {
        return Map.copyOf(healthStatus);
    }
    
    /**
     * Get all connection counts for monitoring
     */
    public Map<DatabaseType, Integer> getAllConnectionCounts() {
        return Map.copyOf(connectionCounts);
    }
    
    /**
     * Reset connection counts to zero (useful for testing)
     */
    public void resetConnectionCounts() {
        connectionCounts.clear();
        connectionCounts.put(DatabaseType.SLAVE_1, 0);
        connectionCounts.put(DatabaseType.SLAVE_2, 0);
        // Note: We don't reset master here as it might have ongoing connections
        log.info("Connection counts reset for testing purposes");
    }
    
    /**
     * Get statistics for a specific database
     */
    public Map<String, Object> getDatabaseStats(DatabaseType type) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("databaseType", type);
        stats.put("connectionCount", getConnectionCount(type));
        stats.put("healthy", isHealthy(type));
        return stats;
    }
    
    /**
     * Helper method to calculate user affinity index
     */
    private Integer calculateUserAffinityIndex(String userId) {
        return Math.abs(userId.hashCode()) % 2;
    }
    
    /**
     * Helper method to safely decrement connection count
     */
    private Integer safeDecrementConnection(Integer current, Integer decrement) {
        return Math.max(0, current + decrement);
    }
}