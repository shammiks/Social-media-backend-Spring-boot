package com.example.DPMHC_backend.config.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Component to clear Redis caches on application startup
 * This prevents LinkedHashMap deserialization issues when Redis configuration changes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheStartupCleaner {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Clear all Redis caches on application ready event
     * This ensures clean state when serialization configuration changes
     */
    @EventListener(ApplicationReadyEvent.class)
    public void clearAllCachesOnStartup() {
        try {
            // Clear all keys with the socialmedia prefix to avoid LinkedHashMap issues
            var keys = redisTemplate.keys("socialmedia:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🧹 Cleared {} Redis cache keys on startup to prevent deserialization issues", keys.size());
            } else {
                log.info("🧹 No Redis cache keys found to clear on startup");
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not clear Redis caches on startup: {}", e.getMessage());
        }
    }
}