package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participants", indexes = {
    @Index(name = "idx_chat_participant_user_active", columnList = "user_id, is_active"),
    @Index(name = "idx_chat_participant_chat_active", columnList = "chat_id, is_active"),
    @Index(name = "idx_chat_participant_role", columnList = "chat_id, role, is_active"),
    @Index(name = "idx_chat_participant_last_seen", columnList = "user_id, last_seen_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "is_muted")
    private Boolean isMuted = false;

    @Column(name = "is_admin")
    private Boolean isAdmin = false;

    @CreationTimestamp
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public enum ParticipantRole {
        OWNER,      // Creator of the chat
        ADMIN,      // Can add/remove users, change settings
        MEMBER      // Regular participant
    }

    // Helper methods
    public boolean isOwner() {
        return role == ParticipantRole.OWNER;
    }

    public boolean isAdminOrOwner() {
        return role == ParticipantRole.ADMIN || role == ParticipantRole.OWNER;
    }

    public void leaveChat() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    public void markAsRead(Long messageId) {
        this.lastReadMessageId = messageId;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void updateLastSeen() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
