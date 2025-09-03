package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.service.MessageService;
import com.example.DPMHC_backend.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;

    /**
     * Send a new message
     */
    @PostMapping
    public ResponseEntity<MessageDTO> sendMessage(
            HttpServletRequest httpRequest,
            @RequestBody String rawBody,
            Authentication authentication) {

        log.info("=== MESSAGE SEND ENDPOINT HIT ===");
        log.info("Authentication: {}", authentication);
        log.info("User Principal: {}", authentication != null ? authentication.getPrincipal() : "NULL");
        log.info("Request URL: {}", httpRequest.getRequestURL());
        log.info("Request Method: {}", httpRequest.getMethod());
        
        // Debug the raw request
        log.info("DEBUG: Content-Type: {}", httpRequest.getContentType());
        log.info("DEBUG: Raw request body: {}", rawBody);

        // Manual deserialization to debug
        ObjectMapper mapper = new ObjectMapper();
        MessageSendRequestDTO request;

        try {
            request = mapper.readValue(rawBody, MessageSendRequestDTO.class);
            log.info("DEBUG: Deserialized DTO: {}", request);
            log.info("DEBUG: After deserialization - MediaUrl: {}, MediaType: {}, MediaSize: {}",
                    request.getMediaUrl(), request.getMediaType(), request.getMediaSize());
        } catch (Exception e) {
            log.error("DEBUG: Deserialization failed: ", e);
            return ResponseEntity.badRequest().body(null);
        }

        Long userId = getUserIdFromAuth(authentication);
        MessageDTO message = messageService.sendMessage(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    /**
     * Get messages in a chat with pagination
     */
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Page<MessageDTO>> getChatMessages(
            @PathVariable Long chatId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MessageDTO> messages = messageService.getChatMessages(chatId, userId, pageable);

        return ResponseEntity.ok(messages);
    }

    /**
     * Get specific message by ID
     */
    @GetMapping("/{messageId}")
    public ResponseEntity<MessageDTO> getMessageById(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageDTO message = messageService.getMessageById(messageId, userId);

        return ResponseEntity.ok(message);
    }

    /**
     * Edit a message
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDTO> editMessage(
            @PathVariable Long messageId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        String newContent = request.get("content");

        if (newContent == null || newContent.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        MessageDTO updatedMessage = messageService.editMessage(messageId, newContent, userId);
        return ResponseEntity.ok(updatedMessage);
    }

    /**
     * Delete a message
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, String>> deleteMessage(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        messageService.deleteMessage(messageId, userId);

        return ResponseEntity.ok(Map.of("message", "Message deleted successfully"));
    }

    /**
     * Pin/Unpin a message
     */
    @PostMapping("/{messageId}/pin")
    public ResponseEntity<MessageDTO> togglePinMessage(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageDTO pinnedMessage = messageService.togglePinMessage(messageId, userId);

        return ResponseEntity.ok(pinnedMessage);
    }

    /**
     * Add emoji reaction to message
     */
    @PostMapping("/{messageId}/react")
    public ResponseEntity<MessageDTO> addReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody ReactionRequestDTO request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        MessageDTO message = messageService.addReaction(messageId, request, userId);

        return ResponseEntity.ok(message);
    }

    /**
     * Remove emoji reaction from message
     */
    @DeleteMapping("/{messageId}/react")
    public ResponseEntity<MessageDTO> removeReaction(
            @PathVariable Long messageId,
            @RequestParam String emoji,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ReactionRequestDTO request = new ReactionRequestDTO(emoji);
        MessageDTO message = messageService.addReaction(messageId, request, userId); // This toggles

        return ResponseEntity.ok(message);
    }

    /**
     * Mark message as read
     */
    @PostMapping("/{messageId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long messageId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        messageService.markAsRead(messageId, userId);

        return ResponseEntity.ok(Map.of("message", "Message marked as read"));
    }

    /**
     * Mark all messages in chat as read
     */
    @PostMapping("/chat/{chatId}/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        messageService.markAllAsRead(chatId, userId);

        return ResponseEntity.ok(Map.of("message", "All messages marked as read"));
    }

    /**
     * Search messages in a chat
     */
    @GetMapping("/chat/{chatId}/search")
    public ResponseEntity<Page<MessageDTO>> searchMessages(
            @PathVariable Long chatId,
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MessageDTO> messages = messageService.searchMessages(chatId, q, userId, pageable);

        return ResponseEntity.ok(messages);
    }

    /**
     * Get pinned messages in a chat
     */
    @GetMapping("/chat/{chatId}/pinned")
    public ResponseEntity<List<MessageDTO>> getPinnedMessages(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<MessageDTO> pinnedMessages = messageService.getPinnedMessages(chatId, userId);

        return ResponseEntity.ok(pinnedMessages);
    }

    /**
     * Get media messages in a chat
     */
    @GetMapping("/chat/{chatId}/media")
    public ResponseEntity<Page<MessageDTO>> getMediaMessages(
            @PathVariable Long chatId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<MessageDTO> mediaMessages = messageService.getMediaMessages(chatId, userId, pageable);

        return ResponseEntity.ok(mediaMessages);
    }

    // Error handling
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        log.error("Message controller error: ", e);
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("Unexpected error in message controller: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }

    // Helper method to extract user ID from authentication - CONSISTENT WITH ChatController
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Authentication required");
        }

        try {
            // Try to get User object first (from JWT token) - CONSISTENT APPROACH
            User user = (User) authentication.getPrincipal();
            return user.getId();
        } catch (ClassCastException e) {
            // Fallback to string-based principal
            try {
                return Long.valueOf(authentication.getName());
            } catch (NumberFormatException nfe) {
                log.error("Could not extract user ID from authentication", e);
                throw new RuntimeException("Invalid authentication principal");
            }
        }
    }
}