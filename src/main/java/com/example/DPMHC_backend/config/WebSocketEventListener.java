package com.example.DPMHC_backend.config;

import com.example.DPMHC_backend.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketService webSocketService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Authentication auth = (Authentication) headerAccessor.getUser();
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof com.example.DPMHC_backend.model.User) {
                com.example.DPMHC_backend.model.User user = (com.example.DPMHC_backend.model.User) principal;
                Long userId = user.getId();
                log.info("WebSocket session connected: {} for user: {}", sessionId, userId);
                
                // CRITICAL: Register the user session immediately upon connection
                webSocketService.registerUserSession(userId, sessionId);
            } else {
                log.warn("WebSocket session connected with unknown principal type: {}", principal.getClass());
            }
        } else {
            log.warn("WebSocket session connected without authentication: {}", sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Unregister user session
        webSocketService.unregisterUserSession(sessionId);

        log.info("WebSocket session disconnected: {}", sessionId);
    }
}