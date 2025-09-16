package com.example.DPMHC_backend.dto.projection;

import java.time.LocalDateTime;

/**
 * Lightweight projection DTO for message listings
 * Reduces payload size for chat message feeds
 */
public interface MessageProjection {
    Long getId();
    String getContent();
    String getMessageType();
    String getMediaUrl();
    String getThumbnailUrl();
    Boolean getIsEdited();
    Boolean getIsDeleted();
    LocalDateTime getCreatedAt();
    
    // Sender info
    Long getSenderId();
    String getSenderUsername();
    String getSenderAvatar();
    
    // Reply info
    Long getReplyToId();
}