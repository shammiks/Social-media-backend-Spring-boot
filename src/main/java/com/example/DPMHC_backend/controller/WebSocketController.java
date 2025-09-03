package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.service.WebSocketService;
import com.example.DPMHC_backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final WebSocketService webSocketService;

    /**
     * Handle typing indicator
     */
    @MessageMapping("/chat/{chatId}/typing")
    public void handleTyping(
            @DestinationVariable Long chatId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        log.info("=== WEBSOCKET TYPING MESSAGE RECEIVED ===");
        log.info("Chat ID: {}", chatId);
        log.info("Payload: {}", payload);
        log.info("Principal: {}", principal);

        try {
            Long userId = getUserIdFromPrincipal(principal);
            Boolean isTyping = (Boolean) payload.get("isTyping");

            log.info("User ID: {}, Is Typing: {}", userId, isTyping);

            if (isTyping == null) {
                isTyping = true;
            }

            webSocketService.broadcastTypingIndicator(chatId, userId, isTyping);
            log.info("Successfully broadcasted typing indicator");

        } catch (Exception e) {
            log.error("Error handling typing indicator", e);
        }
    }

    /**
     * Handle user joining a chat (for online presence)
     */
    @MessageMapping("/chat/{chatId}/join")
    public void handleJoinChat(
            @DestinationVariable Long chatId,
            Principal principal) {

        log.info("=== WEBSOCKET JOIN MESSAGE RECEIVED ===");
        log.info("Chat ID: {}", chatId);
        log.info("Principal: {}", principal);

        try {
            Long userId = getUserIdFromPrincipal(principal);
            log.info("User {} joined chat {}", userId, chatId);

            // Could implement additional logic here like updating last seen, etc.

        } catch (Exception e) {
            log.error("Error handling chat join", e);
        }
    }

    /**
     * Handle user leaving a chat
     */
    @MessageMapping("/chat/{chatId}/leave")
    public void handleLeaveChat(
            @DestinationVariable Long chatId,
            Principal principal) {

        log.info("=== WEBSOCKET LEAVE MESSAGE RECEIVED ===");
        log.info("Chat ID: {}", chatId);
        log.info("Principal: {}", principal);

        try {
            Long userId = getUserIdFromPrincipal(principal);
            log.info("User {} left chat {}", userId, chatId);

            // Stop typing indicator when leaving
            webSocketService.broadcastTypingIndicator(chatId, userId, false);

        } catch (Exception e) {
            log.error("Error handling chat leave", e);
        }
    }

    /**
     * Handle heartbeat/keepalive messages
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(Principal principal) {
        try {
            Long userId = getUserIdFromPrincipal(principal);
            log.trace("Heartbeat from user: {}", userId);

            // Update user's online status or last seen timestamp
            // This can be used to maintain accurate online status

        } catch (Exception e) {
            log.error("Error handling heartbeat", e);
        }
    }

    /**
     * Handle generic client events
     */
    @MessageMapping("/event")
    public void handleGenericEvent(
            @Payload Map<String, Object> payload,
            Principal principal) {

        try {
            Long userId = getUserIdFromPrincipal(principal);
            String eventType = (String) payload.get("type");

            log.debug("Generic event from user {}: {}", userId, eventType);

            switch (eventType) {
                case "USER_ACTIVE":
                    // Handle user becoming active
                    break;
                case "USER_AWAY":
                    // Handle user becoming away
                    break;
                case "FOCUS_CHAT":
                    // Handle user focusing on a specific chat
                    Long chatId = Long.valueOf(payload.get("chatId").toString());
                    // Could mark messages as read, update presence, etc.
                    break;
                default:
                    log.debug("Unknown event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error handling generic event", e);
        }
    }

    /**
     * Handle connection establishment
     */
    @MessageMapping("/connect")
    public void handleConnect(
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            Long userId = getUserIdFromPrincipal(principal);
            String sessionId = headerAccessor.getSessionId();

            webSocketService.registerUserSession(userId, sessionId);

            log.info("WebSocket connection established for user: {} with session: {}", userId, sessionId);

        } catch (Exception e) {
            log.error("Error handling WebSocket connect", e);
        }
    }

    // Helper method to extract user ID consistently from Principal
    private Long getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Authentication required");
        }

        try {
            // If principal is Authentication (when JWT is properly configured)
            if (principal instanceof Authentication) {
                Authentication auth = (Authentication) principal;
                if (auth.getPrincipal() instanceof User) {
                    User user = (User) auth.getPrincipal();
                    return user.getId();
                }
            }

            // Fallback to string-based principal name
            return Long.valueOf(principal.getName());

        } catch (Exception e) {
            log.error("Error extracting user ID from principal: {}", principal.getClass().getName(), e);
            throw new RuntimeException("Invalid authentication principal");
        }
    }
}