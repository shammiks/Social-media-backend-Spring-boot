package com.example.DPMHC_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockUserRequestDTO {
    
    @NotNull(message = "User ID to block is required")
    private Long userId;
}
