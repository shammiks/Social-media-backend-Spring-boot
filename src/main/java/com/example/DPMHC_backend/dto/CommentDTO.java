package com.example.DPMHC_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private Long id;
    private String content;
    private String createdAt;
    private String updatedAt;
    private boolean edited;
    private String username;
    private Long userId;
    private Long postId;
    private String avatar; // NEW: User avatar for frontend
    private String profileImageUrl; // NEW: User profile image for frontend

    // Reply functionality
    private Long parentCommentId;
    private List<CommentDTO> replies;
    private int replyCount;

    // Like functionality
    private int likeCount;
    private boolean likedByCurrentUser;
}