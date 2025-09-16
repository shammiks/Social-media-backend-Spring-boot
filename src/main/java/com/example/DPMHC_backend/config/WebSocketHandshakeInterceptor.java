package com.example.DPMHC_backend.config;

import com.example.DPMHC_backend.security.JwtService;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        log.info("🔥🔥🔥 WebSocket HANDSHAKE intercepted 🔥🔥🔥");
        log.info("Request URI: {}", request.getURI());
        log.info("Request Headers: {}", request.getHeaders());
        
        try {
            // First try Authorization header
            String authHeader = request.getHeaders().getFirst("Authorization");
            String token = null;
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                log.info("Found Authorization header: {}", authHeader);
            } else {
                // Fallback to token query parameter for SockJS compatibility
                String query = request.getURI().getQuery();
                if (query != null && query.contains("token=")) {
                    // Extract token from query string: token=Bearer_JWT_TOKEN
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("token=")) {
                            String tokenParam = param.substring(6); // Remove "token="
                            if (tokenParam.startsWith("Bearer_")) {
                                token = tokenParam.substring(7); // Remove "Bearer_"
                                log.info("Found token in query parameter: Bearer_{}", token.substring(0, Math.min(20, token.length())) + "...");
                                break;
                            }
                        }
                    }
                }
            }
            
            if (token == null) {
                log.warn("❌ No Authorization header or token query parameter found in WebSocket handshake");
                log.info("💡 Client should include JWT token in query parameter: ?token=Bearer_YOUR_JWT_TOKEN");
                return false; // Reject handshake - require authentication
            }
            log.info("Extracted JWT token for WebSocket handshake authentication");
            
            // Validate and extract user from JWT
            try {
                String userEmail = jwtService.extractEmail(token);
                log.info("Extracted email from JWT: {}", userEmail);
                
                if (jwtService.isTokenExpired(token)) {
                    log.warn("❌ JWT token expired for WebSocket handshake. Token expiration: {}", 
                           jwtService.extractExpiration(token));
                    return false; // Reject handshake
                }
                
                log.info("✅ JWT token is valid and not expired");
                
                // Load user from database
                User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
                
                log.info("Loaded user from database: {}", user.getEmail());
                
                // Store user information in WebSocket session attributes
                attributes.put("user", user);
                attributes.put("userId", user.getId());
                attributes.put("userEmail", user.getEmail());
                
                log.info("🔥 WebSocket handshake authentication successful for user: {} (ID: {})", user.getEmail(), user.getId());
                
                return true; // Allow handshake
                
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.error("❌ JWT token expired during WebSocket handshake");
                log.error("   Token expired at: {}", e.getClaims().getExpiration());
                log.error("   Current time: {}", new java.util.Date());
                log.info("💡 Client should refresh JWT token and reconnect to WebSocket");
                log.info("💡 New JWT tokens now have 24-hour expiration instead of 5 minutes");
                return false; // Reject handshake
            } catch (io.jsonwebtoken.JwtException e) {
                log.error("❌ Invalid JWT token during WebSocket handshake: {}", e.getMessage());
                log.info("💡 Client should obtain a new JWT token through login/refresh");
                return false; // Reject handshake
            }
            
        } catch (Exception e) {
            log.error("Error during WebSocket handshake authentication", e);
            return false; // Reject handshake
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
        
        if (exception != null) {
            log.error("WebSocket handshake failed", exception);
        } else {
            log.info("🔥 WebSocket handshake completed successfully");
        }
    }
}