package com.example.DPMHC_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type")
    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "media_size")
    private Long mediaSize;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "is_deleted" , nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Relationships
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageReaction> reactions = new ArrayList<>();

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageReadStatus> readStatuses = new ArrayList<>();

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        AUDIO,
        DOCUMENT,
        EMOJI,
        SYSTEM  // For system messages like "User joined chat"
    }

    // Helper methods
    public void addReaction(MessageReaction reaction) {
        reactions.add(reaction);
        reaction.setMessage(this);
    }

    public void removeReaction(MessageReaction reaction) {
        reactions.remove(reaction);
        reaction.setMessage(null);
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.content = "This message was deleted";
        this.mediaUrl = null;
    }

    public boolean isMediaMessage() {
        return messageType != MessageType.TEXT && messageType != MessageType.EMOJI;
    }
}
