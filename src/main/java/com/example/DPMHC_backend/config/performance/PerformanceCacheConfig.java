package com.example.DPMHC_backend.config.performance;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Performance optimization cache configuration
 * Reduces database query load for frequently accessed data
 */
@Configuration
@EnableCaching
public class PerformanceCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            // User lookup caches - reduce database calls for authentication
            new ConcurrentMapCache("usersByEmail"),    // 5-minute TTL
            new ConcurrentMapCache("usersById"),       // 10-minute TTL  
            new ConcurrentMapCache("usersByUsername"), // 10-minute TTL
            
            // Follow service caches - reduce follow-related query load
            new ConcurrentMapCache("followStatus"),    // Follow relationship status
            new ConcurrentMapCache("followerCount"),   // Follower count caching
            new ConcurrentMapCache("followingCount"),  // Following count caching
            
            // Future: Add more performance caches as needed
            new ConcurrentMapCache("postCounts"),      // Post metadata caching
            new ConcurrentMapCache("notificationCounts") // Notification count caching
        ));
        return cacheManager;
    }
}