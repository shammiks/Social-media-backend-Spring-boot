package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.Chat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatDTO {

    private Long id;
    private String chatName;
    private Chat.ChatType chatType;
    private String chatImageUrl;
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
    private Boolean isActive;

    // Additional fields for response
    private List<ChatParticipantDTO> participants;
    private MessageDTO lastMessage;
    private Integer unreadCount;
    private Boolean isMuted;
    private Boolean isAdmin;
    private Boolean isOwner;

    // Constructor from entity
    public ChatDTO(Chat chat) {
        this.id = chat.getId();
        this.chatName = chat.getChatName();
        this.chatType = chat.getChatType();
        this.chatImageUrl = chat.getChatImageUrl();
        this.description = chat.getDescription();
        this.createdBy = chat.getCreatedBy();
        this.createdAt = chat.getCreatedAt();
        this.updatedAt = chat.getUpdatedAt();
        this.lastMessageAt = chat.getLastMessageAt();
        this.isActive = chat.getIsActive();
    }

    // Static method for easy conversion
    public static ChatDTO fromEntity(Chat chat) {
        return new ChatDTO(chat);
    }
}
