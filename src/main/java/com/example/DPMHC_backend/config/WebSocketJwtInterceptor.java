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
import java.util.Map;

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
            log.info("🔥🔥🔥 WebSocket CONNECT intercepted 🔥🔥🔥");
            
            // Get user information from WebSocket session attributes (set during handshake)
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                User user = (User) sessionAttributes.get("user");
                Long userId = (Long) sessionAttributes.get("userId");
                String userEmail = (String) sessionAttributes.get("userEmail");
                
                if (user != null) {
                    log.info("Found user from WebSocket session: {} (ID: {})", user.getEmail(), user.getId());
                    
                    // CRITICAL FIX: Create authentication with user ID as the principal name
                    // This ensures Spring STOMP can properly route messages to the user
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                        user.getId().toString(), // Use user ID as principal name for message routing
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    
                    // Set user authentication in STOMP session
                    accessor.setUser(auth);
                    log.info("🔥 WebSocket authentication set for user: {} (ID: {})", user.getEmail(), user.getId());
                    log.info("🔥 Authentication object: {}", auth);
                    log.info("🔥 Principal NAME: {}", auth.getName());
                    log.info("🔥 Principal OBJECT: {}", auth.getPrincipal());
                } else if (userId != null && userEmail != null) {
                    // Handle temporary authentication case (for testing)
                    log.info("Using temporary authentication for testing: {} (ID: {})", userEmail, userId);
                    
                    // Load user from database using repository
                    User tempUser = userRepository.findByEmail(userEmail).orElse(null);
                    if (tempUser != null) {
                        // CRITICAL FIX: Use user ID as principal name for temporary auth too
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                            tempUser.getId().toString(), // Use user ID as principal name
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        
                        accessor.setUser(auth);
                        log.info("🔥 WebSocket temporary authentication set for user: {} (ID: {})", tempUser.getEmail(), tempUser.getId());
                        log.info("🔥 Temporary auth Principal NAME: {}", auth.getName());
                    } else {
                        log.warn("Could not find user in database for temporary authentication");
                    }
                } else {
                    log.warn("No user found in WebSocket session attributes");
                }
            } else {
                log.warn("No session attributes found in WebSocket CONNECT");
            }
        }
        
        return message;
    }
}
