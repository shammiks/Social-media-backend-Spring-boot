package com.example.DPMHC_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {
    private Long id;
    private String content;
    private String imageUrl;
    private String videoUrl;
    private String pdfUrl;
    private boolean isPublic;
    private int likes;
    private boolean isLikedByCurrentUser; // NEW: Track if current user liked this post
    private int commentsCount; // NEW: Comments count
    private boolean isBookmarkedByCurrentUser; // NEW: Track if current user bookmarked this post
    private Date createdAt;
    private String username;
    private Long userId; // NEW: Useful for frontend
}