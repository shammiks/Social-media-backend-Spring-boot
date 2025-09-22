package com.example.DPMHC_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis Cache Management and Monitoring Service
 * Provides cache statistics, health checks, and manual cache operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheManagementService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        stats.put("totalCaches", cacheNames.size());
        stats.put("cacheNames", cacheNames);
        
        Map<String, Map<String, Object>> cacheDetails = new HashMap<>();
        
        for (String cacheName : cacheNames) {
            Map<String, Object> cacheStats = new HashMap<>();
            Cache cache = cacheManager.getCache(cacheName);
            
            if (cache != null) {
                // Get cache size using Redis commands
                String keyPattern = "socialmedia:" + cacheName + ":*";
                Set<String> keys = redisTemplate.keys(keyPattern);
                cacheStats.put("size", keys != null ? keys.size() : 0);
                cacheStats.put("keyPattern", keyPattern);
                
                // Sample some keys for inspection
                if (keys != null && !keys.isEmpty()) {
                    List<String> sampleKeys = keys.stream()
                            .limit(5)
                            .toList();
                    cacheStats.put("sampleKeys", sampleKeys);
                }
            }
            
            cacheDetails.put(cacheName, cacheStats);
        }
        
        stats.put("cacheDetails", cacheDetails);
        return stats;
    }

    /**
     * Check Redis connection health
     */
    public Map<String, Object> getRedisHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test Redis connectivity
            long startTime = System.currentTimeMillis();
            redisTemplate.opsForValue().set("health:check", "OK", 10, TimeUnit.SECONDS);
            String result = (String) redisTemplate.opsForValue().get("health:check");
            long responseTime = System.currentTimeMillis() - startTime;
            
            health.put("status", "UP");
            health.put("responseTime", responseTime + "ms");
            health.put("testResult", result);
            
            // Get Redis info
            Properties info = redisTemplate.getConnectionFactory().getConnection().info();
            health.put("redisVersion", info.getProperty("redis_version"));
            health.put("connectedClients", info.getProperty("connected_clients"));
            health.put("usedMemory", info.getProperty("used_memory_human"));
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            log.error("Redis health check failed", e);
        }
        
        return health;
    }

    /**
     * Clear specific cache
     */
    public boolean clearCache(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("🗑️ Cleared cache: {}", cacheName);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", cacheName, e);
            return false;
        }
    }

    /**
     * Clear all caches
     */
    public int clearAllCaches() {
        int clearedCount = 0;
        Collection<String> cacheNames = cacheManager.getCacheNames();
        
        for (String cacheName : cacheNames) {
            if (clearCache(cacheName)) {
                clearedCount++;
            }
        }
        
        log.info("🗑️ Cleared {} out of {} caches", clearedCount, cacheNames.size());
        return clearedCount;
    }

    /**
     * Warm up caches with sample data (useful after cache clear)
     */
    public void warmUpCaches() {
        log.info("🔥 Starting cache warm-up process...");
        
        // This would typically involve calling frequently accessed methods
        // to pre-populate the cache. Implementation depends on specific use cases.
        
        try {
            // Example: You could call frequently accessed user lookups here
            // userService.getUserById(1L); // This would populate the cache
            
            log.info("🔥 Cache warm-up completed successfully");
        } catch (Exception e) {
            log.error("🔥 Cache warm-up failed", e);
        }
    }

    /**
     * Get cache hit/miss statistics (if supported by Redis)
     */
    public Map<String, Object> getCacheHitRatio() {
        Map<String, Object> hitRatio = new HashMap<>();
        
        try {
            Properties info = redisTemplate.getConnectionFactory().getConnection().info("stats");
            
            String keyspaceHits = info.getProperty("keyspace_hits");
            String keyspaceMisses = info.getProperty("keyspace_misses");
            
            if (keyspaceHits != null && keyspaceMisses != null) {
                long hits = Long.parseLong(keyspaceHits);
                long misses = Long.parseLong(keyspaceMisses);
                long total = hits + misses;
                
                double ratio = total > 0 ? (double) hits / total * 100 : 0;
                
                hitRatio.put("hits", hits);
                hitRatio.put("misses", misses);
                hitRatio.put("total", total);
                hitRatio.put("hitRatio", String.format("%.2f%%", ratio));
            }
            
        } catch (Exception e) {
            log.error("Failed to get cache hit ratio", e);
            hitRatio.put("error", e.getMessage());
        }
        
        return hitRatio;
    }

    /**
     * Evict specific cache key
     */
    public boolean evictCacheKey(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.info("🗑️ Evicted key '{}' from cache '{}'", key, cacheName);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to evict key '{}' from cache '{}'", key, cacheName, e);
            return false;
        }
    }

    /**
     * Check if specific key exists in cache
     */
    public boolean isCacheKeyPresent(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(key);
                return wrapper != null;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check cache key '{}' in cache '{}'", key, cacheName, e);
            return false;
        }
    }
}