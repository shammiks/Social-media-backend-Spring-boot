package com.example.DPMHC_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatUpdateRequestDTO {

    @Size(max = 100, message = "Chat name cannot exceed 100 characters")
    private String chatName;

    private String chatImageUrl;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
