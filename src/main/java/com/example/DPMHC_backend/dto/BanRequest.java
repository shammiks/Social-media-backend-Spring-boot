package com.example.DPMHC_backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BanRequest {
    private Long userId;
    private String reason;
}