package com.example.DPMHC_backend.service;

import com.example.DPMHC_backend.model.NotificationPriority;
import com.example.DPMHC_backend.model.NotificationType;
import com.example.DPMHC_backend.model.User;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class NotificationBuilder {
    private String recipientEmail;
    private User actor;
    private NotificationType type;
    private String customMessage;
    private String title;
    private Long entityId;
    private String entityType;
    private String actionUrl;
    private NotificationPriority priority;
    private Map<String, Object> metadata;
    private Date expiresAt;
    private String groupKey;

    @Builder.Default
    private boolean checkDuplicates = false;

    @Builder.Default
    private boolean generateContent = true;
}
