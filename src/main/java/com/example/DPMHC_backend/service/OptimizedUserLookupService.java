package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Optimized User lookup service with caching to reduce database calls
 * Addresses the 150-680ms findByEmail query performance issue
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizedUserLookupService {

    private final UserRepository userRepository;

    /**
     * CACHED: Find user by email with 5-minute cache
     * Reduces database calls for frequently accessed users (JWT validation, authentication)
     */
    @Cacheable(value = "usersByEmail", key = "#email", unless = "#result == null")
    public Optional<User> findByEmailCached(String email) {
        long startTime = System.currentTimeMillis();
        Optional<User> result = userRepository.findByEmail(email);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 100) {
            log.warn("🐌 SLOW USER LOOKUP: findByEmail took {}ms for email: {}", duration, email);
        }
        
        return result;
    }

    /**
     * CACHED: Find user by ID with 10-minute cache
     */
    @Cacheable(value = "usersById", key = "#id", unless = "#result == null")
    public Optional<User> findByIdCached(Long id) {
        long startTime = System.currentTimeMillis();
        Optional<User> result = userRepository.findById(id);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 50) {
            log.warn("🐌 SLOW USER LOOKUP: findById took {}ms for id: {}", duration, id);
        }
        
        return result;
    }

    /**
     * CACHED: Find user by username with 10-minute cache
     */
    @Cacheable(value = "usersByUsername", key = "#username", unless = "#result == null")
    public Optional<User> findByUsernameCached(String username) {
        long startTime = System.currentTimeMillis();
        Optional<User> result = userRepository.findByUsername(username);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 50) {
            log.warn("🐌 SLOW USER LOOKUP: findByUsername took {}ms for username: {}", duration, username);
        }
        
        return result;
    }

    /**
     * Bypass cache for write operations - always get fresh data
     */
    public Optional<User> findByEmailFresh(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Clear user from all caches when updated
     */
    public void evictUserFromCache(User user) {
        // This will be implemented with @CacheEvict annotations in the UserService
        log.debug("User cache eviction requested for: {}", user.getEmail());
    }
}