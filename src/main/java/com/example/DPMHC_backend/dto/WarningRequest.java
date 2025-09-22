package com.example.DPMHC_backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarningRequest {
    private Long postId;
    private Long userId;
    private String reason;
    private String warningMessage;
}