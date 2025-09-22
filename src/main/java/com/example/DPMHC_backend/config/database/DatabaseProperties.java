package com.example.DPMHC_backend.config.database;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for master-slave database setup
 */
@Component
@ConfigurationProperties(prefix = "app.database")
@Data
public class DatabaseProperties {
    
    private Master master;
    private List<Slave> slaves;
    private ConnectionPool connectionPool;
    private HealthCheck healthCheck;
    private LoadBalancing loadBalancing;
    
    @Data
    public static class Master {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maxPoolSize = 25;
        private int minPoolSize = 5;
        private long connectionTimeoutMs = 30000;
        private long idleTimeoutMs = 600000;
        private long maxLifetimeMs = 1800000;
    }
    
    @Data
    public static class Slave {
        private String name;
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maxPoolSize = 20;
        private int minPoolSize = 3;
        private long connectionTimeoutMs = 30000;
        private long idleTimeoutMs = 600000;
        private long maxLifetimeMs = 1800000;
        private boolean enabled = true;
    }
    
    @Data
    public static class ConnectionPool {
        private boolean autoCommit = true;
        private long leakDetectionThresholdMs = 60000;
        private String connectionTestQuery = "SELECT 1";
        private boolean cacheStatements = true;
        private int preparedStatementCacheSize = 250;
    }
    
    @Data
    public static class HealthCheck {
        private long intervalMs = 30000;
        private int timeoutMs = 5000;
        private int failureThreshold = 3;
        private int recoveryThreshold = 2;
        private boolean enabled = true;
    }
    
    @Data
    public static class LoadBalancing {
        private String defaultStrategy = "ROUND_ROBIN";
        private boolean userSpecificRouting = true;
        private long userAffinityTimeoutMs = 300000; // 5 minutes
        private boolean enableMetrics = true;
    }
}