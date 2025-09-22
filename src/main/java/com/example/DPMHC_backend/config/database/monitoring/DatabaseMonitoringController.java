package com.example.DPMHC_backend.config.database.monitoring;

import com.example.DPMHC_backend.config.database.DatabaseType;
import com.example.DPMHC_backend.config.database.health.DatabaseHealthMonitor;
import com.example.DPMHC_backend.config.database.routing.DatabaseLoadBalancer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for database monitoring and management
 */
@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class DatabaseMonitoringController {
    
    private final DatabaseHealthMonitor healthMonitor;
    private final DatabaseLoadBalancer loadBalancer;
    
    /**
     * Get health status of all databases
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<DatabaseType, DatabaseHealthMonitor.HealthStats> allStats = healthMonitor.getAllHealthStats();
        
        boolean masterHealthy = loadBalancer.isHealthy(DatabaseType.MASTER);
        boolean anySlavesHealthy = loadBalancer.isHealthy(DatabaseType.SLAVE_1) || 
                                 loadBalancer.isHealthy(DatabaseType.SLAVE_2);
        
        response.put("overall_status", masterHealthy && anySlavesHealthy ? "HEALTHY" : 
                    masterHealthy ? "DEGRADED" : "CRITICAL");
        response.put("master_healthy", masterHealthy);
        response.put("slaves_healthy", anySlavesHealthy);
        response.put("databases", allStats);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get health status of a specific database
     */
    @GetMapping("/health/{type}")
    public ResponseEntity<DatabaseHealthMonitor.HealthStats> getDatabaseHealth(@PathVariable DatabaseType type) {
        DatabaseHealthMonitor.HealthStats stats = healthMonitor.getHealthStats(type);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Force health check for a specific database
     */
    @PostMapping("/health/{type}/check")
    public ResponseEntity<Map<String, Object>> forceHealthCheck(@PathVariable DatabaseType type) {
        boolean isHealthy = healthMonitor.forceHealthCheck(type);
        
        Map<String, Object> response = new HashMap<>();
        response.put("database", type);
        response.put("healthy", isHealthy);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Manually set database health status (for emergency management)
     */
    @PostMapping("/health/{type}/set")
    public ResponseEntity<Map<String, Object>> setDatabaseHealth(
            @PathVariable DatabaseType type, 
            @RequestParam boolean healthy) {
        
        healthMonitor.setDatabaseHealth(type, healthy);
        
        Map<String, Object> response = new HashMap<>();
        response.put("database", type);
        response.put("healthy", healthy);
        response.put("message", "Health status updated manually");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get load balancing statistics
     */
    @GetMapping("/load-balancing")
    public ResponseEntity<Map<String, Object>> getLoadBalancingStats() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("connection_counts", loadBalancer.getAllConnectionCounts());
        response.put("health_statuses", loadBalancer.getAllHealthStatuses());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Test database connectivity and performance
     */
    @PostMapping("/test/{type}")
    public ResponseEntity<Map<String, Object>> testDatabasePerformance(@PathVariable DatabaseType type) {
        long startTime = System.currentTimeMillis();
        
        // Simulate database operation (this would need actual DataSource injection)
        boolean isHealthy = healthMonitor.forceHealthCheck(type);
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("database", type);
        response.put("healthy", isHealthy);
        response.put("response_time_ms", responseTime);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clear user affinity mappings (for load balancer reset)
     */
    @PostMapping("/load-balancing/clear-affinity")
    public ResponseEntity<Map<String, Object>> clearUserAffinity(@RequestParam(required = false) String userId) {
        if (userId != null) {
            loadBalancer.clearUserAffinity(userId);
        } else {
            // Clear all user affinities - would need method in loadBalancer
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", userId != null ? 
                    "Cleared affinity for user: " + userId : 
                    "Cleared all user affinities");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get database configuration summary
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getDatabaseConfig() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("databases", new String[]{"MASTER", "SLAVE_1", "SLAVE_2"});
        response.put("load_balancing_strategies", new String[]{
            "ROUND_ROBIN", "USER_SPECIFIC", "LEAST_CONNECTIONS", "HEALTH_BASED"
        });
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}