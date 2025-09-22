package com.example.DPMHC_backend.config.logging;

import com.example.DPMHC_backend.config.database.DatabaseContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Aspect for logging HTTP endpoint requests and their database usage
 */
@Aspect
@Component
@Order(1) // Execute after database routing aspect
@Slf4j
public class EndpointLoggingAspect {
    
    /**
     * Log all REST controller method calls with database context
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logEndpointExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return joinPoint.proceed(); // Not an HTTP request, skip logging
        }
        
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        String methodName = joinPoint.getSignature().toShortString();
        String userContext = DatabaseContextHolder.getUserContext();
        
        log.info("📡 API Call → Endpoint: {} | Method: {} | User: {}", 
                endpoint, methodName, userContext != null ? userContext : "anonymous");
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ API Success → Endpoint: {} | Duration: {}ms", endpoint, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ API Failed → Endpoint: {} | Duration: {}ms | Error: {}", 
                    endpoint, executionTime, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get current HTTP request from context
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}