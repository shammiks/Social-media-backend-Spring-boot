package com.example.DPMHC_backend.dto;
import com.example.DPMHC_backend.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSendRequestDTO {

    @NotNull(message = "Chat ID is required")
    @JsonProperty("chatId")
    private Long chatId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("messageType")
    private Message.MessageType messageType = Message.MessageType.TEXT;

    @JsonProperty("mediaUrl")
    private String mediaUrl;
    @JsonProperty("mediaType")
    private String mediaType;
    @JsonProperty("mediaSize")
    private Long mediaSize;
    @JsonProperty("thumbnailUrl")
    private String thumbnailUrl;

    @JsonProperty("replyToId")
    private Long replyToId; // For replying to a message

    // Validation method
    public boolean isValid() {
        if (messageType == Message.MessageType.TEXT || messageType == Message.MessageType.EMOJI) {
            return content != null && !content.trim().isEmpty();
        }
        // Accept media messages if mediaUrl is present, regardless of content
        if (messageType == Message.MessageType.IMAGE || messageType == Message.MessageType.VIDEO || messageType == Message.MessageType.AUDIO || messageType == Message.MessageType.DOCUMENT) {
            return mediaUrl != null && !mediaUrl.trim().isEmpty();
        }
        // Fallback for other types
        return mediaUrl != null && !mediaUrl.trim().isEmpty();
    }
}