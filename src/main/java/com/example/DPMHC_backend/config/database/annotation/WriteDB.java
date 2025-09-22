package com.example.DPMHC_backend.config.database.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that perform write operations and should use master database
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WriteDB {
    
    /**
     * Whether this is a critical write operation that requires immediate consistency
     */
    boolean critical() default false;
    
    /**
     * Operation type for monitoring and logging
     */
    OperationType type() default OperationType.CREATE;
    
    enum OperationType {
        CREATE,
        UPDATE,
        DELETE,
        BULK_OPERATION
    }
}