package com.example.DPMHC_backend.config;

import com.example.DPMHC_backend.security.JwtService;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("WebSocket CONNECT intercepted");
            
            // Extract Authorization header
            List<String> authHeader = accessor.getNativeHeader("Authorization");
            if (authHeader != null && !authHeader.isEmpty()) {
                String authHeaderValue = authHeader.get(0);
                log.info("Found Authorization header: {}", authHeaderValue);
                
                if (authHeaderValue.startsWith("Bearer ")) {
                    String token = authHeaderValue.substring(7);
                    log.info("Extracted JWT token for WebSocket authentication");
                    
                    try {
                        // Validate and extract user from JWT
                        String userEmail = jwtService.extractEmail(token);
                        log.info("Extracted email from JWT: {}", userEmail);
                        
                        if (!jwtService.isTokenExpired(token)) {
                            log.info("JWT token is valid");
                            
                            // Load user from database
                            User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
                            
                            log.info("Loaded user from database: {}", user.getEmail());
                            
                            // Create authentication with User object as principal
                            Authentication auth = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                            
                            // Set user authentication in WebSocket session
                            accessor.setUser(auth);
                            log.info("WebSocket authentication set for user: {} (ID: {})", user.getEmail(), user.getId());
                            
                        } else {
                            log.warn("Expired JWT token for WebSocket connection");
                        }
                    } catch (Exception e) {
                        log.error("Error processing JWT token for WebSocket", e);
                    }
                } else {
                    log.warn("Authorization header does not start with 'Bearer '");
                }
            } else {
                log.warn("No Authorization header found in WebSocket CONNECT");
            }
        }
        
        return message;
    }
}
