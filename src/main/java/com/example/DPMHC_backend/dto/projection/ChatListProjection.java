package com.example.DPMHC_backend.dto.projection;

import java.time.LocalDateTime;

/**
 * Lightweight projection DTO for chat listings
 * Includes only essential chat information for list views
 */
public interface ChatListProjection {
    Long getId();
    String getChatName();
    String getChatType();
    String getChatImageUrl();
    LocalDateTime getLastMessageAt();
    LocalDateTime getCreatedAt();
    
    // Last message preview
    String getLastMessageContent();
    String getLastMessageSender();
}