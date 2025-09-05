package com.example.DPMHC_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// FollowDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowDTO {
    private Long id;
    private Long followerId;
    private String followerUsername;
    private String followerAvatar;
    private String followerProfilePicture;
    private Long followeeId;
    private String followeeUsername;
    private String followeeAvatar;
    private String followeeProfilePicture;
    private LocalDateTime followedAt;
}

