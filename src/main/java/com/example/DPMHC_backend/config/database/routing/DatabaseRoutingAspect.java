package com.example.DPMHC_backend.config.database.routing;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import com.example.DPMHC_backend.config.database.DatabaseType;
import com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB;
import com.example.DPMHC_backend.config.database.annotation.WriteDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * AOP Aspect for database routing based on annotations
 * Only enabled for development profile (master-slave setup)
 */
@Aspect
@Component
@Profile("dev")
@Order(0) // Execute before transaction aspect
@RequiredArgsConstructor
@Slf4j
public class DatabaseRoutingAspect {
    
    private final DatabaseLoadBalancer loadBalancer;
    
    /**
     * Intercept methods annotated with @WriteDB
     */
    @Around("@annotation(writeDB)")
    public Object routeToMasterDatabase(ProceedingJoinPoint joinPoint, WriteDB writeDB) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        log.info("🔴 WRITE Operation → Database: MASTER | Method: {}", methodName);
        
        DatabaseType previousType = DatabaseContextHolder.getDatabaseType();
        long startTime = System.currentTimeMillis();
        
        try {
            // Always route write operations to master
            DatabaseContextHolder.setDatabaseType(DatabaseType.MASTER);
            loadBalancer.incrementConnectionCount(DatabaseType.MASTER);
            
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ WRITE Success → Database: MASTER | Method: {} | Duration: {}ms", 
                    methodName, executionTime);
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ WRITE Failed → Database: MASTER | Method: {} | Duration: {}ms | Error: {}", 
                    methodName, executionTime, e.getMessage());
            throw e;
        } finally {
            loadBalancer.decrementConnectionCount(DatabaseType.MASTER);
            
            // Restore previous context if it was different
            if (previousType != DatabaseType.MASTER) {
                DatabaseContextHolder.setDatabaseType(previousType);
            }
        }
    }
    
    /**
     * Intercept methods annotated with @ReadOnlyDB
     */
    @Around("@annotation(readOnlyDB)")
    public Object routeToSlaveDatabase(ProceedingJoinPoint joinPoint, ReadOnlyDB readOnlyDB) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        DatabaseType previousType = DatabaseContextHolder.getDatabaseType();
        DatabaseType selectedType = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if we should force read from master (for strong consistency)
            if (DatabaseContextHolder.isForceReadFromMaster()) {
                selectedType = DatabaseType.MASTER;
                log.info("🟡 READ Operation → Database: MASTER (Forced) | Method: {} | Strategy: FORCE_MASTER", 
                        methodName);
            } else {
                // Select appropriate database based on strategy
                String userId = readOnlyDB.userSpecific() ? DatabaseContextHolder.getUserContext() : null;
                selectedType = loadBalancer.selectSlave(readOnlyDB.strategy(), userId);
                
                // Fallback to master if no healthy slaves and fallback is enabled
                if (selectedType.isSlave() && !loadBalancer.isHealthy(selectedType) && readOnlyDB.fallbackToMaster()) {
                    selectedType = DatabaseType.MASTER;
                    log.warn("🟠 READ Operation → Database: MASTER (Fallback) | Method: {} | Strategy: {} | Reason: Slave {} unhealthy", 
                            methodName, readOnlyDB.strategy(), selectedType);
                } else {
                    String dbIcon = selectedType.isSlave() ? "🔵" : "🟡";
                    log.info("{} READ Operation → Database: {} | Method: {} | Strategy: {}", 
                            dbIcon, selectedType, methodName, readOnlyDB.strategy());
                }
            }
            
            DatabaseContextHolder.setDatabaseType(selectedType);
            loadBalancer.incrementConnectionCount(selectedType);
            
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            String dbIcon = selectedType.isSlave() ? "🔵" : "🟡";
            log.info("✅ READ Success → Database: {} | Method: {} | Duration: {}ms", 
                    selectedType, methodName, executionTime);
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ READ Failed → Database: {} | Method: {} | Duration: {}ms | Error: {}", 
                    selectedType, methodName, executionTime, e.getMessage());
            
            // If operation failed on slave and fallback is enabled, try master
            if (selectedType != null && selectedType.isSlave() && readOnlyDB.fallbackToMaster()) {
                log.warn("🔄 READ Retry → Database: MASTER | Method: {} | Reason: Slave {} failed", 
                        methodName, selectedType);
                try {
                    DatabaseContextHolder.setDatabaseType(DatabaseType.MASTER);
                    Object result = joinPoint.proceed();
                    long retryTime = System.currentTimeMillis() - startTime;
                    log.info("✅ READ Success (Retry) → Database: MASTER | Method: {} | Total Duration: {}ms", 
                            methodName, retryTime);
                    return result;
                } catch (Exception masterException) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.error("❌ READ Failed (Final) → Database: MASTER | Method: {} | Total Duration: {}ms | Error: {}", 
                            methodName, totalTime, masterException.getMessage());
                    throw masterException;
                }
            }
            
            throw e;
        } finally {
            if (selectedType != null) {
                loadBalancer.decrementConnectionCount(selectedType);
            }
            
            // Restore previous context
            if (previousType != selectedType) {
                DatabaseContextHolder.setDatabaseType(previousType);
            }
        }
    }
    
    /**
     * Intercept class-level annotations
     */
    @Around("@within(readOnlyDB) && execution(public * *(..))")
    public Object routeClassLevelReadOnly(ProceedingJoinPoint joinPoint, ReadOnlyDB readOnlyDB) throws Throwable {
        // Check if method has its own annotation (method-level takes precedence)
        Method method = getMethod(joinPoint);
        if (method != null && (method.isAnnotationPresent(ReadOnlyDB.class) || method.isAnnotationPresent(WriteDB.class))) {
            return joinPoint.proceed();
        }
        
        return routeToSlaveDatabase(joinPoint, readOnlyDB);
    }
    
    @Around("@within(writeDB) && execution(public * *(..))")
    public Object routeClassLevelWrite(ProceedingJoinPoint joinPoint, WriteDB writeDB) throws Throwable {
        // Check if method has its own annotation (method-level takes precedence)
        Method method = getMethod(joinPoint);
        if (method != null && (method.isAnnotationPresent(ReadOnlyDB.class) || method.isAnnotationPresent(WriteDB.class))) {
            return joinPoint.proceed();
        }
        
        return routeToMasterDatabase(joinPoint, writeDB);
    }
    
    /**
     * Handle transactional methods without explicit routing annotations
     */
    @Around("@annotation(transactional) && !@annotation(com.example.DPMHC_backend.config.database.annotation.ReadOnlyDB) && !@annotation(com.example.DPMHC_backend.config.database.annotation.WriteDB)")
    public Object routeTransactionalMethod(ProceedingJoinPoint joinPoint, Transactional transactional) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        // Route based on transaction readOnly property
        if (transactional.readOnly()) {
            String userId = DatabaseContextHolder.getUserContext();
            DatabaseType selectedType = loadBalancer.selectSlave(ReadOnlyDB.LoadBalanceStrategy.ROUND_ROBIN, userId);
            
            log.info("🔵 READ (@Transactional) → Database: {} | Method: {} | Strategy: ROUND_ROBIN", 
                    selectedType, methodName);
            
            DatabaseType previousType = DatabaseContextHolder.getDatabaseType();
            
            try {
                DatabaseContextHolder.setDatabaseType(selectedType);
                loadBalancer.incrementConnectionCount(selectedType);
                Object result = joinPoint.proceed();
                
                long executionTime = System.currentTimeMillis() - startTime;
                log.info("✅ READ Success (@Transactional) → Database: {} | Method: {} | Duration: {}ms", 
                        selectedType, methodName, executionTime);
                return result;
            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                log.error("❌ READ Failed (@Transactional) → Database: {} | Method: {} | Duration: {}ms | Error: {}", 
                        selectedType, methodName, executionTime, e.getMessage());
                throw e;
            } finally {
                loadBalancer.decrementConnectionCount(selectedType);
                DatabaseContextHolder.setDatabaseType(previousType);
            }
        } else {
            log.info("🔴 WRITE (@Transactional) → Database: MASTER | Method: {}", methodName);
            
            DatabaseType previousType = DatabaseContextHolder.getDatabaseType();
            
            try {
                DatabaseContextHolder.setDatabaseType(DatabaseType.MASTER);
                loadBalancer.incrementConnectionCount(DatabaseType.MASTER);
                Object result = joinPoint.proceed();
                
                long executionTime = System.currentTimeMillis() - startTime;
                log.info("✅ WRITE Success (@Transactional) → Database: MASTER | Method: {} | Duration: {}ms", 
                        methodName, executionTime);
                return result;
            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                log.error("❌ WRITE Failed (@Transactional) → Database: MASTER | Method: {} | Duration: {}ms | Error: {}", 
                        methodName, executionTime, e.getMessage());
                throw e;
            } finally {
                loadBalancer.decrementConnectionCount(DatabaseType.MASTER);
                DatabaseContextHolder.setDatabaseType(previousType);
            }
        }
    }
    
    /**
     * Get method from join point
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Class<?>[] parameterTypes = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes();
            return joinPoint.getTarget().getClass().getDeclaredMethod(methodName, parameterTypes);
        } catch (Exception e) {
            log.warn("Could not get method from join point: {}", e.getMessage());
            return null;
        }
    }
}