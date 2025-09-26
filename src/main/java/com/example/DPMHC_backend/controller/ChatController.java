package com.example.DPMHC_backend.controller;


import com.example.DPMHC_backend.dto.*;
import com.example.DPMHC_backend.dto.AddParticipantsRequestDTO;
import com.example.DPMHC_backend.service.ChatService;
import com.example.DPMHC_backend.service.UserBlockService;
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
import com.example.DPMHC_backend.model.User; // Add this import

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserBlockService userBlockService;

    /**
     * Create a new chat
     */
    @PostMapping
    public ResponseEntity<ChatDTO> createChat(
            @Valid @RequestBody ChatCreateRequestDTO request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ChatDTO chat = chatService.createChat(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(chat);
    }

    /**
     * Get user's chats with pagination
     */
    @GetMapping
    public ResponseEntity<Page<ChatDTO>> getUserChats(
            @PageableDefault(size = 20, sort = "lastMessageAt") Pageable pageable,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        Page<ChatDTO> chats = chatService.getUserChats(userId, pageable);

        return ResponseEntity.ok(chats);
    }

    /**
     * Get user's chats as list (without pagination)
     */
    /**
     * Get user's chats as list (without pagination)
     */
    @GetMapping("/list")
    public ResponseEntity<List<ChatDTO>> getUserChatsList(Authentication authentication) {
        log.info("=== CHAT CONTROLLER /list ENDPOINT DEBUG ===");
        log.info("Authentication object: {}", authentication);
        log.info("Authentication class: {}", authentication != null ? authentication.getClass().getName() : "null");
        log.info("Principal: {}", authentication != null ? authentication.getPrincipal() : "null");
        log.info("Principal class: {}", authentication != null && authentication.getPrincipal() != null ?
                authentication.getPrincipal().getClass().getName() : "null");
        log.info("Authorities: {}", authentication != null ? authentication.getAuthorities() : "null");
        log.info("Is authenticated: {}", authentication != null ? authentication.isAuthenticated() : "false");

        try {
            Long userId = getUserIdFromAuth(authentication);
            log.info("Successfully extracted user ID: {}", userId);

            List<ChatDTO> chats = chatService.getUserChatsList(userId);
            log.info("Successfully loaded {} chats", chats.size());

            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            log.error("Error in getUserChatsList: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get specific chat by ID
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatDTO> getChatById(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ChatDTO chat = chatService.getChatById(chatId, userId);

        return ResponseEntity.ok(chat);
    }

    /**
     * Update chat settings
     */
    @PutMapping("/{chatId}")
    public ResponseEntity<ChatDTO> updateChat(
            @PathVariable Long chatId,
            @Valid @RequestBody ChatUpdateRequestDTO request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ChatDTO updatedChat = chatService.updateChat(chatId, request, userId);

        return ResponseEntity.ok(updatedChat);
    }

    /**
     * Add participants to chat
     */
    @PostMapping("/{chatId}/participants")
    public ResponseEntity<ChatDTO> addParticipants(
            @PathVariable Long chatId,
            @Valid @RequestBody AddParticipantsRequestDTO request,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        ChatDTO updatedChat = chatService.addParticipants(chatId, request, userId);

        return ResponseEntity.ok(updatedChat);
    }

    /**
     * Remove participant from chat
     */
    @DeleteMapping("/{chatId}/participants/{participantId}")
    public ResponseEntity<Map<String, String>> removeParticipant(
            @PathVariable Long chatId,
            @PathVariable Long participantId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        chatService.removeParticipant(chatId, participantId, userId);

        return ResponseEntity.ok(Map.of("message", "Participant removed successfully"));
    }

    /**
     * Leave chat (remove yourself)
     */
    @PostMapping("/{chatId}/leave")
    public ResponseEntity<Map<String, String>> leaveChat(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        chatService.removeParticipant(chatId, userId, userId);

        return ResponseEntity.ok(Map.of("message", "Left chat successfully"));
    }

    /**
     * Delete entire chat
     */
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Map<String, String>> deleteChat(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        chatService.deleteChat(chatId, userId);

        return ResponseEntity.ok(Map.of("message", "Chat deleted successfully"));
    }

    /**
     * Search chats by name or participant
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChatDTO>> searchChats(
            @RequestParam String q,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<ChatDTO> chats = chatService.searchChats(userId, q);

        return ResponseEntity.ok(chats);
    }

    /**
     * Get chat participants
     */
    @GetMapping("/{chatId}/participants")
    public ResponseEntity<List<ChatParticipantDTO>> getChatParticipants(
            @PathVariable Long chatId,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        List<ChatParticipantDTO> participants = chatService.getChatParticipants(chatId, userId);

        return ResponseEntity.ok(participants);
    }

    /**
     * Block a user from the chat context
     */
    @PostMapping("/{chatId}/block/{userId}")
    public ResponseEntity<Map<String, String>> blockUserInChat(
            @PathVariable Long chatId,
            @PathVariable Long userId,
            Authentication authentication) {

        Long blockerId = getUserIdFromAuth(authentication);
        
        // Verify the chat exists and users are participants
        ChatDTO chat = chatService.getChatById(chatId, blockerId);
        if (chat.getChatType() != com.example.DPMHC_backend.model.Chat.ChatType.PRIVATE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Can only block users in private chats"));
        }

        try {
            UserBlockDTO block = userBlockService.blockUser(blockerId, userId);
            return ResponseEntity.ok(Map.of(
                    "message", "User blocked successfully",
                    "blockedAt", block.getBlockedAt().toString()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Unblock a user from the chat context
     */
    @DeleteMapping("/{chatId}/block/{userId}")
    public ResponseEntity<Map<String, String>> unblockUserInChat(
            @PathVariable Long chatId,
            @PathVariable Long userId,
            Authentication authentication) {

        Long blockerId = getUserIdFromAuth(authentication);
        
        try {
            userBlockService.unblockUser(blockerId, userId);
            return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if a user is blocked in chat context
     */
    @GetMapping("/{chatId}/block-status/{userId}")
    public ResponseEntity<Map<String, Object>> getBlockStatus(
            @PathVariable Long chatId,
            @PathVariable Long userId,
            Authentication authentication) {

        Long currentUserId = getUserIdFromAuth(authentication);
        
        boolean currentUserBlockedOther = userBlockService.isSpecificUserBlocked(currentUserId, userId);
        boolean otherUserBlockedCurrent = userBlockService.isSpecificUserBlocked(userId, currentUserId);
        boolean areBlocked = userBlockService.areUsersBlocked(currentUserId, userId);
        
        // Determine block status and who initiated it
        String blockStatus = "none";
        if (currentUserBlockedOther && otherUserBlockedCurrent) {
            blockStatus = "mutual";
        } else if (currentUserBlockedOther) {
            blockStatus = "i_blocked_them";
        } else if (otherUserBlockedCurrent) {
            blockStatus = "they_blocked_me";
        }
        
        return ResponseEntity.ok(Map.of(
                "isBlocked", currentUserBlockedOther,
                "areBlocked", areBlocked,
                "canSendMessage", !areBlocked,
                "blockStatus", blockStatus,
                "iBlockedThem", currentUserBlockedOther,
                "theyBlockedMe", otherUserBlockedCurrent
        ));
    }

    // Error handling
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        log.error("Chat controller error: ", e);
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("Unexpected error in chat controller: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }

    // Helper method to extract user ID from authentication
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Authentication required");
        }

        try {
            // Get the User object from the principal (set in JwtAuthenticationFilter)
            User user = (User) authentication.getPrincipal();
            return user.getId();
        } catch (ClassCastException e) {
            log.error("Expected User object as principal, but got: {}",
                    authentication.getPrincipal().getClass().getName());
            throw new RuntimeException("Invalid authentication principal");
        }
    }
}
