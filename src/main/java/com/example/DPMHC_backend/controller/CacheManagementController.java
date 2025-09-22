package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.service.CacheManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cache Management REST Controller
 * Provides endpoints for cache monitoring, statistics, and management
 * Restricted to admin users only for security
 */
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")  // Restrict to admin users only
public class CacheManagementController {

    private final CacheManagementService cacheManagementService;

    /**
     * Get comprehensive cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        log.info("📊 Admin requested cache statistics");
        Map<String, Object> stats = cacheManagementService.getCacheStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get Redis health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        log.info("❤️ Admin requested Redis health check");
        Map<String, Object> health = cacheManagementService.getRedisHealthCheck();
        return ResponseEntity.ok(health);
    }

    /**
     * Get cache hit/miss ratio
     */
    @GetMapping("/hit-ratio")
    public ResponseEntity<Map<String, Object>> getCacheHitRatio() {
        log.info("📈 Admin requested cache hit ratio");
        Map<String, Object> hitRatio = cacheManagementService.getCacheHitRatio();
        return ResponseEntity.ok(hitRatio);
    }

    /**
     * Clear specific cache
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        log.warn("🗑️ Admin clearing cache: {}", cacheName);
        boolean success = cacheManagementService.clearCache(cacheName);
        
        Map<String, Object> response = Map.of(
            "success", success,
            "message", success ? "Cache cleared successfully" : "Failed to clear cache",
            "cacheName", cacheName
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Clear all caches
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        log.warn("🗑️ Admin clearing ALL caches");
        int clearedCount = cacheManagementService.clearAllCaches();
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Caches cleared successfully",
            "clearedCount", clearedCount
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Warm up caches
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmUpCaches() {
        log.info("🔥 Admin requested cache warm-up");
        cacheManagementService.warmUpCaches();
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Cache warm-up initiated"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Evict specific key from cache
     */
    @DeleteMapping("/{cacheName}/keys/{key}")
    public ResponseEntity<Map<String, Object>> evictCacheKey(
            @PathVariable String cacheName,
            @PathVariable String key) {
        log.info("🗑️ Admin evicting key '{}' from cache '{}'", key, cacheName);
        boolean success = cacheManagementService.evictCacheKey(cacheName, key);
        
        Map<String, Object> response = Map.of(
            "success", success,
            "message", success ? "Key evicted successfully" : "Failed to evict key",
            "cacheName", cacheName,
            "key", key
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check if specific key exists in cache
     */
    @GetMapping("/{cacheName}/keys/{key}/exists")
    public ResponseEntity<Map<String, Object>> checkCacheKey(
            @PathVariable String cacheName,
            @PathVariable String key) {
        log.info("🔍 Admin checking key '{}' in cache '{}'", key, cacheName);
        boolean exists = cacheManagementService.isCacheKeyPresent(cacheName, key);
        
        Map<String, Object> response = Map.of(
            "exists", exists,
            "cacheName", cacheName,
            "key", key
        );
        
        return ResponseEntity.ok(response);
    }
}