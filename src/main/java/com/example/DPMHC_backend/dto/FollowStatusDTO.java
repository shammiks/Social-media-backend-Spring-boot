package com.example.DPMHC_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowStatusDTO {
    private boolean isFollowing;
    private boolean following;      // Alternative field name
    private long followersCount;
    private String message;
}