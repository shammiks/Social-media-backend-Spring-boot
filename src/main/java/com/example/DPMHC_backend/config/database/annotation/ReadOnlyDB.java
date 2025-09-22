package com.example.DPMHC_backend.config.database.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that perform read operations and should use slave databases
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnlyDB {
    
    /**
     * Load balancing strategy for read operations
     */
    LoadBalanceStrategy strategy() default LoadBalanceStrategy.ROUND_ROBIN;
    
    /**
     * Whether to use user-specific routing for better cache locality
     */
    boolean userSpecific() default false;
    
    /**
     * Fallback to master if all slaves are unavailable
     */
    boolean fallbackToMaster() default true;
    
    /**
     * Priority level for routing (higher priority gets preference)
     */
    int priority() default 0;
    
    enum LoadBalanceStrategy {
        ROUND_ROBIN,
        USER_SPECIFIC,
        LEAST_CONNECTIONS,
        HEALTH_BASED
    }
}