package com.example.DPMHC_backend.config.performance;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Performance optimization cache configuration
 * Now uses Redis for distributed caching (configured in RedisConfig)
 * This class is kept for backward compatibility and future performance configs
 */
@Configuration
@EnableCaching
public class PerformanceCacheConfig {
    
    // Redis cache configuration is now handled in RedisConfig.java
    // This class can be used for additional performance-related configurations
    // such as cache warming, custom cache interceptors, etc.
}