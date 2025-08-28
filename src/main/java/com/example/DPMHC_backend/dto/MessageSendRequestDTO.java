package com.example.DPMHC_backend.dto;


import com.example.DPMHC_backend.model.Chat;
import com.example.DPMHC_backend.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSendRequestDTO {

    @NotNull(message = "Chat ID is required")
    private Long chatId;

    private String content;

    private Message.MessageType messageType = Message.MessageType.TEXT;

    private String mediaUrl;
    private String mediaType;
    private Long mediaSize;
    private String thumbnailUrl;

    private Long replyToId; // For replying to a message

    // Validation method
    public boolean isValid() {
        if (messageType == Message.MessageType.TEXT) {
            return content != null && !content.trim().isEmpty();
        } else if (messageType == Message.MessageType.EMOJI) {
            return content != null && !content.trim().isEmpty();
        } else {
            return mediaUrl != null && !mediaUrl.trim().isEmpty();
        }
    }
}