package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.NotificationPriority;
import com.example.DPMHC_backend.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private boolean isRead;
    private boolean isSeen;
    private Date createdAt;
    private Date readAt;
    private String actionUrl;
    private Long entityId;
    private String entityType;
    private String groupKey;

    // Actor information (who triggered the notification)
    private UserSummaryDTO actor;

    // Additional metadata for complex notifications
    private String metadata;

    // Time-related fields
    private String timeAgo; // "2 minutes ago", "1 hour ago", etc.
    private Date expiresAt;
    private boolean isExpired;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummaryDTO {
        private Long id;
        private String username;
        private String avatar;
    }
}
