package com.example.DPMHC_backend.config;

import com.example.DPMHC_backend.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.example.DPMHC_backend.model.User;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketService webSocketService;

    public WebSocketConfig(@Lazy WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint with SockJS
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Register endpoint without SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        // Handle connection
                        Authentication auth = (Authentication) accessor.getUser();
                        if (auth != null) {
                            try {
                                // Extract userId consistently with other controllers
                                Long userId = getUserIdFromAuth(auth);
                                String sessionId = accessor.getSessionId();
                                webSocketService.registerUserSession(userId, sessionId);
                                log.info("User {} connected via WebSocket with session {}", userId, sessionId);
                            } catch (Exception e) {
                                log.error("Error extracting user ID during WebSocket connect", e);
                            }
                        }
                    } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                        // Handle disconnection
                        String sessionId = accessor.getSessionId();
                        webSocketService.unregisterUserSession(sessionId);
                        log.info("WebSocket session {} disconnected", sessionId);
                    }
                }

                return message;
            }
        });
    }

    // Helper method to extract user ID consistently
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Authentication required");
        }

        try {
            // Try to get User object first (from JWT token)
            User user = (User) authentication.getPrincipal();
            return user.getId();
        } catch (ClassCastException e) {
            // Fallback to string-based principal (for WebSocket)
            try {
                return Long.valueOf(authentication.getName());
            } catch (NumberFormatException nfe) {
                log.error("Could not extract user ID from authentication", e);
                throw new RuntimeException("Invalid authentication principal");
            }
        }
    }
}