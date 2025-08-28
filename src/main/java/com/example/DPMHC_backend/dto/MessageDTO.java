package com.example.DPMHC_backend.dto;

import com.example.DPMHC_backend.model.Message;
import com.example.DPMHC_backend.model.MessageReaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDTO {

    private Long id;
    private Long chatId;
    private UserDTO sender;
    private String content;
    private Message.MessageType messageType;
    private String mediaUrl;
    private String mediaType;
    private Long mediaSize;
    private String thumbnailUrl;
    private Boolean isEdited;
    private Boolean isDeleted;
    private Boolean isPinned;
    private Long replyToId;
    private MessageDTO replyToMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // Additional fields for response
    private Map<String, List<UserDTO>> reactions; // emoji -> list of users who reacted
    private List<MessageReactionDTO> reactionsList;
    private Boolean isRead;
    private Boolean isDelivered;
    private Integer readByCount;

    // Constructor from entity
    public MessageDTO(Message message) {
        this.id = message.getId();
        this.chatId = message.getChat() != null ? message.getChat().getId() : null;
        this.content = message.getContent();
        this.messageType = message.getMessageType();
        this.mediaUrl = message.getMediaUrl();
        this.mediaType = message.getMediaType();
        this.mediaSize = message.getMediaSize();
        this.thumbnailUrl = message.getThumbnailUrl();
        this.isEdited = message.getIsEdited();
        this.isDeleted = message.getIsDeleted();
        this.isPinned = message.getIsPinned();
        this.replyToId = message.getReplyToId();
        this.createdAt = message.getCreatedAt();
        this.updatedAt = message.getUpdatedAt();
        this.deletedAt = message.getDeletedAt();

        // Convert reactions to grouped format
        if (message.getReactions() != null && !message.getReactions().isEmpty()) {
            this.reactions = message.getReactions().stream()
                    .collect(Collectors.groupingBy(
                            MessageReaction::getEmoji,
                            Collectors.mapping(reaction -> new UserDTO(reaction.getUser()), Collectors.toList())
                    ));
        }
    }

    // Static method for easy conversion
    public static MessageDTO fromEntity(Message message) {
        return new MessageDTO(message);
    }
}
