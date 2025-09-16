package com.example.DPMHC_backend.dto.projection;

import java.util.Date;

/**
 * Lightweight projection DTO for post feed listings
 * Reduces data transfer and memory usage by including only essential fields
 */
public interface PostFeedProjection {
    Long getId();
    String getContent();
    String getImageUrl();
    String getVideoUrl();
    String getPdfUrl();
    Boolean getIsPublic();
    Integer getLikesCount();
    Date getCreatedAt();
    
    // User fields
    String getUsername();
    Long getUserId();
    String getAvatar();
    String getProfileImageUrl();
}