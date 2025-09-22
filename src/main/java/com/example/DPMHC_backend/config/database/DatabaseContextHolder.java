package com.example.DPMHC_backend.config.database;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for database routing context
 */
@Slf4j
public class DatabaseContextHolder {
    
    private static final ThreadLocal<DatabaseType> contextHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userContextHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> forceReadFromMaster = new ThreadLocal<>();
    
    /**
     * Set the database type for current thread
     */
    public static void setDatabaseType(DatabaseType databaseType) {
        log.debug("Switching to database: {}", databaseType);
        contextHolder.set(databaseType);
    }
    
    /**
     * Get current database type for thread
     */
    public static DatabaseType getDatabaseType() {
        DatabaseType type = contextHolder.get();
        return type != null ? type : DatabaseType.MASTER;
    }
    
    /**
     * Set user context for user-specific routing
     */
    public static void setUserContext(String userId) {
        userContextHolder.set(userId);
    }
    
    /**
     * Get user context
     */
    public static String getUserContext() {
        return userContextHolder.get();
    }
    
    /**
     * Force read operations to use master database (for strong consistency)
     */
    public static void forceReadFromMaster(boolean force) {
        forceReadFromMaster.set(force);
    }
    
    /**
     * Check if reads should be forced to master
     */
    public static boolean isForceReadFromMaster() {
        Boolean force = forceReadFromMaster.get();
        return force != null && force;
    }
    
    /**
     * Clear all context for current thread
     */
    public static void clear() {
        log.debug("Clearing database context");
        contextHolder.remove();
        userContextHolder.remove();
        forceReadFromMaster.remove();
    }
    
    /**
     * Clear only database type context
     */
    public static void clearDatabaseType() {
        contextHolder.remove();
    }
    
    /**
     * Check if any context is set
     */
    public static boolean hasContext() {
        return contextHolder.get() != null;
    }
}