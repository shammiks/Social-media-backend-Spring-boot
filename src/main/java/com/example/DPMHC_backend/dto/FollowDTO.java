package com.example.DPMHC_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// FollowDTO.java
@Data
@Builder
public class FollowDTO {
    private Long id;
    private Long followerId;
    private String followerUsername;
    private Long followeeId;
    private String followeeUsername;
    private LocalDateTime followedAt;
}

