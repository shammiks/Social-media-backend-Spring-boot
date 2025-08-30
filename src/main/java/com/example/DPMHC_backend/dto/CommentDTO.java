package com.example.DPMHC_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}