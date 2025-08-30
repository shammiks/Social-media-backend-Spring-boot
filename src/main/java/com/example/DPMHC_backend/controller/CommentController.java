package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.CommentDTO;
import com.example.DPMHC_backend.model.Comment;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.CommentService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/{commentId}/reply")
    public ResponseEntity<CommentDTO> addReply(@PathVariable Long commentId,
                                               @RequestBody CommentRequest request,
                                               @AuthenticationPrincipal User user) {
        Comment reply = commentService.addReply(commentId, request.getContent(), user.getEmail());

        CommentDTO dto = CommentDTO.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt().toString())
                .username(reply.getUser().getUsername())
                .userId(reply.getUser().getId())
                .postId(reply.getPost().getId())
                .parentCommentId(reply.getParentComment().getId())
                .likeCount(0)
                .likedByCurrentUser(false)
                .replyCount(0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<LikeResponse> toggleCommentLike(@PathVariable Long commentId,
                                                          @AuthenticationPrincipal User user) {
        boolean liked = commentService.toggleCommentLike(commentId, user.getEmail());

        LikeResponse response = new LikeResponse(liked, liked ? "Comment liked" : "Comment unliked");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<CommentDTO>> getReplies(@PathVariable Long commentId,
                                                       @AuthenticationPrincipal User user) {
        String userEmail = user != null ? user.getEmail() : null;
        List<CommentDTO> replies = commentService.getReplies(commentId, userEmail);
        return ResponseEntity.ok(replies);
    }

    @GetMapping("/posts/{postId}/comments")
    public Page<CommentDTO> getComments(
            @PathVariable Long postId,
            Pageable pageable,
            @AuthenticationPrincipal User user) {

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("createdAt").descending()
        );

        String userEmail = user != null ? user.getEmail() : null;
        return commentService.getCommentsWithUser(postId, sortedPageable, userEmail);
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

    @Data
    @AllArgsConstructor
    static class LikeResponse {
        private boolean liked;
        private String message;
    }
}
