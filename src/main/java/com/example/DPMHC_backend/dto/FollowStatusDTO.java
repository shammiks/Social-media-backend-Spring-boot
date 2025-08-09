package com.example.DPMHC_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowStatusDTO {
    private boolean isFollowing;
    private long followersCount;
    private String message; // Optional field for additional info
}