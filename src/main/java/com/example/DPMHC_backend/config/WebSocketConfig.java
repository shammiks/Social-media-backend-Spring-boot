package com.example.DPMHC_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry messages back to the client
        config.enableSimpleBroker("/topic", "/queue");

        // Designates the "/app" prefix for messages that are bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");

        // Enable user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoints for WebSocket connection
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*") // Configure according to your frontend domain
                .withSockJS(); // Enable SockJS fallback options

        // Additional endpoint without SockJS for modern browsers
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*");
    }
}
