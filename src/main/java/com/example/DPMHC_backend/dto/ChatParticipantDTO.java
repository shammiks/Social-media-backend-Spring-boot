package com.example.DPMHC_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatParticipantDTO {

    private Long id;
    private UserDTO user;
    private String role;
    private Boolean isMuted;
    private Boolean isAdmin;
    private String joinedAt;
    private String lastSeenAt;
    private Boolean isActive;
    private Boolean isOnline;
}