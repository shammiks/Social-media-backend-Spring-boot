package com.example.DPMHC_backend.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration
 * Provides comprehensive Redis caching setup with optimized performance settings
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * Redis connection factory with optimized settings
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(redisHost);
        serverConfig.setPort(redisPort);
        serverConfig.setDatabase(redisDatabase);
        
        if (!redisPassword.isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig);
        factory.setValidateConnection(true);
        
        log.info("🔌 Redis connection factory configured for {}:{}", redisHost, redisPort);
        return factory;
    }

    /**
     * Redis template with JSON serialization
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();

        log.info("📝 Redis template configured with JSON serialization");
        return template;
    }

    /**
     * Cache manager with cache-specific TTL configurations
     */
    @Bean
    @Primary
    public CacheManager cacheManager(LettuceConnectionFactory connectionFactory) {
        // Default cache configuration with socialmedia prefix
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("socialmedia:")
                .disableCachingNullValues();

        // Cache-specific configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User caches - frequently accessed, longer TTL
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("user-emails", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("user-profiles", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // Optimized user lookup caches
        cacheConfigurations.put("usersByEmail", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("usersById", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("usersByUsername", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // Post caches - moderate TTL for content
        cacheConfigurations.put("posts", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("user-posts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("post-comments", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Notification caches - short TTL for real-time data
        cacheConfigurations.put("notification-counts", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("notification-stats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Chat caches - moderate TTL
        cacheConfigurations.put("chat-lists", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("chat-messages", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Social relationship caches
        cacheConfigurations.put("followers", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("following", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("follow-suggestions", defaultConfig.entryTtl(Duration.ofMinutes(60)));
        
        // Like caches - short TTL for dynamic data
        cacheConfigurations.put("post-likes", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("user-likes", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("user-liked-posts", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // Comment caches - moderate TTL
        cacheConfigurations.put("comment-counts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("comment-replies", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("comment-likes", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Message caches - moderate TTL for chat functionality
        cacheConfigurations.put("message-details", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("message-counts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // User block caches - longer TTL since blocks are relatively stable
        cacheConfigurations.put("user-blocks", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("user-mutual-blocks", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("user-blocked-lists", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        cacheConfigurations.put("user-blocked-ids", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // System caches
        cacheConfigurations.put("system-stats", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("admin-data", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();

        log.info("🏗️ Redis cache manager configured with {} cache types", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Object mapper for JSON serialization
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}