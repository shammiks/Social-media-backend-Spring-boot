package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.CommentDTO;
import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.CommentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/{postId}")
    public ResponseEntity<CommentDTO> addComment(@PathVariable Long postId,
                                                 @RequestBody CommentRequest request,
                                                 @AuthenticationPrincipal User user) {
        Comment comment = commentService.addComment(postId, request.getContent(), user.getEmail());

        CommentDTO dto = CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().toString())
                .username(comment.getUser().getUsername())
                .build();

        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDTO> editComment(@PathVariable Long commentId,
                                                  @RequestBody CommentRequest request,
                                                  @AuthenticationPrincipal User user) {
        Comment comment = commentService.editComment(commentId, request.getContent(), user.getEmail());

        CommentDTO dto = CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().toString())
                .updatedAt(comment.getUpdatedAt() != null ? comment.getUpdatedAt().toString() : null)
                .edited(comment.getUpdatedAt() != null)
                .username(comment.getUser().getUsername())
                .userId(comment.getUser().getId())
                .postId(comment.getPost().getId())
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/posts/{postId}/comments")
    public Page<CommentDTO> getComments(
            @PathVariable Long postId,
            Pageable pageable) {

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending()
        );

        return commentService.getComments(postId, sortedPageable);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id, @AuthenticationPrincipal User user) {
        commentService.deleteComment(id, user.getEmail());
        return ResponseEntity.noContent().build();
    }


    @Data
    static class CommentRequest {
        private String content;
    }
}
