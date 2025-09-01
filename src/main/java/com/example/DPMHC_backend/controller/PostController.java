package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.PostDTO;
import com.example.DPMHC_backend.model.Post;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.PostService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.example.DPMHC_backend.service.NotificationService;
import com.example.DPMHC_backend.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final NotificationService notificationService;

    @PostMapping("/upload")
    public ResponseEntity<PostDTO> createPost(
            @RequestParam("content") String content,
            @RequestParam(value = "isPublic", required = false) Boolean isPublic,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "video", required = false) MultipartFile video,
            @RequestPart(value = "pdf", required = false) MultipartFile pdf,
            @AuthenticationPrincipal User user
    ) {
        boolean isPostPublic = isPublic != null ? isPublic : false;

        Post created = postService.createPost(
                content,
                image,
                video,
                pdf,
                isPostPublic,
                user.getEmail()
        );

        return ResponseEntity.ok(postService.mapToDTO(created, user.getEmail()));
    }

    @GetMapping
    public Page<PostDTO> getAllPosts(Pageable pageable, @AuthenticationPrincipal User user) {
        return postService.getAllPosts(pageable, user.getEmail());
    }

    @GetMapping("/me")
    public ResponseEntity<List<PostDTO>> getUserPosts(
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(
                postService.getUserPosts(user.getEmail(), user.getEmail())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDTO> getPost(@PathVariable Long id, Principal principal) {
        String userEmail = principal.getName();
        PostDTO post = postService.getPost(id, userEmail);
        return ResponseEntity.ok(post);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @RequestBody PostRequest request,
            @AuthenticationPrincipal User user
    ) {
        Post updated = new Post();
        updated.setContent(request.getContent());
        updated.setPdfUrl(request.getPdfUrl());
        updated.setImageUrl(request.getImageUrl());
        updated.setVideoUrl(request.getVideoUrl());

        if (request.getIsPublic() != null) {
            updated.setIsPublic(request.getIsPublic());
        }

        postService.updatePost(id, updated, user.getEmail());
        return ResponseEntity.ok("Post updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        postService.deletePost(id, user.getEmail());
        return ResponseEntity.ok("Post deleted successfully");
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<PostService.LikeResponse> toggleLike(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user
    ) {
        // Your existing like logic
        PostService.LikeResponse response = postService.toggleLike(postId, user.getEmail());

        // 🔥 NEW: Send notification if it's a new like
        try {
            // Get the post to find the owner
            Post post = postService.getPostById(postId); // You might need to add this method to PostService

            // Only send notification if:
            // 1. It's a new like (not unlike)
            // 2. User is not liking their own post
            if (response.isLiked() && !user.getId().equals(post.getUser().getId())) {
                notificationService.createNotification(
                        post.getUser().getId(),     // recipient (post owner)
                        user.getId(),               // actor (person who liked)
                        NotificationType.LIKE,
                        postId,                     // target (the post)
                        null                        // no additional message
                );
            }
        } catch (Exception e) {
            // Log the error but don't fail the like operation
            System.err.println("Failed to send like notification: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/{postId}/like-status")
    public ResponseEntity<Boolean> getLikeStatus(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user
    ) {
        boolean isLiked = postService.hasUserLikedPost(postId, user.getEmail());
        return ResponseEntity.ok(isLiked);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<PostDTO>> getPostsByUser(
            @PathVariable Long userId,
            Pageable pageable,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                postService.getPostsByUser(userId, pageable, currentUser.getEmail())
        );
    }

    @Data
    static class PostRequest {
        private String content;
        private String imageUrl;
        private String videoUrl;
        private Boolean isPublic;
        private String pdfUrl;
    }
}