package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_user_read", columnList = "user_id, is_read"),
    @Index(name = "idx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who receives this notification
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User recipient;

    // The user who triggered this notification (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String message;

    // Reference to the related entity (post, comment, etc.)
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_type")
    private String entityType; // "POST", "COMMENT", "USER", etc.

    // URL to navigate when notification is clicked
    @Column(name = "action_url")
    private String actionUrl;

    // Additional data as JSON string for complex notifications
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Builder.Default
    @Column(name = "is_seen", nullable = false)
    private boolean isSeen = false; // For marking as seen in notification dropdown

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "read_at")
    private Date readAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Date expiresAt; // For temporary notifications

    // Priority level for notification ordering
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    // For grouping similar notifications
    @Column(name = "group_key")
    private String groupKey;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = new Date();
    }

    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }

    public void markAsSeen() {
        this.isSeen = true;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }
}
