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

import java.util.List;

// ==================== Chat Creation Request ====================
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCreateRequestDTO {

    @Size(max = 100, message = "Chat name cannot exceed 100 characters")
    private String chatName;

    @NotNull(message = "Chat type is required")
    private Chat.ChatType chatType;

    private String chatImageUrl;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotEmpty(message = "At least one participant is required")
    private List<Long> participantIds;
}