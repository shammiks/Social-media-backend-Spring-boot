package com.example.DPMHC_backend.config.performance;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Performance monitoring aspect to track database query performance
 * Helps identify slow queries and N+1 problems
 */
@Aspect
@Component
@Slf4j
public class QueryPerformanceMonitor {
    
    private static final long SLOW_QUERY_THRESHOLD_MS = 100;
    
    @Around("execution(* com.example.DPMHC_backend.repository..*(..))")
    public Object monitorRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("🐌 SLOW QUERY DETECTED: {} took {}ms", methodName, duration);
            } else {
                log.debug("⚡ QUERY: {} took {}ms", methodName, duration);
            }
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ QUERY FAILED: {} failed after {}ms - {}", methodName, duration, e.getMessage());
            throw e;
        }
    }
    
    @Around("execution(* com.example.DPMHC_backend.service.PostBatchService.*(..))")
    public Object monitorBatchServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = "PostBatchService." + joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("🚀 OPTIMIZED BATCH QUERY: {} completed in {}ms", methodName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ BATCH QUERY FAILED: {} failed after {}ms - {}", methodName, duration, e.getMessage());
            throw e;
        }
    }
}