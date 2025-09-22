package com.example.DPMHC_backend.dto;

import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPostDTO {
    
    private Long id;
    private String content;
    private String imageUrl;
    private String videoUrl;
    private String pdfUrl;
    private boolean isPublic;
    private boolean reported;
    private int likesCount;
    private int commentsCount;
    private Date createdAt;
    
    // User information
    private Long userId;
    private String username;
    private String userEmail;
    private String userAvatar;
    private boolean userBanned;
    private boolean userIsAdmin;
    
    // Warning information
    private int warningCount;
    private boolean hasWarnings;
    
    // Additional metadata for admin
    private Date lastWarningDate;
    private String lastWarningReason;
}